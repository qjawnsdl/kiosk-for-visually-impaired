package com.example.myapplication;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class BusApiActivity extends AppCompatActivity {

    private TextToSpeech textToSpeech;
    private TextView textView;

    private static final String API_KEY = "Iccsp%2Bh4atStazP0ipDtFoT8fsL7hDKbQtu9nwiOW3azW7tSQ%2Br9B5P0lGxbvo2q23z8D3NTL14M4ncTOfdt6Q%3D%3D";
    private static final String STID = "122000141";

    private Handler handler = new Handler(); // 핸들러 추가

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bus_api);

        textView = findViewById(R.id.textView);

        // Intent에서 전달받은 버스 번호
        String busNumber = getIntent().getStringExtra("busNumber");
        if (busNumber == null || busNumber.isEmpty()) {
            textView.setText("버스 번호를 전달받지 못했습니다.");
            return;
        }

        Log.d("BusApiActivity", "사용자가 입력한 버스 번호: " + busNumber);

        // TextToSpeech 초기화
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.KOREAN);
                new GetBusInfoTask(busNumber).execute();
            }
        });
    }

    private class GetBusInfoTask extends AsyncTask<Void, Void, String> {
        private final String userInput;
        private boolean busFound = true; // 버스 발견 여부 플래그

        public GetBusInfoTask(String userInput) {
            this.userInput = userInput;
        }

        @Override
        protected String doInBackground(Void... voids) {
            String result;
            String busRouteId = "";
            String ord = "";

            // 입력된 버스 번호를 기반으로 busRouteId와 ord 매핑
            switch (userInput) {
                case "343":
                    busRouteId = "107000002";
                    ord = "47";
                    break;
                case "401":
                    busRouteId = "100100062";
                    ord = "75";
                    break;
                case "2416":
                    busRouteId = "104000010";
                    ord = "44";
                    break;
                case "4318":
                    busRouteId = "100100237";
                    ord = "26";
                    break;
                case "4425":
                    busRouteId = "100100609";
                    ord = "57";
                    break;
                case "강남01":
                    busRouteId = "122900003";
                    ord = "24";
                    break;
                case "강남06":
                    busRouteId = "122900005";
                    ord = "26";
                    break;
                default:
                    busFound = false;
                    return "해당 번호의 버스를 찾을 수 없습니다.";
            }

            String apiUrl = "http://ws.bus.go.kr/api/rest/arrive/getArrInfoByRoute?" +
                    "serviceKey=" + API_KEY +
                    "&stId=" + STID +
                    "&busRouteId=" + busRouteId +
                    "&ord=" + ord;

            try {
                URL url = new URL(apiUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                InputStream inputStream = connection.getInputStream();
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "UTF-8");

                XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                XmlPullParser xmlPullParser = factory.newPullParser();
                xmlPullParser.setInput(inputStreamReader);

                int eventType = xmlPullParser.getEventType();
                boolean isItemTag = false;
                String tag = "";
                String arrivalInfo1 = "";
                String arrivalInfo2 = "";

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    switch (eventType) {
                        case XmlPullParser.START_TAG:
                            tag = xmlPullParser.getName();
                            if (tag.equals("itemList")) {
                                isItemTag = true; // itemList 시작
                            } else if (isItemTag && tag.equals("arrmsg1")) {
                                arrivalInfo1 = xmlPullParser.nextText(); // arrmsg1 파싱
                                Log.d("BusApiActivity", "첫 번째 도착 정보: " + arrivalInfo1);
                            } else if (isItemTag && tag.equals("arrmsg2")) {
                                arrivalInfo2 = xmlPullParser.nextText(); // arrmsg2 파싱
                                Log.d("BusApiActivity", "두 번째 도착 정보: " + arrivalInfo2);
                            }
                            break;

                        case XmlPullParser.END_TAG:
                            if (xmlPullParser.getName().equals("itemList")) {
                                isItemTag = false; // itemList 종료
                            }
                            break;
                    }
                    eventType = xmlPullParser.next();
                }

                // 도착 정보가 없는 경우 기본 메시지 설정
                if (arrivalInfo1.isEmpty()) {
                    arrivalInfo1 = "첫 번째 도착 정보 없음";
                }
                if (arrivalInfo2.isEmpty()) {
                    arrivalInfo2 = "두 번째 도착 정보 없음";
                }

                result = "첫 번째 도착 정보: " + arrivalInfo1 + "\n" +
                        "두 번째 도착 정보: " + arrivalInfo2;

            } catch (Exception e) {
                Log.e("BusApiActivity", "API 호출 실패: " + e.getMessage());
                result = "버스 정보를 가져오는 데 실패했습니다.";
            }

            return result;
        }

        @Override
        protected void onPostExecute(String result) {
            textView.setText(result);
            if (textToSpeech != null) {
                textToSpeech.speak(result, TextToSpeech.QUEUE_FLUSH, null, null);
            }

            if (!busFound) {
                // 버스를 찾을 수 없을 때 3초 후 MainActivity로 이동
                handler.postDelayed(() -> {
                    Intent intent = new Intent(BusApiActivity.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                }, 3000);
            } else {
                // 버스 정보를 표시한 후 10초 후 MainActivity로 이동
                handler.postDelayed(() -> {
                    Intent intent = new Intent(BusApiActivity.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                }, 15000);
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (handler != null) {
            handler.removeCallbacksAndMessages(null); // 핸들러 콜백 제거
        }
        super.onDestroy();
    }
}
