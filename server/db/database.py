from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from sqlalchemy import text
from db.models import Base
import config

engine = create_async_engine(config.DATABASE_URL, echo=False)
AsyncSessionLocal = async_sessionmaker(engine, expire_on_commit=False)


async def get_db():
    async with AsyncSessionLocal() as session:
        yield session


async def init_db():
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
        await conn.execute(text("""
            CREATE VIRTUAL TABLE IF NOT EXISTS fts_transcripts
            USING fts5(word, session_id UNINDEXED, start_s UNINDEXED, content=words, content_rowid=id)
        """))
        # Migrate columns added after initial deployment
        for ddl in [
            "ALTER TABLE sessions ADD COLUMN sentiment TEXT",
            "ALTER TABLE sessions ADD COLUMN session_type TEXT DEFAULT 'ambient'",
        ]:
            try:
                await conn.execute(text(ddl))
            except Exception:
                pass  # column already exists
