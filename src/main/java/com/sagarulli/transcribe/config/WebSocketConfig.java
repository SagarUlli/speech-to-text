package com.sagarulli.transcribe.config;

import com.sagarulli.transcribe.web.TranscribeWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final TranscribeWebSocketHandler handler;

    public WebSocketConfig(TranscribeWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/transcribe").setAllowedOriginPatterns("*");
    }

    /** Allow ~1 MB binary frames (audio chunks are tiny, but leave headroom). */
    @org.springframework.context.annotation.Bean
    public org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean wsContainer() {
        var c = new org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean();
        c.setMaxBinaryMessageBufferSize(1 << 20);
        c.setMaxTextMessageBufferSize(1 << 16);
        return c;
    }
}
