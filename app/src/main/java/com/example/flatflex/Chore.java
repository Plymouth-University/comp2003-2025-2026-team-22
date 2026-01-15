package com.example.flatflex;

public class Chore {
    public String id;
    public String title;
    public String assignedTo;
    public boolean completed;

    public Chore(String id, String title, String assignedTo, boolean completed) {
        this.id = id;
        this.title = title;
        this.assignedTo = assignedTo;
        this.completed = completed;
    }
}
