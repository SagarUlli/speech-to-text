package com.sagarulli.transcribe.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.sagarulli.transcribe.web.TranscribeWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
	private final TranscribeWebSocketHandler handler;

	public WebSocketConfig(TranscribeWebSocketHandler handler) {
		this.handler = handler;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		// Audio frames are ~3 KB (100 ms of 16 kHz PCM16), well under Tomcat's default
		// 8 KB
		// WebSocket buffer, so no custom container sizing is needed.
		registry.addHandler(handler, "/ws/transcribe").setAllowedOriginPatterns("*");
	}
}