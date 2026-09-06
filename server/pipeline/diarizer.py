from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from db.models import Session as SessionModel, Word
import config
from pipeline import job_queue

_pipeline = None


def _get_pipeline():
    global _pipeline
    if _pipeline is None:
        from pyannote.audio import Pipeline
        _pipeline = Pipeline.from_pretrained(
            "pyannote/speaker-diarization-3.1",
            token=config.HUGGINGFACE_TOKEN
        )
    return _pipeline


async def diarize_session(session_id: str, db: AsyncSession):
    result = await db.execute(select(SessionModel).where(SessionModel.id == session_id))
    session = result.scalar_one_or_none()
    if not session or not session.audio_path:
        return

    pipeline = _get_pipeline()
    diarization = pipeline(session.audio_path)

    # Build speaker map: list of (start, end, speaker)
    turns = [(turn.start, turn.end, speaker) for turn, _, speaker in diarization.itertracks(yield_label=True)]
    speakers_seen = set(s for _, _, s in turns)

    words_result = await db.execute(
        select(Word).where(Word.session_id == session_id).order_by(Word.word_index)
    )
    words = words_result.scalars().all()

    for word in words:
        for start, end, speaker in turns:
            if start <= word.start_s < end:
                word.speaker = speaker
                break

    session.speaker_count = len(speakers_seen)
    session.status = "processing"
    await db.commit()
    await job_queue.put(("process", session_id))
