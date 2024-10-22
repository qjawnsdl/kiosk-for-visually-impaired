package com.example.myapplication;

import static android.content.Intent.*;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.myapplication.R;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

@ExperimentalGetImage
public class MainActivity extends AppCompatActivity {

    private TextToSpeech textToSpeech;
    private PoseDetector poseDetector;  // ML Kit 포즈 감지기
    private SpeechRecognizer speechRecognizer;
    private boolean isPersonDetected = false;
    private boolean isQuestionAsked = false;
    private boolean isListening = false;
    private ImageAnalysis imageAnalysis;

    TextView textView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.textView);


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // TextToSpeech 초기화
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int langResult = textToSpeech.setLanguage(Locale.KOREAN);
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TextToSpeech", "Language not supported or missing data");
                } else {
                    Log.i("TextToSpeech", "Language set to Korean successfully");
                    // TextView의 텍스트를 음성으로 읽기
                    speak(textView.getText().toString());
                }
            } else {
                Log.e("TextToSpeech", "Initialization failed");
            }
        });

        // 버튼 클릭 리스너 설정

        // ML Kit의 포즈 감지 옵션 설정 (빠르고 경량화된 기본 감지 옵션 사용)
        PoseDetectorOptions options = new PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build();
        poseDetector = PoseDetection.getClient(options);

        // SpeechRecognizer 초기화
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new SpeechRecognitionListener());

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
        // CameraX 프리뷰 설정
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)  // 전면 캠 사용
                .build();

        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        // 실시간 프레임 분석
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageProxy -> {
            if (!isQuestionAsked) {
                detectPoseFromImage(imageProxy);  // 이미지 분석 및 포즈 감지
            }
        });

        cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis);  // CameraX 바인딩
    }

    // ML Kit을 사용하여 이미지에서 포즈 감지
    private void detectPoseFromImage(ImageProxy imageProxy) {
        @NonNull InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

        poseDetector.process(image)
                .addOnSuccessListener(pose -> runOnUiThread(() -> {
                    // 포즈 분석 성공
                    if (!isPersonDetected && pose.getAllPoseLandmarks().size() > 0 && !isQuestionAsked) {
                        isPersonDetected = true;  // 사람이 감지되었음을 표시
                        askQuestion();  // 질문을 던진다
                    } else if (pose.getAllPoseLandmarks().size() == 0) {
                        isPersonDetected = false;  // 사람이 감지되지 않음
                    }
                }))
                .addOnFailureListener(e -> Log.e("Pose Detection", "Error detecting pose", e))
                .addOnCompleteListener(task -> imageProxy.close());  // 이미지 분석 후 리소스 해제
    }

    private void askQuestion() {
        // 질문을 음성으로 출력
        speak("시각장애인 이신가요?");
        isQuestionAsked = true;  // 질문을 던졌으므로 플래그 설정

        // 음성 인식 시작
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

    private void stopListening() {
        if (isListening) {
            isListening = false;
            speechRecognizer.stopListening();
        }
    }

    // 사용자가 "예"라고 응답하면 버튼의 기능을 대신 실행
    private void handleUserResponse(String response) {
        if (response != null) {
            response = response.toLowerCase();  // 응답을 소문자로 변환
            if (response.contains("예") || response.contains("네")) {
                speak("이용하실 버스 번호를 말씀해 주세요");

                speak("버스 정보를 불러옵니다.");
                Intent intent = new Intent(MainActivity.this, BusApiActivity.class);
                startActivity(intent);
            } else if (response.contains("아니요")) {
                speak("이 키오스크는 시각장애인 전용입니다.");
            } else {
                speak("죄송합니다. 이해하지 못했습니다.");
            }
        } else {
            speak("응답을 인식할 수 없습니다.");
        }
        isQuestionAsked = false;
        stopListening();
    }

    // TextToSpeech를 통해 음성을 출력하는 메서드
    private void speak(String text) {
        Log.i("TextToSpeech", "Attempting to speak: " + text);
        if (textToSpeech != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        } else {
            Log.e("TextToSpeech", "TextToSpeech instance is null");
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

    private class SpeechRecognitionListener implements RecognitionListener {

        @Override
        public void onReadyForSpeech(Bundle params) {
            Log.i("SpeechRecognizer", "Ready for speech");
        }

        @Override
        public void onBeginningOfSpeech() {
            Log.i("SpeechRecognizer", "Speech beginning");
        }

        @Override
        public void onRmsChanged(float rmsdB) {
            // Do nothing
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
            // Do nothing
        }

        @Override
        public void onEndOfSpeech() {
            Log.i("SpeechRecognizer", "Speech ended");
        }

        @Override
        public void onError(int error) {
            Log.e("SpeechRecognizer", "Error occurred: " + error);
            stopListening();
        }

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String response = matches.get(0);
                handleUserResponse(response);  // 응답 처리
            } else {
                speak("응답을 인식할 수 없습니다.");
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            // Do nothing
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
            // Do nothing
        }
    }
}
