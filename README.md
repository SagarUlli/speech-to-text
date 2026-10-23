# Bol — Real-time Hindi + English Transcription (Spring Boot)

Speak into your browser mic — in Hindi, English or a mix — and watch the words appear live.
Every session is stored in a relational database and managed from a history dashboard (create / read / update / delete, search, pagination, copy, download).

**Stack:** Java 17 · Spring Boot 4.1.2 (Web, WebSocket, Data JPA, Validation) · H2 / PostgreSQL / MySQL · vanilla JS + AudioWorklet (no build step)

## How it works

```
Browser mic ─▶ AudioWorklet (downsample → 16 kHz PCM16, 100 ms frames)
            ─▶ WebSocket /ws/transcribe ─▶ Spring Boot ─▶ SttEngine ─▶ Gemini Live  (or self-hosted Whisper)
            ◀─ interim / final text ◀──────────────────────────────────┘
Stop ─▶ POST /api/transcriptions ─▶ JPA ─▶ MySQL / PostgreSQL
```

* The browser never sees the API key; Spring Boot proxies the audio stream.
* **`SttEngine`** is a small interface, so engines are pluggable:
  * `GeminiLiveEngine` — Gemini Live API (`gemini-3.5-transcribe-live`) with `inputAudioTranscription` and **automatic language detection** (empty `languageCodes`), which is what handles Hindi/English code-switching. Shows low-latency interim text, then the finalised segment.
  * `WhisperEngine` — **open-source, self-hosted** option: any OpenAI-compatible Whisper server (e.g. [Speaches / faster-whisper-server](https://github.com/speaches-ai/speaches)). Audio is sent in ~4 s chunks with language auto-detect and a Hinglish prompt. Enable by setting `WHISPER_BASE_URL`.
* The UI only offers engines that are configured on the server (`GET /api/engines`).

## Run locally

```bash
export GEMINI_API_KEY=...        # free key: https://aistudio.google.com/apikey
mvn spring-boot:run              # http://localhost:8080  (in-memory H2 by default)
```
Mic access works on `http://localhost` and on any `https://` origin.

Self-hosted open-source engine instead:
```bash
docker run -p 8000:8000 ghcr.io/speaches-ai/speaches:latest-cpu
WHISPER_BASE_URL=http://localhost:8000 TRANSCRIBE_DEFAULT_ENGINE=whisper mvn spring-boot:run
```

### Databases
| DB | Settings |
|---|---|
| H2 (default, dev) | nothing |
| PostgreSQL | `DB_URL=jdbc:postgresql://host:5432/bol DB_USER=… DB_PASSWORD=…` |
| MySQL | `DB_URL=jdbc:mysql://host:3306/bol DB_USER=… DB_PASSWORD=…` |

Tables are created automatically (`ddl-auto: update`) — fine for this scope; use Flyway for production.

## REST API
| Method | Path | |
|---|---|---|
| GET | `/api/transcriptions?q=&page=&size=` | list newest first, search title/text |
| GET | `/api/transcriptions/{id}` | read |
| POST | `/api/transcriptions` | create `{title, content, language?, engine?, durationSeconds?}` |
| PUT | `/api/transcriptions/{id}` | update |
| DELETE | `/api/transcriptions/{id}` | delete |
| GET | `/api/engines` | configured engines |

## Deploy (Render, free tier)
1. Push this repo to GitHub.
2. Render → **New → Blueprint** → select the repo (`render.yaml` provisions Postgres + the Docker service).
3. Set `GEMINI_API_KEY` in the service's environment. Done — Render serves HTTPS, so the mic works.

The `Dockerfile` also runs anywhere else (Railway, Fly.io, Cloud Run, …); just pass the env vars above.

## Tests
`mvn test` — CRUD lifecycle + validation (MockMvc on H2) and Gemini message-parsing unit tests.

## Known limits / next steps
* Gemini Live sessions last ~10 minutes; auto-reconnect/resume is not implemented yet.
* No authentication — history is shared by everyone who opens the app. Next step: Spring Security + per-user ownership.
* Whisper engine is chunk-based (higher latency than Gemini streaming); VAD-based chunking would improve it.
