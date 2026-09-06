import os
import json
import re
from datetime import datetime
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from db.models import Session as SessionModel, Word, Entity, ActionItem, Marker
import config


def _slug(name: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")


def _format_ts(seconds: float) -> str:
    h = int(seconds // 3600)
    m = int((seconds % 3600) // 60)
    s = int(seconds % 60)
    return f"{h:02d}:{m:02d}:{s:02d}"


async def write_session_vault(session_id: str, mindmap_code: str, db: AsyncSession):
    result = await db.execute(select(SessionModel).where(SessionModel.id == session_id))
    session = result.scalar_one_or_none()
    if not session:
        return

    words_result = await db.execute(
        select(Word).where(Word.session_id == session_id).order_by(Word.word_index)
    )
    words = words_result.scalars().all()

    entities_result = await db.execute(select(Entity).where(Entity.session_id == session_id))
    entities = entities_result.scalars().all()

    actions_result = await db.execute(select(ActionItem).where(ActionItem.session_id == session_id))
    action_items = actions_result.scalars().all()

    markers_result = await db.execute(select(Marker).where(Marker.session_id == session_id))
    markers = markers_result.scalars().all()

    started_dt = datetime.fromisoformat(session.started_at.replace("Z", ""))
    date_str = started_dt.strftime("%Y-%m-%d")
    time_str = started_dt.strftime("%H:%M")

    if session.ended_at:
        ended_dt = datetime.fromisoformat(session.ended_at.replace("Z", ""))
        duration_s = int((ended_dt - started_dt).total_seconds())
    else:
        duration_s = int(words[-1].end_s) if words else 0

    speakers = list(set(w.speaker for w in words))
    people = json.loads(session.people_json or "[]")

    # Parse sentiment
    sentiment_data = {}
    if session.sentiment:
        try:
            sentiment_data = json.loads(session.sentiment)
        except Exception:
            pass

    # Build diarised transcript lines
    lines = []
    if words:
        cur_speaker = words[0].speaker
        cur_words = []
        cur_start = words[0].start_s
        for w in words:
            if w.speaker != cur_speaker:
                lines.append((cur_speaker, cur_start, " ".join(cur_words)))
                cur_speaker = w.speaker
                cur_words = [w.corrected_text or w.word]
                cur_start = w.start_s
            else:
                cur_words.append(w.corrected_text or w.word)
        if cur_words:
            lines.append((cur_speaker, cur_start, " ".join(cur_words)))

    transcript_text = "\n".join(
        f"**{spk}** *({_format_ts(ts)})*: {text}" for spk, ts, text in lines
    )

    markers_text = "\n".join(
        f"- **{_format_ts(m.offset_s)}** — "
        + " ".join(
            w.corrected_text or w.word for w in words
            if m.offset_s - 3 <= w.start_s <= m.offset_s + 3
        )
        for m in markers
    ) or "_(none)_"

    action_items_text = "\n".join(f"- [ ] {a.text}" for a in action_items) or "_(none)_"

    mindmap_section = ""
    if mindmap_code and mindmap_code.strip():
        mindmap_section = f"## Mind Map\n```mermaid\n{mindmap_code}\n```\n"

    sentiment_section = ""
    if sentiment_data:
        sent = sentiment_data.get("sentiment", "")
        tone = sentiment_data.get("tone", "")
        energy = sentiment_data.get("energy", "")
        key_moments = sentiment_data.get("key_moments", [])
        if sent or tone:
            sentiment_section = f"## Tone & Sentiment\n**{sent}** · {tone} · {energy} energy\n"
            if key_moments:
                sentiment_section += "\n**Key moments:**\n" + "\n".join(f"- {m}" for m in key_moments) + "\n"
            sentiment_section += "\n"

    note = f"""---
date: {date_str}
time: {time_str}
duration_s: {duration_s}
speakers: {json.dumps(speakers)}
people: {json.dumps(people)}
action_items_count: {len(action_items)}
sentiment: {sentiment_data.get('sentiment', '')}
ton: {sentiment_data.get('tone', '')}
session_type: {session.session_type or 'ambient'}
---

# Session — {time_str} · {duration_s // 60}m

## Summary
{session.summary or '_Not yet generated._'}

{sentiment_section}{mindmap_section}## Action Items
{action_items_text}

## Transcript
{transcript_text}

## Markers (⧑ Important)
{markers_text}
"""

    transcript_dir = os.path.join(config.VAULT_DIR, "Transcripts", date_str)
    os.makedirs(transcript_dir, exist_ok=True)
    note_path = os.path.join(transcript_dir, f"session-{time_str.replace(':', '-')}-{session_id[:8]}.md")
    with open(note_path, "w") as f:
        f.write(note)

    # People graph
    people_dir = os.path.join(config.VAULT_DIR, "People")
    os.makedirs(people_dir, exist_ok=True)
    for entity in entities:
        if entity.entity_type != "PERSON":
            continue
        slug = _slug(entity.name)
        person_path = os.path.join(people_dir, f"{slug}.md")
        session_link = f"- [{date_str} {time_str}]({note_path})"
        if os.path.exists(person_path):
            with open(person_path, "a") as f:
                f.write(f"\n{session_link}")
        else:
            with open(person_path, "w") as f:
                f.write(f"---\nname: {entity.name}\nfirst_seen: {date_str}\n---\n\n# {entity.name}\n\n## Sessions\n{session_link}\n")

    # Rolling action items
    if action_items:
        ai_path = os.path.join(config.VAULT_DIR, "Action-Items", "open.md")
        os.makedirs(os.path.dirname(ai_path), exist_ok=True)
        with open(ai_path, "a") as f:
            f.write(f"\n### {date_str} {time_str} ([session]({note_path}))\n")
            for a in action_items:
                f.write(f"- [ ] {a.text}\n")

    # Important moments
    if markers:
        im_path = os.path.join(config.VAULT_DIR, "Important-Moments", "open.md")
        os.makedirs(os.path.dirname(im_path), exist_ok=True)
        with open(im_path, "a") as f:
            for m in markers:
                context = " ".join(
                    w.corrected_text or w.word for w in words
                    if m.offset_s - 3 <= w.start_s <= m.offset_s + 3
                )
                f.write(f"- **{date_str} {_format_ts(m.offset_s)}** ([session]({note_path})): {context}\n")
