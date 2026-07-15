package com.arshad.studdy_app_android_only.ui.student.proctoring;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages CameraX and ML Kit Face Detection for exam proctoring.
 * Tracks face presence, multi-face presence, and head orientation (looking away).
 */
public class FaceDetectionManager {

    private static final String TAG = "FaceDetectionManager";

    public interface ProctoringListener {
        void onNoFaceDetected();
        void onMultipleFacesDetected();
        void onLookingAwayDetected();
        void onNormalState();
    }

    private final Context context;
    private final LifecycleOwner lifecycleOwner;
    private final PreviewView previewView;
    private final ProctoringListener listener;
    
    private ExecutorService cameraExecutor;
    private FaceDetector faceDetector;
    private ProcessCameraProvider cameraProvider;

    // Time-based warning triggers (in milliseconds)
    private static final long TIME_THRESHOLD_NO_FACE = 3000;       // 3 seconds
    private static final long TIME_THRESHOLD_LOOKING_AWAY = 2000;   // 2 seconds
    private static final float TURN_ANGLE_THRESHOLD = 30f;          // Euler Y angle in degrees

    private long lastNormalTimestamp = System.currentTimeMillis();
    private long noFaceStartTimestamp = 0;
    private long lookingAwayStartTimestamp = 0;

    public FaceDetectionManager(Context context, LifecycleOwner lifecycleOwner, PreviewView previewView, ProctoringListener listener) {
        this.context = context;
        this.lifecycleOwner = lifecycleOwner;
        this.previewView = previewView;
        this.listener = listener;
        this.cameraExecutor = Executors.newSingleThreadExecutor();

        // Configure Face Detector for fast execution
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build();
        this.faceDetector = FaceDetection.getClient(options);
    }

    public void startProctoring() {
        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(context);
        providerFuture.addListener(() -> {
            try {
                cameraProvider = providerFuture.get();
                bindProctoringUseCases();
            } catch (Exception e) {
                Log.e(TAG, "Failed to start proctoring camera Provider", e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    public void stopProctoring() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (faceDetector != null) {
            faceDetector.close();
        }
    }

    private void bindProctoringUseCases() {
        if (cameraProvider == null) return;

        // 1. Preview use case (optional display in corner)
        Preview preview = new Preview.Builder().build();
        if (previewView != null) {
            preview.setSurfaceProvider(previewView.getSurfaceProvider());
        }

        // 2. Image analysis use case for face recognition
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
            @OptIn(markerClass = ExperimentalGetImage.class)
            @Override
            public void analyze(@NonNull ImageProxy imageProxy) {
                android.media.Image mediaImage = imageProxy.getImage();
                if (mediaImage != null) {
                    InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                    faceDetector.process(image)
                            .addOnSuccessListener(faces -> {
                                processFaceResults(faces);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Face detection failed", e);
                            })
                            .addOnCompleteListener(task -> {
                                imageProxy.close();
                            });
                } else {
                    imageProxy.close();
                }
            }
        });

        // Use front camera for proctoring
        CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis);
        } catch (Exception e) {
            Log.e(TAG, "Failed to bind proctoring use cases to lifecycle", e);
        }
    }

    private void processFaceResults(List<Face> faces) {
        long now = System.currentTimeMillis();

        if (faces == null || faces.isEmpty()) {
            // Case 1: No face detected
            lookingAwayStartTimestamp = 0;
            if (noFaceStartTimestamp == 0) {
                noFaceStartTimestamp = now;
            } else if (now - noFaceStartTimestamp >= TIME_THRESHOLD_NO_FACE) {
                listener.onNoFaceDetected();
            }
            return;
        }

        noFaceStartTimestamp = 0;

        if (faces.size() > 1) {
            // Case 2: Multiple faces detected (Immediate alert)
            lookingAwayStartTimestamp = 0;
            listener.onMultipleFacesDetected();
            return;
        }

        // Case 3: Single face check orientation
        Face face = faces.get(0);
        float headEulerY = face.getHeadEulerAngleY(); // Left/Right turn angle

        if (Math.abs(headEulerY) > TURN_ANGLE_THRESHOLD) {
            // Turning head left or right (Euler Y angle > threshold)
            if (lookingAwayStartTimestamp == 0) {
                lookingAwayStartTimestamp = now;
            } else if (now - lookingAwayStartTimestamp >= TIME_THRESHOLD_LOOKING_AWAY) {
                listener.onLookingAwayDetected();
            }
        } else {
            // Everything normal
            lookingAwayStartTimestamp = 0;
            listener.onNormalState();
        }
    }
}
