package com.example.flatflex;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import android.widget.Spinner;

/**
 * RecyclerView adapter responsible for displaying chore items.
 *
 * Handles:
 * - Binding chore data to UI
 * - Displaying status (upcoming, due today, overdue, completed)
 * - Handling user actions via listener callbacks
 */
public class ChoreAdapter extends RecyclerView.Adapter<ChoreAdapter.VH> {

    public interface ChoreActionListener {
        void onAssign(Chore chore);
        void onSwap(Chore chore);
        void onDelete(Chore chore);
        void onToggleComplete(Chore chore);
    }

    private final List<Chore> chores;
    private final Context context;
    private final ChoreActionListener listener;



    public ChoreAdapter(Context context, List<Chore> chores, ChoreActionListener listener) {
        this.context = context;
        this.chores = chores;
        this.listener = listener;
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView title, assignedTo, status;
        Button markComplete;
        Button options;

        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.choreTitle);
            assignedTo = itemView.findViewById(R.id.choreAssignedTo);
            status = itemView.findViewById(R.id.choreStatus);
            markComplete = itemView.findViewById(R.id.btnMarkComplete);
            options = itemView.findViewById(R.id.btnOptions);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chore, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {

        Chore c = chores.get(position);

        holder.title.setText(c.title);

        holder.assignedTo.setText(
                "Assigned to: " + (c.assignedTo == null ? "Unassigned" : c.assignedTo)
        );

        // Determine status based on completion + due date
        String statusText;

        if (c.completed) {
            statusText = "Completed";
        } else if (c.dueDate == null) {
            statusText = "Upcoming";
        } else {
            long now = System.currentTimeMillis();
            long due = c.dueDate.toDate().getTime();

            long diff = due - now;
            long oneDay = 24 * 60 * 60 * 1000;

            if (Math.abs(diff) < oneDay) {
                statusText = "Due Today";
            } else if (diff < 0) {
                statusText = "Overdue";
            } else {
                statusText = "Upcoming";
            }
        }

        holder.status.setText("Status: " + statusText);

        holder.markComplete.setEnabled(true); // ALWAYS clickable
        holder.markComplete.setAlpha(c.completed ? 0.5f : 1f);

        holder.markComplete.setOnClickListener(v -> {
            if (listener != null && !c.completed) {
                listener.onToggleComplete(c);
            }
        });

        // Options menu (assign / swap / delete)
        holder.options.setOnClickListener(v -> {
            PopupMenu pm = new PopupMenu(context, holder.options);

            pm.getMenu().add("Assign");
            pm.getMenu().add("Swap");
            pm.getMenu().add("Delete");

            pm.setOnMenuItemClickListener(item -> {
                if (listener == null) return true;

                String action = item.getTitle().toString();

                switch (action) {
                    case "Assign":
                        listener.onAssign(c);
                        return true;
                    case "Swap":
                        listener.onSwap(c);
                        return true;
                    case "Delete":
                        listener.onDelete(c);
                        return true;
                    default:
                        return false;
                }
            });

            pm.show();
        });
    }

    @Override
    public int getItemCount() {
        return chores.size();
    }
}