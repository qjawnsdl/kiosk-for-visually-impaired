package com.example.myapplication;

import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class BusApiActivity extends AppCompatActivity {

    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bus_api);

        textView = findViewById(R.id.textView);

        // 정류소 ID로 버스 정보 불러오기
        String stopId = getIntent().getStringExtra("stopId");
        new BusInfoTask(stopId).execute();
    }

    private class BusInfoTask extends AsyncTask<Void, Void, String> {
        private final String stopId;

        public BusInfoTask(String stopId) {
            this.stopId = stopId;
        }

        @Override
        protected String doInBackground(Void... voids) {
            String result = "";
            String apiUrl = "http://ws.bus.go.kr/api/rest/arrive/getArrInfoByRoute" +
                    "?serviceKey=YOUR_SERVICE_KEY&stId=" + stopId;

            try {
                URL url = new URL(apiUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                InputStream inputStream = connection.getInputStream();
                XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                XmlPullParser parser = factory.newPullParser();
                parser.setInput(inputStream, null);

                int eventType = parser.getEventType();
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    // XML 파싱 로직 추가
                    eventType = parser.next();
                }

                result = "버스 정보 로드 완료";
            } catch (Exception e) {
                result = "API 오류 발생: " + e.getMessage();
            }

            return result;
        }

        @Override
        protected void onPostExecute(String result) {
            textView.setText(result);
        }
    }
}
