package com.example.flatflex;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Adds a new chore into Firestore under users/{uid}/chores.
 */
public class AddChoreActivity extends AppCompatActivity {

    private final ChoreRepository repo = new ChoreRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_chore);

        EditText choreName = findViewById(R.id.choreNameInput);
        EditText assignedTo = findViewById(R.id.assignedToInput);
        Button save = findViewById(R.id.btnSaveChore);

        save.setOnClickListener(v -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                Toast.makeText(this, "Please log in first.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            String name = choreName.getText().toString().trim();
            String assignee = assignedTo.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                choreName.setError("Required");
                return;
            }
            if (TextUtils.isEmpty(assignee)) {
                assignee = "Unassigned";
            }

            String uid = user.getUid();
            repo.addChore(uid, name, assignee)
                    .addOnSuccessListener(ref -> {
                        Toast.makeText(this, "Chore added.", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Failed to add chore.", Toast.LENGTH_SHORT).show()
                    );
        });
    }
}
