import json
import boto3
from datetime import datetime
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from db.models import Session as SessionModel, Word, Entity, ActionItem
import config
from pipeline import job_queue
from pipeline.mindmap import extract_mermaid

_bedrock_last_call: dict = {"status": "never", "at": None}


def get_bedrock_status() -> str:
    return _bedrock_last_call["status"]


def _call_bedrock(prompt: str) -> str:
    client = boto3.client("bedrock-runtime", region_name=config.AWS_DEFAULT_REGION)
    body = json.dumps({
        "anthropic_version": "bedrock-2023-05-31",
        "max_tokens": 2048,
        "messages": [{"role": "user", "content": prompt}]
    })
    resp = client.invoke_model(modelId=config.BEDROCK_MODEL_ID, body=body)
    result = json.loads(resp["body"].read())
    return result["content"][0]["text"]


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
        # Entity extraction
        entity_resp = _call_bedrock(
            f'Given this transcript, extract all named entities. Return only valid JSON: {{"entities": [{{"name": "...", "type": "PERSON|ORG|PLACE|PROJECT"}}]}}. Transcript:\n{full_text}'
        )
        _bedrock_last_call["status"] = "ok"
        try:
            entities_data = json.loads(entity_resp).get("entities", [])
        except json.JSONDecodeError:
            import re
            m = re.search(r'\{.*\}', entity_resp, re.DOTALL)
            entities_data = json.loads(m.group()).get("entities", []) if m else []

        for e in entities_data:
            db.add(Entity(session_id=session_id, name=e["name"], entity_type=e.get("type", "PERSON")))

        people = [e["name"] for e in entities_data if e.get("type") == "PERSON"]
        session.people_json = json.dumps(people)

        # Action items
        action_resp = _call_bedrock(
            f'Extract concrete action items and commitments from this transcript. Return only valid JSON: {{"action_items": ["..."]}}.  Transcript:\n{full_text}'
        )
        try:
            action_data = json.loads(action_resp).get("action_items", [])
        except json.JSONDecodeError:
            action_data = []

        for item in action_data:
            db.add(ActionItem(session_id=session_id, text=item))

        # Summary
        summary = _call_bedrock(
            f"Write a 3-sentence summary of this conversation. Be specific about what was discussed and decided. Return only the summary text, no preamble. Transcript:\n{full_text}"
        )
        session.summary = summary.strip()

        # Mind map
        mindmap_resp = _call_bedrock(
            f"Create a Mermaid diagram (mindmap syntax) showing the main topics and subtopics discussed. Return only the Mermaid code block. Transcript:\n{full_text}"
        )
        session.status = "complete"
        await db.flush()

        mindmap_code = extract_mermaid(mindmap_resp)
        await db.commit()
        await job_queue.put(("vault", session_id, mindmap_code))

    except Exception as e:
        _bedrock_last_call["status"] = "error"
        session.status = "error"
        await db.commit()
        raise
