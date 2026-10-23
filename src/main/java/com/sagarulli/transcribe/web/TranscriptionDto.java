package com.sagarulli.transcribe.web;

import com.sagarulli.transcribe.domain.Transcription;
import java.time.Instant;

public record TranscriptionDto(Long id, String title, String content, String language, String engine,
                               Integer durationSeconds, Instant createdAt, Instant updatedAt) {
    public static TranscriptionDto from(Transcription t) {
        return new TranscriptionDto(t.getId(), t.getTitle(), t.getContent(), t.getLanguage(), t.getEngine(),
                t.getDurationSeconds(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
