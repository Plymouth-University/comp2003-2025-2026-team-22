package com.example.flatflex;

import com.google.firebase.Timestamp;

/**
 * Data model representing a single chore stored in Firestore.
 *
 * This class is intentionally simple with public fields so that
 * Firestore can automatically serialise and deserialise documents.
 */
public class Chore {

    // Firestore document ID (manually assigned after retrieval)
    public String id;

    // Title or name of the chore (e.g. "Take bins out")
    public String title;

    // Display name of the assigned user (used for UI)
    public String assignedTo;

    // UID of the assigned user (used for filtering and logic)
    public String assignedToId;

    // Whether the chore has been completed
    public boolean completed;

    // Indicates if this chore was automatically generated (suggested chore)
    public boolean isDefault;

    // Timestamp when the chore was created
    public Timestamp createdAt;

    // Timestamp when the chore was completed
    public Timestamp completedAt;

    // Timestamp representing when the chore is due
    public Timestamp dueDate;

    /**
     * Required empty constructor for Firestore.
     */
    public Chore() { }

    /**
     * Convenience constructor for manual creation (optional use).
     */
    public Chore(String id, String title, String assignedTo, boolean completed) {
        this.id = id;
        this.title = title;
        this.assignedTo = assignedTo;
        this.completed = completed;
        this.isDefault = false;
    }
}