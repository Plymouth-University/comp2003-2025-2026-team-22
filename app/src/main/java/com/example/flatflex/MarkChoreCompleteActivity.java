package com.example.flatflex;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MarkChoreCompleteActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mark_chore_complete);

        String choreId = getIntent().getStringExtra("CHORE_ID");

        TextView info = findViewById(R.id.choreInfo);
        info.setText("Chore ID: " + choreId);

        Button confirm = findViewById(R.id.btnConfirmComplete);
        confirm.setOnClickListener(v -> finish());
    }
}
