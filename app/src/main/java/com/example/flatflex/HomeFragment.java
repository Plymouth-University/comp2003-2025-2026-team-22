package com.example.flatflex;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;

public class HomeFragment extends Fragment {

    private static final String PREFS = "flatflex_prefs";
    private static final String KEY_FLAT_NAME = "flat_name";
    private static final String KEY_JOIN_CODE = "join_code";

    public HomeFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_home, container, false);

        // Signed-in label
        TextView currentUserText = v.findViewById(R.id.currentUserText);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUserText != null && user != null) {
            String name = user.getDisplayName();
            if (!TextUtils.isEmpty(name)) {
                currentUserText.setText("Signed in as: " + name);
            } else if (!TextUtils.isEmpty(user.getEmail())) {
                currentUserText.setText("Signed in as: " + user.getEmail());
            } else {
                currentUserText.setText("Signed in as: ");
            }
        }

        // Household info (from prefs, set in Settings)
        TextView flatNameText = v.findViewById(R.id.flatNameText);
        TextView joinCodeText = v.findViewById(R.id.joinCodeText);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            String userId = firebaseUser.getUid();

            // STEP 1: Ensure user document exists
            db.collection("users")
                    .document(userId)
                    .set(new HashMap<>(), SetOptions.merge());

            // STEP 2: Now read user data
            db.collection("users")
                    .document(userId)
                    .get()
                    .addOnSuccessListener(userDoc -> {
                        if (userDoc.exists()) {
                            String flatId = userDoc.getString("flatId");

                            if (flatId != null) {
                                db.collection("flats")
                                        .document(flatId)
                                        .get()
                                        .addOnSuccessListener(flatDoc -> {
                                            if (flatDoc.exists()) {
                                                String flatName = flatDoc.getString("name");
                                                String joinCode = flatDoc.getString("joinCode");
                                                if (flatNameText != null) {
                                                    flatNameText.setText("Flat: " + flatName);
                                                }

                                                if (joinCodeText != null) {
                                                    joinCodeText.setText("Join code: " + joinCode);
                                                }
                                            }
                                        });
                            }
                        }
                    });
        }

        // "Open Chores" button switches bottom nav to Chores tab
        View goToChores = v.findViewById(R.id.btnGoToChores);
        if (goToChores != null) {
            goToChores.setOnClickListener(view -> {
                if (getActivity() == null) return;
                BottomNavigationView bottomNav = getActivity().findViewById(R.id.bottomNav);
                if (bottomNav != null) {
                    bottomNav.setSelectedItemId(R.id.nav_chores);
                }
            });
        }

        return v;
    }
}
