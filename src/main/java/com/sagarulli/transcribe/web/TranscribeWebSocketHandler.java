package com.sagarulli.transcribe.web;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import com.sagarulli.transcribe.engine.EngineRegistry;
import com.sagarulli.transcribe.engine.SttEngine;

import tools.jackson.databind.ObjectMapper;

/**
 * Browser protocol on /WS/transcribe client -> server : text
 * {"type":"start","engine":"Gemini"} | {"type":"stop"} ; binary = PCM16 mono 16
 * kHz server -> client : text {"type":"ready|interim|final|error|closed",
 * "text"?: "...", "message"?: "..."}
 */
@Component
public class TranscribeWebSocketHandler extends AbstractWebSocketHandler {
	private static final Logger log = LoggerFactory.getLogger(TranscribeWebSocketHandler.class);

	private final EngineRegistry registry;
	private final ObjectMapper mapper = new ObjectMapper();
	private final Map<String, SttEngine.SttSession> sessions = new ConcurrentHashMap<>();

	public TranscribeWebSocketHandler(EngineRegistry registry) {
		this.registry = registry;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession raw) {
		// Engine callbacks arrive on other threads; the decorator serializes sends.
		raw.getAttributes().put("safe", new ConcurrentWebSocketSessionDecorator(raw, 10_000, 512 * 1024));
	}

	@Override
	protected void handleTextMessage(WebSocketSession raw, TextMessage message) throws IOException {
		var node = mapper.readTree(message.getPayload());
		String type = node.path("type").asString();
		if ("start".equals(type)) {
			start(raw, node.path("engine").asString(registry.defaultId()));
		} else if ("stop".equals(type)) {
			var s = sessions.get(raw.getId());
			if (s != null)
				s.finish();
		}
	}

	private void start(WebSocketSession raw, String engineId) {
		WebSocketSession out = safe(raw);
		if (sessions.containsKey(raw.getId())) {
			send(out, Map.of("type", "error", "message", "Already started"));
			return;
		}
		SttEngine engine = registry.find(engineId).filter(SttEngine::isAvailable).orElse(null);
		if (engine == null) {
			send(out, Map.of("type", "error", "message", "Engine '" + engineId
					+ "' is not configured on the server (set GEMINI_API_KEY or WHISPER_BASE_URL)."));
			return;
		}
		try {
			var session = engine.open(new SttEngine.SttListener() {
				public void onReady() {
					send(out, Map.of("type", "ready", "engine", engine.id()));
				}

				public void onInterim(String t) {
					send(out, Map.of("type", "interim", "text", t));
				}

				public void onFinal(String t) {
					send(out, Map.of("type", "final", "text", t));
				}

				public void onError(String m) {
					send(out, Map.of("type", "error", "message", m));
				}

				public void onClosed() {
					send(out, Map.of("type", "closed"));
				}
			});
			sessions.put(raw.getId(), session);
		} catch (Exception e) {
			log.warn("Failed to open engine {}", engineId, e);
			send(out, Map.of("type", "error", "message", "Failed to start engine: " + e.getMessage()));
		}
	}

	@Override
	protected void handleBinaryMessage(WebSocketSession raw, BinaryMessage message) {
		var s = sessions.get(raw.getId());
		if (s == null)
			return;
		var buf = message.getPayload();
		byte[] pcm = new byte[buf.remaining()];
		buf.get(pcm);
		s.sendAudio(pcm);
	}

	@Override
	public void afterConnectionClosed(WebSocketSession raw, CloseStatus status) {
		var s = sessions.remove(raw.getId());
		if (s != null)
			s.close();
	}

	@Override
	public void handleTransportError(WebSocketSession raw, Throwable ex) {
		log.debug("WS transport error: {}", ex.getMessage());
	}

	private WebSocketSession safe(WebSocketSession raw) {
		return (WebSocketSession) raw.getAttributes().get("safe");
	}

	private void send(WebSocketSession s, Map<String, ?> payload) {
		try {
			if (s.isOpen())
				s.sendMessage(new TextMessage(mapper.writeValueAsString(payload)));
		} catch (Exception e) {
			log.debug("send failed: {}", e.getMessage());
		}
	}
}
