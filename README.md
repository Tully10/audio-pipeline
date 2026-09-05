# Audio Pipeline — Ambient Intelligence Capture System

> 24/7 ambient audio capture on Samsung S10 → self-hosted transcription (Whisper large-v3) → AWS Bedrock intelligence → Markdown second-brain vault.

## Components

| Component | Directory | Description |
|-----------|-----------|-------------|
| Android app | `app/` | Kotlin foreground service — records, queues, uploads WAV chunks |
| Server | `server/` | FastAPI + Whisper + Pyannote + Bedrock pipeline |
| OLauncher fork | separate repo | Home screen status widget + health dashboard |

## Quick start

See [INSTALL.md](INSTALL.md) for complete step-by-step setup.

## Architecture

```
[Samsung S10]
  └─ Audio Capture App (foreground service, always on)
       ├─ AudioRecord — built-in mic locked, earbuds ignored
       ├─ 60s WAV chunks → local SQLite queue
       ├─ WiFi-only upload (configurable)
       └─ POST /audio/chunks → [Server]

[Server — dedicated AWS instance]
  └─ FastAPI ingestion API
       ├─ Chunk assembler (2-min gap = new session)
       ├─ Whisper large-v3 — transcription + word-level timestamps + confidence
       ├─ Pyannote — speaker diarization
       ├─ AWS Bedrock (claude-sonnet)
       │    ├─ Entity extraction (people, orgs, places)
       │    ├─ Action item detection
       │    ├─ Session summary (3 sentences)
       │    └─ Mind map (Mermaid)
       └─ Vault writer → /data/vault/

[OLauncher fork — home screen]
  └─ Status widget: recording dot + today stats
       └─ Swipe up → health dashboard
```

## License

Server and Android app: MIT. OLauncher fork: GPLv3 (inherits upstream license).
