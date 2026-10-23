package com.sagarulli.transcribe.engine;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sagarulli.transcribe.config.TranscribeProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Streams audio to the Gemini Live API (BidiGenerateContent WebSocket) and
 * reads back input-audio transcriptions. Language auto-detection is enabled so
 * Hindi/English code-mixed speech ("Hinglish") is handled in a single stream.
 * The API key never leaves the server.
 */
@Component
public class GeminiLiveEngine implements SttEngine {
	private static final Logger log = LoggerFactory.getLogger(GeminiLiveEngine.class);
	private static final String ENDPOINT = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta"
			+ ".GenerativeService.BidiGenerateContent?key=";

	private final TranscribeProperties props;
	private final ObjectMapper mapper = new ObjectMapper();
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	public GeminiLiveEngine(TranscribeProperties props) {
		this.props = props;
	}

	@Override
	public String id() {
		return "gemini";
	}

	@Override
	public String label() {
		return "Gemini Live (cloud, auto language detect)";
	}

	@Override
	public boolean isAvailable() {
		return !props.gemini().apiKey().isBlank();
	}

	@Override
	public SttSession open(SttListener listener) {
		GeminiSession s = new GeminiSession(listener);
		s.connect();
		return s;
	}

	/** Builds the one-time setup frame. Package-private for tests. */
	ObjectNode setupMessage() {
		ObjectNode root = mapper.createObjectNode();
		ObjectNode setup = root.putObject("setup");
		setup.put("model", "models/" + props.gemini().model());
		setup.putObject("generationConfig").putArray("responseModalities").add("TEXT");
		setup.putObject("inputAudioTranscription").putArray("languageCodes"); // empty = auto-detect
		return root;
	}

	/**
	 * Extracts transcript events from one server frame. Package-private for tests.
	 */
	public static void dispatch(JsonNode msg, SttListener l) {
		JsonNode sc = msg.path("serverContent");
		String interim = sc.path("interimInputTranscription").path("text").asString("");
		if (!interim.isEmpty())
			l.onInterim(interim);
		String fin = sc.path("inputTranscription").path("text").asString("");
		if (!fin.isEmpty())
			l.onFinal(fin);
	}

	private final class GeminiSession implements SttSession, WebSocket.Listener {
		private final SttListener listener;
		private final ByteArrayOutputStream frame = new ByteArrayOutputStream();
		private final List<byte[]> pending = new ArrayList<>(); // audio queued until setupComplete
		private volatile WebSocket ws;
		private volatile boolean ready;
		private volatile boolean closed;
		private CompletableFuture<WebSocket> sendChain = CompletableFuture.completedFuture(null);

		GeminiSession(SttListener listener) {
			this.listener = listener;
		}

		void connect() {
			http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10))
					.buildAsync(URI.create(ENDPOINT + props.gemini().apiKey()), this).exceptionally(ex -> {
						listener.onError("Could not reach Gemini Live: " + rootMessage(ex));
						listener.onClosed();
						return null;
					});
		}

		// ---- out bound
		// -------------------------------------------------------------------
		private synchronized void send(String json) {
			WebSocket w = ws;
			if (w == null || closed)
				return;
			// WebSocket.sendText must not be called concurrently: chain the sends.
			sendChain = sendChain.handle((r, e) -> (WebSocket) null).thenCompose(x -> w.sendText(json, true));
		}

		private String audioJson(byte[] pcm) {
			ObjectNode root = mapper.createObjectNode();
			ObjectNode audio = root.putObject("realtimeInput").putObject("audio");
			audio.put("data", Base64.getEncoder().encodeToString(pcm));
			audio.put("mimeType", "audio/pcm;rate=16000");
			return root.toString();
		}

		@Override
		public synchronized void sendAudio(byte[] pcm) {
			if (closed)
				return;
			if (!ready) {
				pending.add(pcm);
				return;
			}
			send(audioJson(pcm));
		}

		@Override
		public synchronized void finish() {
			if (closed)
				return;
			pending.forEach(p -> send(audioJson(p)));
			pending.clear();
			send("{\"realtimeInput\":{\"audioStreamEnd\":true}}");
		}

		@Override
		public synchronized void close() {
			if (closed)
				return;
			closed = true;
			WebSocket w = ws;
			if (w != null)
				w.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
		}

		// ---- inbound
		// --------------------------------------------------------------------
		@Override
		public void onOpen(WebSocket webSocket) {
			this.ws = webSocket;
			send(setupMessage().toString());
			webSocket.request(1);
		}

		@Override
		public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) {
			frame.writeBytes(data.toString().getBytes(StandardCharsets.UTF_8));
			if (last)
				flushFrame();
			w.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onBinary(WebSocket w, ByteBuffer data, boolean last) {
			byte[] b = new byte[data.remaining()];
			data.get(b);
			frame.writeBytes(b); // Live API often delivers JSON in binary frames
			if (last)
				flushFrame();
			w.request(1);
			return null;
		}

		private void flushFrame() {
			String json = frame.toString(StandardCharsets.UTF_8);
			frame.reset();
			try {
				JsonNode msg = mapper.readTree(json);
				if (msg.has("setupComplete")) {
					synchronized (this) {
						ready = true;
						pending.forEach(p -> send(audioJson(p)));
						pending.clear();
					}
					listener.onReady();
				} else if (msg.has("error")) {
					listener.onError(msg.path("error").path("message").asString(msg.path("error").toString()));
				} else {
					dispatch(msg, listener);
				}
			} catch (Exception e) {
				log.warn("Unparseable Gemini frame: {}", e.getMessage());
			}
		}

		@Override
		public CompletionStage<?> onClose(WebSocket w, int code, String reason) {
			if (!closed && code != WebSocket.NORMAL_CLOSURE) {
				listener.onError("Gemini closed the stream (" + code + "): " + reason);
			}
			closed = true;
			listener.onClosed();
			return null;
		}

		@Override
		public void onError(WebSocket w, Throwable error) {
			if (!closed)
				listener.onError("Gemini stream error: " + rootMessage(error));
			closed = true;
			listener.onClosed();
		}
	}

	private static String rootMessage(Throwable t) {
		while (t.getCause() != null)
			t = t.getCause();
		return String.valueOf(t.getMessage());
	}
}
