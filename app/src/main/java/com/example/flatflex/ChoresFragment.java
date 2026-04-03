package com.example.flatflex;

import android.app.DatePickerDialog;
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

/**
 * Fragment responsible for displaying and managing chores within a household.
 *
 * Features:
 * - Displays chores stored under a household (flat) in Firestore
 * - Allows adding new chores with assignment
 * - Supports filtering between "My chores" and "All chores"
 * - Displays real-time updates using Firestore snapshot listeners
 */
public class ChoresFragment extends Fragment implements ChoreAdapter.ChoreActionListener {

    // List currently displayed in RecyclerView (after filtering)
    private final List<Chore> chores = new ArrayList<>();

    // Full dataset retrieved from Firestore (unfiltered)
    private final List<Chore> allChores = new ArrayList<>();

    private ChoreAdapter adapter;
    private ListenerRegistration choresListener;

    // Firebase user ID of the currently logged-in user
    private String uid;

    // Determines whether only the current user's chores are shown
    private boolean showOnlyMine = true;

    public ChoresFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_chores, container, false);

        // Input field for entering a new chore name
        EditText choreInput = root.findViewById(R.id.choreInput);

        // Dropdown containing household member names
        android.widget.Spinner userSpinner = root.findViewById(R.id.userSpinner);

        // Button used to save a new chore
        View saveBtn = root.findViewById(R.id.btnSaveChore);

        // Ensure a user is logged in before proceeding
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            safeToast("Please log in to view chores.");
            return root;
        }
        uid = user.getUid();

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        /**
         * Load all users belonging to the same household (flat)
         * and populate the dropdown (spinner) with their names.
         */
        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(userDoc -> {

                    String flatId = userDoc.getString("flatId");
                    if (flatId == null) return;

                    db.collection("users")
                            .whereEqualTo("flatId", flatId)
                            .get()
                            .addOnSuccessListener(query -> {

                                List<String> names = new ArrayList<>();

                                for (var doc : query.getDocuments()) {
                                    String name = doc.getString("name");
                                    if (name != null) names.add(name);
                                }

                                if (names.isEmpty()) names.add("Unassigned");

                                android.widget.ArrayAdapter<String> adapter =
                                        new android.widget.ArrayAdapter<>(
                                                requireContext(),
                                                android.R.layout.simple_spinner_item,
                                                names
                                        );

                                adapter.setDropDownViewResource(
                                        android.R.layout.simple_spinner_dropdown_item
                                );

                                userSpinner.setAdapter(adapter);
                            });
                });

        /**
         * Handles saving a new chore to Firestore.
         * The chore is stored under the household (flat) rather than per-user.
         */
        if (saveBtn != null) {
            saveBtn.setOnClickListener(v -> {

                String choreName = choreInput.getText().toString().trim();

                if (TextUtils.isEmpty(choreName)) {
                    choreInput.setError("Enter a chore name");
                    return;
                }

                final String assignedName =
                        (userSpinner != null && userSpinner.getSelectedItem() != null)
                                ? userSpinner.getSelectedItem().toString()
                                : "Unassigned";

                Calendar cal = Calendar.getInstance();

                String[] options = {"Today", "Tomorrow", "Pick a date"};

                new AlertDialog.Builder(requireContext())
                        .setTitle("When is this due?")
                        .setItems(options, (dialog, which) -> {

                            if (which == 0) {
                                cal.set(Calendar.HOUR_OF_DAY, 23);
                                cal.set(Calendar.MINUTE, 59);
                                cal.set(Calendar.SECOND, 0);
                                saveChoreWithDate(cal, choreName, assignedName);

                            } else if (which == 1) {
                                cal.add(Calendar.DAY_OF_YEAR, 1);
                                cal.set(Calendar.HOUR_OF_DAY, 23);
                                cal.set(Calendar.MINUTE, 59);
                                cal.set(Calendar.SECOND, 0);
                                saveChoreWithDate(cal, choreName, assignedName);

                            } else {

                                new DatePickerDialog(requireContext(),
                                        (view, year, month, day) -> {

                                            cal.set(year, month, day, 23, 59, 0);
                                            saveChoreWithDate(cal, choreName, assignedName);

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

        // Set up RecyclerView for displaying chores
        RecyclerView rv = root.findViewById(R.id.choresRecycler);
        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            adapter = new ChoreAdapter(requireContext(), chores, this);
            rv.setAdapter(adapter);
        }

        /**
         * Toggle buttons to switch between:
         * - Only current user's chores
         * - All household chores
         */
        MaterialButton btnMy = root.findViewById(R.id.btnMyChores);
        MaterialButton btnAll = root.findViewById(R.id.btnAllChores);

        if (btnMy != null && btnAll != null) {

            btnMy.setAlpha(1f);
            btnAll.setAlpha(0.5f);

            btnMy.setOnClickListener(v -> {
                showOnlyMine = true;
                btnMy.setAlpha(1f);
                btnAll.setAlpha(0.5f);
                filterChores();
            });

            btnAll.setOnClickListener(v -> {
                showOnlyMine = false;
                btnAll.setAlpha(1f);
                btnMy.setAlpha(0.5f);
                filterChores();
            });
        }

        // Listen for real-time updates to chores in the household
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

    private void saveChoreWithDate(Calendar cal, String choreName, String assignedName) {

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
                    chore.put("assignedToId", uid);
                    chore.put("completed", false);
                    chore.put("createdAt", FieldValue.serverTimestamp());
                    chore.put("dueDate", new com.google.firebase.Timestamp(cal.getTime()));

                    db.collection("flats")
                            .document(flatId)
                            .collection("chores")
                            .add(chore)
                            .addOnSuccessListener(doc -> {
                                Toast.makeText(requireContext(),
                                        "Chore added!",
                                        Toast.LENGTH_SHORT).show();
                            });
                });
    }

    private void filterChores() {

        if (!isAdded()) return;

        chores.clear();

        if (showOnlyMine && uid != null) {

            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            String myName = currentUser != null ? currentUser.getDisplayName() : null;

            for (Chore c : allChores) {
                if (c.assignedTo != null && c.assignedTo.equals(myName)) {
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
    public void onAssign(Chore chore) { }

    @Override
    public void onSwap(Chore chore) {

        if (chore == null || chore.id == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(userDoc -> {

                    String flatId = userDoc.getString("flatId");
                    if (flatId == null) return;

                    // Get all users in the same flat
                    db.collection("users")
                            .whereEqualTo("flatId", flatId)
                            .get()
                            .addOnSuccessListener(query -> {

                                List<String> names = new ArrayList<>();

                                for (var doc : query.getDocuments()) {
                                    String name = doc.getString("name");

                                    // Exclude current assignee
                                    if (name != null && !name.equals(chore.assignedTo)) {
                                        names.add(name);
                                    }
                                }

                                if (names.isEmpty()) {
                                    Toast.makeText(requireContext(),
                                            "No other users to swap with",
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                new AlertDialog.Builder(requireContext())
                                        .setTitle("Swap chore with:")
                                        .setItems(names.toArray(new String[0]), (dialog, which) -> {

                                            String selectedUser = names.get(which);

                                            db.collection("flats")
                                                    .document(flatId)
                                                    .collection("chores")
                                                    .document(chore.id)
                                                    .update("assignedTo", selectedUser)
                                                    .addOnSuccessListener(aVoid ->
                                                            Toast.makeText(requireContext(),
                                                                    "Chore reassigned!",
                                                                    Toast.LENGTH_SHORT).show()
                                                    );
                                        })
                                        .setNegativeButton("Cancel", null)
                                        .show();
                            });
                });
    }

    @Override
    public void onDelete(Chore chore) {

        if (chore == null || chore.id == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Delete chore")
                .setMessage("Are you sure you want to delete \"" + safeTitle(chore) + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> {

                    FirebaseFirestore db = FirebaseFirestore.getInstance();

                    db.collection("users")
                            .document(uid)
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
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onToggleComplete(Chore chore) {

        if (chore == null || chore.id == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users")
                .document(uid)
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

    private void safeToast(String msg) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private static String safeTitle(Chore chore) {
        if (chore == null) return "(Chore)";
        return TextUtils.isEmpty(chore.title) ? "(Untitled chore)" : chore.title;
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