#!/bin/bash
set -e

echo "=== Audio Pipeline Server Setup ==="

# Detect OS
if [ -f /etc/os-release ]; then
    . /etc/os-release
    OS=$ID
else
    OS=unknown
fi

# Install Docker
if ! command -v docker &>/dev/null; then
    echo "Installing Docker..."
    if [ "$OS" = "ubuntu" ] || [ "$OS" = "debian" ]; then
        apt-get update -q
        apt-get install -y -q docker.io docker-compose-plugin
        systemctl enable --now docker
    elif [ "$OS" = "amzn" ]; then
        yum update -y -q
        yum install -y -q docker
        systemctl enable --now docker
        # docker compose plugin for Amazon Linux
        mkdir -p /usr/local/lib/docker/cli-plugins
        curl -SL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-$(uname -m)" \
            -o /usr/local/lib/docker/cli-plugins/docker-compose
        chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
    else
        echo "Unsupported OS: $OS. Install Docker manually then re-run."
        exit 1
    fi
fi

# Create data directory tree
echo "Creating /data directory tree..."
mkdir -p /data/audio/chunks
mkdir -p /data/audio/sessions
mkdir -p /data/vault/Transcripts
mkdir -p /data/vault/People
mkdir -p /data/vault/Summaries
mkdir -p /data/vault/Action-Items
mkdir -p /data/vault/Important-Moments
mkdir -p /data/db
mkdir -p /data/logs

# Get the server directory (this script lives in repo/server/)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Generate or prompt for API key
echo ""
read -p "Enter API key (press Enter to generate a random one): " USER_API_KEY
if [ -z "$USER_API_KEY" ]; then
    API_KEY=$(openssl rand -hex 32 2>/dev/null || cat /proc/sys/kernel/random/uuid | tr -d '-')
else
    API_KEY="$USER_API_KEY"
fi

# Prompt for HuggingFace token
echo ""
echo "A HuggingFace token is required for speaker diarization (pyannote)."
echo "Get one free at https://huggingface.co/settings/tokens"
echo "Then accept the model terms at https://huggingface.co/pyannote/speaker-diarization-3.1"
read -p "Enter HuggingFace token: " HF_TOKEN

# Write .env
cat > .env <<EOF
API_KEY=${API_KEY}
HUGGINGFACE_TOKEN=${HF_TOKEN}
AWS_DEFAULT_REGION=eu-west-1
AUDIO_DIR=/data/audio
VAULT_DIR=/data/vault
DATABASE_URL=sqlite+aiosqlite:////data/db/pipeline.db
WHISPER_MODEL=large-v3
SESSION_GAP_MINUTES=2
BEDROCK_MODEL_ID=eu.anthropic.claude-sonnet-4-6
EOF

echo ""
echo "Building and starting the server (this will download Whisper large-v3 on first run — ~3GB)..."
docker compose up -d --build

PUBLIC_IP=$(curl -s --max-time 5 ifconfig.me || echo "your-server-ip")

echo ""
echo "==================================================="
echo "Server is running at http://${PUBLIC_IP}:8080"
echo "==================================================="
echo ""
echo "Your API key is: ${API_KEY}"
echo "Enter this in the Android app under Settings > API Key"
echo "Enter this server URL in Settings > Server URL: http://${PUBLIC_IP}:8080"
echo ""
echo "To view logs: docker compose logs -f"
echo "To stop:      docker compose down"
