# Installation Guide

Follow this guide in order. Total setup time: ~30 minutes.

---

## Part 1 — Provision the server

### 1.1 Launch an AWS EC2 instance

The server needs enough RAM to run Whisper large-v3 and Pyannote simultaneously.

**Recommended:** `g4dn.xlarge` (4 vCPU, 16 GB RAM, 1× NVIDIA T4 GPU) — transcription takes ~30 seconds per 30-minute session.

**Budget alternative:** `r6g.xlarge` (4 vCPU, 32 GB RAM, ARM/Graviton, no GPU) — transcription on CPU takes ~5–10 minutes per 30-minute session but still catches up with a normal day's audio.

Settings:
- **AMI:** Ubuntu 22.04 LTS
- **Storage:** 100 GB gp3
- **Security group inbound rules:**
  - Port 22 (SSH) — your IP only
  - Port 8080 (API) — your phone's IP or `0.0.0.0/0` if you want access anywhere

Note the **public IP address** of the instance — you'll need it.

---

### 1.2 Run the setup script

SSH into the instance and run:

```bash
ssh ubuntu@<YOUR_SERVER_IP>

# Clone the repo
git clone https://github.com/Tully10/audio-pipeline.git
cd audio-pipeline/server

# Run setup (installs Docker, creates directories, writes .env, starts server)
bash setup.sh
```

The script will:
1. Install Docker and docker-compose
2. Create `/data/` directory structure (audio, vault, database)
3. Ask you for:
   - **API Key** — press Enter to auto-generate a random one (recommended), or type your own
   - **HuggingFace token** — get a free one at https://huggingface.co/settings/tokens (needed for speaker diarization). Accept the `pyannote/speaker-diarization-3.1` model license at https://huggingface.co/pyannote/speaker-diarization-3.1
4. Build the Docker image and start the server
5. Print your API key and server URL

**Save these — you'll enter them in the Android app:**
```
Server URL:  http://<YOUR_SERVER_IP>:8080
API Key:     <printed by setup.sh>
```

---

### 1.3 Verify the server is running

```bash
curl http://<YOUR_SERVER_IP>:8080/health
```

Expected response:
```json
{"status": "ok", "upload_queue_depth": 0, "whisper_worker_status": "idle", ...}
```

---

### 1.4 (Optional) Set up a domain name

If you want to use the app outside your home WiFi (at hackathons, conferences), point a domain name at the server's IP.

Options:
- **Cloudflare:** add an A record pointing `audio.yourdomain.com` → server IP
- **AWS Route 53:** create a hosted zone and A record

Then update the Server URL in the Android app to `http://audio.yourdomain.com:8080`.

For HTTPS (recommended for mobile data uploads): install Caddy on the server as a reverse proxy.

---

## Part 2 — Install the Android app

### 2.1 Enable Developer Mode on your S10

1. Open **Settings** → **About phone** → **Software information**
2. Tap **Build number** 7 times
3. Enter your PIN if prompted
4. You'll see "Developer mode has been turned on"

### 2.2 Enable USB Debugging (for sideloading)

1. **Settings** → **Developer options** (now visible at the bottom of Settings)
2. Turn on **USB debugging**

### 2.3 Download the APK

Every push to the `main` branch automatically builds an APK via GitHub Actions.

1. Go to: https://github.com/Tully10/audio-pipeline/actions
2. Click the latest **Build APK** workflow run
3. Scroll to **Artifacts** → download **debug-apk**
4. Unzip to get `app-debug.apk`
5. Transfer to your phone (AirDrop equivalent: use a USB cable, Google Drive, or email it to yourself)
6. On your phone, open the APK file and tap **Install**
   - If prompted "Install unknown apps", allow it for your file manager

### 2.4 Configure the app

1. Open **Audio Capture** from your app drawer
2. Tap **Settings**
3. Enter:
   - **Server URL:** `http://<YOUR_SERVER_IP>:8080`
   - **API Key:** the key from setup.sh
4. Leave **WiFi only uploads** ON (recommended)

### 2.5 Grant permissions

The app will request:
- **Microphone** → Allow (required)
- **Run in background** → Allow (required)
- **Ignore battery optimisations** → Allow (critical — without this Android may kill the service)

Manually verify battery optimisation:
1. **Settings** → **Device care** → **Battery** → **App power management**
2. Find **Audio Capture** → set to **Unrestricted**

### 2.6 Start recording

The app starts recording automatically. You'll see a persistent notification: **"● Recording · 0 chunks pending"**

The notification means it's running. You don't need to keep the app open.

---

## Part 3 — Bixby button setup

The Bixby button (left side of S10) can be used to mark important moments during a conversation.

**Long-press Bixby button** = inserts a ⚑ timestamp marker into the current session.

No additional setup needed — the app registers for the Bixby key event automatically. If you have another app capturing the Bixby button:
1. **Settings** → **Advanced features** → **Bixby key**
2. Set to **Open Bixby** (the app handles it at a lower level)

Fallback: if Bixby button interception doesn't work on your firmware, **double-tap volume down** also inserts a marker.

---

## Part 4 — OLauncher home screen widget

> This step adds a recording status indicator to your home screen that shows recording state + today's stats, and a swipe-up health dashboard.

### 4.1 Install the modified OLauncher

1. Go to: https://github.com/Tully10/olauncher-fork/releases
2. Download the latest APK and install it (same process as Part 2.3)
3. When prompted "Set as default home", tap **Always**

### 4.2 Configure the widget

1. Long-press the home screen → **Widget settings**
2. Enter your **Server URL** (same as the Android app)
3. The status line will appear below the clock: `● 0 sessions · 0 people`

### 4.3 Health dashboard

Swipe up from the status line to see the full health dashboard:

| Indicator | Meaning |
|-----------|--------|
| ● Recording | Service is active |
| Upload queue: 0 | All audio is synced |
| Server: online | Server reachable |
| Last transcript: Xm ago | Pipeline is running |
| Disk: X GB free | Storage healthy |

If any indicator shows red/error, the dashboard tells you exactly what's wrong.

---

## Part 5 — Verification

### 5.1 Test the full pipeline

1. With the app running, speak for 2 minutes: *"Testing one two three, my name is Tom, I'm testing the audio pipeline"*
2. Open WiFi if you're not on it
3. Wait 3 minutes (chunk uploads + assembler + transcription)
4. Check: `curl http://<YOUR_SERVER_IP>:8080/today/status`

Expected: `sessions_today: 1, queue_depth: 0`

5. Check: `curl http://<YOUR_SERVER_IP>:8080/sessions`

You should see one session with status `complete`.

6. On the server: `ls /data/vault/Transcripts/$(date +%Y-%m-%d)/`

You should see a `.md` file with your transcript.

### 5.2 Test the playback screen

1. Open the Audio Capture app
2. Tap **Sessions**
3. Tap your test session
4. The transcript should appear with your words. Any uncertain words will be amber.
5. Tap a word — audio should seek to that position.

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| Notification disappears | Battery optimisation not set to Unrestricted. Repeat step 2.5. |
| Chunks pending, not uploading | Check WiFi connection. Or tap "Upload now" in the app. |
| Sessions stuck in "transcribing" | SSH to server, run `docker logs audio-pipeline-server-1`. Likely out of memory — check `free -h`. |
| `curl /health` shows server unreachable | Check EC2 security group has port 8080 open. |
| Pyannote fails to load | Check HUGGINGFACE_TOKEN is set and you accepted the model license at huggingface.co/pyannote/speaker-diarization-3.1 |
| AWS Bedrock calls failing | Ensure the EC2 instance has an IAM role with `bedrock:InvokeModel` permission for `eu-west-1`. |
| Bixby button not working | Double-tap volume down instead. Or check if another app has claimed the Bixby key. |

---

## AWS IAM setup (for Bedrock)

The server needs permission to call AWS Bedrock. Two options:

**Option A (recommended): EC2 instance role**
1. In AWS Console → IAM → Roles → Create role
2. Trusted entity: EC2
3. Attach policy: `AmazonBedrockFullAccess` (or a scoped policy for `bedrock:InvokeModel`)
4. Attach the role to your EC2 instance
5. Leave `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` blank in `.env` — the SDK uses the role automatically

**Option B: Access keys**
Add `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` to `.env` on the server.

---

## Directory structure on the server

```
/data/
  audio/
    chunks/          # raw WAV chunks (deleted after assembly)
    sessions/        # assembled session WAVs (kept for playback)
  vault/
    Transcripts/     # one folder per day, one .md per session
    People/          # one .md per person detected
    Summaries/       # daily digest
    Action-Items/    # rolling open action items
    Important-Moments/ # Bixby-marked moments
  db/
    pipeline.db      # SQLite database
  logs/
```

---

## Updating

**Android app:** GitHub Actions builds a new APK on every push. Check the Actions tab, download the latest artifact, install over the existing app.

**Server:** 
```bash
cd ~/audio-pipeline
git pull
cd server
docker compose down && docker compose up -d --build
```
