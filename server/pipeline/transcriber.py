import os
import json
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update
from db.models import Session as SessionModel, Word, Marker
import config
from pipeline import job_queue

_whisper_model = None
_whisper_status = "idle"  # idle / processing / down


def get_whisper_status() -> str:
    return _whisper_status


def _get_model():
    global _whisper_model
    if _whisper_model is None:
        from faster_whisper import WhisperModel
        device = config.WHISPER_DEVICE
        if device == "auto":
            try:
                import torch
                device = "cuda" if torch.cuda.is_available() else "cpu"
            except ImportError:
                device = "cpu"
        _whisper_model = WhisperModel(config.WHISPER_MODEL, device=device, compute_type="int8")
    return _whisper_model


async def transcribe_session(session_id: str, db: AsyncSession):
    global _whisper_status
    result = await db.execute(select(SessionModel).where(SessionModel.id == session_id))
    session = result.scalar_one_or_none()
    if not session or not session.audio_path or not os.path.exists(session.audio_path):
        return

    _whisper_status = "processing"
    try:
        model = _get_model()
        segments, _ = model.transcribe(session.audio_path, word_timestamps=True, language=None)

        words = []
        for segment in segments:
            if segment.words:
                for w in segment.words:
                    words.append({
                        "word": w.word.strip(),
                        "start": w.start,
                        "end": w.end,
                        "probability": w.probability,
                    })

        word_rows = []
        for idx, w in enumerate(words):
            row = Word(
                session_id=session_id,
                word_index=idx,
                word=w["word"],
                start_s=w["start"],
                end_s=w["end"],
                confidence=w["probability"],
                low_confidence=w["probability"] < config.LOW_CONFIDENCE_THRESHOLD,
            )
            word_rows.append(row)
            db.add(row)

        await db.flush()

        # Populate FTS5
        from sqlalchemy import text
        for row in word_rows:
            await db.execute(
                text("INSERT INTO fts_transcripts(rowid, word, session_id, start_s) VALUES (:rid, :word, :sid, :start_s)"),
                {"rid": row.id, "word": row.word, "sid": session_id, "start_s": row.start_s}
            )

        # Load markers from sidecar
        sidecar = session.audio_path + ".markers.json"
        if os.path.exists(sidecar):
            with open(sidecar) as f:
                raw_markers = json.load(f)
            for m in raw_markers:
                db.add(Marker(session_id=session_id, offset_s=m["offset_s"], label=m.get("label", "IMPORTANT")))
            os.remove(sidecar)

        session.status = "diarizing"
        await db.commit()
        await job_queue.put(("diarize", session_id))
    except Exception as e:
        session.status = "error"
        await db.commit()
        raise
    finally:
        _whisper_status = "idle"
