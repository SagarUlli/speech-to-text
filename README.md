🎙️ Bol — Real-Time Hindi & English Speech-to-Text

<p align="center">
  A real-time multilingual speech-to-text application built with <strong>Java 17</strong> and <strong>Spring Boot</strong>.
  <br/>
  Convert spoken Hindi, English, and Hinglish into text using AI-powered transcription engines.
</p><p align="center">
  <img src="https://img.shields.io/badge/Java-17-orange?logo=openjdk" alt="Java 17"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-4.1.2-brightgreen?logo=springboot" alt="Spring Boot"/>
  <img src="https://img.shields.io/badge/WebSocket-Real--Time-blue" alt="WebSocket"/>
  <img src="https://img.shields.io/badge/AI-Gemini%20%7C%20Whisper-purple" alt="AI Engines"/>
  <img src="https://img.shields.io/badge/Database-H2%20%7C%20MySQL%20%7C%20PostgreSQL-blue" alt="Databases"/>
  <img src="https://img.shields.io/badge/License-MIT-yellow" alt="License"/>
</p>---

📌 Overview

Bol is a real-time multilingual speech-to-text application that converts spoken language into text using AI-powered speech recognition.

It supports Hindi, English, and Hinglish through two interchangeable transcription engines:

- Gemini Live API – Real-time streaming transcription.
- Whisper – Self-hosted, chunk-based audio transcription.

The application uses Java Spring Boot for backend processing, WebSocket for real-time communication, and JavaScript AudioWorklet for browser-based audio capture.

✨ Features

Feature| Description
🎤 Real-Time Transcription| Convert speech into text while speaking.
🌐 Multilingual Support| Supports Hindi, English, and Hinglish.
🤖 Multiple AI Engines| Supports Gemini Live API and Whisper.
⚡ Live Updates| Displays interim and finalized transcription results.
📝 Transcription History| Save and manage previous sessions.
🔄 CRUD Operations| Create, read, update, and delete transcriptions.
🔍 Search| Search previous transcriptions.
📄 Pagination| Navigate transcription history efficiently.
📥 Export| Copy and download transcription text.
🗄️ Database Flexibility| Supports H2, PostgreSQL, and MySQL.
🐳 Docker Support| Containerized application deployment.

🛠️ Technology Stack

Category| Technology
Programming Language| Java 17
Backend Framework| Spring Boot 4.1.2
REST API| Spring Web
Real-Time Communication| Spring WebSocket
Persistence| Spring Data JPA, Hibernate
Validation| Jakarta Bean Validation
Databases| H2, PostgreSQL, MySQL
Frontend| HTML5, CSS3, JavaScript
Audio Processing| Web Audio API, AudioWorklet
Speech Recognition| Gemini Live API, Whisper
Build Tool| Maven
Containerization| Docker
Deployment| Render

🏗️ Architecture

Bol follows a provider-independent architecture using the "SttEngine" interface, allowing different speech recognition engines to be integrated without changing the core WebSocket communication flow.

System Architecture

flowchart TD
    A["Browser / Frontend"] --> B["Microphone Input"]
    B --> C["AudioWorklet"]
    C --> D["16 kHz Mono PCM16"]
    D --> E["WebSocket Connection"]
    E --> F["Spring Boot Backend"]
    F --> G["SttEngine Interface"]

    G --> H["Gemini Live API"]
    G --> I["Whisper Server"]

    H --> J["Transcription Results"]
    I --> J

    J --> K["Interim / Final Events"]
    K --> A

    J --> L["REST API"]
    L --> M["Spring Data JPA"]
    M --> N[("H2 / PostgreSQL / MySQL")]

Audio Processing Workflow

1. Browser requests microphone permission.
2. AudioWorklet captures microphone audio.
3. Audio is converted into mono, 16 kHz, 16-bit PCM format.
4. Audio frames are streamed to the backend through WebSocket.
5. Spring Boot selects the configured transcription engine.
6. Gemini processes streaming audio, while Whisper processes audio in chunks.
7. Interim and finalized transcription results are sent to the browser.
8. Completed transcription sessions are saved through the REST API.
9. Spring Data JPA persists transcription records in the configured database.

«🔐 Security: Transcription API credentials remain on the backend and are never exposed to the browser.»

📂 Project Structure

speech-to-text/
│
├── src/
│   ├── main/
│   │   ├── java/com/sagarulli/transcribe/
│   │   │   ├── config/
│   │   │   ├── domain/
│   │   │   ├── engine/
│   │   │   ├── web/
│   │   │   └── TranscriptionApplication.java
│   │   │
│   │   └── resources/
│   │       ├── static/
│   │       │   ├── index.html
│   │       │   ├── styles.css
│   │       │   ├── app.js
│   │       │   └── pcm-worklet.js
│   │       ├── application.yml
│   │       └── application-postgres.yml
│   │
│   └── test/
│       └── java/
│
├── Dockerfile
├── render.yaml
├── pom.xml
├── .env.example
└── README.md

🚀 Getting Started

Prerequisites

Make sure the following tools are installed:

- Java 17 or later
- Maven 3.9+
- Gemini API key (for Gemini transcription)
- Docker (optional, for Whisper or containerized deployment)

1. Clone the Repository

git clone https://github.com/SagarUlli/speech-to-text.git

cd speech-to-text

2. Configure Gemini API Key

Linux / macOS

export GEMINI_API_KEY=your_api_key

Windows PowerShell

$env:GEMINI_API_KEY="your_api_key"

3. Run the Application

mvn spring-boot:run

Open the application in your browser:

http://localhost:8080

The application uses an in-memory H2 database by default.

🎧 Configure Whisper

Bol also supports a self-hosted Whisper-compatible transcription server using "Speaches" (https://github.com/speaches-ai/speaches).

1. Start the Whisper Server

docker run -p 8000:8000 ghcr.io/speaches-ai/speaches:latest-cpu

2. Configure Environment Variables

Linux / macOS

export WHISPER_BASE_URL=http://localhost:8000
export TRANSCRIBE_DEFAULT_ENGINE=whisper

Windows PowerShell

$env:WHISPER_BASE_URL="http://localhost:8000"
$env:TRANSCRIBE_DEFAULT_ENGINE="whisper"

3. Start Spring Boot

mvn spring-boot:run

The application will use Whisper when configured and available.

🗄️ Database Configuration

H2 is the default development database.

PostgreSQL

export DB_URL=jdbc:postgresql://localhost:5432/bol
export DB_USER=postgres
export DB_PASSWORD=your_password

MySQL

export DB_URL=jdbc:mysql://localhost:3306/bol
export DB_USER=root
export DB_PASSWORD=your_password

⚙️ Environment Variables

Configure the following environment variables according to your deployment requirements.

Variable| Description
"GEMINI_API_KEY"| Gemini API key
"GEMINI_MODEL"| Gemini model identifier
"TRANSCRIBE_DEFAULT_ENGINE"| Default transcription engine
"WHISPER_BASE_URL"| Whisper server URL
"WHISPER_MODEL"| Whisper model identifier
"WHISPER_CHUNK_SECONDS"| Audio chunk duration
"WHISPER_PROMPT"| Transcription context prompt
"WHISPER_API_KEY"| Optional Whisper server API key
"DB_URL"| Database JDBC URL
"DB_USER"| Database username
"DB_PASSWORD"| Database password
"PORT"| Application server port

«⚠️ Never commit actual API keys or database credentials to GitHub.»

🔌 REST API Documentation

Base URL

http://localhost:8080

Transcription Endpoints

HTTP Method| Endpoint| Description
"GET"| "/api/transcriptions"| Get paginated transcription history
"GET"| "/api/transcriptions/{id}"| Get transcription by ID
"POST"| "/api/transcriptions"| Create a transcription
"PUT"| "/api/transcriptions/{id}"| Update a transcription
"DELETE"| "/api/transcriptions/{id}"| Delete a transcription
"GET"| "/api/engines"| Get available transcription engines

Example: Create Transcription

Request

POST /api/transcriptions
Content-Type: application/json

Request Body

{
  "title": "Daily Meeting",
  "content": "Namaste, welcome to today's meeting.",
  "language": "Hindi + English",
  "engine": "gemini",
  "durationSeconds": 120
}

Example: Search Transcriptions

GET /api/transcriptions?q=meeting&page=0&size=10

🔄 WebSocket Communication

WebSocket Endpoint

/ws/transcribe

The browser communicates with the backend using text and binary WebSocket messages.

Client → Server

- "start" – Start a transcription session.
- "stop" – Stop recording and finish transcription.
- Binary messages – Send PCM16 audio frames.

Server → Client

Event| Description
"ready"| Transcription session is ready.
"interim"| Partial transcription result.
"final"| Finalized transcription segment.
"error"| Error during transcription.
"closed"| Transcription session closed.

🧪 Running Tests

Execute the automated test suite:

mvn test

The existing tests cover:

- Transcription CRUD operations.
- Request validation.
- REST API behavior.
- Engine availability.
- Gemini transcription message parsing.

🐳 Docker Deployment

Build Docker Image

docker build -t bol-transcription .

Run Docker Container

docker run -p 8080:8080 \
  -e GEMINI_API_KEY=your_api_key \
  bol-transcription

The repository also includes a "render.yaml" configuration for Render deployment.

⚠️ Current Limitations

- Authentication and user-specific transcription history are not implemented.
- Automatic Gemini session recovery is not implemented.
- Whisper uses chunk-based rather than true streaming transcription.
- Production database migrations should be introduced.
- Additional WebSocket lifecycle and resource-management protections are needed.

🔮 Future Improvements

- [ ] Spring Security and JWT authentication.
- [ ] User-specific transcription history.
- [ ] Flyway database migrations.
- [ ] Improved WebSocket session lifecycle management.
- [ ] Automatic reconnection and recovery.
- [ ] Better audio buffering and backpressure handling.
- [ ] Expanded integration and concurrency testing.
- [ ] Monitoring and application health metrics.

👨‍💻 Author

Sagar Ulli

- GitHub: "@SagarUlli" (https://github.com/SagarUlli)
- Project Repository: "speech-to-text" (https://github.com/SagarUlli/speech-to-text)

---

<p align="center">
  Built with ❤️ using Java, Spring Boot, WebSocket, and AI-powered speech recognition.
</p>
