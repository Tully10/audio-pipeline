import json
import re
import asyncio
from concurrent.futures import ThreadPoolExecutor
import boto3
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from db.models import Session as SessionModel, Word, Entity, ActionItem
import config
from pipeline import job_queue
from pipeline.mindmap import extract_mermaid

_bedrock_last_call: dict = {"status": "never"}
_executor = ThreadPoolExecutor(max_workers=1)


def get_bedrock_status() -> str:
    return _bedrock_last_call["status"]


def _bedrock(prompt: str) -> str:
    client = boto3.client("bedrock-runtime", region_name=config.AWS_DEFAULT_REGION)
    resp = client.invoke_model(
        modelId=config.BEDROCK_MODEL_ID,
        body=json.dumps({
            "anthropic_version": "bedrock-2023-05-31",
            "max_tokens": 2048,
            "messages": [{"role": "user", "content": prompt}],
        })
    )
    return json.loads(resp["body"].read())["content"][0]["text"]


def _extract_json(text: str) -> dict:
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        m = re.search(r'\{.*\}', text, re.DOTALL)
        try:
            return json.loads(m.group()) if m else {}
        except Exception:
            return {}


def _process_sync(full_text: str) -> dict:
    """All Bedrock calls in one thread so the event loop is never blocked."""
    entities = _extract_json(
        _bedrock(f'Extract named entities. Return only JSON: {{"entities": [{{"name":"...", "type":"PERSON|ORG|PLACE|PROJECT"}}]}}.\nTranscript:\n{full_text}')
    ).get("entities", [])

    action_items = _extract_json(
        _bedrock(f'Extract action items and commitments. Return only JSON: {{"action_items":["..."]}}.\nTranscript:\n{full_text}')
    ).get("action_items", [])

    summary = _bedrock(
        f"Write a 3-sentence summary. Be specific about what was discussed and decided. Return only the summary text, no preamble.\nTranscript:\n{full_text}"
    ).strip()

    mindmap_resp = _bedrock(
        f"Create a Mermaid mindmap of the main topics discussed. Return only the Mermaid code block.\nTranscript:\n{full_text}"
    )

    sentiment = {}
    if len(full_text.strip()) >= 50:
        sentiment = _extract_json(
            _bedrock(
                f'Analyze conversation tone. Return only JSON: {{"sentiment":"positive|negative|neutral|mixed","tone":"professional|casual|tense|enthusiastic|concerned","energy":"high|medium|low","key_moments":["moment description"]}}\nTranscript:\n{full_text[:3000]}'
            )
        )

    return {
        "entities": entities,
        "action_items": action_items,
        "summary": summary,
        "mindmap_resp": mindmap_resp,
        "sentiment": sentiment,
    }


async def process_session(session_id: str, db: AsyncSession):
    result = await db.execute(select(SessionModel).where(SessionModel.id == session_id))
    session = result.scalar_one_or_none()
    if not session:
        return

    words_result = await db.execute(
        select(Word).where(Word.session_id == session_id).order_by(Word.word_index)
    )
    words = words_result.scalars().all()
    full_text = " ".join(w.corrected_text or w.word for w in words)

    if len(full_text.strip()) < 20:
        session.status = "complete"
        await db.commit()
        return

    try:
        loop = asyncio.get_event_loop()
        data = await loop.run_in_executor(_executor, _process_sync, full_text)
        _bedrock_last_call["status"] = "ok"

        for e in data["entities"]:
            db.add(Entity(
                session_id=session_id,
                name=e.get("name", ""),
                entity_type=e.get("type", "PERSON"),
            ))

        people = [e.get("name", "") for e in data["entities"] if e.get("type") == "PERSON"]
        session.people_json = json.dumps(people)

        for item in data["action_items"]:
            db.add(ActionItem(session_id=session_id, text=item))

        session.summary = data["summary"]
        session.sentiment = json.dumps(data["sentiment"]) if data["sentiment"] else None
        session.status = "complete"
        await db.flush()

        mindmap_code = extract_mermaid(data["mindmap_resp"])
        await db.commit()
        await job_queue.put(("vault", session_id, mindmap_code))

    except Exception:
        _bedrock_last_call["status"] = "error"
        session.status = "error"
        await db.commit()
        raise
