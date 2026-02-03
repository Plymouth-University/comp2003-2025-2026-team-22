package com.example.flatflex;

import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.biometric.BiometricManager;
import androidx.fragment.app.Fragment;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class SettingsFragment extends Fragment {

    private static final String PREFS = "flatflex_prefs";
    private static final String KEY_THEME_MODE = "theme_mode"; // system|light|dark
    private static final String KEY_NOTIFS_ENABLED = "notifs_enabled";
    private static final String KEY_NOTIF_HOUR = "notif_hour";
    private static final String KEY_NOTIF_MIN = "notif_min";
    private static final String KEY_BIOMETRIC_ENABLED = "biometric_enabled";
    private static final String KEY_RELOGIN_DAYS = "relogin_days";
    private static final String KEY_LAST_LOGIN_MS = "last_login_ms";
    private static final String KEY_FLAT_NAME = "flat_name";
    private static final String KEY_JOIN_CODE = "join_code";
    private static final String KEY_TUTORIAL_SEEN = "tutorial_seen";

    private SharedPreferences prefs;

    public SettingsFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_settings, container, false);
        prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        // --- Profile ---
        EditText nameInput = v.findViewById(R.id.nameInput);
        Button saveProfileButton = v.findViewById(R.id.saveProfileButton);

        EditText flatNameInput = v.findViewById(R.id.flatNameInput);
        Button saveFlatButton = v.findViewById(R.id.saveFlatButton);

        TextView joinCodeText = v.findViewById(R.id.joinCodeText);
        Button generateJoinCodeButton = v.findViewById(R.id.generateJoinCodeButton);
        Button copyJoinCodeButton = v.findViewById(R.id.copyJoinCodeButton);

        // Prefill flat settings
        flatNameInput.setText(prefs.getString(KEY_FLAT_NAME, ""));
        String existingCode = prefs.getString(KEY_JOIN_CODE, "");
        joinCodeText.setText(TextUtils.isEmpty(existingCode) ? "Not set" : existingCode);

        // Prefill name from Firebase
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            if (!TextUtils.isEmpty(user.getDisplayName())) {
                nameInput.setText(user.getDisplayName());
            }
        }

        
// Load profile from Firestore (keeps app data in sync across devices)
if (user != null) {
    FirebaseFirestore db = FirebaseFirestore.getInstance();
    String uid = user.getUid();
    db.collection("users").document(uid)
            .get()
            .addOnSuccessListener(doc -> {
                if (doc != null && doc.exists()) {
                    String n = doc.getString("name");
                    String flat = doc.getString("flatName");
                    String code = doc.getString("joinCode");

                    if (!TextUtils.isEmpty(n)) {
                        nameInput.setText(n);
                    }
                    if (!TextUtils.isEmpty(flat)) {
                        flatNameInput.setText(flat);
                        prefs.edit().putString(KEY_FLAT_NAME, flat).apply();
                    }
                    if (!TextUtils.isEmpty(code)) {
                        joinCodeText.setText(code);
                        prefs.edit().putString(KEY_JOIN_CODE, code).apply();
                    }
                } else {
                    // First time after upgrade: create doc from what we already know
                    Map<String, Object> seed = new HashMap<>();
                    String display = user.getDisplayName();
                    seed.put("name", TextUtils.isEmpty(display) ? "" : display);
                    seed.put("email", user.getEmail() == null ? "" : user.getEmail());
                    seed.put("flatName", prefs.getString(KEY_FLAT_NAME, ""));
                    seed.put("joinCode", prefs.getString(KEY_JOIN_CODE, ""));
                    seed.put("role", "tenant");
                    seed.put("createdAt", FieldValue.serverTimestamp());
                    db.collection("users").document(uid).set(seed);
                }
            });
}
saveProfileButton.setOnClickListener(view -> updateDisplayName(nameInput));
        saveFlatButton.setOnClickListener(view -> {
            String flatName = flatNameInput.getText().toString().trim();
            prefs.edit().putString(KEY_FLAT_NAME, flatName).apply();
            // Sync to Firestore so it persists across devices
FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
if (u != null) {
    FirebaseFirestore db = FirebaseFirestore.getInstance();
    Map<String, Object> updates = new HashMap<>();
    updates.put("flatName", flatName);
    db.collection("users").document(u.getUid())
            .set(updates, com.google.firebase.firestore.SetOptions.merge());
}
Toast.makeText(requireContext(), "Flat name saved", Toast.LENGTH_SHORT).show();
        });

        generateJoinCodeButton.setOnClickListener(view -> {
            String code = generateJoinCode();
            prefs.edit().putString(KEY_JOIN_CODE, code).apply();
            joinCodeText.setText(code);
            // Sync to Firestore
FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
if (u != null) {
    FirebaseFirestore db = FirebaseFirestore.getInstance();
    Map<String, Object> updates = new HashMap<>();
    updates.put("joinCode", code);
    db.collection("users").document(u.getUid())
            .set(updates, com.google.firebase.firestore.SetOptions.merge());
}
Toast.makeText(requireContext(), "Join code generated", Toast.LENGTH_SHORT).show();
        });

        copyJoinCodeButton.setOnClickListener(view -> {
            String code = prefs.getString(KEY_JOIN_CODE, "");
            if (TextUtils.isEmpty(code)) {
                Toast.makeText(requireContext(), "Generate a join code first", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("FlatFlex Join Code", code));
            Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
        });

        // --- Account ---
        Button changePasswordButton = v.findViewById(R.id.changePasswordButton);
        Button changeEmailButton = v.findViewById(R.id.changeEmailButton);

        changePasswordButton.setOnClickListener(view -> sendPasswordReset());
        changeEmailButton.setOnClickListener(view -> showChangeEmailDialog());

        // --- Preferences ---
        Spinner themeSpinner = v.findViewById(R.id.themeSpinner);
        ArrayAdapter<String> themeAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                new String[]{"System", "Light", "Dark"}
        );
        themeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        themeSpinner.setAdapter(themeAdapter);

        String currentTheme = prefs.getString(KEY_THEME_MODE, "system");
        themeSpinner.setSelection("light".equals(currentTheme) ? 1 : "dark".equals(currentTheme) ? 2 : 0);

        Button applyThemeButton = v.findViewById(R.id.applyThemeButton);
        applyThemeButton.setOnClickListener(view -> {
            int pos = themeSpinner.getSelectedItemPosition();
            String mode = (pos == 1) ? "light" : (pos == 2) ? "dark" : "system";
            prefs.edit().putString(KEY_THEME_MODE, mode).apply();
            applyTheme(mode);
            Toast.makeText(requireContext(), "Theme updated", Toast.LENGTH_SHORT).show();
        });

        SwitchMaterial notifSwitch = v.findViewById(R.id.notificationsSwitch);
        Button notifTimeButton = v.findViewById(R.id.notificationTimeButton);

        notifSwitch.setChecked(prefs.getBoolean(KEY_NOTIFS_ENABLED, false));
        notifTimeButton.setText(formatTime(prefs.getInt(KEY_NOTIF_HOUR, 19), prefs.getInt(KEY_NOTIF_MIN, 0)));

        notifSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_NOTIFS_ENABLED, isChecked).apply();
            updateReminderWorker();
        });

        notifTimeButton.setOnClickListener(view -> {
            int hour = prefs.getInt(KEY_NOTIF_HOUR, 19);
            int min = prefs.getInt(KEY_NOTIF_MIN, 0);

            TimePickerDialog dialog = new TimePickerDialog(requireContext(), (timePicker, h, m) -> {
                prefs.edit().putInt(KEY_NOTIF_HOUR, h).putInt(KEY_NOTIF_MIN, m).apply();
                notifTimeButton.setText(formatTime(h, m));
                updateReminderWorker();
            }, hour, min, true);

            dialog.show();
        });

        SwitchMaterial biometricSwitch = v.findViewById(R.id.biometricSwitch);
        biometricSwitch.setChecked(prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false));
        biometricSwitch.setOnCheckedChangeListener((buttonView, enabled) -> {
            if (enabled) {
                BiometricManager bm = BiometricManager.from(requireContext());
                int canAuth = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | BiometricManager.Authenticators.BIOMETRIC_WEAK);
                if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
                    biometricSwitch.setChecked(false);
                    Toast.makeText(requireContext(), "Biometrics not available on this device", Toast.LENGTH_LONG).show();
                    return;
                }
            }
            prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply();
        });

        Spinner reloginSpinner = v.findViewById(R.id.reloginSpinner);
        ArrayAdapter<String> reloginAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                new String[]{"Never", "1 day", "7 days", "30 days"}
        );
        reloginAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        reloginSpinner.setAdapter(reloginAdapter);

        int currentDays = prefs.getInt(KEY_RELOGIN_DAYS, 0);
        int sel = currentDays == 1 ? 1 : currentDays == 7 ? 2 : currentDays == 30 ? 3 : 0;
        reloginSpinner.setSelection(sel);

        Button applyReloginButton = v.findViewById(R.id.applyReloginButton);
        applyReloginButton.setOnClickListener(view -> {
            int p = reloginSpinner.getSelectedItemPosition();
            int days = (p == 1) ? 1 : (p == 2) ? 7 : (p == 3) ? 30 : 0;
            prefs.edit().putInt(KEY_RELOGIN_DAYS, days).apply();
            Toast.makeText(requireContext(), "Security setting saved", Toast.LENGTH_SHORT).show();
        });

        // --- Support ---
        Button faqButton = v.findViewById(R.id.faqButton);
        Button reportProblemButton = v.findViewById(R.id.reportProblemButton);
        Button termsButton = v.findViewById(R.id.termsButton);
        Button privacyButton = v.findViewById(R.id.privacyButton);
        Button aboutButton = v.findViewById(R.id.aboutButton);

        faqButton.setOnClickListener(view -> startActivity(new Intent(requireContext(), FaqActivity.class)));

        reportProblemButton.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "FlatFlex Support");
            intent.putExtra(Intent.EXTRA_TEXT, buildSupportBody());
            startActivity(Intent.createChooser(intent, "Send email"));
        });

        termsButton.setOnClickListener(view -> openLink("https://example.com/terms"));
        privacyButton.setOnClickListener(view -> openLink("https://example.com/privacy"));
        aboutButton.setOnClickListener(view -> startActivity(new Intent(requireContext(), AboutActivity.class)));

        // --- Data controls ---
        Button exportButton = v.findViewById(R.id.exportButton);
        Button clearCompletedButton = v.findViewById(R.id.clearCompletedButton);
        Button resetHintsButton = v.findViewById(R.id.resetHintsButton);

        exportButton.setOnClickListener(view -> SettingsExportHelper.exportSettings(requireContext()));
        clearCompletedButton.setOnClickListener(view -> {
            // If/when chores are stored, hook this up. For now we clear a placeholder preference bucket.
            prefs.edit().remove("completed_chores").apply();
            Toast.makeText(requireContext(), "Completed chores cleared", Toast.LENGTH_SHORT).show();
        });
        resetHintsButton.setOnClickListener(view -> {
            prefs.edit().putBoolean(KEY_TUTORIAL_SEEN, false).apply();
            Toast.makeText(requireContext(), "Hints reset", Toast.LENGTH_SHORT).show();
        });

        // --- Danger zone ---
        Button logoutButton = v.findViewById(R.id.logoutButton);
        Button deleteAccountButton = v.findViewById(R.id.deleteAccountButton);

        logoutButton.setOnClickListener(view -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(requireContext(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            requireActivity().finish();
        });

        deleteAccountButton.setOnClickListener(view -> showDeleteAccountDialog());

        // Ensure reminder state matches current prefs
        updateReminderWorker();

        return v;
    }

    private void updateDisplayName(EditText nameInput) {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) {
            Toast.makeText(requireContext(), "You're signed out. Please log in again.", Toast.LENGTH_LONG).show();
            return;
        }

        String newName = nameInput.getText().toString().trim();
        if (TextUtils.isEmpty(newName)) {
            nameInput.setError("Name required");
            nameInput.requestFocus();
            return;
        }

        UserProfileChangeRequest req = new UserProfileChangeRequest.Builder()
                .setDisplayName(newName)
                .build();

        u.updateProfile(req)
                .addOnSuccessListener(unused -> {
                    // Keep Firestore profile in sync
                    FirebaseFirestore db = FirebaseFirestore.getInstance();
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("name", newName);
                    db.collection("users").document(u.getUid())
                            .set(updates, com.google.firebase.firestore.SetOptions.merge());

                    Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void sendPasswordReset() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null || TextUtils.isEmpty(u.getEmail())) {
            Toast.makeText(requireContext(), "No email found for this account.", Toast.LENGTH_LONG).show();
            return;
        }

        FirebaseAuth.getInstance().sendPasswordResetEmail(u.getEmail())
                .addOnSuccessListener(unused -> Toast.makeText(requireContext(), "Password reset email sent", Toast.LENGTH_LONG).show())
                .addOnFailureListener(e -> Toast.makeText(requireContext(), "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void showChangeEmailDialog() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) {
            Toast.makeText(requireContext(), "You're signed out. Please log in again.", Toast.LENGTH_LONG).show();
            return;
        }

        View form = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_change_email, null, false);
        EditText newEmailInput = form.findViewById(R.id.newEmailInput);
        EditText currentPasswordInput = form.findViewById(R.id.currentPasswordInput);

        new AlertDialog.Builder(requireContext())
                .setTitle("Change email")
                .setView(form)
                .setPositiveButton("Update", (dialog, which) -> {
                    String newEmail = newEmailInput.getText().toString().trim();
                    String currentPassword = currentPasswordInput.getText().toString();

                    if (TextUtils.isEmpty(newEmail)) {
                        Toast.makeText(requireContext(), "Enter a new email", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (TextUtils.isEmpty(currentPassword)) {
                        Toast.makeText(requireContext(), "Enter your current password", Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (TextUtils.isEmpty(u.getEmail())) {
                        Toast.makeText(requireContext(), "No current email found.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    u.reauthenticate(EmailAuthProvider.getCredential(u.getEmail(), currentPassword))
                            .addOnSuccessListener(unused -> u.updateEmail(newEmail)
                                    .addOnSuccessListener(unused2 -> Toast.makeText(requireContext(), "Email updated", Toast.LENGTH_SHORT).show())
                                    .addOnFailureListener(e -> Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show()))
                            .addOnFailureListener(e -> Toast.makeText(requireContext(), "Re-auth failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteAccountDialog() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) {
            Toast.makeText(requireContext(), "You're signed out. Please log in again.", Toast.LENGTH_LONG).show();
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Delete account")
                .setMessage("This will permanently delete your account. This can't be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    u.delete()
                            .addOnSuccessListener(unused -> {
                                FirebaseAuth.getInstance().signOut();
                                Intent intent = new Intent(requireContext(), LoginActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                requireActivity().finish();
                            })
                            .addOnFailureListener(e -> Toast.makeText(requireContext(),
                                    "Delete failed: " + e.getMessage() + "\nTry changing password then retry.",
                                    Toast.LENGTH_LONG).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applyTheme(String mode) {
        if ("light".equals(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if ("dark".equals(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
        // Recreate to apply immediately
        requireActivity().recreate();
    }

    private void updateReminderWorker() {
        boolean enabled = prefs.getBoolean(KEY_NOTIFS_ENABLED, false);
        if (!enabled) {
            WorkManager.getInstance(requireContext()).cancelUniqueWork(ReminderWorker.UNIQUE_NAME);
            return;
        }

        // Run once a day; ReminderWorker checks if it's near the configured time.
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(ReminderWorker.class, 24, TimeUnit.HOURS)
                .build();

        WorkManager.getInstance(requireContext())
                .enqueueUniquePeriodicWork(ReminderWorker.UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, req);
    }

    private String formatTime(int hour, int min) {
        return String.format(Locale.getDefault(), "%02d:%02d", hour, min);
    }

    private String generateJoinCode() {
        final String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }

    private void openLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "No browser found", Toast.LENGTH_SHORT).show();
        }
    }

    private String buildSupportBody() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        String email = (u != null && u.getEmail() != null) ? u.getEmail() : "(unknown)";
        String model = Build.MODEL;
        String device = Build.MANUFACTURER + " " + model;
        String sdk = String.valueOf(Build.VERSION.SDK_INT);

        return "Describe your issue here...\n\n"
                + "----\n"
                + "Account: " + email + "\n"
                + "Device: " + device + "\n"
                + "Android SDK: " + sdk + "\n";
    }
}
