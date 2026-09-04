package com.example.robotvoice;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Vosk lifecycle wrapper.
 *
 * The Vosk model is bundled into the APK by GitHub Actions. At runtime we
 * copy it from assets to app-private storage and create the native Model on
 * a background thread. This avoids blocking the UI and also avoids the
 * StorageService/uuid requirement that caused the previous failure.
 */
public class VoskRecognizer {

    private static final String TAG = "VoskRecognizer";
    private static final float SAMPLE_RATE = 16000.0f;
    private static final String MODEL_DIR_NAME = "vosk-model-fa";
    private static final String READY_MARKER = ".model_ready";

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private Model model;
    private SpeechService speechService;
    private volatile boolean loading = false;
    private volatile boolean closed = false;
    private Exception loadError;

    public interface ModelLoadListener {
        void onProgress(int percent, String message);
        void onModelReady();
        void onModelError(Exception exception);
    }

    public VoskRecognizer(Context context, String assetModelPath,
                          final ModelLoadListener listener) {
        this.context = context.getApplicationContext();
        loading = true;

        Log.d(TAG, "Starting Vosk model preparation from assets/" + assetModelPath);
        notifyProgress(listener, 0, "در حال آماده‌سازی مدل... 0٪");

        executor.execute(() -> prepareModel(assetModelPath, listener));
    }

    private void prepareModel(String assetModelPath, ModelLoadListener listener) {
        File modelDir = new File(context.getFilesDir(), MODEL_DIR_NAME);

        try {
            if (closed) return;

            // Reuse a previously copied model. The native Model is still
            // created below, but the expensive asset copy is skipped.
            if (!new File(modelDir, READY_MARKER).isFile()) {
                deleteRecursively(modelDir);
                if (!modelDir.mkdirs() && !modelDir.isDirectory()) {
                    throw new IOException("Could not create model directory: " + modelDir);
                }

                long totalBytes = calculateAssetSize(assetModelPath);
                if (totalBytes <= 0) {
                    throw new IOException("Vosk model assets are empty: " + assetModelPath);
                }

                notifyProgress(listener, 1, "در حال کپی مدل... 1٪");
                copyAssets(assetModelPath, modelDir, totalBytes, new Progress() {
                    @Override
                    public void onBytesCopied(long copied, long total) {
                        int percent = (int) Math.min(99L, (copied * 100L) / Math.max(1L, total));
                        notifyProgress(listener, percent,
                                "در حال کپی مدل... " + percent + "٪");
                    }
                });

                // Only mark it ready after the copy completed successfully.
                File marker = new File(modelDir, READY_MARKER);
                if (!marker.createNewFile() && !marker.isFile()) {
                    throw new IOException("Could not create model ready marker");
                }
            } else {
                notifyProgress(listener, 70, "مدل از قبل آماده است... 70٪");
            }

            if (closed) return;

            notifyProgress(listener, 99, "در حال بارگذاری مدل Vosk... 99٪");
            Log.d(TAG, "Creating Vosk Model from " + modelDir.getAbsolutePath());

            Model loadedModel = new Model(modelDir.getAbsolutePath());

            if (closed) {
                loadedModel.close();
                return;
            }

            model = loadedModel;
            loading = false;
            notifyProgress(listener, 100, "مدل آماده شد — 100٪");

            mainHandler.post(() -> {
                if (!closed && listener != null) {
                    listener.onModelReady();
                }
            });

            Log.d(TAG, "Vosk model loaded successfully.");

        } catch (Exception e) {
            loading = false;
            loadError = e;
            Log.e(TAG, "Failed to prepare/load Vosk model.", e);

            mainHandler.post(() -> {
                if (!closed && listener != null) {
                    listener.onModelError(e);
                }
            });
        }
    }

    private interface Progress {
        void onBytesCopied(long copied, long total);
    }

    private long calculateAssetSize(String assetPath) throws IOException {
        return calculateAssetSize(context.getAssets(), assetPath);
    }

    private long calculateAssetSize(AssetManager assets, String path) throws IOException {
        String[] children = assets.list(path);
        if (children == null || children.length == 0) {
            try (java.io.InputStream in = assets.open(path)) {
                return in.available();
            }
        }

        long total = 0;
        for (String child : children) {
            String childPath = path + "/" + child;
            total += calculateAssetSize(assets, childPath);
        }
        return total;
    }

    private void copyAssets(String assetPath, File destination, long totalBytes,
                            Progress progress) throws IOException {
        CopyState state = new CopyState();
        copyAssetsRecursive(context.getAssets(), assetPath, destination, totalBytes, state, progress);
    }

    private static class CopyState {
        long copiedBytes = 0;
    }

    private void copyAssetsRecursive(AssetManager assets, String assetPath, File destination,
                                     long totalBytes, CopyState state, Progress progress)
            throws IOException {
        String[] children = assets.list(assetPath);

        if (children == null || children.length == 0) {
            File parent = destination.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("Could not create directory: " + parent);
            }

            try (java.io.InputStream input = assets.open(assetPath);
                 FileOutputStream output = new FileOutputStream(destination)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (closed) return;
                    output.write(buffer, 0, read);
                    state.copiedBytes += read;
                    progress.onBytesCopied(state.copiedBytes, totalBytes);
                }
            }
            return;
        }

        if (!destination.isDirectory() && !destination.mkdirs() && !destination.isDirectory()) {
            throw new IOException("Could not create directory: " + destination);
        }

        for (String child : children) {
            File childDestination = new File(destination, child);
            copyAssetsRecursive(assets, assetPath + "/" + child,
                    childDestination, totalBytes, state, progress);
        }
    }

    private void notifyProgress(ModelLoadListener listener, int percent, String message) {
        mainHandler.post(() -> {
            if (!closed && listener != null) {
                listener.onProgress(Math.max(0, Math.min(100, percent)), message);
            }
        });
    }

    private void deleteRecursively(File file) {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!file.delete()) {
            Log.w(TAG, "Could not delete: " + file.getAbsolutePath());
        }
    }

    public boolean isReady() {
        return !closed && model != null;
    }

    public boolean isLoading() {
        return loading;
    }

    public Exception getLoadError() {
        return loadError;
    }

    public void start(RecognitionListener listener) {
        if (closed) {
            Log.e(TAG, "Cannot start recognition: VoskRecognizer is closed.");
            return;
        }

        if (!isReady()) {
            Log.e(TAG, "Model is unavailable; recognition was not started.", loadError);
            return;
        }

        stop();

        try {
            Recognizer recognizer = new Recognizer(model, SAMPLE_RATE);
            speechService = new SpeechService(recognizer, SAMPLE_RATE);
            speechService.startListening(listener);
            Log.d(TAG, "Speech recognition started.");
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Failed to start speech service.", e);
            if (speechService != null) {
                speechService.shutdown();
                speechService = null;
            }
        }
    }

    public void stop() {
        if (speechService != null) {
            try {
                speechService.stop();
                speechService.shutdown();
            } catch (Exception e) {
                Log.w(TAG, "Error while stopping speech service.", e);
            } finally {
                speechService = null;
            }
        }
    }

    public void close() {
        if (closed) return;
        closed = true;
        loading = false;

        stop();
        executor.shutdownNow();

        if (model != null) {
            try {
                model.close();
            } catch (Exception e) {
                Log.w(TAG, "Error while closing Vosk model.", e);
            } finally {
                model = null;
            }
        }

        Log.d(TAG, "Vosk resources released.");
    }
}
