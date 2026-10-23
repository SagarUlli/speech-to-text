package com.sagarulli.transcribe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "transcribe")
public record TranscribeProperties(
        @DefaultValue("gemini") String defaultEngine,
        @DefaultValue Gemini gemini,
        @DefaultValue Whisper whisper) {

    public record Gemini(@DefaultValue("") String apiKey,
                         @DefaultValue("gemini-3.5-transcribe-live") String model) {}

    public record Whisper(@DefaultValue("") String baseUrl,
                          @DefaultValue("Systran/faster-whisper-small") String model,
                          @DefaultValue("4") int chunkSeconds,
                          @DefaultValue("") String prompt,
                          @DefaultValue("") String apiKey) {}
}
