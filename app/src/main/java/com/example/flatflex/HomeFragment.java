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

        String flatName = requireContext().getSharedPreferences(PREFS, 0).getString(KEY_FLAT_NAME, "");
        String joinCode = requireContext().getSharedPreferences(PREFS, 0).getString(KEY_JOIN_CODE, "");

        if (flatNameText != null) {
            flatNameText.setText(TextUtils.isEmpty(flatName) ? "Flat: (not set)" : "Flat: " + flatName);
        }
        if (joinCodeText != null) {
            joinCodeText.setText(TextUtils.isEmpty(joinCode) ? "Join code: (not set)" : "Join code: " + joinCode);
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
