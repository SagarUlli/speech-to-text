package com.sagarulli.transcribe.engine;

/** A speech-to-text backend that accepts a live stream of 16 kHz mono 16-bit PCM. */
public interface SttEngine {

    /** Short id used by the UI/API, e.g. "gemini" or "whisper". */
    String id();

    /** Human readable label. */
    String label();

    /** True when the engine is configured (API key / server URL present). */
    boolean isAvailable();

    /** Opens a streaming session. Results are pushed to the listener from arbitrary threads. */
    SttSession open(SttListener listener) throws Exception;

    interface SttSession extends AutoCloseable {
        /** Raw little-endian 16-bit mono PCM @ 16 kHz. */
        void sendAudio(byte[] pcm);

        /** Signals end of the audio stream; engine should flush pending text. */
        void finish();

        @Override
        void close();
    }

    interface SttListener {
        void onReady();
        void onInterim(String text);
        void onFinal(String text);
        void onError(String message);
        void onClosed();
    }
}
