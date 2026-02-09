package com.example.flatflex;

import com.google.firebase.Timestamp;

/**
 * Firestore-friendly model for a chore.
 * Public fields + a no-arg constructor allow Firestore to deserialize easily.
 */
public class Chore {
    public String id;
    public String title;
    public String assignedTo;
    public boolean completed;

    // True if this chore was auto-created as a suggested/default chore
    public boolean isDefault;

    // Optional timestamps (can be null if not set)
    public Timestamp createdAt;
    public Timestamp completedAt;

    // Required for Firestore
    public Chore() { }

    public Chore(String id, String title, String assignedTo, boolean completed) {
        this.id = id;
        this.title = title;
        this.assignedTo = assignedTo;
        this.completed = completed;
        this.isDefault = false;
    }
}
