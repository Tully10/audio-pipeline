import asyncio
import os
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from db.database import init_db, AsyncSessionLocal
from db.models import Session as SessionModel
from sqlalchemy import select
from api import chunks, sessions, search, ask, status, health
from pipeline import job_queue
import config


async def _worker():
    """Background worker that drains the job queue."""
    while True:
        job = await job_queue.get()
        try:
            async with AsyncSessionLocal() as db:
                if job[0] == "transcribe":
                    from pipeline.transcriber import transcribe_session
                    await transcribe_session(job[1], db)
                elif job[0] == "diarize":
                    from pipeline.diarizer import diarize_session
                    await diarize_session(job[1], db)
                elif job[0] == "process":
                    from pipeline.processor import process_session
                    await process_session(job[1], db)
                elif job[0] == "vault":
                    from pipeline.vault_writer import write_session_vault
                    await write_session_vault(job[1], job[2], db)
        except Exception as e:
            print(f"[worker] error processing {job}: {e}")
        finally:
            job_queue.task_done()


def _vault_path_for(session: SessionModel) -> str:
    from datetime import datetime
    started_dt = datetime.fromisoformat(session.started_at.replace("Z", ""))
    date_str = started_dt.strftime("%Y-%m-%d")
    time_str = started_dt.strftime("%H-%M")
    return os.path.join(config.VAULT_DIR, "Transcripts", date_str,
                        f"session-{time_str}-{session.id[:8]}.md")


async def _recover_sessions():
    """Re-enqueue any sessions left in-flight from before a restart."""
    job_map = {
        "transcribing": "transcribe",
        "diarizing": "diarize",
        "processing": "process",
    }
    async with AsyncSessionLocal() as db:
        # Re-enqueue in-flight sessions
        result = await db.execute(
            select(SessionModel).where(
                SessionModel.status.in_(list(job_map.keys()))
            )
        )
        in_flight = result.scalars().all()
        for s in in_flight:
            await job_queue.put((job_map[s.status], s.id))

        # Re-enqueue vault writes for complete sessions missing their file
        vault_result = await db.execute(
            select(SessionModel).where(
                SessionModel.status == "complete",
                SessionModel.summary.isnot(None),
            )
        )
        complete = vault_result.scalars().all()
        vault_missing = [s for s in complete if not os.path.exists(_vault_path_for(s))]
        for s in vault_missing:
            await job_queue.put(("vault", s.id, ""))

        total = len(in_flight) + len(vault_missing)
        if total:
            print(f"[startup] re-enqueued {len(in_flight)} in-flight, {len(vault_missing)} vault-missing sessions")


@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_db()
    asyncio.create_task(_worker())
    await _recover_sessions()
    yield


app = FastAPI(title="Audio Pipeline", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(chunks.router)
app.include_router(sessions.router)
app.include_router(search.router)
app.include_router(ask.router)
app.include_router(status.router)
app.include_router(health.router)


@app.get("/")
async def root():
    return {"service": "audio-pipeline", "status": "ok"}
