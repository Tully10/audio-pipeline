import os
import uuid
from fastapi import APIRouter, UploadFile, File, Form, Depends, HTTPException, BackgroundTasks, Header
from sqlalchemy.ext.asyncio import AsyncSession
from db.database import get_db
from db.models import Chunk
from models.schemas import ChunkUploadResponse
import config
from pipeline.assembler import assemble_pending

router = APIRouter()


@router.post("/audio/chunks", response_model=ChunkUploadResponse)
async def upload_chunk(
    background_tasks: BackgroundTasks,
    audio_file: UploadFile = File(...),
    device_id: str = Form(...),
    chunk_seq: str = Form(...),
    recorded_at: str = Form(...),
    markers: str = Form(default="[]"),
    x_api_key: str = Header(default=""),
    db: AsyncSession = Depends(get_db),
):
    if x_api_key != config.API_KEY:
        raise HTTPException(status_code=401, detail="Invalid API key")

    chunk_id = str(uuid.uuid4())
    chunks_dir = os.path.join(config.AUDIO_DIR, "chunks", device_id)
    os.makedirs(chunks_dir, exist_ok=True)
    safe_ts = recorded_at.replace(":", "-").replace("+", "")
    file_path = os.path.join(chunks_dir, f"{chunk_seq}_{safe_ts}.wav")

    contents = await audio_file.read()
    with open(file_path, "wb") as f:
        f.write(contents)

    chunk = Chunk(
        id=chunk_id,
        device_id=device_id,
        chunk_seq=int(chunk_seq),
        recorded_at=recorded_at,
        file_path=file_path,
        markers_json=markers,
    )
    db.add(chunk)
    await db.commit()

    # assemble_pending creates its own session — safe to run after request closes
    background_tasks.add_task(assemble_pending)

    return ChunkUploadResponse(chunk_id=chunk_id, status="received")
