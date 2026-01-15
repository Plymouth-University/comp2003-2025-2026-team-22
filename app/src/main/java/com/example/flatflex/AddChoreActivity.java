package com.example.flatflex;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class AddChoreActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_chore);

        EditText choreName = findViewById(R.id.choreNameInput);
        EditText assignedTo = findViewById(R.id.assignedToInput);
        Button save = findViewById(R.id.btnSaveChore);

        save.setOnClickListener(v -> {
            String name = choreName.getText().toString().trim();
            String user = assignedTo.getText().toString().trim();

            Toast.makeText(this, "Added: " + name + " -> " + user, Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
