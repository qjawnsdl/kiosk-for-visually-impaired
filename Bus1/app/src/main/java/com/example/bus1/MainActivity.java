package com.example.bus1;

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
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

@ExperimentalGetImage
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Bus1";

    private TextToSpeech textToSpeech;
    private PoseDetector poseDetector;
    private SpeechRecognizer speechRecognizer;

    private boolean isPersonDetected = false;
    private boolean isUserInSession = false;
    private boolean isBusNumberRequested = false;

    private Handler timeoutHandler = new Handler();
    private Runnable timeoutRunnable;

    private static final String API_KEY = "39LvkKBDQ3ntFTS50jGpUGMSkfUC7tNdr%2FeaiK8tHCbtwAfGL%2BXaBpluvSHRUtnGlj%2FT94eglPA6l1qpSvQ6GA%3D%3D";  // 실제 API 키로 대체하세요
    private static final String STOP_ID = "124000414";     // 정류장 ID

    private static final int PERMISSION_REQUEST_CODE = 100;

    private static final int LISTENING_TIMEOUT = 10000; // 10초
    private static final int BUS_NUMBER_LISTENING_TIME = 5000; // 5초
    private static final int RETRY_DELAY = 2000; // 2초

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 권한 요청
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET)
                        != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA, Manifest.permission.INTERNET},
                    PERMISSION_REQUEST_CODE);
        } else {
            initializeComponents();  // 권한이 있으면 초기화 진행
        }
    }

    // 권한 요청 결과 처리
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);  // 상위 클래스 메서드 호출

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean permissionsGranted = true;
            for (int result : grantResults) {
                permissionsGranted &= (result == PackageManager.PERMISSION_GRANTED);
            }
            if (permissionsGranted) {
                initializeComponents();  // 권한이 허용되면 초기화 진행
            } else {
                // 권한이 거부되었을 경우 처리
                finish();
            }
        }
    }

    private void initializeComponents() {
        // TextToSpeech 초기화
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.KOREAN);
                startCamera();
            }
        });

        // PoseDetector 초기화
        PoseDetectorOptions options = new PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build();
        poseDetector = PoseDetection.getClient(options);

        // SpeechRecognizer 초기화
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new SpeechRecognitionListener());
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindAnalysis(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera initialization failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindAnalysis(@NonNull ProcessCameraProvider cameraProvider) {
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .build();

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageProxy -> {
            if (!isUserInSession) {
                detectPoseFromImage(imageProxy);
            } else {
                imageProxy.close();
            }
        });

        cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis);
    }

    private void detectPoseFromImage(ImageProxy imageProxy) {
        if (imageProxy == null || imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }


        poseDetector.process(image)
                .addOnSuccessListener(pose -> {
                    if (!isPersonDetected && pose.getAllPoseLandmarks().size() > 0) {
                        isPersonDetected = true;
                        isUserInSession = true;
                        askIfVisuallyImpaired();
                    } else if (pose.getAllPoseLandmarks().size() == 0) {
                        isPersonDetected = false;
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Pose detection error", e))
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void askIfVisuallyImpaired() {
        speak("시각장애인 이신가요?");
        startListeningWithTimeout(LISTENING_TIMEOUT);
    }

    private void askForBusNumber() {
        speak("이용하실 버스 번호를 말씀해주세요.");
        isBusNumberRequested = true;
        startListeningWithTimeout(BUS_NUMBER_LISTENING_TIME);
    }

    private void startListeningWithTimeout(int timeout) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREAN.toString());
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizer.startListening(intent);

        // 타임아웃 설정
        timeoutHandler.removeCallbacksAndMessages(null);
        timeoutRunnable = () -> {
            speechRecognizer.stopListening();
            resetSession();
        };
        timeoutHandler.postDelayed(timeoutRunnable, timeout);
    }

    private void resetSession() {
        isUserInSession = false;
        isBusNumberRequested = false;
        isPersonDetected = false;
        timeoutHandler.removeCallbacksAndMessages(null);
        // 처음으로 돌아감
    }

    private void speak(String text) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TTS_ID");
    }

    // 버스 번호로부터 버스 노선 ID를 얻는 메서드
    private String getBusRouteId(String busNumber) throws Exception {
        String urlStr = "http://ws.bus.go.kr/api/rest/busRouteInfo/getBusRouteList?serviceKey=" + API_KEY + "&strSrch=" + busNumber;

        URL url = new URL(urlStr);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        InputSource is = new InputSource(new StringReader(convertStreamToString(connection.getInputStream())));
        Document doc = builder.parse(is);

        NodeList itemList = doc.getElementsByTagName("itemList");
        for (int i = 0; i < itemList.getLength(); i++) {
            Element item = (Element) itemList.item(i);
            String busRouteNm = item.getElementsByTagName("busRouteNm").item(0).getTextContent();
            if (busRouteNm.equals(busNumber)) {
                String busRouteId = item.getElementsByTagName("busRouteId").item(0).getTextContent();
                connection.disconnect();
                return busRouteId;
            }
        }
        connection.disconnect();
        return null;
    }

    // 버스 도착 정보를 가져오는 메서드
    private void getBusArrivalInfo(String busNumber) {
        new Thread(() -> {
            try {
                String busRouteId = getBusRouteId(busNumber);
                if (busRouteId == null) {
                    runOnUiThread(() -> {
                        speak("죄송합니다, 버스 번호 인식에 실패하였습니다.");
                        retryAfterDelay();
                    });
                    return;
                }

                String urlStr = "http://ws.bus.go.kr/api/rest/arrive/getArrInfoByRouteAll?serviceKey=" + API_KEY + "&busRouteId=" + busRouteId;

                URL url = new URL(urlStr);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                InputSource is = new InputSource(new StringReader(convertStreamToString(connection.getInputStream())));
                Document doc = builder.parse(is);

                NodeList itemList = doc.getElementsByTagName("itemList");
                boolean found = false;
                for (int i = 0; i < itemList.getLength(); i++) {
                    Element item = (Element) itemList.item(i);
                    String stId = item.getElementsByTagName("stId").item(0).getTextContent();
                    if (stId.equals(STOP_ID)) {
                        String busArriveTime = item.getElementsByTagName("arrmsg1").item(0).getTextContent();
                        runOnUiThread(() -> {
                            speak(busNumber + "번 버스는 " + busArriveTime + "에 도착합니다.");
                            resetSession();
                        });
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    runOnUiThread(() -> {
                        speak("죄송합니다, 버스 번호 인식에 실패하였습니다.");
                        retryAfterDelay();
                    });
                }

                connection.disconnect();

            } catch (Exception e) {
                runOnUiThread(() -> {
                    speak("죄송합니다, 버스 번호 인식에 실패하였습니다.");
                    retryAfterDelay();
                });
            }
        }).start();
    }

    private void retryAfterDelay() {
        new Handler().postDelayed(() -> askForBusNumber(), RETRY_DELAY);
    }

    // InputStream을 String으로 변환하는 메서드
    private String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is, "UTF-8").useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
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
        timeoutHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private class SpeechRecognitionListener implements RecognitionListener {

        @Override
        public void onReadyForSpeech(Bundle params) {
            Log.i(TAG, "Ready for speech");
        }

        @Override
        public void onBeginningOfSpeech() {
            Log.i(TAG, "Speech beginning");
            timeoutHandler.removeCallbacks(timeoutRunnable);  // 사용자가 말하기 시작하면 타임아웃 취소
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
            Log.i(TAG, "Speech ended");
        }

        @Override
        public void onError(int error) {
            Log.e(TAG, "Error occurred: " + error);
            if (isBusNumberRequested) {
                speak("죄송합니다, 버스 번호 인식에 실패하였습니다.");
                retryAfterDelay();
            } else {
                resetSession();
            }
        }

        @Override
        public void onResults(Bundle results) {
            timeoutHandler.removeCallbacksAndMessages(null);  // 타임아웃 취소
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String response = matches.get(0).replaceAll("\\s+", "");
                if (isBusNumberRequested) {
                    getBusArrivalInfo(response);
                } else {
                    handleUserResponse(response);
                }
            } else {
                if (isBusNumberRequested) {
                    speak("죄송합니다, 버스 번호 인식에 실패하였습니다.");
                    retryAfterDelay();
                } else {
                    resetSession();
                }
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

    private void handleUserResponse(String response) {
        response = response.toLowerCase().replaceAll("\\s+", "");
        if (response.contains("예")) {
            askForBusNumber();
        } else if (response.contains("아니요")) {
            speak("이 키오스크는 시각장애인을 위한 키오스크입니다.");
            resetSession();
        } else {
            resetSession();
        }
    }
}
