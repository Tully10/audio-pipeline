from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import text
from db.database import get_db
from models.schemas import SearchResult

router = APIRouter()


@router.get("/search", response_model=list[SearchResult])
async def search(
    q: str = Query(...),
    limit: int = 20,
    db: AsyncSession = Depends(get_db),
):
    result = await db.execute(
        text("""
            SELECT rowid,
                   snippet(fts_transcripts, 0, '<b>', '</b>', '...', 20) as snippet,
                   session_id,
                   start_s
            FROM fts_transcripts
            WHERE fts_transcripts MATCH :q
            LIMIT :limit
        """),
        {"q": q, "limit": limit},
    )
    rows = result.fetchall()
    return [
        SearchResult(session_id=row.session_id, snippet=row.snippet, timestamp_s=row.start_s)
        for row in rows
    ]
