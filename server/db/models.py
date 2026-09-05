import uuid
from datetime import datetime
from sqlalchemy import (
    Column, String, Integer, Float, Boolean, DateTime, ForeignKey, Text
)
from sqlalchemy.orm import declarative_base, relationship

Base = declarative_base()


def _uuid():
    return str(uuid.uuid4())


class Chunk(Base):
    __tablename__ = "chunks"
    id = Column(String, primary_key=True, default=_uuid)
    device_id = Column(String, nullable=False)
    chunk_seq = Column(Integer, nullable=False)
    recorded_at = Column(String, nullable=False)
    file_path = Column(String, nullable=False)
    markers_json = Column(Text, default="[]")
    session_id = Column(String, ForeignKey("sessions.id"), nullable=True)
    status = Column(String, default="pending")  # pending/assembled/error
    created_at = Column(DateTime, default=datetime.utcnow)


class Session(Base):
    __tablename__ = "sessions"
    id = Column(String, primary_key=True, default=_uuid)
    device_id = Column(String, nullable=False)
    started_at = Column(String, nullable=False)
    ended_at = Column(String, nullable=True)
    audio_path = Column(String, nullable=True)
    status = Column(String, default="assembling")  # assembling/transcribing/diarizing/processing/complete/error
    speaker_count = Column(Integer, default=0)
    summary = Column(Text, nullable=True)
    people_json = Column(Text, default="[]")
    created_at = Column(DateTime, default=datetime.utcnow)
    words = relationship("Word", back_populates="session", cascade="all, delete-orphan")
    entities = relationship("Entity", back_populates="session", cascade="all, delete-orphan")
    action_items = relationship("ActionItem", back_populates="session", cascade="all, delete-orphan")
    markers = relationship("Marker", back_populates="session", cascade="all, delete-orphan")


class Word(Base):
    __tablename__ = "words"
    id = Column(Integer, primary_key=True, autoincrement=True)
    session_id = Column(String, ForeignKey("sessions.id"), nullable=False)
    word_index = Column(Integer, nullable=False)
    word = Column(String, nullable=False)
    start_s = Column(Float, nullable=False)
    end_s = Column(Float, nullable=False)
    confidence = Column(Float, default=1.0)
    speaker = Column(String, default="SPEAKER_0")
    low_confidence = Column(Boolean, default=False)
    corrected_text = Column(String, nullable=True)
    session = relationship("Session", back_populates="words")


class Entity(Base):
    __tablename__ = "entities"
    id = Column(Integer, primary_key=True, autoincrement=True)
    session_id = Column(String, ForeignKey("sessions.id"), nullable=False)
    name = Column(String, nullable=False)
    entity_type = Column(String, nullable=False)  # PERSON/ORG/PLACE/PROJECT
    session = relationship("Session", back_populates="entities")


class ActionItem(Base):
    __tablename__ = "action_items"
    id = Column(Integer, primary_key=True, autoincrement=True)
    session_id = Column(String, ForeignKey("sessions.id"), nullable=False)
    text = Column(Text, nullable=False)
    resolved = Column(Boolean, default=False)
    session = relationship("Session", back_populates="action_items")


class Marker(Base):
    __tablename__ = "markers"
    id = Column(Integer, primary_key=True, autoincrement=True)
    session_id = Column(String, ForeignKey("sessions.id"), nullable=False)
    offset_s = Column(Float, nullable=False)
    label = Column(String, default="IMPORTANT")
    session = relationship("Session", back_populates="markers")
