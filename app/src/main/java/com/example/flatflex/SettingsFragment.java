package com.example.flatflex;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

/**
 * Settings screen: Edit Profile (display name) stored in Firebase Auth.
 */
public class SettingsFragment extends Fragment {

    public SettingsFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_settings, container, false);

        EditText nameInput = v.findViewById(R.id.nameInput);
        Button saveBtn = v.findViewById(R.id.saveProfileButton);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            String currentName = user.getDisplayName();
            if (!TextUtils.isEmpty(currentName)) {
                nameInput.setText(currentName);
            }
        }

        saveBtn.setOnClickListener(view -> {
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
                        Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show();
                        // If HomeFragment is visible later, it will pull the latest name from FirebaseAuth.
                    })
                    .addOnFailureListener(e -> Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show());
        });

        return v;
    }
}
