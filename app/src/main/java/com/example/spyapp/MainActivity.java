package com.example.spyapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.Gson;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public class MainActivity extends AppCompatActivity {
    private final Logger logger = Logger.getLogger(this.getClass().getName());

    private Button startButton;
    private Button stopButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startButton = findViewById(R.id.start_button);
        stopButton = findViewById(R.id.stop_button);

        startButton.setOnClickListener(v -> {
            startForegroundService(new Intent(MainActivity.this, SpyService.class));
        });

        stopButton.setOnClickListener(v -> {
            stopService(new Intent(MainActivity.this, SpyService.class));
        });
    }

}
