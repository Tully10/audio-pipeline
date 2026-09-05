import asyncio
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from db.database import init_db
from api import chunks, sessions, search, ask, status, health
from pipeline import job_queue


async def _worker():
    """Background worker that drains the job queue."""
    from db.database import AsyncSessionLocal
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


@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_db()
    # Pre-load Whisper model in background to avoid cold start on first chunk
    asyncio.create_task(_worker())
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
