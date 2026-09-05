import os

API_KEY = os.environ["API_KEY"]
HUGGINGFACE_TOKEN = os.environ["HUGGINGFACE_TOKEN"]
AWS_DEFAULT_REGION = os.getenv("AWS_DEFAULT_REGION", "eu-west-1")
AWS_ACCESS_KEY_ID = os.getenv("AWS_ACCESS_KEY_ID")
AWS_SECRET_ACCESS_KEY = os.getenv("AWS_SECRET_ACCESS_KEY")
AUDIO_DIR = os.getenv("AUDIO_DIR", "/data/audio")
VAULT_DIR = os.getenv("VAULT_DIR", "/data/vault")
DATABASE_URL = os.getenv("DATABASE_URL", "sqlite+aiosqlite:////data/db/pipeline.db")
WHISPER_MODEL = os.getenv("WHISPER_MODEL", "large-v3")
WHISPER_DEVICE = os.getenv("WHISPER_DEVICE", "auto")
SESSION_GAP_MINUTES = int(os.getenv("SESSION_GAP_MINUTES", "2"))
BEDROCK_MODEL_ID = os.getenv("BEDROCK_MODEL_ID", "eu.anthropic.claude-sonnet-4-6")
LOW_CONFIDENCE_THRESHOLD = float(os.getenv("LOW_CONFIDENCE_THRESHOLD", "0.75"))
