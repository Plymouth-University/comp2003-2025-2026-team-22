package com.example.flatflex;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * Chores screen:
 * - Stores chores in Firestore under users/{uid}/chores
 * - Suggested chores controlled by per-user toggle:
 *   users/{uid}/settings/chores { prepopulateEnabled: true|false }
 * - Per-chore actions: assign, delete, swap assignments, mark complete
 */
public class LeaderboardFragment extends Fragment {

    public LeaderboardFragment() {
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_leaderboard, container, false);
        return v;
    }
}
