import os
import struct
import uuid
import json
from datetime import datetime, timedelta
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from db.models import Chunk, Session as SessionModel
from db.database import AsyncSessionLocal
import config
from pipeline import job_queue

SESSION_MAX_DURATION = timedelta(minutes=30)


def _parse_dt(s: str) -> datetime:
    for fmt in ("%Y-%m-%dT%H:%M:%S.%fZ", "%Y-%m-%dT%H:%M:%SZ", "%Y-%m-%dT%H:%M:%S", "%Y-%m-%dT%H:%M:%S.%f"):
        try:
            return datetime.strptime(s, fmt)
        except ValueError:
            continue
    return datetime.fromisoformat(s.replace("Z", "+00:00").replace("+00:00", ""))


def _write_wav_header(f, num_samples: int, sample_rate=48000, num_channels=1, bits_per_sample=16):
    data_size = num_samples * num_channels * (bits_per_sample // 8)
    f.write(b"RIFF")
    f.write(struct.pack("<I", 36 + data_size))
    f.write(b"WAVE")
    f.write(b"fmt ")
    f.write(struct.pack("<I", 16))
    f.write(struct.pack("<H", 1))  # PCM
    f.write(struct.pack("<H", num_channels))
    f.write(struct.pack("<I", sample_rate))
    f.write(struct.pack("<I", sample_rate * num_channels * (bits_per_sample // 8)))
    f.write(struct.pack("<H", num_channels * (bits_per_sample // 8)))
    f.write(struct.pack("<H", bits_per_sample))
    f.write(b"data")
    f.write(struct.pack("<I", data_size))


async def assemble_pending():
    """Creates its own DB session — safe to call from BackgroundTasks."""
    async with AsyncSessionLocal() as db:
        result = await db.execute(
            select(Chunk).where(Chunk.status == "pending").order_by(Chunk.device_id, Chunk.recorded_at)
        )
        chunks = result.scalars().all()
        if not chunks:
            return

        by_device: dict[str, list[Chunk]] = {}
        for c in chunks:
            by_device.setdefault(c.device_id, []).append(c)

        gap = timedelta(minutes=config.SESSION_GAP_MINUTES)
        now = datetime.utcnow()

        for device_id, device_chunks in by_device.items():
            device_chunks.sort(key=lambda c: c.recorded_at)
            groups: list[list[Chunk]] = []
            current_group: list[Chunk] = [device_chunks[0]]

            for prev, curr in zip(device_chunks, device_chunks[1:]):
                prev_dt = _parse_dt(prev.recorded_at)
                curr_dt = _parse_dt(curr.recorded_at)
                group_start_dt = _parse_dt(current_group[0].recorded_at)
                silence_gap = curr_dt - prev_dt > gap
                duration_exceeded = curr_dt - group_start_dt >= SESSION_MAX_DURATION
                if silence_gap or duration_exceeded:
                    groups.append(current_group)
                    current_group = [curr]
                else:
                    current_group.append(curr)

            if current_group:
                first_dt = _parse_dt(current_group[0].recorded_at)
                last_dt = _parse_dt(current_group[-1].recorded_at)
                if now - last_dt > gap or last_dt - first_dt >= SESSION_MAX_DURATION:
                    groups.append(current_group)

            for group in groups:
                await _assemble_group(group, device_id, db)

        await db.commit()


async def _assemble_group(group: list[Chunk], device_id: str, db: AsyncSession):
    session_id = str(uuid.uuid4())
    started_at = group[0].recorded_at
    ended_at = group[-1].recorded_at

    sessions_dir = os.path.join(config.AUDIO_DIR, "sessions")
    os.makedirs(sessions_dir, exist_ok=True)
    out_path = os.path.join(sessions_dir, f"{session_id}.wav")

    pcm_data = bytearray()
    sample_rate = 48000
    for chunk in group:
        if not os.path.exists(chunk.file_path):
            continue
        with open(chunk.file_path, "rb") as f:
            raw = f.read()
        pcm_data += raw[44:]  # strip WAV header from every chunk

    num_samples = len(pcm_data) // 2  # 16-bit = 2 bytes per sample
    with open(out_path, "wb") as f:
        _write_wav_header(f, num_samples, sample_rate=sample_rate)
        f.write(pcm_data)

    all_markers = []
    chunk_duration_s = config.CHUNK_LENGTH_S
    for i, chunk in enumerate(group):
        for m in json.loads(chunk.markers_json or "[]"):
            all_markers.append({
                "offset_s": i * chunk_duration_s + m["offset_s"],
                "label": m.get("label", "IMPORTANT"),
            })

    session = SessionModel(
        id=session_id,
        device_id=device_id,
        started_at=started_at,
        ended_at=ended_at,
        audio_path=out_path,
        status="transcribing",
    )
    db.add(session)

    for chunk in group:
        chunk.session_id = session_id
        chunk.status = "assembled"

    sidecar = out_path + ".markers.json"
    with open(sidecar, "w") as f:
        json.dump(all_markers, f)

    await job_queue.put(("transcribe", session_id))
