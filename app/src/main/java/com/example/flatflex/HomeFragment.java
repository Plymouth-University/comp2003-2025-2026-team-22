package com.example.flatflex;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment implements ChoreAdapter.ChoreActionListener {

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

        // Household info
        TextView flatNameText = v.findViewById(R.id.flatNameText);
        TextView joinCodeText = v.findViewById(R.id.joinCodeText);

        // Dashboard stat views
        TextView todayDueText = v.findViewById(R.id.todayDueText);
        TextView overdueText = v.findViewById(R.id.overdueText);
        TextView completedText = v.findViewById(R.id.completedWeekText);

        // Recycler for today's chores
        RecyclerView todayRecycler = v.findViewById(R.id.todayChoresRecycler);

        // Upcoming recycler
        RecyclerView upcomingRecycler = v.findViewById(R.id.upcomingChoresRecycler);

        // List + adapter for today's chores
        List<Chore> todayChores = new ArrayList<>();
        ChoreAdapter todayAdapter = new ChoreAdapter(requireContext(), todayChores, this);

        // Upcoming list + adapter
        List<Chore> upcomingChores = new ArrayList<>();
        ChoreAdapter upcomingAdapter = new ChoreAdapter(requireContext(), upcomingChores, this);

        if (todayRecycler != null) {
            todayRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
            todayRecycler.setAdapter(todayAdapter);
        }

        // Setup upcoming recycler
        if (upcomingRecycler != null) {
            upcomingRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
            upcomingRecycler.setAdapter(upcomingAdapter);
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();

        if (firebaseUser != null) {
            String userId = firebaseUser.getUid();

            // Ensure user doc exists
            db.collection("users")
                    .document(userId)
                    .set(new HashMap<>(), SetOptions.merge());

            // Get user data
            db.collection("users")
                    .document(userId)
                    .get()
                    .addOnSuccessListener(userDoc -> {

                        if (!userDoc.exists()) return;

                        String flatId = userDoc.getString("flatId");

                        if (flatId == null) return;

                        // Load household info
                        db.collection("flats")
                                .document(flatId)
                                .get()
                                .addOnSuccessListener(flatDoc -> {

                                    if (!flatDoc.exists()) return;

                                    String flatName = flatDoc.getString("name");
                                    String joinCode = flatDoc.getString("joinCode");

                                    if (flatNameText != null) {
                                        flatNameText.setText("Flat: " + flatName);
                                    }

                                    if (joinCodeText != null) {
                                        joinCodeText.setText("Join code: " + joinCode);
                                    }
                                });

                        /**
                         * REAL-TIME LISTENER FOR CHORES
                         * - Counts stats (today, overdue, completed)
                         * - Populates today's chore list
                         */
                        db.collection("flats")
                                .document(flatId)
                                .collection("chores")
                                .addSnapshotListener((snapshots, e) -> {

                                    if (e != null || snapshots == null) return;

                                    int todayCount = 0;
                                    int overdueCount = 0;
                                    int completedCount = 0;

                                    todayChores.clear();
                                    upcomingChores.clear();

                                    long now = System.currentTimeMillis();
                                    long oneDay = 24 * 60 * 60 * 1000;
                                    long threeDays = 3 * oneDay;

                                    String myName = firebaseUser.getDisplayName();

                                    for (var doc : snapshots.getDocuments()) {

                                        Chore c = doc.toObject(Chore.class);
                                        if (c == null) continue;

                                        // Set chore ID
                                        c.id = doc.getId();

                                        if (c.assignedTo == null || !c.assignedTo.equals(myName)) continue;

                                        if (c.completed) {
                                            completedCount++;
                                            continue;
                                        }

                                        if (c.dueDate == null) continue;

                                        long due = c.dueDate.toDate().getTime();
                                        long diff = due - now;

                                        if (Math.abs(diff) < oneDay) {
                                            todayCount++;
                                            todayChores.add(c);

                                        } else if (diff > oneDay && diff < threeDays) {
                                            upcomingChores.add(c);

                                        } else if (diff < 0) {
                                            overdueCount++;
                                        }
                                    }

                                    if (todayDueText != null) {
                                        todayDueText.setText(String.valueOf(todayCount));
                                    }

                                    if (overdueText != null) {
                                        overdueText.setText(String.valueOf(overdueCount));
                                    }

                                    if (completedText != null) {
                                        completedText.setText(String.valueOf(completedCount));
                                    }

                                    todayAdapter.notifyDataSetChanged();
                                    upcomingAdapter.notifyDataSetChanged();
                                });

                    });
        }

        // Navigate to chores screen
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

    @Override
    public void onToggleComplete(Chore chore) {

        if (chore == null || chore.id == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) return;

        db.collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(userDoc -> {

                    String flatId = userDoc.getString("flatId");
                    if (flatId == null) return;

                    db.collection("flats")
                            .document(flatId)
                            .collection("chores")
                            .document(chore.id)
                            .update(
                                    "completed", true,
                                    "completedAt", FieldValue.serverTimestamp()
                            )
                            .addOnSuccessListener(aVoid ->
                                    Toast.makeText(requireContext(), "Completed!", Toast.LENGTH_SHORT).show()
                            );
                });
    }

    @Override public void onDelete(Chore chore) {}
    @Override public void onAssign(Chore chore) {}
    @Override public void onSwap(Chore chore) {}
}