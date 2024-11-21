package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

@ExperimentalGetImage
public class MainActivity extends AppCompatActivity {

    private TextToSpeech textToSpeech;
    private PoseDetector poseDetector;
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    private boolean isQuestionAsked = false;

    private ImageAnalysis imageAnalysis;
    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.textView);

        // TextToSpeech 초기화
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int langResult = textToSpeech.setLanguage(Locale.KOREAN);
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TextToSpeech", "Language not supported or missing data");
                }
            } else {
                Log.e("TextToSpeech", "Initialization failed");
            }
        });

        // ML Kit 포즈 감지 초기화
        PoseDetectorOptions options = new PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build();
        poseDetector = PoseDetection.getClient(options);

        // SpeechRecognizer 초기화
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new InitialRecognitionListener());

        // 카메라 초기화
        startCamera();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraX", "Camera initialization failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageProxy -> {
            if (!isQuestionAsked) {
                detectPoseFromImage(imageProxy);
            }
        });

        cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis);
    }

    private void detectPoseFromImage(ImageProxy imageProxy) {
        InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

        poseDetector.process(image)
                .addOnSuccessListener(pose -> runOnUiThread(() -> {
                    if (pose.getAllPoseLandmarks().size() > 0 && !isQuestionAsked) {
                        isQuestionAsked = true;
                        askQuestion();
                    }
                }))
                .addOnFailureListener(e -> Log.e("Pose Detection", "Error detecting pose", e))
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void askQuestion() {
        speak("시각장애인 이신가요?");
        startListening();
    }

    private void startListening() {
        if (!isListening) {
            isListening = true;
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "응답을 말해 주세요");
            speechRecognizer.startListening(intent);
        }
    }

    private void startListeningForBusNumber() {
        isListening = true;
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "버스 번호를 말씀해 주세요.");
        speechRecognizer.setRecognitionListener(new BusNumberRecognitionListener());
        speechRecognizer.startListening(intent);
    }

    private void speak(String text) {
        if (textToSpeech != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        super.onDestroy();
    }

    private class InitialRecognitionListener implements RecognitionListener {

        @Override
        public void onReadyForSpeech(Bundle params) {
        }

        @Override
        public void onBeginningOfSpeech() {
        }

        @Override
        public void onRmsChanged(float rmsdB) {
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
        }

        @Override
        public void onEndOfSpeech() {
        }

        @Override
        public void onError(int error) {
            stopListening();
        }

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String response = matches.get(0).toLowerCase();
                if (response.contains("예")) {
                    speak("이용하실 버스 번호를 말씀해 주세요.");
                    new Handler().postDelayed(MainActivity.this::startListeningForBusNumber, 2000);
                } else {
                    speak("다시 질문을 시작합니다.");
                }
            }
            stopListening();
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
        }
    }

    private class BusNumberRecognitionListener implements RecognitionListener {

        @Override
        public void onReadyForSpeech(Bundle params) {
        }

        @Override
        public void onBeginningOfSpeech() {
        }

        @Override
        public void onRmsChanged(float rmsdB) {
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
        }

        @Override
        public void onEndOfSpeech() {
        }

        @Override
        public void onError(int error) {
            MainActivity.this.stopListening();  // MainActivity의 stopListening() 호출
            MainActivity.this.speak("버스 번호를 인식하지 못했습니다. 다시 시도해 주세요.");
            MainActivity.this.startListeningForBusNumber();  // 다시 시작
        }

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String response = matches.get(0).toLowerCase();
                if (response.contains("3312")) {
                    MainActivity.this.speak("버스 정보를 불러옵니다.");
                    Intent intent = new Intent(MainActivity.this, BusApiActivity.class);
                    MainActivity.this.startActivity(intent);
                } else {
                    MainActivity.this.speak("다시 한 번 버스 번호를 말씀해 주세요.");
                    MainActivity.this.startListeningForBusNumber();
                }
            } else {
                MainActivity.this.speak(".");
                MainActivity.this.startListeningForBusNumber();
            }
            MainActivity.this.stopListening();
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
        }
    }
    private void stopListening() {
        if (speechRecognizer != null && isListening) {
            try {
                isListening = false; // 상태 플래그 초기화
                speechRecognizer.stopListening();
                Log.i("SpeechRecognizer", "SpeechRecognizer stopped successfully");
            } catch (Exception e) {
                Log.e("SpeechRecognizer", "Error while stopping SpeechRecognizer: " + e.getMessage(), e);
            }
        }
    }
}