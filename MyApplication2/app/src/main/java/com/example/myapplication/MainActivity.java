package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText destinationInput;
    private Button findBusStopButton;
    private TextView resultView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        destinationInput = findViewById(R.id.destinationInput);
        findBusStopButton = findViewById(R.id.findBusStopButton);
        resultView = findViewById(R.id.resultView);

        findBusStopButton.setOnClickListener(v -> {
            String destination = destinationInput.getText().toString().trim();
            if (!destination.isEmpty()) {
                Log.i("Destination", "사용자 입력: " + destination);

                // Geocoding API로 좌표 찾기 및 가장 가까운 정류소 찾기
                new GeocodingTask(destination, (latitude, longitude) -> {
                    BusStopFinder finder = new BusStopFinder(getApplicationContext());
                    BusStop nearestStop = finder.findNearestStop(latitude, longitude);

                    if (nearestStop != null) {
                        String message = "가장 가까운 정류소는 " + nearestStop.getName() + "입니다.";
                        resultView.setText(message);
                        Log.i("BusStopFinder", message);

                        // BusApiActivity로 이동
                        Intent intent = new Intent(MainActivity.this, BusApiActivity.class);
                        intent.putExtra("stopId", nearestStop.getId());
                        startActivity(intent);
                    } else {
                        String message = "근처 정류소를 찾을 수 없습니다.";
                        resultView.setText(message);
                        Log.e("BusStopFinder", message);
                    }
                }).execute();
            } else {
                resultView.setText("목적지를 입력하세요.");
            }
        });
    }
}
