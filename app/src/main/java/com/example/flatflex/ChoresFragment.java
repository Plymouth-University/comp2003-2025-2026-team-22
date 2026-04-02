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

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * Chores screen:
 * - Displays chores retrieved from Firestore
 * - Supports filtering between "My chores" and "Household chores"
 * - Allows assigning, deleting, swapping, and completing chores
 */
public class ChoresFragment extends Fragment implements ChoreAdapter.ChoreActionListener {

    // List currently displayed in the UI (filtered)
    private final List<Chore> chores = new ArrayList<>();

    // Full list retrieved from Firestore (unfiltered)
    private final List<Chore> allChores = new ArrayList<>();

    private ChoreAdapter adapter;
    private ListenerRegistration choresListener;

    private final ChoreRepository repo = new ChoreRepository();
    private String uid;

    // Prevents toggle callback firing when setting initial state
    private boolean suppressToggleCallback = false;

    // Controls whether only the current user's chores are shown
    private boolean showOnlyMine = true;

    public ChoresFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_chores, container, false);

        // Set up RecyclerView
        RecyclerView rv = root.findViewById(R.id.choresRecycler);
        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            adapter = new ChoreAdapter(requireContext(), chores, this);
            rv.setAdapter(adapter);
        }

        // Set up toggle buttons for filtering view mode
        MaterialButton btnMy = root.findViewById(R.id.btnMyChores);
        MaterialButton btnAll = root.findViewById(R.id.btnAllChores);

        if (btnMy != null && btnAll != null) {

            // Default state: show only user's chores
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

        // Ensure user is logged in
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            safeToast("Please log in to view chores.");
            return root;
        }
        uid = user.getUid();

        // Suggested chores toggle (pre-populated chores feature)
        SwitchCompat toggle = root.findViewById(R.id.switchPrepopulate);
        if (toggle != null) {

            toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (suppressToggleCallback) return;
                if (uid == null) return;

                repo.setPrepopulateEnabled(uid, isChecked)
                        .addOnFailureListener(e -> safeToast("Failed to save setting."));

                if (isChecked) {
                    repo.ensureDefaultChores(uid)
                            .addOnFailureListener(e -> safeToast("Failed to add suggested chores."));
                } else {
                    repo.deleteDefaultChores(uid)
                            .addOnSuccessListener(x -> safeToast("Suggested chores removed."))
                            .addOnFailureListener(e -> safeToast("Failed to remove suggested chores."));
                }
            });

            // Load saved toggle state without triggering the listener
            suppressToggleCallback = true;
            repo.getPrepopulateEnabled(uid)
                    .addOnSuccessListener(enabled -> {
                        if (!isAdded()) return;
                        suppressToggleCallback = true;
                        toggle.setChecked(enabled);
                        suppressToggleCallback = false;

                        if (enabled) repo.ensureDefaultChores(uid);
                    })
                    .addOnFailureListener(e -> {
                        if (!isAdded()) return;
                        suppressToggleCallback = true;
                        toggle.setChecked(true);
                        suppressToggleCallback = false;

                        repo.ensureDefaultChores(uid);
                    })
                    .addOnCompleteListener(t -> suppressToggleCallback = false);
        }

        // Listen for real-time updates to chores
        choresListener = repo.listenToChores(uid, (snapshots, e) -> {
            if (!isAdded()) return;

            if (e != null) {
                safeToast("Failed to load chores.");
                return;
            }

            // Update full dataset from Firestore
            allChores.clear();
            allChores.addAll(ChoreRepository.toChoreList(snapshots));

            // Apply current filter
            filterChores();
        });

        return root;
    }

    /**
     * Filters the full chore list based on the selected view mode.
     * Updates the RecyclerView with the filtered result.
     */
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

    // -----------------------
    // Chore actions
    // -----------------------

    @Override
    public void onAssign(Chore chore) {
        if (!isAdded()) return;
        if (uid == null || chore == null || TextUtils.isEmpty(chore.id)) return;

        repo.getAssignableMemberNames(uid)
                .addOnSuccessListener(names -> {
                    if (!isAdded()) return;

                    if (names == null || names.isEmpty()) {
                        showManualAssignDialog(chore);
                        return;
                    }

                    String[] items = names.toArray(new String[0]);

                    new AlertDialog.Builder(requireContext())
                            .setTitle("Assign chore")
                            .setMessage("Assign: " + safeTitle(chore))
                            .setItems(items, (d, which) -> {
                                String selected = items[which];
                                repo.assignChore(uid, chore.id, selected)
                                        .addOnFailureListener(e -> safeToast("Failed to assign chore."));
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .addOnFailureListener(e -> showManualAssignDialog(chore));
    }

    private void showManualAssignDialog(Chore chore) {
        if (!isAdded()) return;

        EditText input = new EditText(requireContext());
        input.setHint("e.g., Alex");
        input.setText(chore.assignedTo == null ? "" : chore.assignedTo);

        new AlertDialog.Builder(requireContext())
                .setTitle("Assign chore")
                .setMessage("Who should do: " + safeTitle(chore) + "?")
                .setView(input)
                .setPositiveButton("Assign", (d, which) -> {
                    String who = input.getText().toString().trim();
                    if (TextUtils.isEmpty(who)) who = "Unassigned";
                    repo.assignChore(uid, chore.id, who)
                            .addOnFailureListener(e -> safeToast("Failed to assign chore."));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onSwap(Chore chore) {
        if (!isAdded()) return;
        if (uid == null || chore == null || TextUtils.isEmpty(chore.id)) return;

        List<Chore> others = new ArrayList<>();
        for (Chore c : chores) {
            if (c != null && c.id != null && !c.id.equals(chore.id)) {
                others.add(c);
            }
        }

        if (others.isEmpty()) {
            safeToast("No other chores to swap with.");
            return;
        }

        String[] labels = new String[others.size()];
        for (int i = 0; i < others.size(); i++) {
            Chore c = others.get(i);
            String who = (c.assignedTo == null ? "Unassigned" : c.assignedTo);
            labels[i] = safeTitle(c) + " (assigned to " + who + ")";
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Swap assignment with…")
                .setItems(labels, (d, which) -> {
                    Chore target = others.get(which);
                    if (target == null || TextUtils.isEmpty(target.id)) return;

                    repo.swapAssignments(uid, chore.id, target.id)
                            .addOnSuccessListener(x -> safeToast("Assignments swapped."))
                            .addOnFailureListener(e -> safeToast("Failed to swap assignments."));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onDelete(Chore chore) {
        if (!isAdded()) return;
        if (uid == null || chore == null || TextUtils.isEmpty(chore.id)) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Delete chore")
                .setMessage("Delete \"" + safeTitle(chore) + "\"?")
                .setPositiveButton("Delete", (d, which) -> {
                    repo.deleteChore(uid, chore.id)
                            .addOnFailureListener(e -> safeToast("Failed to delete chore."));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onToggleComplete(Chore chore) {
        if (!isAdded()) return;
        if (uid == null || chore == null || TextUtils.isEmpty(chore.id)) return;

        if (chore.completed) {
            safeToast("Already completed.");
            return;
        }

        repo.markComplete(uid, chore.id)
                .addOnSuccessListener(x -> safeToast("Marked complete."))
                .addOnFailureListener(e -> safeToast("Failed to update chore."));
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