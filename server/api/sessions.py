import os
import json
from fastapi import APIRouter, Depends, HTTPException, Header
from fastapi.responses import StreamingResponse
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from db.database import get_db
from db.models import Session as SessionModel, Word, Entity, ActionItem, Marker
from models.schemas import SessionOut, TranscriptResponse, WordEntry, EntityOut, MarkerOut, CorrectionRequest
import config

router = APIRouter()


def _auth(x_api_key: str = Header(default="")):
    if x_api_key != config.API_KEY:
        raise HTTPException(status_code=401, detail="Invalid API key")


@router.get("/sessions", response_model=list[SessionOut])
async def list_sessions(
    date: str = None,
    limit: int = 20,
    offset: int = 0,
    db: AsyncSession = Depends(get_db),
    _=Depends(_auth),
):
    q = select(SessionModel).order_by(SessionModel.started_at.desc()).limit(limit).offset(offset)
    if date:
        q = q.where(SessionModel.started_at.like(f"{date}%"))
    result = await db.execute(q)
    sessions = result.scalars().all()
    out = []
    for s in sessions:
        try:
            from datetime import datetime
            s_dt = datetime.fromisoformat(s.started_at.replace("Z", ""))
            e_dt = datetime.fromisoformat(s.ended_at.replace("Z", "")) if s.ended_at else s_dt
            duration_s = int((e_dt - s_dt).total_seconds())
        except Exception:
            duration_s = 0
        people = json.loads(s.people_json or "[]")
        out.append(SessionOut(
            id=s.id,
            started_at=s.started_at,
            ended_at=s.ended_at,
            duration_s=duration_s,
            speaker_count=s.speaker_count,
            summary=s.summary,
            people=people,
            status=s.status,
            sentiment=s.sentiment,
        ))
    return out


@router.get("/sessions/{session_id}/transcript", response_model=TranscriptResponse)
async def get_transcript(
    session_id: str,
    db: AsyncSession = Depends(get_db),
    _=Depends(_auth),
):
    session = (await db.execute(select(SessionModel).where(SessionModel.id == session_id))).scalar_one_or_none()
    if not session:
        raise HTTPException(status_code=404)

    words = (await db.execute(select(Word).where(Word.session_id == session_id).order_by(Word.word_index))).scalars().all()
    entities = (await db.execute(select(Entity).where(Entity.session_id == session_id))).scalars().all()
    action_items = (await db.execute(select(ActionItem).where(ActionItem.session_id == session_id))).scalars().all()
    markers = (await db.execute(select(Marker).where(Marker.session_id == session_id))).scalars().all()

    return TranscriptResponse(
        id=session_id,
        words=[
            WordEntry(
                word=w.corrected_text or w.word,
                start_s=w.start_s,
                end_s=w.end_s,
                confidence=w.confidence,
                speaker=w.speaker,
                low_confidence=w.low_confidence,
            ) for w in words
        ],
        summary=session.summary,
        entities=[EntityOut(name=e.name, type=e.entity_type) for e in entities],
        action_items=[a.text for a in action_items],
        markers=[MarkerOut(offset_s=m.offset_s, label=m.label) for m in markers],
        sentiment=session.sentiment,
    )


@router.get("/sessions/{session_id}/audio")
async def stream_audio(
    session_id: str,
    db: AsyncSession = Depends(get_db),
    _=Depends(_auth),
):
    session = (await db.execute(select(SessionModel).where(SessionModel.id == session_id))).scalar_one_or_none()
    if not session or not session.audio_path or not os.path.exists(session.audio_path):
        raise HTTPException(status_code=404)

    file_size = os.path.getsize(session.audio_path)

    def iter_file():
        with open(session.audio_path, "rb") as f:
            while chunk := f.read(65536):
                yield chunk

    return StreamingResponse(
        iter_file(),
        media_type="audio/wav",
        headers={"Content-Length": str(file_size), "Accept-Ranges": "bytes"},
    )


@router.put("/sessions/{session_id}/words/{word_index}")
async def correct_word(
    session_id: str,
    word_index: int,
    body: CorrectionRequest,
    db: AsyncSession = Depends(get_db),
    _=Depends(_auth),
):
    word = (await db.execute(
        select(Word).where(Word.session_id == session_id, Word.word_index == word_index)
    )).scalar_one_or_none()
    if not word:
        raise HTTPException(status_code=404)
    word.corrected_text = body.corrected_text
    await db.commit()
    return {"ok": True}
