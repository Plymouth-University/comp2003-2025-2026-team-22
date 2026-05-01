package com.example.flatflex;

/**
 * Fragment responsible for displaying and managing chores within a household.
 *
 * Features:
 * - Displays chores stored under a household (flat) in Firestore
 * - Allows adding new chores with assignment
 * - Supports filtering between "My chores" and "All chores"
 * - Displays real-time updates using Firestore snapshot listeners
 */

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Spinner;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class ChoresFragment extends Fragment implements ChoreAdapter.ChoreActionListener {

    private final List<Chore> chores = new ArrayList<>();
    private final List<Chore> allChores = new ArrayList<>();

    private ChoreAdapter adapter;
    private ListenerRegistration choresListener;

    private String uid;
    private boolean showOnlyMine = true;

    private Spinner userSpinner;
    private String selectedAssigneeId;

    private final List<String> memberNames = new ArrayList<>();
    private final List<String> memberIds = new ArrayList<>();

    public ChoresFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_chores, container, false);

        android.widget.EditText choreInput = root.findViewById(R.id.choreInput);
        userSpinner = root.findViewById(R.id.userSpinner);

        View saveBtn = root.findViewById(R.id.btnSaveChore);
        View autoAssignBtn = root.findViewById(R.id.btnAutoAssign);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            safeToast("Please log in to view chores.");
            return root;
        }

        uid = user.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(userDoc -> {

                    String flatId = userDoc.getString("flatId");
                    if (flatId == null) return;

                    loadFlatMembers(db, flatId);
                });

        if (autoAssignBtn != null) {
            autoAssignBtn.setOnClickListener(v -> autoAssignChore());
        }

        if (saveBtn != null) {
            saveBtn.setOnClickListener(v -> {

                String choreName = choreInput.getText().toString().trim();

                if (TextUtils.isEmpty(choreName)) {
                    choreInput.setError("Enter a chore name");
                    return;
                }

                int selectedIndex = userSpinner.getSelectedItemPosition();

                final String assignedName =
                        selectedIndex >= 0 && selectedIndex < memberNames.size()
                                ? memberNames.get(selectedIndex)
                                : "Unassigned";

                final String assignedUserId =
                        selectedIndex >= 0 && selectedIndex < memberIds.size()
                                ? memberIds.get(selectedIndex)
                                : null;

                Calendar cal = Calendar.getInstance();

                String[] options = {"Today", "Tomorrow", "Pick a date"};

                new AlertDialog.Builder(requireContext())
                        .setTitle("When is this due?")
                        .setItems(options, (dialog, which) -> {

                            if (which == 0) {
                                cal.set(Calendar.HOUR_OF_DAY, 23);
                                cal.set(Calendar.MINUTE, 59);
                                cal.set(Calendar.SECOND, 0);
                                saveChoreWithDate(cal, choreName, assignedName, assignedUserId);

                            } else if (which == 1) {
                                cal.add(Calendar.DAY_OF_YEAR, 1);
                                cal.set(Calendar.HOUR_OF_DAY, 23);
                                cal.set(Calendar.MINUTE, 59);
                                cal.set(Calendar.SECOND, 0);
                                saveChoreWithDate(cal, choreName, assignedName, assignedUserId);

                            } else {

                                new DatePickerDialog(requireContext(),
                                        (view, year, month, day) -> {
                                            cal.set(year, month, day, 23, 59, 0);
                                            saveChoreWithDate(cal, choreName, assignedName, assignedUserId);
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                ).show();
                            }

                        })
                        .show();
            });
        }

        RecyclerView rv = root.findViewById(R.id.choresRecycler);
        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            adapter = new ChoreAdapter(requireContext(), chores, this);
            rv.setAdapter(adapter);
        }

        MaterialButton btnMy = root.findViewById(R.id.btnMyChores);
        MaterialButton btnAll = root.findViewById(R.id.btnAllChores);

        if (btnMy != null && btnAll != null) {

            // Default state
            btnMy.setAlpha(1f);
            btnMy.setElevation(8f);
            btnMy.setTypeface(null, android.graphics.Typeface.BOLD);

            btnAll.setAlpha(0.7f);
            btnAll.setElevation(0f);
            btnAll.setTypeface(null, android.graphics.Typeface.NORMAL);

            btnMy.setOnClickListener(v -> {
                showOnlyMine = true;

                btnMy.setAlpha(1f);
                btnMy.setElevation(8f);
                btnMy.setTypeface(null, android.graphics.Typeface.BOLD);

                btnAll.setAlpha(0.7f);
                btnAll.setElevation(0f);
                btnAll.setTypeface(null, android.graphics.Typeface.NORMAL);

                filterChores();
            });

            btnAll.setOnClickListener(v -> {
                showOnlyMine = false;

                btnAll.setAlpha(1f);
                btnAll.setElevation(8f);
                btnAll.setTypeface(null, android.graphics.Typeface.BOLD);

                btnMy.setAlpha(0.7f);
                btnMy.setElevation(0f);
                btnMy.setTypeface(null, android.graphics.Typeface.NORMAL);

                filterChores();
            });
        }

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(userDoc -> {

                    String flatId = userDoc.getString("flatId");
                    if (flatId == null) return;

                    choresListener = db.collection("flats")
                            .document(flatId)
                            .collection("chores")
                            .addSnapshotListener((snapshots, e) -> {

                                if (!isAdded()) return;

                                if (e != null) {
                                    safeToast("Failed to load chores.");
                                    return;
                                }

                                allChores.clear();

                                for (var doc : snapshots.getDocuments()) {
                                    Chore c = doc.toObject(Chore.class);
                                    if (c != null) {
                                        c.id = doc.getId();
                                        allChores.add(c);
                                    }
                                }

                                filterChores();
                            });
                });

        return root;
    }

    private void loadFlatMembers(FirebaseFirestore db, String flatId) {
        db.collection("users")
                .whereEqualTo("flatId", flatId)
                .get()
                .addOnSuccessListener(query -> {

                    memberNames.clear();
                    memberIds.clear();

                    for (var doc : query.getDocuments()) {
                        String name = doc.getString("name");

                        if (TextUtils.isEmpty(name)) {
                            name = doc.getString("email");
                        }

                        if (!TextUtils.isEmpty(name)) {
                            memberNames.add(name);
                            memberIds.add(doc.getId());
                        }
                    }

                    if (memberNames.isEmpty()) {
                        memberNames.add("Unassigned");
                        memberIds.add(null);
                    }

                    ArrayAdapter<String> spinnerAdapter =
                            new ArrayAdapter<>(
                                    requireContext(),
                                    android.R.layout.simple_spinner_item,
                                    memberNames
                            );

                    spinnerAdapter.setDropDownViewResource(
                            android.R.layout.simple_spinner_dropdown_item
                    );

                    userSpinner.setAdapter(spinnerAdapter);
                });
    }

    private void autoAssignChore() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            safeToast("You must be logged in first");
            return;
        }

        db.collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(userDoc -> {

                    String flatId = userDoc.getString("flatId");

                    if (flatId == null || flatId.isEmpty()) {
                        safeToast("You are not part of a flat");
                        return;
                    }

                    loadFlatMembersAndAssign(db, flatId);
                })
                .addOnFailureListener(e ->
                        safeToast("Failed to load user data")
                );
    }

    private void loadFlatMembersAndAssign(FirebaseFirestore db, String flatId) {

        db.collection("users")
                .whereEqualTo("flatId", flatId)
                .get()
                .addOnSuccessListener(usersSnapshot -> {

                    if (usersSnapshot.isEmpty()) {
                        safeToast("No flat members found");
                        return;
                    }

                    Map<String, Integer> workloadCount = new HashMap<>();

                    for (var userDoc : usersSnapshot.getDocuments()) {
                        workloadCount.put(userDoc.getId(), 0);
                    }

                    db.collection("flats")
                            .document(flatId)
                            .collection("chores")
                            .whereEqualTo("completed", false)
                            .get()
                            .addOnSuccessListener(choresSnapshot -> {

                                for (var choreDoc : choresSnapshot.getDocuments()) {
                                    String assignedToId = choreDoc.getString("assignedToId");

                                    if (assignedToId != null && workloadCount.containsKey(assignedToId)) {
                                        workloadCount.put(
                                                assignedToId,
                                                workloadCount.get(assignedToId) + 1
                                        );
                                    }
                                }

                                String bestUserId = null;
                                int lowestWorkload = Integer.MAX_VALUE;

                                for (String userId : workloadCount.keySet()) {
                                    int count = workloadCount.get(userId);

                                    if (count < lowestWorkload) {
                                        lowestWorkload = count;
                                        bestUserId = userId;
                                    }
                                }

                                if (bestUserId != null) {
                                    selectedAssigneeId = bestUserId;

                                    int spinnerIndex = memberIds.indexOf(bestUserId);

                                    if (spinnerIndex >= 0) {
                                        userSpinner.setSelection(spinnerIndex);

                                        String suggestedName = memberNames.get(spinnerIndex);

                                        safeToast("Suggested assignee: " + suggestedName);
                                    }
                                }
                            });
                });
    }

    private void saveChoreWithDate(Calendar cal, String choreName, String assignedName, String assignedUserId) {

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(userDoc -> {

                    String flatId = userDoc.getString("flatId");
                    if (flatId == null) return;

                    Map<String, Object> chore = new HashMap<>();
                    chore.put("title", choreName);
                    chore.put("assignedTo", assignedName);
                    chore.put("assignedToId", assignedUserId);
                    chore.put("completed", false);
                    chore.put("createdAt", FieldValue.serverTimestamp());
                    chore.put("dueDate", new com.google.firebase.Timestamp(cal.getTime()));

                    db.collection("flats")
                            .document(flatId)
                            .collection("chores")
                            .add(chore)
                            .addOnSuccessListener(doc -> {
                                safeToast("Chore added!");
                            });
                });
    }

    private void filterChores() {

        if (!isAdded()) return;

        chores.clear();

        if (showOnlyMine && uid != null) {

            for (Chore c : allChores) {
                if (uid.equals(c.assignedToId)) {
                    chores.add(c);
                }
            }

        } else {
            chores.addAll(allChores);
        }

        if (adapter != null) adapter.notifyDataSetChanged();

        View v = getView();
        if (v != null) {
            TextView hint = v.findViewById(R.id.choresHint);
            if (hint != null) {
                hint.setVisibility(chores.isEmpty() ? View.VISIBLE : View.GONE);
            }
        }
    }


    @Override
    public void onSwap(Chore chore) {

        if (!isAdded() || chore == null || chore.id == null) return;

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(userDoc -> {

                    String flatId = userDoc.getString("flatId");
                    if (flatId == null) return;

                    db.collection("users")
                            .whereEqualTo("flatId", flatId)
                            .get()
                            .addOnSuccessListener(query -> {

                                List<String> names = new ArrayList<>();
                                List<String> ids = new ArrayList<>();

                                for (var doc : query.getDocuments()) {
                                    String name = doc.getString("name");
                                    if (name == null) continue;

                                    names.add(name);
                                    ids.add(doc.getId());
                                }

                                if (names.isEmpty()) return;

                                new android.app.AlertDialog.Builder(requireContext())
                                        .setTitle("Assign Chore")
                                        .setItems(names.toArray(new String[0]), (dialog, which) -> {

                                            String newName = names.get(which);
                                            String newId = ids.get(which);

                                            db.collection("flats")
                                                    .document(flatId)
                                                    .collection("chores")
                                                    .document(chore.id)
                                                    .update(
                                                            "assignedTo", newName,
                                                            "assignedToId", newId
                                                    )
                                                    .addOnSuccessListener(aVoid -> safeToast("Chore reassigned"))
                                                    .addOnFailureListener(e -> safeToast("Failed to reassign"));
                                        })
                                        .show();
                            });
                });
    }

    @Override
    public void onDelete(Chore chore) {

        if (!isAdded() || chore == null || chore.id == null) return;

        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Delete Chore")
                .setMessage("Are you sure you want to delete this chore?")
                .setPositiveButton("Delete", (dialog, which) -> {

                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user == null) return;

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
                                        .delete()
                                        .addOnSuccessListener(aVoid -> safeToast("Chore deleted"))
                                        .addOnFailureListener(e -> safeToast("Failed to delete"));
                            });

                })
                .setNegativeButton("Cancel", null)
                .show();
    }

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
                            )
                            .addOnSuccessListener(aVoid -> safeToast("Chore completed"))
                            .addOnFailureListener(e -> safeToast("Update failed"));
                });
    }

    private void safeToast(String msg) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (choresListener != null) {
            choresListener.remove();
            choresListener = null;
        }
    }
}