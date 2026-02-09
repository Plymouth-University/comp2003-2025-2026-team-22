package com.example.flatflex;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import com.google.android.gms.tasks.Tasks;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Firestore-backed repository for chores.
 *
 * Data model:
 * users/{uid}/chores/{choreId}
 */
public class ChoreRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private CollectionReference choresRef(@NonNull String uid) {
        return db.collection("users").document(uid).collection("chores");
    }

    private DocumentReference settingsRef(@NonNull String uid) {
        // Single settings doc for chores
        return db.collection("users").document(uid)
                .collection("settings").document("chores");
    }

    /**
     * Reads whether pre-populated chores are enabled for this user.
     * Default: true (if the setting document doesn't exist yet).
     */
    public Task<Boolean> getPrepopulateEnabled(@NonNull String uid) {
        return settingsRef(uid).get().continueWith(task -> {
            if (!task.isSuccessful()) {
                throw task.getException();
            }
            DocumentSnapshot doc = task.getResult();
            if (doc == null || !doc.exists()) {
                return true;
            }
            Boolean v = doc.getBoolean("prepopulateEnabled");
            return v == null ? true : v;
        });
    }

    public Task<Void> setPrepopulateEnabled(@NonNull String uid, boolean enabled) {
        Map<String, Object> m = new HashMap<>();
        m.put("prepopulateEnabled", enabled);
        m.put("updatedAt", FieldValue.serverTimestamp());
        return settingsRef(uid).set(m, SetOptions.merge());
}

    
    /**
     * Attempts to load member display names for the current user's flat/house.
     *
     * This method is written to be schema-tolerant because projects name the grouping differently.
     *
     * It will:
     * 1) Read users/{uid} and try to find one of these fields: houseId, flatId, householdId, groupId
     * 2) Query the matching collection (households/flats/houses/groups) and subcollection "members"
     * 3) For each member doc, it tries fields: name, displayName, fullName, email
     *
     * If nothing is found, returns an empty list.
     */
    public Task<List<String>> getAssignableMemberNames(@NonNull String uid) {
        return db.collection("users").document(uid).get().continueWithTask(t -> {
            if (!t.isSuccessful()) {
                return Tasks.forResult(new ArrayList<>());
            }
            DocumentSnapshot userDoc = t.getResult();
            if (userDoc == null || !userDoc.exists()) {
                return Tasks.forResult(new ArrayList<>());
            }

            String groupId =
                    firstNonEmpty(
                            userDoc.getString("houseId"),
                            userDoc.getString("flatId"),
                            userDoc.getString("householdId"),
                            userDoc.getString("groupId")
                    );

            if (groupId == null) {
                return Tasks.forResult(new ArrayList<>());
            }

            // Try common parent collections in order
            String[] parents = new String[]{"households", "flats", "houses", "groups"};
            return tryMemberCollections(parents, groupId, 0);
        });
    }

    private Task<List<String>> tryMemberCollections(String[] parents, String groupId, int idx) {
        if (idx >= parents.length) {
            return Tasks.forResult(new ArrayList<>());
        }
        String parent = parents[idx];
        return db.collection(parent).document(groupId).collection("members").get()
                .continueWithTask(qt -> {
                    if (qt.isSuccessful() && qt.getResult() != null && !qt.getResult().isEmpty()) {
                        List<String> names = new ArrayList<>();
                        for (DocumentSnapshot d : qt.getResult().getDocuments()) {
                            String name = firstNonEmpty(
                                    d.getString("name"),
                                    d.getString("displayName"),
                                    d.getString("fullName"),
                                    d.getString("email")
                            );
                            if (!TextUtils.isEmpty(name)) names.add(name);
                            else names.add(d.getId());
                        }
                        return Tasks.forResult(names);
                    }
                    return tryMemberCollections(parents, groupId, idx + 1);
                });
    }

    private static String firstNonEmpty(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (!TextUtils.isEmpty(v)) return v;
        }
        return null;
    }

/**
     * Deletes all default (pre-populated) chores for this user.
     */
    public Task<Void> deleteDefaultChores(@NonNull String uid) {
        return choresRef(uid).whereEqualTo("isDefault", true).get().continueWithTask(task -> {
            if (!task.isSuccessful()) {
                throw task.getException();
            }
            QuerySnapshot snap = task.getResult();
            if (snap == null || snap.isEmpty()) {
                return com.google.android.gms.tasks.Tasks.forResult(null);
            }
            WriteBatch batch = db.batch();
            for (DocumentSnapshot d : snap.getDocuments()) {
                batch.delete(d.getReference());
            }
            return batch.commit();
        });
    }

    public Task<Void> assignChore(@NonNull String uid, @NonNull String choreId, @NonNull String assignedTo) {
        return choresRef(uid).document(choreId).update(
                "assignedTo", assignedTo,
                "updatedAt", FieldValue.serverTimestamp()
        );
    }

    public Task<Void> deleteChore(@NonNull String uid, @NonNull String choreId) {
        return choresRef(uid).document(choreId).delete();
    }

    /**
     * Swap the "assignedTo" values between two chores atomically.
     */
    public Task<Void> swapAssignments(@NonNull String uid, @NonNull String choreIdA, @NonNull String choreIdB) {
        DocumentReference a = choresRef(uid).document(choreIdA);
        DocumentReference b = choresRef(uid).document(choreIdB);

        return db.runTransaction(tx -> {
            DocumentSnapshot da = tx.get(a);
            DocumentSnapshot dbs = tx.get(b);

            String aAssigned = da.getString("assignedTo");
            String bAssigned = dbs.getString("assignedTo");

            if (aAssigned == null) aAssigned = "Unassigned";
            if (bAssigned == null) bAssigned = "Unassigned";

            tx.update(a, "assignedTo", bAssigned, "updatedAt", FieldValue.serverTimestamp());
            tx.update(b, "assignedTo", aAssigned, "updatedAt", FieldValue.serverTimestamp());
            return null;
        });
    }


    /**
     * If the user's chores collection is empty, insert a default starter set.
     */
    public Task<Void> ensureDefaultChores(@NonNull String uid) {
        return choresRef(uid).limit(1).get().continueWithTask(task -> {
            if (!task.isSuccessful()) {
                // Propagate failure
                throw task.getException();
            }
            QuerySnapshot snap = task.getResult();
            if (snap != null && !snap.isEmpty()) {
                // Already has chores
                return com.google.android.gms.tasks.Tasks.forResult(null);
            }

            List<Map<String, Object>> defaults = new ArrayList<>();
            defaults.add(defaultChore("Take out the bins", "Unassigned"));
            defaults.add(defaultChore("Wash dishes", "Unassigned"));
            defaults.add(defaultChore("Vacuum living room", "Unassigned"));
            defaults.add(defaultChore("Clean bathroom", "Unassigned"));
            defaults.add(defaultChore("Wipe kitchen surfaces", "Unassigned"));
            defaults.add(defaultChore("Mop floors", "Unassigned"));

            WriteBatch batch = db.batch();
            for (Map<String, Object> c : defaults) {
                DocumentReference doc = choresRef(uid).document();
                batch.set(doc, c);
            }
            return batch.commit();
        });
    }

    private Map<String, Object> defaultChore(String title, String assignedTo) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", title);
        m.put("assignedTo", assignedTo);
        m.put("completed", false);
        m.put("isDefault", true);
        m.put("createdAt", FieldValue.serverTimestamp());
        m.put("completedAt", null);
        return m;
    }

    public Task<DocumentReference> addChore(@NonNull String uid,
                                            @NonNull String title,
                                            @NonNull String assignedTo) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", title);
        m.put("assignedTo", assignedTo);
        m.put("completed", false);
        m.put("isDefault", false);
        m.put("createdAt", FieldValue.serverTimestamp());
        m.put("completedAt", null);
        return choresRef(uid).add(m);
    }

    public Task<Void> markComplete(@NonNull String uid, @NonNull String choreId) {
        return choresRef(uid).document(choreId).update(
                "completed", true,
                "completedAt", FieldValue.serverTimestamp()
        );
    }

    /**
     * Live listener for chore list changes.
     */
    public ListenerRegistration listenToChores(@NonNull String uid,
                                               @NonNull EventListener<QuerySnapshot> listener) {
        // You can add ordering later if desired (e.g., by createdAt)
        return choresRef(uid).addSnapshotListener(listener);
    }

    public static List<Chore> toChoreList(QuerySnapshot snapshots) {
        List<Chore> out = new ArrayList<>();
        if (snapshots == null) return out;
        for (DocumentSnapshot doc : snapshots.getDocuments()) {
            Chore c = doc.toObject(Chore.class);
            if (c == null) continue;
            c.id = doc.getId();
            out.add(c);
        }
        return out;
    }
}
