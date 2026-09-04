package com.example.robotvoice;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.Locale;
import java.util.UUID;

/** Uses the Android system's selected/default TTS engine and Persian voice. */
public final class PersianTtsEngine {
    private static final String TAG = "PersianTtsEngine";

    private final TextToSpeech tts;
    private final Listener initListener;
    private volatile boolean closed;
    private volatile boolean ready;
    private volatile Listener currentSpeechListener;

    public interface Listener {
        default void onProgress(int percent, String message) {}
        default void onReady() {}
        default void onError(Exception error) {}
        default void onSpeechStarted() {}
        default void onSpeechFinished() {}
    }

    public PersianTtsEngine(Context context, Listener listener) {
        initListener = listener;
        tts = new TextToSpeech(context.getApplicationContext(), status -> {
            if (closed) return;
            if (status != TextToSpeech.SUCCESS) {
                notifyError(listener, new IllegalStateException("Android TTS initialization failed: " + status));
                return;
            }

            int result = tts.setLanguage(new Locale("fa", "IR"));
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                notifyError(listener, new IllegalStateException("زبان فارسی در موتور TTS انتخاب‌شده پشتیبانی نمی‌شود."));
                return;
            }

            tts.setSpeechRate(1.0f);
            tts.setPitch(1.0f);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) {
                    if (closed) return;
                    Listener speech = currentSpeechListener;
                    if (speech != null) speech.onSpeechStarted();
                    if (initListener != null && initListener != speech) initListener.onSpeechStarted();
                }

                @Override public void onDone(String utteranceId) {
                    if (closed) return;
                    Listener speech = currentSpeechListener;
                    currentSpeechListener = null;
                    if (speech != null) speech.onSpeechFinished();
                    if (initListener != null && initListener != speech) initListener.onSpeechFinished();
                }

                @Override public void onError(String utteranceId) {
                    if (closed) return;
                    Listener speech = currentSpeechListener;
                    currentSpeechListener = null;
                    Exception error = new IllegalStateException("Android TTS playback failed.");
                    if (speech != null) speech.onError(error);
                    if (initListener != null && initListener != speech) initListener.onError(error);
                }

                @Override public void onError(String utteranceId, int errorCode) {
                    if (closed) return;
                    Listener speech = currentSpeechListener;
                    currentSpeechListener = null;
                    Exception error = new IllegalStateException("Android TTS playback failed: " + errorCode);
                    if (speech != null) speech.onError(error);
                    if (initListener != null && initListener != speech) initListener.onError(error);
                }
            });

            ready = true;
            if (listener != null) {
                listener.onProgress(100, "موتور صدای فارسی سیستم آماده است");
                listener.onReady();
            }
        });
    }

    public boolean isReady() { return !closed && ready; }

    public void speak(String text, Listener listener) {
        if (closed || !ready) {
            notifyError(listener, new IllegalStateException("موتور صدای فارسی آماده نیست."));
            return;
        }
        if (text == null || text.trim().isEmpty()) {
            if (listener != null) listener.onSpeechFinished();
            return;
        }

        currentSpeechListener = listener;
        String id = UUID.randomUUID().toString();
        int result;
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            result = tts.speak(text.trim(), TextToSpeech.QUEUE_FLUSH, new Bundle(), id);
        } else {
            result = tts.speak(text.trim(), TextToSpeech.QUEUE_FLUSH, null);
        }
        if (result == TextToSpeech.ERROR) {
            currentSpeechListener = null;
            notifyError(listener, new IllegalStateException("Android TTS could not start speaking."));
        }
    }

    public void stop() {
        currentSpeechListener = null;
        try { tts.stop(); } catch (Exception e) { Log.w(TAG, "TTS stop failed", e); }
    }

    public void close() {
        if (closed) return;
        closed = true;
        ready = false;
        currentSpeechListener = null;
        try { tts.stop(); tts.shutdown(); } catch (Exception e) { Log.w(TAG, "TTS shutdown failed", e); }
    }

    private static void notifyError(Listener listener, Exception error) {
        if (listener != null) listener.onError(error);
    }
}
