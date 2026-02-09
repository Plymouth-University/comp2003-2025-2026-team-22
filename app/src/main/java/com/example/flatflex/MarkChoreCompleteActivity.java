package com.example.flatflex;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Marks a chore as complete in Firestore.
 * Expects intent extra: CHORE_ID
 */
public class MarkChoreCompleteActivity extends AppCompatActivity {

    private final ChoreRepository repo = new ChoreRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mark_chore_complete);

        String choreId = getIntent().getStringExtra("CHORE_ID");

        TextView info = findViewById(R.id.choreInfo);
        info.setText("Chore ID: " + (choreId == null ? "(none)" : choreId));

        Button confirm = findViewById(R.id.btnConfirmComplete);
        confirm.setOnClickListener(v -> {
            if (TextUtils.isEmpty(choreId)) {
                Toast.makeText(this, "No chore selected.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                Toast.makeText(this, "Please log in first.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            repo.markComplete(user.getUid(), choreId)
                    .addOnSuccessListener(x -> {
                        Toast.makeText(this, "Marked complete.", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Failed to update chore.", Toast.LENGTH_SHORT).show()
                    );
        });
    }
}
