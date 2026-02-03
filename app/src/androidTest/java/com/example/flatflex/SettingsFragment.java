
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

public class SettingsFragment extends Fragment {

    private EditText nameInput;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        nameInput = view.findViewById(R.id.nameInput);
        Button saveButton = view.findViewById(R.id.saveButton);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getDisplayName() != null) {
            nameInput.setText(user.getDisplayName());
        }

        saveButton.setOnClickListener(v -> {
            String newName = nameInput.getText().toString().trim();

            if (TextUtils.isEmpty(newName)) {
                nameInput.setError("Name required");
                return;
            }

            if (user != null) {
                UserProfileChangeRequest request =
                        new UserProfileChangeRequest.Builder()
                                .setDisplayName(newName)
                                .build();

                user.updateProfile(request)
                        .addOnSuccessListener(unused ->
                                Toast.makeText(getContext(),
                                        "Profile updated",
                                        Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(e ->
                                Toast.makeText(getContext(),
                                        e.getMessage(),
                                        Toast.LENGTH_LONG).show());
            }
        });

        return view;
    }
}
