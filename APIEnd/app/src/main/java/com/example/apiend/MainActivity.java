package com.example.apiend;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    TextView textView;
    Button button;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.textView);
        button = findViewById(R.id.button);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new GetBusInfoTask().execute();  // 버튼 클릭 시 API 호출
            }
        });
    }

    private class GetBusInfoTask extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(Void... voids) {
            String result = "";
            String apiUrl = "http://ws.bus.go.kr/api/rest/arrive/getArrInfoByRoute?" +
                    "serviceKey=39LvkKBDQ3ntFTS50jGpUGMSkfUC7tNdr%2FeaiK8tHCbtwAfGL%2BXaBpluvSHRUtnGlj%2FT94eglPA6l1qpSvQ6GA%3D%3D" +
                    "&stId=124000414&busRouteId=100100578&ord=29";

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
                                isItemTag = true;
                            }
                            break;
                        case XmlPullParser.TEXT:
                            if (isItemTag && tag.equals("arrmsg1")) {
                                arrivalInfo1 = xmlPullParser.getText(); // 첫 번째 도착 정보
                            } else if (isItemTag && tag.equals("arrmsg2")) {
                                arrivalInfo2 = xmlPullParser.getText(); // 두 번째 도착 정보
                            }
                            break;
                        case XmlPullParser.END_TAG:
                            if (xmlPullParser.getName().equals("itemList")) {
                                isItemTag = false;
                            }
                            break;
                    }
                    eventType = xmlPullParser.next();
                }

                result = "첫 번째 도착 정보: " + arrivalInfo1 + "\n두 번째 도착 정보: " + arrivalInfo2;
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("Error", "API 호출 중 오류 발생: " + e.getMessage());
            }

            return result;
        }

        @Override
        protected void onPostExecute(String result) {
            textView.setText(result);
        }
    }
}
