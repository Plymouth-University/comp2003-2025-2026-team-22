package com.example.flatflex;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class HomeFragment extends Fragment {

    public HomeFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_home, container, false);

        // Show who is signed in (Firestore preferred, falls back to Firebase Auth)
TextView currentUserText = v.findViewById(R.id.currentUserText);
FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
if (currentUserText != null && user != null) {
    // Quick fallback while Firestore loads
    String fallback = !TextUtils.isEmpty(user.getDisplayName()) ? user.getDisplayName()
            : (!TextUtils.isEmpty(user.getEmail()) ? user.getEmail() : "");
    currentUserText.setText("Signed in as: " + fallback);

    FirebaseFirestore db = FirebaseFirestore.getInstance();
    db.collection("users").document(user.getUid())
            .get()
            .addOnSuccessListener(doc -> {
                if (doc != null && doc.exists()) {
                    String name = doc.getString("name");
                    if (!TextUtils.isEmpty(name)) {
                        currentUserText.setText("Signed in as: " + name);
                    }
                }
            });
}

// Add Chore button -> opens AddChoreActivity
        View addBtn = v.findViewById(R.id.btnAddChore);
        if (addBtn != null) {
            addBtn.setOnClickListener(view ->
                    startActivity(new Intent(requireContext(), AddChoreActivity.class))
            );
        }

        // Rotate Chores button -> opens RotateChoresActivity
        View rotateBtn = v.findViewById(R.id.btnRotateChores);
        if (rotateBtn != null) {
            rotateBtn.setOnClickListener(view ->
                    startActivity(new Intent(requireContext(), RotateChoresActivity.class))
            );
        }

        // Swap Chores button -> opens SwapChoresActivity
        View swapBtn = v.findViewById(R.id.btnSwapChores);
        if (swapBtn != null) {
            swapBtn.setOnClickListener(view ->
                    startActivity(new Intent(requireContext(), SwapChoresActivity.class))
            );
        }

        return v;
    }
}
