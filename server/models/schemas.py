from pydantic import BaseModel
from typing import List, Optional


class ChunkUploadResponse(BaseModel):
    chunk_id: str
    status: str


class TodayStatus(BaseModel):
    recording: bool
    sessions_today: int
    people_today: int
    queue_depth: int
    last_transcript_at: Optional[str]


class SessionOut(BaseModel):
    id: str
    started_at: str
    ended_at: Optional[str]
    duration_s: int
    speaker_count: int
    summary: Optional[str]
    people: List[str]
    status: str
    sentiment: Optional[str] = None


class WordEntry(BaseModel):
    word: str
    start_s: float
    end_s: float
    confidence: float
    speaker: str
    low_confidence: bool


class EntityOut(BaseModel):
    name: str
    type: str


class MarkerOut(BaseModel):
    offset_s: float
    label: str


class TranscriptResponse(BaseModel):
    id: str
    words: List[WordEntry]
    summary: Optional[str]
    entities: List[EntityOut]
    action_items: List[str]
    markers: List[MarkerOut]
    sentiment: Optional[str] = None


class CorrectionRequest(BaseModel):
    corrected_text: str


class AskRequest(BaseModel):
    q: str


class AskSource(BaseModel):
    session_id: str
    timestamp_s: float
    quote: str


class AskResponse(BaseModel):
    answer: str
    sources: List[AskSource]


class SearchResult(BaseModel):
    session_id: str
    snippet: str
    timestamp_s: float


class HealthResponse(BaseModel):
    status: str
    upload_queue_depth: int
    whisper_worker_status: str
    last_transcript_at: Optional[str]
    disk_free_gb: float
    bedrock_last_call_status: str
