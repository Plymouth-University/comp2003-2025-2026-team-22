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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Chores screen:
 * - Displays chores retrieved from Firestore
 * - Supports filtering between "My chores" and "Household chores"
 * - Allows assigning, deleting, swapping, and completing chores
 */
public class ChoresFragment extends Fragment implements ChoreAdapter.ChoreActionListener {

    private final List<Chore> chores = new ArrayList<>();
    private final List<Chore> allChores = new ArrayList<>();

    private ChoreAdapter adapter;
    private ListenerRegistration choresListener;

    private final ChoreRepository repo = new ChoreRepository();
    private String uid;

    private boolean suppressToggleCallback = false;
    private boolean showOnlyMine = true;

    public ChoresFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_chores, container, false);

        EditText choreInput = root.findViewById(R.id.choreInput);
        android.widget.Spinner userSpinner = root.findViewById(R.id.userSpinner);
        View saveBtn = root.findViewById(R.id.btnSaveChore);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            safeToast("Please log in to view chores.");
            return root;
        }
        uid = user.getUid();

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Load users into spinner
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

                                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                                userSpinner.setAdapter(adapter);

                            });

                });

        // Save chore button logic
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

                db.collection("users")
                        .document(uid)
                        .get()
                        .addOnSuccessListener(userDoc -> {

                            String flatId = userDoc.getString("flatId");

                            if (flatId == null) {
                                Toast.makeText(requireContext(), "You are not in a household", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            Map<String, Object> chore = new HashMap<>();
                            chore.put("title", choreName);
                            chore.put("assignedTo", assignedName);
                            chore.put("assignedToId", "");
                            chore.put("completed", false);
                            chore.put("createdAt", FieldValue.serverTimestamp());

                            db.collection("flats")
                                    .document(flatId)
                                    .collection("chores")
                                    .add(chore)
                                    .addOnSuccessListener(doc -> {
                                        Toast.makeText(requireContext(), "Chore added!", Toast.LENGTH_SHORT).show();
                                        choreInput.setText("");
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    });

                        });

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

        SwitchCompat toggle = root.findViewById(R.id.switchPrepopulate);
        if (toggle != null) {

            toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (suppressToggleCallback) return;
                if (uid == null) return;

                repo.setPrepopulateEnabled(uid, isChecked);

                if (isChecked) {
                    repo.ensureDefaultChores(uid);
                } else {
                    repo.deleteDefaultChores(uid);
                }
            });

            suppressToggleCallback = true;
            repo.getPrepopulateEnabled(uid)
                    .addOnSuccessListener(enabled -> {
                        if (!isAdded()) return;
                        suppressToggleCallback = true;
                        toggle.setChecked(enabled);
                        suppressToggleCallback = false;

                        if (enabled) repo.ensureDefaultChores(uid);
                    })
                    .addOnCompleteListener(t -> suppressToggleCallback = false);
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
    public void onSwap(Chore chore) { }

    @Override
    public void onDelete(Chore chore) { }

    @Override
    public void onToggleComplete(Chore chore) { }

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