package com.example.flatflex;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

/**
 * Login screen using Firebase Authentication (Email/Password).
 * Layout: res/layout/loginactivity.xml
 */
public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private EditText emailInput;
    private EditText passwordInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // IMPORTANT: must match your XML file name
        setContentView(R.layout.loginactivity);

        auth = FirebaseAuth.getInstance();

        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);

        Button loginButton = findViewById(R.id.loginButton);
        Button signupButton = findViewById(R.id.signupButton);
        TextView forgotPassword = findViewById(R.id.forgotPassword);

        // LOGIN
        loginButton.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString();

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

            auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener(result -> {
                        // MainActivity loads HomeFragment by default
                        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                        startActivity(intent);
                        finish();
                    })
                    .addOnFailureListener(e -> Toast.makeText(
                            LoginActivity.this,
                            "Login failed: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show());
        });

        // SIGNUP
        signupButton.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, SignupActivity.class));
        });

        // FORGOT PASSWORD
        forgotPassword.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            if (TextUtils.isEmpty(email)) {
                emailInput.setError("Enter your email first");
                emailInput.requestFocus();
                return;
            }

            auth.sendPasswordResetEmail(email)
                    .addOnSuccessListener(unused -> Toast.makeText(
                            LoginActivity.this,
                            "Password reset email sent",
                            Toast.LENGTH_LONG
                    ).show())
                    .addOnFailureListener(e -> Toast.makeText(
                            LoginActivity.this,
                            "Failed: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show());
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // If user already logged in, skip login
        if (auth != null && auth.getCurrentUser() != null) {
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        }
    }
}
