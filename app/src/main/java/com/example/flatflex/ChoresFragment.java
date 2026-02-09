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
public class ChoresFragment extends Fragment implements ChoreAdapter.ChoreActionListener {

    private final List<Chore> chores = new ArrayList<>();
    private ChoreAdapter adapter;
    private ListenerRegistration choresListener;

    private final ChoreRepository repo = new ChoreRepository();
    private String uid;

    // Prevent initial setChecked() from firing listener
    private boolean suppressToggleCallback = false;

    public ChoresFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_chores, container, false);

        View addBtn = root.findViewById(R.id.btnAddChore);
        if (addBtn != null) {
            addBtn.setOnClickListener(v ->
                    startActivity(new android.content.Intent(requireContext(), AddChoreActivity.class))
            );
        }

        // Recycler wiring
        RecyclerView rv = root.findViewById(R.id.choresRecycler);
        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            adapter = new ChoreAdapter(requireContext(), chores, this);
            rv.setAdapter(adapter);
        }

        // Firestore hookup: need logged-in user
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            safeToast("Please log in to view chores.");
            return root;
        }
        uid = user.getUid();

        // Suggested chores toggle
        SwitchCompat toggle = root.findViewById(R.id.switchPrepopulate);
        if (toggle != null) {

            // Listener (gated)
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

            // Load persisted state (default true) without triggering listener
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

        // Live listener for chore list changes
        choresListener = repo.listenToChores(uid, (snapshots, e) -> {
            if (!isAdded()) return;

            if (e != null) {
                safeToast("Failed to load chores.");
                return;
            }

            chores.clear();
            chores.addAll(ChoreRepository.toChoreList(snapshots));
            if (adapter != null) adapter.notifyDataSetChanged();

            View v = getView();
            if (v != null) {
                TextView hint = v.findViewById(R.id.choresHint);
                if (hint != null) {
                    hint.setVisibility(chores.isEmpty() ? View.VISIBLE : View.GONE);
                }
            }
        });

        return root;
    }

    // -----------------------
    // Item actions
    // -----------------------

    @Override
    public void onAssign(Chore chore) {
        if (!isAdded()) return;
        if (uid == null || chore == null || TextUtils.isEmpty(chore.id)) return;

        repo.getAssignableMemberNames(uid)
                .addOnSuccessListener(names -> {
                    if (!isAdded()) return;

                    // If we couldn't load members, fall back to manual input
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

        // Build a list of other chores to swap with
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
