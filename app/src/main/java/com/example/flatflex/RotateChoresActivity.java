package com.example.flatflex;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class RotateChoresActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rotate_chores);

        Button rotate = findViewById(R.id.btnDoRotate);
        rotate.setOnClickListener(v -> {
            Toast.makeText(this, "Rotated chores (demo)", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
