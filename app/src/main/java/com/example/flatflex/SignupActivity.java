package com.example.flatflex;

import android.content.Intent;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Signup screen using Firebase Authentication (Email/Password).
 * Also stores the user's name in Firebase Auth as displayName.
 * Layout: res/layout/signupactivity.xml
 */
public class SignupActivity extends AppCompatActivity {

    private static final String PREFS = "flatflex_prefs";
    private static final String KEY_LAST_LOGIN_MS = "last_login_ms";

    private FirebaseAuth auth;
    private EditText nameInput;
    private EditText emailInput;
    private EditText passwordInput;
    private EditText confirmPasswordInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.signupactivity);

        auth = FirebaseAuth.getInstance();

        nameInput = findViewById(R.id.signupNameInput);
        emailInput = findViewById(R.id.signupEmailInput);
        passwordInput = findViewById(R.id.signupPasswordInput);
        confirmPasswordInput = findViewById(R.id.signupConfirmPasswordInput);

        Button createAccountButton = findViewById(R.id.createAccountButton);
        TextView backToLogin = findViewById(R.id.backToLogin);

        createAccountButton.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString();
            String confirmPassword = confirmPasswordInput.getText().toString();

            if (TextUtils.isEmpty(name)) {
                nameInput.setError("Name required");
                nameInput.requestFocus();
                return;
            }

            if (TextUtils.isEmpty(email)) {
                emailInput.setError("Email required");
                emailInput.requestFocus();
                return;
            }

            if (TextUtils.isEmpty(password)) {
                passwordInput.setError("Password required");
                passwordInput.requestFocus();
                return;
            }

            if (password.length() < 6) {
                passwordInput.setError("Password must be at least 6 characters");
                passwordInput.requestFocus();
                return;
            }

            if (!password.equals(confirmPassword)) {
                confirmPasswordInput.setError("Passwords do not match");
                confirmPasswordInput.requestFocus();
                return;
            }

            auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener(result -> {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            UserProfileChangeRequest req = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(name)
                                    .build();
                            user.updateProfile(req);
                        }

                        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
                        prefs.edit().putLong(KEY_LAST_LOGIN_MS, System.currentTimeMillis()).apply();

                        
// Create / update Firestore user profile
FirebaseFirestore db = FirebaseFirestore.getInstance();
String uid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;
if (uid != null) {
    Map<String, Object> userDoc = new HashMap<>();
    userDoc.put("name", name);
    userDoc.put("email", email);
    userDoc.put("flatName", "");
    userDoc.put("joinCode", "");
    userDoc.put("role", "tenant");
    userDoc.put("createdAt", FieldValue.serverTimestamp());

    db.collection("users").document(uid)
            .set(userDoc)
            .addOnFailureListener(err -> Toast.makeText(
                    SignupActivity.this,
                    "Failed to save profile: " + err.getMessage(),
                    Toast.LENGTH_LONG
            ).show());
}

                        // Account created and signed in
                        Intent intent = new Intent(SignupActivity.this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .addOnFailureListener(e -> Toast.makeText(
                            SignupActivity.this,
                            "Signup failed: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show());
        });

        backToLogin.setOnClickListener(v -> {
            startActivity(new Intent(SignupActivity.this, LoginActivity.class));
            finish();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // If user is already logged in, skip signup
        if (auth != null && auth.getCurrentUser() != null) {
            Intent intent = new Intent(SignupActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }
}
