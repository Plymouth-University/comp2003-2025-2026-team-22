package com.example.flatflex;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "flatflex_prefs";
    private static final String KEY_THEME_MODE = "theme_mode"; // system|light|dark
    private static final String KEY_BIOMETRIC_ENABLED = "biometric_enabled";
    private static final String KEY_LAST_LOGIN_MS = "last_login_ms";
    private static final String KEY_RELOGIN_DAYS = "relogin_days"; // 0 = never

    private FirebaseAuth auth;
    private boolean biometricPassedThisSession = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();

        // Apply theme mode early
        applyThemeFromPrefs();

        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragmentContainer, new HomeFragment())
                        .commit();
                return true;
            }

            if (id == R.id.nav_settings) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragmentContainer, new SettingsFragment())
                        .commit();
                return true;
            }

            return false;
        });

        // Load HomeFragment into the container
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, new HomeFragment())
                    .commit();

            bottomNav.setSelectedItemId(R.id.nav_home);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Guard: must be logged in
        if (auth.getCurrentUser() == null) {
            goToLoginAndClearTask();
            return;
        }

        // Enforce "re-login after X days" if enabled
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int reloginDays = prefs.getInt(KEY_RELOGIN_DAYS, 0);
        long lastLoginMs = prefs.getLong(KEY_LAST_LOGIN_MS, 0);

        if (reloginDays > 0 && lastLoginMs > 0) {
            long maxAgeMs = reloginDays * 24L * 60L * 60L * 1000L;
            if (System.currentTimeMillis() - lastLoginMs > maxAgeMs) {
                auth.signOut();
                Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_LONG).show();
                goToLoginAndClearTask();
                return;
            }
        }

        // Optional biometric lock
        boolean biometricEnabled = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false);
        if (biometricEnabled && !biometricPassedThisSession) {
            promptBiometricOrDisable();
        }
    }

    private void goToLoginAndClearTask() {
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void applyThemeFromPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String mode = prefs.getString(KEY_THEME_MODE, "system");
        if ("light".equals(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if ("dark".equals(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    private void promptBiometricOrDisable() {
        BiometricManager bm = BiometricManager.from(this);
        int canAuth = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG
                | BiometricManager.Authenticators.BIOMETRIC_WEAK);

        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            // Device can't do biometrics; disable the toggle to avoid trapping the user.
            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_BIOMETRIC_ENABLED, false)
                    .apply();

            Toast.makeText(this, "Biometric lock isn't available on this device.", Toast.LENGTH_LONG).show();
            biometricPassedThisSession = true; // don't keep prompting
            return;
        }

        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt prompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                biometricPassedThisSession = true;
            }

            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                // If they cancel, keep them in app but not unlocked; if they hit back repeatedly they may be stuck.
                Toast.makeText(MainActivity.this, errString, Toast.LENGTH_SHORT).show();
                // Conservative: send them back to login (still signed in, but protected).
                goToLoginAndClearTask();
            }

            @Override
            public void onAuthenticationFailed() {
                Toast.makeText(MainActivity.this, "Authentication failed", Toast.LENGTH_SHORT).show();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock FlatFlex")
                .setSubtitle("Confirm it's you")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .setNegativeButtonText("Cancel")
                .build();

        prompt.authenticate(promptInfo);
    }
}
