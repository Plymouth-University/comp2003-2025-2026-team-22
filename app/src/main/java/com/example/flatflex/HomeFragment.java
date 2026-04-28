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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public class HomeFragment extends Fragment implements ChoreAdapter.ChoreActionListener {

    public HomeFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_home, container, false);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        // ========================
        // TEXT VIEWS
        // ========================
        TextView currentUserText = v.findViewById(R.id.currentUserText);
        TextView flatNameText = v.findViewById(R.id.flatNameText);
        TextView joinCodeText = v.findViewById(R.id.joinCodeText);

        TextView todayDueText = v.findViewById(R.id.todayDueText);
        TextView overdueText = v.findViewById(R.id.overdueText);
        TextView completedText = v.findViewById(R.id.completedWeekText);

        // ========================
        // RECYCLERS
        // ========================
        RecyclerView todayRecycler = v.findViewById(R.id.todayChoresRecycler);
        RecyclerView overdueRecycler = v.findViewById(R.id.overdueChoresRecycler);
        RecyclerView upcomingRecycler = v.findViewById(R.id.upcomingChoresRecycler);

        // ========================
        // USER DISPLAY
        // ========================
        if (user != null && currentUserText != null) {
            String name = user.getDisplayName();
            if (!TextUtils.isEmpty(name)) {
                currentUserText.setText("Signed in as: " + name);
            } else if (user.getEmail() != null) {
                currentUserText.setText("Signed in as: " + user.getEmail());
            }
        }

        // ========================
        // LISTS
        // ========================
        List<Chore> todayChores = new ArrayList<>();
        List<Chore> overdueChores = new ArrayList<>();
        List<Chore> upcomingChores = new ArrayList<>();

        // ========================
        // ADAPTERS
        // ========================
        ChoreAdapter todayAdapter = new ChoreAdapter(requireContext(), todayChores, this);
        ChoreAdapter overdueAdapter = new ChoreAdapter(requireContext(), overdueChores, this);
        ChoreAdapter upcomingAdapter = new ChoreAdapter(requireContext(), upcomingChores, this);

        // ========================
        // RECYCLER SETUP
        // ========================
        todayRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        todayRecycler.setAdapter(todayAdapter);

        overdueRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        overdueRecycler.setAdapter(overdueAdapter);

        upcomingRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        upcomingRecycler.setAdapter(upcomingAdapter);

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        if (user != null) {
            String userId = user.getUid();

            // Ensure user doc exists
            db.collection("users").document(userId)
                    .set(new HashMap<>(), SetOptions.merge());

            db.collection("users").document(userId)
                    .get()
                    .addOnSuccessListener(userDoc -> {

                        String flatId = userDoc.getString("flatId");
                        if (flatId == null) return;

                        // ========================
                        // LOAD FLAT INFO
                        // ========================
                        db.collection("flats").document(flatId)
                                .get()
                                .addOnSuccessListener(flatDoc -> {
                                    if (flatNameText != null)
                                        flatNameText.setText("Flat: " + flatDoc.getString("name"));

                                    if (joinCodeText != null)
                                        joinCodeText.setText("Join code: " + flatDoc.getString("joinCode"));
                                });

                        // ========================
                        // REALTIME CHORES
                        // ========================
                        db.collection("flats")
                                .document(flatId)
                                .collection("chores")
                                .addSnapshotListener((snapshots, e) -> {

                                    if (snapshots == null) return;

                                    int todayCount = 0;
                                    int overdueCount = 0;
                                    int completedCount = 0;

                                    todayChores.clear();
                                    overdueChores.clear();
                                    upcomingChores.clear();

                                    long now = System.currentTimeMillis();
                                    long oneDay = 86400000;

                                    long startOfToday = now - (now % oneDay);
                                    long startOfTomorrow = startOfToday + oneDay;

                                    String myName = user.getDisplayName();

                                    for (var doc : snapshots.getDocuments()) {

                                        Chore c = doc.toObject(Chore.class);
                                        if (c == null) continue;

                                        c.id = doc.getId();

                                        // Only my chores
                                        if (c.assignedTo == null || !c.assignedTo.equals(myName))
                                            continue;

                                        // Completed
                                        if (c.completed) {
                                            completedCount++;
                                            continue;
                                        }

                                        if (c.dueDate == null) continue;

                                        long due = c.dueDate.toDate().getTime();

                                        if (due >= startOfToday && due < startOfTomorrow) {
                                            todayCount++;
                                            todayChores.add(c);

                                        } else if (due >= startOfTomorrow) {
                                            upcomingChores.add(c);

                                        } else {
                                            overdueCount++;
                                            overdueChores.add(c);
                                        }
                                    }

                                    // SORT
                                    Collections.sort(upcomingChores, (a, b) -> a.dueDate.compareTo(b.dueDate));
                                    Collections.sort(overdueChores, (a, b) -> a.dueDate.compareTo(b.dueDate));

                                    // ========================
                                    // UPDATE UI
                                    // ========================
                                    if (todayDueText != null)
                                        todayDueText.setText(String.valueOf(todayCount));

                                    if (overdueText != null)
                                        overdueText.setText(String.valueOf(overdueCount));

                                    if (completedText != null)
                                        completedText.setText(String.valueOf(completedCount));

                                    todayAdapter.notifyDataSetChanged();
                                    overdueAdapter.notifyDataSetChanged();
                                    upcomingAdapter.notifyDataSetChanged();
                                });
                    });
        }

        return v;
    }

    // ========================
    // ACTIONS
    // ========================
    @Override
    public void onToggleComplete(Chore chore) {

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || chore == null || chore.id == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users").document(user.getUid())
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
                                    "completedAt", com.google.firebase.firestore.FieldValue.serverTimestamp()
                            );
                });
    }

    @Override
    public void onDelete(Chore chore) {

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || chore == null || chore.id == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(userDoc -> {

                    String flatId = userDoc.getString("flatId");
                    if (flatId == null) return;

                    db.collection("flats")
                            .document(flatId)
                            .collection("chores")
                            .document(chore.id)
                            .delete();
                });
    }

    @Override
    public void onAssign(Chore chore) {
        // For now: no-op (handle assignment in a dialog elsewhere - possibly obsolete?)
        // You can expand this later if needed
    }

    @Override
    public void onSwap(Chore chore) {
        // Placeholder – depends on your swap logic (not implemented in your system yet)
    }
}