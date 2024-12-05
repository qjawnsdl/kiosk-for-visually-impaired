package com.example.myapplication;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
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
    private ProcessCameraProvider cameraProvider;
    private ImageAnalysis imageAnalysis;
    private boolean isQuestionAsked = false;
    private boolean isListening = false;
    private boolean isProcessingFrame = false;

    private static final int REQUEST_RECORD_AUDIO_PERMISSION_CODE = 1;
    private static final int REQUEST_CAMERA_PERMISSION_CODE = 2;

    private RecognitionListener recognitionListener;
    private boolean isListeningForYesNo = false;
    private boolean isListeningForBusNumber = false;

    private Handler timeoutHandler;
    private Runnable timeoutRunnable;

    private boolean isCooldown = false; // 쿨다운 플래그 추가

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Timeout 핸들러 초기화
        timeoutHandler = new Handler();

        // 권한 요청
        requestPermissions();

        // TextToSpeech 초기화
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.KOREAN);
            }
        });

        // SpeechRecognizer 초기화
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            initializeRecognitionListener();
            speechRecognizer.setRecognitionListener(recognitionListener);
        } else {
            Toast.makeText(this, "이 디바이스에서는 음성 인식이 지원되지 않습니다.", Toast.LENGTH_SHORT).show();
            finish();
        }

        // PoseDetector 초기화
        PoseDetectorOptions options = new PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build();
        poseDetector = PoseDetection.getClient(options);

        // CameraX 초기화
        initializeCamera();
    }

    private void requestPermissions() {
        // 오디오 권한 요청
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION_CODE);
        }

        // 카메라 권한 요청
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION_CODE);
        }
    }

    private void initializeRecognitionListener() {
        recognitionListener = new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.d("SpeechRecognizer", "Ready for speech");
            }

            @Override
            public void onBeginningOfSpeech() {
                Log.d("SpeechRecognizer", "Speech input started");
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                // 음성 입력의 소리 크기 변화를 감지
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
                // 사용자가 말한 음성 데이터를 버퍼로 받을 때 호출
            }

            @Override
            public void onEndOfSpeech() {
                Log.d("SpeechRecognizer", "Speech input ended");
            }

            @Override
            public void onError(int error) {
                String errorMessage = getErrorText(error);
                Log.e("SpeechRecognizer", "Error occurred: " + errorMessage);
                isListening = false; // 상태 업데이트
                stopTimeout(); // 타임아웃 중지
                speak("응답을 인식하지 못했습니다. 다시 시도해 주세요.");
                resetToPoseDetection();
            }

            @Override
            public void onResults(Bundle results) {
                stopTimeout(); // 타임아웃 핸들러 중지
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String response = matches.get(0).toLowerCase();
                    Log.d("SpeechRecognizer", "인식된 결과: " + response);
                    if (isListeningForYesNo) {
                        handleYesNoResponse(response);
                    } else if (isListeningForBusNumber) {
                        handleBusNumberResponse(response);
                    }
                } else {
                    speak("응답을 인식하지 못했습니다. 다시 시도해 주세요.");
                    resetToPoseDetection();
                }
                isListening = false; // 상태 업데이트
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                // 음성 인식의 중간 결과를 받을 때 호출
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
                // 이벤트 발생 시 호출
            }
        };
    }

    private String getErrorText(int errorCode) {
        String message;
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                message = "오디오 녹음 오류";
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                message = "클라이언트 오류";
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                message = "권한 없음";
                break;
            case SpeechRecognizer.ERROR_NETWORK:
                message = "네트워크 오류";
                break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                message = "네트워크 타임아웃";
                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
                message = "일치하는 결과 없음";
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                message = "음성 인식기가 바쁨";
                break;
            case SpeechRecognizer.ERROR_SERVER:
                message = "서버 오류";
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                message = "입력된 음성 없음";
                break;
            default:
                message = "알 수 없는 오류";
                break;
        }
        return message;
    }

    private void handleYesNoResponse(String response) {
        if (response.contains("예") || response.contains("네")) {
            speak("이용하실 버스 번호를 말씀해 주세요.");
            startListeningForBusNumber(); // TTS와 동시에 음성 인식 시작
        } else {
            speak("여기는 시각장애인 전용 키오스크입니다.");
            resetToPoseDetection();
        }
    }

    private void handleBusNumberResponse(String response) {
        String spokenNumber = normalizeBusNumber(response);
        if (spokenNumber == null) {
            speak("버스 번호를 인식하지 못했습니다. 다시 말씀해 주세요.");
            startListeningForBusNumber(); // TTS와 동시에 음성 인식 시작
        } else {
            Intent intent = new Intent(MainActivity.this, BusApiActivity.class);
            intent.putExtra("busNumber", spokenNumber);
            startActivity(intent);
            // isQuestionAsked를 재설정하지 않음
            Log.d("MainActivity", "Navigating to BusApiActivity with busNumber: " + spokenNumber);
        }
    }

    private void resetToPoseDetection() {
        Log.d("MainActivity", "resetToPoseDetection called");
        isQuestionAsked = false;
    }

    private void initializeCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build();

                bindPreview(cameraSelector);
            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraX", "Camera initialization failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(CameraSelector cameraSelector) {
        if (cameraProvider == null) return;

        cameraProvider.unbindAll();

        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageProxy -> {
            if (!isQuestionAsked && !isCooldown) { // 쿨다운 중에는 포즈 감지 일시 중지
                detectPose(imageProxy);
            } else {
                imageProxy.close();
            }
        });

        cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis);
    }

    private void detectPose(ImageProxy imageProxy) {
        if (isProcessingFrame) {
            imageProxy.close();
            return;
        }
        isProcessingFrame = true;

        try {
            if (imageProxy.getImage() == null) {
                imageProxy.close();
                isProcessingFrame = false;
                return;
            }

            InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

            poseDetector.process(image)
                    .addOnSuccessListener(pose -> {
                        if (pose != null && pose.getAllPoseLandmarks() != null && !pose.getAllPoseLandmarks().isEmpty() && !isQuestionAsked) {
                            isQuestionAsked = true;
                            Log.d("MainActivity", "Pose detected, asking question");
                            speak("시각장애인 이신가요?");
                            startListeningForYesNo(); // TTS와 동시에 음성 인식 시작
                        }
                    })
                    .addOnFailureListener(e -> Log.e("PoseDetection", "Pose detection failed", e))
                    .addOnCompleteListener(task -> {
                        isProcessingFrame = false;
                        imageProxy.close();
                    });
        } catch (Exception e) {
            Log.e("PoseDetection", "Exception during pose detection", e);
            isProcessingFrame = false;
            imageProxy.close();
        }
    }

    private void startListeningForYesNo() {
        if (isListening) {
            speechRecognizer.cancel();
            isListening = false;
        }
        isListeningForYesNo = true;
        isListeningForBusNumber = false;

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR"); // 한국어로 설정

        isListening = true;
        speechRecognizer.startListening(intent);

        // 타임아웃 핸들러 시작
        startTimeout();
    }

    private void startListeningForBusNumber() {
        if (isListening) {
            speechRecognizer.cancel();
            isListening = false;
        }
        isListeningForYesNo = false;
        isListeningForBusNumber = true;

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR"); // 한국어로 설정

        isListening = true;
        speechRecognizer.startListening(intent);

        // 타임아웃 핸들러 시작
        startTimeout();
    }

    private void startTimeout() {
        stopTimeout(); // 기존 타임아웃 제거
        timeoutRunnable = () -> {
            if (isListening) {
                speechRecognizer.cancel();
                isListening = false;
                speak("응답 시간이 초과되었습니다. 다시 시도해 주세요.");
                resetToPoseDetection();
            }
        };
        timeoutHandler.postDelayed(timeoutRunnable, 7000); // 7초 후 타임아웃
    }

    private void stopTimeout() {
        if (timeoutHandler != null && timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
        }
    }

    private String normalizeBusNumber(String busNumber) {
        busNumber = busNumber.replace("번", "")
                .replace("백", "0")
                .replace("천", "000")
                .replace("143", "343")
                .replace("313", "343")
                .replace("303", "343")
                .replace("43", "343")
                .replace("3343", "343")
                .replace("삼사삼", "343")
                .replace("사공일", "401")
                .replace("사백일", "401")
                .replace("이사일육", "2416")
                .replace("이천사백십육", "2416")
                .replace("사삼일팔", "4318")
                .replace("사천삼백십팔", "4318")
                .replace("사사이오", "4425")
                .replace("사천사백이십오", "4425")
                .replace("강남공일", "강남01")
                .replace("강남일", "강남01")
                .replace("강남공육", "강남06")
                .replace("강남육", "강남06");

        return busNumber.matches("\\d+|강남01|강남06") ? busNumber : null;
    }

    private void speak(String text) {
        if (textToSpeech != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("MainActivity", "onResume called");
        // BusApiActivity로부터 돌아왔을 때 쿨다운 시작
        isCooldown = true;
        new Handler().postDelayed(() -> {
            isCooldown = false;
            isQuestionAsked = false; // 쿨다운 후에 질문 상태 재설정
            Log.d("MainActivity", "Cooldown ended, isQuestionAsked reset to false");
        }, 3000); // 3초 후 쿨다운 해제
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
        if (poseDetector != null) {
            poseDetector.close();
        }
        stopTimeout();
        super.onDestroy();
    }

    // 권한 요청 결과 처리
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "마이크 사용 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else if (requestCode == REQUEST_CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "카메라 사용 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
