package com.sagarulli.transcribe.engine;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sagarulli.transcribe.config.TranscribeProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Open-source, self-hosted option: any OpenAI-compatible Whisper server
 * (Speeches / faster-whisper-server). Audio is cut into short chunks and posted
 * to {@code /v1/audio/transcriptions}. Language is left unset so Whisper
 * auto-detects; a HindiEnglish prompt biases it toward Roman-script Hindi mixed
 * with English.
 */
@Component
public class WhisperEngine implements SttEngine {
	private static final Logger log = LoggerFactory.getLogger(WhisperEngine.class);
	private static final int SAMPLE_RATE = 16_000;

	private final TranscribeProperties props;
	private final ObjectMapper mapper = new ObjectMapper();
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	public WhisperEngine(TranscribeProperties props) {
		this.props = props;
	}

	@Override
	public String id() {
		return "whisper";
	}

	@Override
	public String label() {
		return "Whisper (self-hosted, open source)";
	}

	@Override
	public boolean isAvailable() {
		return !props.whisper().baseUrl().isBlank();
	}

	@Override
	public SttSession open(SttListener listener) {
		listener.onReady();
		return new WhisperSession(listener);
	}

	/** Wraps raw PCM in a minimal WAV container. Package-private for tests. */
	static byte[] toWav(byte[] pcm) {
		ByteBuffer b = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
		b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + pcm.length)
				.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) 1)
				.putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2).putShort((short) 2).putShort((short) 16)
				.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(pcm.length).put(pcm);
		return b.array();
	}

	private final class WhisperSession implements SttSession {
		private final SttListener listener;
		private final ExecutorService worker = Executors.newSingleThreadExecutor(); // keeps chunks ordered
		private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
		private final int chunkBytes = Math.max(1, props.whisper().chunkSeconds()) * SAMPLE_RATE * 2;
		private volatile boolean closed;

		WhisperSession(SttListener listener) {
			this.listener = listener;
		}

		@Override
		public synchronized void sendAudio(byte[] pcm) {
			if (closed)
				return;
			buf.writeBytes(pcm);
			if (buf.size() >= chunkBytes)
				flush();
		}

		private void flush() {
			byte[] chunk = buf.toByteArray();
			buf.reset();
			if (chunk.length < SAMPLE_RATE / 2)
				return; // < 0.5 s: nothing worth transcribing
			worker.submit(() -> transcribe(chunk));
		}

		private void transcribe(byte[] pcm) {
			try {
				String boundary = "----tx" + UUID.randomUUID();
				ByteArrayOutputStream body = new ByteArrayOutputStream();
				part(body, boundary, "model", props.whisper().model());
				part(body, boundary, "response_format", "json");
				part(body, boundary, "temperature", "0");
				if (!props.whisper().prompt().isBlank())
					part(body, boundary, "prompt", props.whisper().prompt());
				body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; "
						+ "filename=\"chunk.wav\"\r\nContent-Type: audio/wav\r\n\r\n")
						.getBytes(StandardCharsets.UTF_8));
				body.writeBytes(toWav(pcm));
				body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

				String base = props.whisper().baseUrl().replaceAll("/+$", "");
				HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(base + "/v1/audio/transcriptions"))
						.timeout(Duration.ofSeconds(60))
						.header("Content-Type", "multipart/form-data; boundary=" + boundary)
						.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
				if (!props.whisper().apiKey().isBlank()) {
					rb.header("Authorization", "Bearer " + props.whisper().apiKey());
				}
				HttpResponse<String> res = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
				if (res.statusCode() / 100 != 2) {
					listener.onError("Whisper server returned " + res.statusCode());
					return;
				}
				JsonNode json = mapper.readTree(res.body());
				String text = json.path("text").asString("").trim();
				if (!text.isEmpty())
					listener.onFinal(text + " ");
			} catch (Exception e) {
				log.warn("Whisper chunk failed", e);
				listener.onError("Whisper request failed: " + e.getMessage());
			}
		}

		private void part(ByteArrayOutputStream out, String boundary, String name, String value) {
			out.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
					+ value + "\r\n").getBytes(StandardCharsets.UTF_8));
		}

		@Override
		public synchronized void finish() {
			if (!closed)
				flush();
			worker.shutdown(); // queued chunks still complete
		}

		@Override
		public synchronized void close() {
			closed = true;
			worker.shutdown();
		}
	}
}
