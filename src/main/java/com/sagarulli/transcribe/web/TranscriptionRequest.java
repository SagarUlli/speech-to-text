package com.sagarulli.transcribe.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TranscriptionRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull @Size(max = 200_000) String content,
        @Size(max = 40) String language,
        @Size(max = 40) String engine,
        @Min(0) Integer durationSeconds) {
}
