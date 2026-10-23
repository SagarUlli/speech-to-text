package com.sagarulli.transcribe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sagarulli.transcribe.config.TranscribeProperties;
import com.sagarulli.transcribe.engine.GeminiLiveEngine;
import com.sagarulli.transcribe.engine.SttEngine;

import tools.jackson.databind.ObjectMapper;

/**
 * Lives in the engine package's visibility via the public static dispatch +
 * public setup builder.
 */
class EngineUnitTest {
	private final TranscribeProperties props = new TranscribeProperties("gemini",
			new TranscribeProperties.Gemini("key", "gemini-3.5-transcribe-live"),
			new TranscribeProperties.Whisper("", "m", 4, "", ""));

	@Test
	void availabilityDependsOnConfig() {
		assertTrue(new GeminiLiveEngine(props).isAvailable());
		var none = new TranscribeProperties("gemini", new TranscribeProperties.Gemini("", "m"),
				new TranscribeProperties.Whisper("", "m", 4, "", ""));
		assertFalse(new GeminiLiveEngine(none).isAvailable());
	}

	@Test
	void parsesInterimAndFinalTranscripts() throws Exception {
		List<String> got = new ArrayList<>();
		SttEngine.SttListener l = new SttEngine.SttListener() {
			public void onReady() {
			}

			public void onInterim(String t) {
				got.add("i:" + t);
			}

			public void onFinal(String t) {
				got.add("f:" + t);
			}

			public void onError(String m) {
			}

			public void onClosed() {
			}
		};
		var m = new ObjectMapper();
		GeminiLiveEngine
				.dispatch(m.readTree("{\"serverContent\":{\"interimInputTranscription\":{\"text\":\"namas\"}}}"), l);
		GeminiLiveEngine
				.dispatch(m.readTree("{\"serverContent\":{\"inputTranscription\":{\"text\":\"नमस्ते hello\"}}}"), l);
		GeminiLiveEngine.dispatch(m.readTree("{\"usageMetadata\":{}}"), l);
		assertEquals(List.of("i:namas", "f:नमस्ते hello"), got);
	}
}
