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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.Map;

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

        // Profile
        EditText nameInput = v.findViewById(R.id.nameInput);
        Button saveProfileButton = v.findViewById(R.id.saveProfileButton);

        // Flat name
        EditText flatNameInput = v.findViewById(R.id.flatNameInput);
        Button saveFlatButton = v.findViewById(R.id.saveFlatButton);

        // Join code
        TextView joinCodeText = v.findViewById(R.id.joinCodeText);
        Button generateJoinCodeButton = v.findViewById(R.id.generateJoinCodeButton);
        Button copyJoinCodeButton = v.findViewById(R.id.copyJoinCodeButton);

        // Join household by code
        EditText joinCodeInput = v.findViewById(R.id.joinCodeInput);
        Button joinFlatButton = v.findViewById(R.id.joinFlatButton);

        // Leave household
        Button leaveFlatButton = v.findViewById(R.id.leaveFlatButton);

        // View household members
        Button viewMembersButton = v.findViewById(R.id.viewMembersButton);

        // Prefill flat settings
        flatNameInput.setText(prefs.getString(KEY_FLAT_NAME, ""));

        // Prefill name from Firebase
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show();
            return v;
        }

        String userId = user.getUid();
        if (!TextUtils.isEmpty(user.getDisplayName())) {
            nameInput.setText(user.getDisplayName());
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Change password
        Button changePasswordButton = v.findViewById(R.id.changePasswordButton);
        if (changePasswordButton != null) {
            changePasswordButton.setOnClickListener(v1 -> sendPasswordReset());
        }

        // Reset email
        Button changeEmailButton = v.findViewById(R.id.changeEmailButton);
        if (changeEmailButton != null) {
            changeEmailButton.setOnClickListener(v1 -> showChangeEmailDialog());
        }

        // Logout
        Button logoutButton = v.findViewById(R.id.logoutButton);
        if (logoutButton != null) {
            logoutButton.setOnClickListener(v1 -> {
                FirebaseAuth.getInstance().signOut();

                Intent intent = new Intent(requireContext(), LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);

                requireActivity().finish();
            });
        }

        // Delete account
        Button deleteAccountButton = v.findViewById(R.id.deleteAccountButton);
        if (deleteAccountButton != null) {deleteAccountButton.setOnClickListener(v1 -> showDeleteAccountDialog());
        }

        // Apply themes
        Button applyThemeButton = v.findViewById(R.id.applyThemeButton);
        Spinner themeSpinner = v.findViewById(R.id.themeSpinner);

        if (applyThemeButton != null && themeSpinner != null) {
            applyThemeButton.setOnClickListener(v1 -> {
                String selected = themeSpinner.getSelectedItem().toString().toLowerCase();
                applyTheme(selected);
            });
        }

        // Notifications
        SwitchMaterial notificationsSwitch = v.findViewById(R.id.notificationsSwitch);

        if (notificationsSwitch != null) {
            notificationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean(KEY_NOTIFS_ENABLED, isChecked).apply();
                updateReminderWorker();
            });
        }

        // FAQs
        Button faqButton = v.findViewById(R.id.faqButton);

        if (faqButton != null) {
            faqButton.setOnClickListener(v1 ->
                    openLink("https://your-faq-link.com"));
        }

        // Report a problem
        Button reportProblemButton = v.findViewById(R.id.reportProblemButton);

        if (reportProblemButton != null) {
            reportProblemButton.setOnClickListener(v1 -> {

                Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                emailIntent.setData(Uri.parse("mailto:support@yourapp.com"));
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, "FlatFlex Support");
                emailIntent.putExtra(Intent.EXTRA_TEXT, buildSupportBody());

                startActivity(emailIntent);
            });
        }

        // --- Profile ---
        saveProfileButton.setOnClickListener(view -> updateDisplayName(nameInput));
        saveFlatButton.setOnClickListener(view -> {
            String flatName = flatNameInput.getText().toString().trim();

            if (flatName.isEmpty()) {
                flatNameInput.setError("Enter a household name");
                flatNameInput.requestFocus();
                return;
            }

            flatNameInput.clearFocus();
            Toast.makeText(requireContext(), "Flat name saved", Toast.LENGTH_SHORT).show();
        });

        // Load existing household on page open
        db.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(userDoc -> {

                    String flatId = userDoc.getString("flatId");

                    if (flatId != null) {

                        db.collection("flats")
                                .document(flatId)
                                .get()
                                .addOnSuccessListener(flatDoc -> {

                                    if (flatDoc.exists()) {

                                        String joinCode = flatDoc.getString("joinCode");

                                        // Show join code
                                        if (joinCode != null) {
                                            joinCodeText.setText(joinCode);
                                        }

                                        // Disable button
                                        generateJoinCodeButton.setEnabled(false);
                                        generateJoinCodeButton.setText("Already in a household");
                                        generateJoinCodeButton.setAlpha(0.5f);

                                        // UX improvements
                                        joinFlatButton.setEnabled(false);
                                        joinFlatButton.setAlpha(0.5f);
                                        joinCodeInput.setEnabled(false);

                                        // Show household members button
                                        viewMembersButton.setVisibility(View.VISIBLE);
                                    }

                                });

                    } else {
                        // Reset UI if no flat
                        joinCodeText.setText("Not set");
                        generateJoinCodeButton.setEnabled(true);
                        generateJoinCodeButton.setAlpha(1f);
                        generateJoinCodeButton.setText("Generate");

                        // UX reset
                        joinFlatButton.setEnabled(true);
                        joinFlatButton.setAlpha(1f);
                        joinCodeInput.setEnabled(true);

                        // Hide show household members button
                        viewMembersButton.setVisibility(View.GONE);
                    }

                });

        // --- Join Code ---
        generateJoinCodeButton.setOnClickListener(view -> {

            String flatName = flatNameInput.getText().toString().trim();

            if (flatName.isEmpty()) {
                flatNameInput.setError("Enter a household name first");
                flatNameInput.requestFocus();
                return;
            }

            // First check if user already has a flat
            db.collection("users")
                    .document(userId)
                    .get()
                    .addOnSuccessListener(userDoc -> {

                        String existingFlatId = userDoc.getString("flatId");

                        // Stop if already in a flat
                        if (existingFlatId != null) {

                            // Fetch existing flat to show join code
                            db.collection("flats")
                                    .document(existingFlatId)
                                    .get()
                                    .addOnSuccessListener(flatDoc -> {

                                        if (flatDoc.exists()) {

                                            String joinCode = flatDoc.getString("joinCode");

                                            // Show join code
                                            if (joinCode != null) {
                                                joinCodeText.setText(joinCode);
                                            }

                                            // Disable button
                                            generateJoinCodeButton.setEnabled(false);
                                            generateJoinCodeButton.setText("Already in a household");
                                            generateJoinCodeButton.setAlpha(0.5f);

                                            Toast.makeText(requireContext(), "You already have a household", Toast.LENGTH_SHORT).show();
                                        }

                                    });

                            return;
                        }

                        // Otherwise, create new flat
                        String code = generateJoinCode();
                        joinCodeText.setText("Code: " + code);

                        Map<String, Object> flat = new HashMap<>();
                        flat.put("name", flatName);
                        flat.put("joinCode", code);

                        db.collection("flats")
                                .add(flat)
                                .addOnSuccessListener(doc -> {

                                    String flatId = doc.getId();

                                    // link user to flat
                                    db.collection("users")
                                            .document(userId)
                                            .set(Collections.singletonMap("flatId", flatId), SetOptions.merge());

                                    // UX improvements
                                    flatNameInput.setText("");
                                    flatNameInput.clearFocus();

                                    generateJoinCodeButton.setEnabled(false);
                                    generateJoinCodeButton.setText("Already in a household");
                                    generateJoinCodeButton.setAlpha(0.5f);

                                    joinFlatButton.setEnabled(false);
                                    joinFlatButton.setAlpha(0.5f);
                                    joinCodeInput.setEnabled(false);

                                    Toast.makeText(requireContext(), "Household created!", Toast.LENGTH_SHORT).show();
                                });

                    });
        });

        // --- Copy Join Code ---
        copyJoinCodeButton.setOnClickListener(view -> {
            String code = joinCodeText.getText().toString();

            if (TextUtils.isEmpty(code) || code.equals("Not set")) {
                Toast.makeText(requireContext(), "No join code available", Toast.LENGTH_SHORT).show();
                return;
            }

            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("FlatFlex Join Code", code));
            Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
        });

        // --- Join Flat by Code ---
        joinFlatButton.setOnClickListener(view -> {

            String code = joinCodeInput.getText().toString().trim();

            if (code.isEmpty()) {
                joinCodeInput.setError("Enter a join code");
                joinCodeInput.requestFocus();
                return;
            }

            // Find flat with this code
            db.collection("flats")
                    .whereEqualTo("joinCode", code)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {

                        if (querySnapshot.isEmpty()) {
                            Toast.makeText(requireContext(), "Invalid join code", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // Get the first matching flat
                        String flatId = querySnapshot.getDocuments().get(0).getId();

                        // Link user to flat
                        db.collection("users")
                                .document(userId)
                                .set(Collections.singletonMap("flatId", flatId), SetOptions.merge())
                                .addOnSuccessListener(unused -> {

                                    // UX improvements
                                    joinCodeInput.setText("");
                                    joinCodeInput.clearFocus();

                                    joinCodeText.setText("Code: " + code);

                                    generateJoinCodeButton.setEnabled(false);
                                    generateJoinCodeButton.setText("Already in a household");
                                    generateJoinCodeButton.setAlpha(0.5f);

                                    joinFlatButton.setEnabled(false);
                                    joinFlatButton.setAlpha(0.5f);
                                    joinCodeInput.setEnabled(false);

                                    Toast.makeText(requireContext(), "Joined household!", Toast.LENGTH_SHORT).show();
                                });

                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });

        });

        // Leave household
        leaveFlatButton.setOnClickListener(view -> {

            new AlertDialog.Builder(requireContext())
                    .setTitle("Leave Household")
                    .setMessage("Are you sure you want to leave this household?")
                    .setPositiveButton("Leave", (dialog, which) -> {

                        db.collection("users")
                                .document(userId)
                                .update("flatId", null)
                                .addOnSuccessListener(unused -> {

                                    // Reset UI
                                    joinCodeText.setText("Not set");

                                    generateJoinCodeButton.setEnabled(true);
                                    generateJoinCodeButton.setText("Generate");
                                    generateJoinCodeButton.setAlpha(1f);

                                    joinFlatButton.setEnabled(true);
                                    joinFlatButton.setAlpha(1f);
                                    joinCodeInput.setEnabled(true);

                                    flatNameInput.setText("");
                                    joinCodeInput.setText("");

                                    Toast.makeText(requireContext(),
                                            "You left the household",
                                            Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(requireContext(),
                                                "Failed to leave household",
                                                Toast.LENGTH_SHORT).show());
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        // View household members
        if (viewMembersButton != null) {
            viewMembersButton.setOnClickListener(view -> {

                db.collection("users")
                        .document(userId)
                        .get()
                        .addOnSuccessListener(userDoc -> {

                            String flatId = userDoc.getString("flatId");

                            if (flatId == null) {
                                Toast.makeText(requireContext(),
                                        "You are not in a household",
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }

                            db.collection("users")
                                    .whereEqualTo("flatId", flatId)
                                    .get()
                                    .addOnSuccessListener(querySnapshot -> {

                                        if (querySnapshot.isEmpty()) {
                                            Toast.makeText(requireContext(),
                                                    "No members found",
                                                    Toast.LENGTH_SHORT).show();
                                            return;
                                        }

                                        StringBuilder members = new StringBuilder();

                                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                                            String name = doc.getString("name");

                                            if (name == null) name = "Unnamed user";

                                            members.append("• ").append(name).append("\n");
                                        }

                                        new AlertDialog.Builder(requireContext())
                                                .setTitle("Household Members")
                                                .setMessage(members.toString())
                                                .setPositiveButton("OK", null)
                                                .show();
                                    });
                        });
            });
        }

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

                    // Save to Firestore
                    FirebaseFirestore db = FirebaseFirestore.getInstance();

                    Map<String, Object> userData = new HashMap<>();
                    userData.put("name", newName);
                    userData.put("email", u.getEmail());

                    db.collection("users")
                            .document(u.getUid())
                            .set(userData, SetOptions.merge());

                    Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }

    private void sendPasswordReset() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();

        if (u == null || TextUtils.isEmpty(u.getEmail())) {
            Toast.makeText(requireContext(),
                    "We couldn't find your account email. Please log in again.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        FirebaseAuth.getInstance().sendPasswordResetEmail(u.getEmail())
                .addOnSuccessListener(unused ->
                        Toast.makeText(requireContext(),
                                "Password reset email sent. Check your inbox.",
                                Toast.LENGTH_LONG).show())
                .addOnFailureListener(e -> {
                    String message;

                    if (e.getMessage() != null && e.getMessage().contains("network")) {
                        message = "Network error. Please check your connection and try again.";
                    } else {
                        message = "Something went wrong. Please try again.";
                    }

                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                });
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
