import json
from fastapi import APIRouter, Depends, Header, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import text
from db.database import get_db
from models.schemas import AskRequest, AskResponse, AskSource
import boto3
import config

router = APIRouter()


@router.post("/ask", response_model=AskResponse)
async def ask(
    body: AskRequest,
    db: AsyncSession = Depends(get_db),
    x_api_key: str = Header(default=""),
):
    if x_api_key != config.API_KEY:
        raise HTTPException(status_code=401, detail="Invalid API key")

    result = await db.execute(
        text("""
            SELECT snippet(fts_transcripts, 0, '', '', '...', 40) as quote,
                   session_id, start_s
            FROM fts_transcripts
            WHERE fts_transcripts MATCH :q
            LIMIT 5
        """),
        {"q": body.q},
    )
    rows = result.fetchall()

    if not rows:
        return AskResponse(answer="No relevant transcripts found.", sources=[])

    context = "\n\n".join(
        f"[Session {r.session_id[:8]} @ {r.start_s:.1f}s]: {r.quote}"
        for r in rows
    )

    prompt = (
        f"Answer this question based on the transcript excerpts below. "
        f"Cite the session ID for each claim.\n\nQuestion: {body.q}\n\nExcerpts:\n{context}"
    )

    client = boto3.client("bedrock-runtime", region_name=config.AWS_DEFAULT_REGION)
    resp = client.invoke_model(
        modelId=config.BEDROCK_MODEL_ID,
        body=json.dumps({
            "anthropic_version": "bedrock-2023-05-31",
            "max_tokens": 1024,
            "messages": [{"role": "user", "content": prompt}],
        })
    )
    answer = json.loads(resp["body"].read())["content"][0]["text"]

    sources = [
        AskSource(session_id=r.session_id, timestamp_s=r.start_s, quote=r.quote)
        for r in rows
    ]
    return AskResponse(answer=answer, sources=sources)
