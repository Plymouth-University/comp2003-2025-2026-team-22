package com.example.flatflex;

import android.content.Intent;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.Editable;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

public class SignupActivity extends AppCompatActivity {

    private static final String PREFS = "flatflex_prefs";
    private static final String KEY_LAST_LOGIN_MS = "last_login_ms";

    private FirebaseAuth auth;
    private EditText nameInput;
    private EditText emailInput;
    private EditText passwordInput;
    private EditText confirmPasswordInput;

    // Password rule indicators
    private TextView ruleLength;
    private TextView ruleUpper;
    private TextView ruleNumber;
    private TextView ruleSpecial;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.signupactivity);

        auth = FirebaseAuth.getInstance();

        nameInput = findViewById(R.id.signupNameInput);
        emailInput = findViewById(R.id.signupEmailInput);
        passwordInput = findViewById(R.id.signupPasswordInput);
        confirmPasswordInput = findViewById(R.id.signupConfirmPasswordInput);

        // Link rule TextViews
        ruleLength = findViewById(R.id.ruleLength);
        ruleUpper = findViewById(R.id.ruleUpper);
        ruleNumber = findViewById(R.id.ruleNumber);
        ruleSpecial = findViewById(R.id.ruleSpecial);

        Button createAccountButton = findViewById(R.id.createAccountButton);
        TextView backToLogin = findViewById(R.id.backToLogin);

        // Live password validation
        passwordInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                updatePasswordRules(s.toString());
            }

            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

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

            // Use proper validation
            if (!isValidPassword(password)) {
                passwordInput.setError("Password must meet all requirements below");
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

                        Intent intent = new Intent(SignupActivity.this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .addOnFailureListener(e -> Toast.makeText(
                            SignupActivity.this,
                            "Signup failed. Please check your details.",
                            Toast.LENGTH_LONG
                    ).show());
        });

        backToLogin.setOnClickListener(v -> {
            startActivity(new Intent(SignupActivity.this, LoginActivity.class));
            finish();
        });
    }

    // Password rule UI updates
    private void updatePasswordRules(String password) {

        if (password.length() >= 8) {
            ruleLength.setText("✔ At least 8 characters");
            ruleLength.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            ruleLength.setText("✖ At least 8 characters");
            ruleLength.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        }

        if (password.matches(".*[A-Z].*")) {
            ruleUpper.setText("✔ One uppercase letter");
            ruleUpper.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            ruleUpper.setText("✖ One uppercase letter");
            ruleUpper.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        }

        if (password.matches(".*\\d.*")) {
            ruleNumber.setText("✔ One number");
            ruleNumber.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            ruleNumber.setText("✖ One number");
            ruleNumber.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        }

        if (password.matches(".*[@#$%^&+=!].*")) {
            ruleSpecial.setText("✔ One special character");
            ruleSpecial.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            ruleSpecial.setText("✖ One special character");
            ruleSpecial.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        }
    }

    // ✅ NEW: validation function
    private boolean isValidPassword(String password) {
        return password.length() >= 8 &&
                password.matches(".*[A-Z].*") &&
                password.matches(".*\\d.*") &&
                password.matches(".*[@#$%^&+=!].*");
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (auth != null && auth.getCurrentUser() != null) {
            Intent intent = new Intent(SignupActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }
}