package com.example.robotvoice;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;
import org.vosk.android.RecognitionListener;

/** Main UI for the voice assistant. */
public final class MainActivity extends ComponentActivity implements RecognitionListener {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_RECORD_AUDIO = 1001;

    private TextView statusText;
    private TextView transcriptText;
    private TextView answerText;
    private ProgressBar progressBar;
    private Button talkButton;
    private MascotView mascotView;

    private PersianTtsEngine ttsEngine;
    private VoskRecognizer voskRecognizer;
    private volatile boolean ttsReady;
    private volatile boolean voskReady;
    private volatile boolean conversationActive;
    private volatile boolean waitingForAnswer;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        transcriptText = findViewById(R.id.transcriptText);
        answerText = findViewById(R.id.answerText);
        progressBar = findViewById(R.id.progressBar);
        talkButton = findViewById(R.id.talkButton);
        mascotView = findViewById(R.id.mascotView);

        talkButton.setEnabled(false);
        talkButton.setOnClickListener(v -> toggleConversation());
        mascotView.setState(MascotView.State.IDLE);

        startSystemTts();
        startVosk();
    }

    private void startSystemTts() {
        ttsEngine = new PersianTtsEngine(this, new PersianTtsEngine.Listener() {
            @Override public void onProgress(int percent, String message) {
                runOnUiThread(() -> { progressBar.setProgress(percent); statusText.setText(message); });
            }
            @Override public void onReady() {
                runOnUiThread(() -> { ttsReady = true; updateReadyState(); });
            }
            @Override public void onError(Exception error) {
                runOnUiThread(() -> {
                    ttsReady = false;
                    Log.e(TAG, "Android TTS error", error);
                    statusText.setText("خطا در موتور صدای فارسی: " +
                            (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
                    mascotView.setState(MascotView.State.IDLE);
                });
            }
            @Override public void onSpeechStarted() {
                // Defensive stop: never let Vosk hear the assistant's own voice.
                stopRecognizerForTts();
                runOnUiThread(() -> mascotView.setState(MascotView.State.SPEAKING));
            }
            @Override public void onSpeechFinished() { }
        });
    }

    private void startVosk() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            return;
        }

        voskRecognizer = new VoskRecognizer(this, "model-fa", new VoskRecognizer.ModelLoadListener() {
            @Override public void onProgress(int percent, String message) {
                runOnUiThread(() -> { if (!ttsReady) statusText.setText(message); });
            }
            @Override public void onModelReady() {
                runOnUiThread(() -> { voskReady = true; updateReadyState(); });
            }
            @Override public void onModelError(Exception exception) {
                runOnUiThread(() -> {
                    voskReady = false;
                    Log.e(TAG, "Vosk model error", exception);
                    if (!ttsReady) statusText.setText("خطا در مدل تشخیص گفتار: " +
                            (exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
                });
            }
        });
    }

    private void updateReadyState() {
        runOnUiThread(() -> {
            if (ttsReady && voskReady) {
                progressBar.setProgress(100);
                statusText.setText("آماده — برای شروع مکالمه دکمه را بزنید");
                talkButton.setEnabled(true);
                talkButton.setText("شروع گفت‌وگو");
                mascotView.setState(MascotView.State.IDLE);
            } else if (ttsReady) {
                statusText.setText("موتور صدا آماده است؛ در حال آماده‌سازی تشخیص گفتار...");
            } else if (voskReady) {
                statusText.setText("تشخیص گفتار آماده است؛ موتور صدا در دسترس نیست");
                talkButton.setEnabled(true);
                talkButton.setText("شروع گفت‌وگو");
            }
        });
    }

    private void toggleConversation() {
        if (!voskReady) return;
        if (conversationActive) stopConversation();
        else startConversation();
    }

    private void startConversation() {
        conversationActive = true;
        waitingForAnswer = false;
        talkButton.setText("توقف گفت‌وگو");
        statusText.setText("در حال گوش دادن...");
        mascotView.setState(MascotView.State.LISTENING);
        startRecognizer();
    }

    private void stopConversation() {
        conversationActive = false;
        waitingForAnswer = false;
        stopRecognizer();
        if (ttsEngine != null) ttsEngine.stop();
        talkButton.setText("شروع گفت‌وگو");
        statusText.setText("مکالمه متوقف شد");
        mascotView.setState(MascotView.State.IDLE);
    }

    private void startRecognizer() {
        if (!conversationActive || waitingForAnswer || !voskReady || voskRecognizer == null) return;
        try {
            voskRecognizer.start(this);
            runOnUiThread(() -> {
                if (conversationActive && !waitingForAnswer) mascotView.setState(MascotView.State.LISTENING);
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to start recognizer", e);
        }
    }

    private void stopRecognizer() {
        if (voskRecognizer != null) voskRecognizer.stop();
    }

    private void stopRecognizerForTts() {
        if (conversationActive) stopRecognizer();
    }

    @Override public void onPartialResult(String hypothesis) {
        if (!conversationActive || waitingForAnswer) return;
        String text = extractJsonText(hypothesis, "partial");
        if (!text.isEmpty()) runOnUiThread(() -> transcriptText.setText(text));
    }

    @Override public void onResult(String hypothesis) {
        if (!conversationActive || waitingForAnswer) return;
        String text = extractJsonText(hypothesis, "text");
        if (text.isEmpty()) return;

        // Immediately release the microphone before sending the request.
        waitingForAnswer = true;
        stopRecognizer();
        runOnUiThread(() -> {
            transcriptText.setText(text);
            statusText.setText("در حال دریافت پاسخ...");
            mascotView.setState(MascotView.State.IDLE);
        });

        OpenAIClient.send("", text, new OpenAIClient.Callback() {
            @Override public void onSuccess(String answer) {
                runOnUiThread(() -> {
                    if (!conversationActive) return;
                    answerText.setText(answer);
                    statusText.setText("در حال پخش پاسخ...");
                    mascotView.setState(MascotView.State.SPEAKING);
                    if (ttsEngine != null && ttsEngine.isReady()) {
                        ttsEngine.speak(answer, new PersianTtsEngine.Listener() {
                            @Override public void onSpeechStarted() {
                                stopRecognizerForTts();
                                runOnUiThread(() -> mascotView.setState(MascotView.State.SPEAKING));
                            }
                            @Override public void onSpeechFinished() {
                                onTtsFinished();
                            }
                            @Override public void onError(Exception e) {
                                runOnUiThread(() -> statusText.setText("خطا در پخش صدا: " + e.getMessage()));
                                onTtsFinished();
                            }
                        });
                    } else {
                        onTtsFinished();
                    }
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    statusText.setText("خطا: " + message);
                    waitingForAnswer = false;
                    if (conversationActive) {
                        mascotView.setState(MascotView.State.LISTENING);
                        startRecognizer();
                    }
                });
            }
        });
    }

    private void onTtsFinished() {
        if (!conversationActive) {
            waitingForAnswer = false;
            runOnUiThread(() -> mascotView.setState(MascotView.State.IDLE));
            return;
        }
        // Short dance transition, then reopen the microphone.
        runOnUiThread(() -> {
            mascotView.setState(MascotView.State.DANCING);
            statusText.setText("پاسخ تمام شد — دوباره گوش می‌دهم...");
        });
        new android.os.Handler(getMainLooper()).postDelayed(() -> {
            if (!conversationActive) return;
            waitingForAnswer = false;
            mascotView.setState(MascotView.State.LISTENING);
            statusText.setText("در حال گوش دادن...");
            startRecognizer();
        }, 900L);
    }

    @Override public void onFinalResult(String hypothesis) {
        // Vosk can send a final callback after onResult; do not process it twice.
        if (hypothesis != null && !hypothesis.isEmpty()) {
            String text = extractJsonText(hypothesis, "text");
            if (!text.isEmpty()) runOnUiThread(() -> transcriptText.setText(text));
        }
    }

    @Override public void onError(Exception exception) {
        runOnUiThread(() -> {
            if (!conversationActive) return;
            waitingForAnswer = false;
            statusText.setText("خطای میکروفون: " + exception.getMessage());
            mascotView.setState(MascotView.State.IDLE);
        });
    }

    @Override public void onTimeout() {
        runOnUiThread(() -> {
            if (!conversationActive || waitingForAnswer) return;
            statusText.setText("زمان گوش دادن تمام شد — دوباره گوش می‌دهم...");
            startRecognizer();
        });
    }

    private static String extractJsonText(String json, String key) {
        try { return new JSONObject(json == null ? "{}" : json).optString(key, "").trim(); }
        catch (Exception ignored) { return ""; }
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                                      @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVosk();
        } else {
            runOnUiThread(() -> statusText.setText("اجازه استفاده از میکروفون داده نشد"));
        }
    }

    @Override protected void onDestroy() {
        conversationActive = false;
        waitingForAnswer = false;
        if (voskRecognizer != null) voskRecognizer.close();
        if (ttsEngine != null) ttsEngine.close();
        super.onDestroy();
    }
}
