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
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chore, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Chore c = chores.get(position);

        holder.title.setText(c.title);
        holder.assignedTo.setText("Assigned to: " + (c.assignedTo == null ? "Unassigned" : c.assignedTo));
        holder.status.setText("Status: " + (c.completed ? "Completed" : "Pending"));

        holder.markComplete.setEnabled(!c.completed);
        holder.markComplete.setOnClickListener(v -> {
            if (listener != null) listener.onToggleComplete(c);
        });

        holder.options.setOnClickListener(v -> {
            PopupMenu pm = new PopupMenu(context, holder.options);
            pm.getMenu().add("Assign");
            pm.getMenu().add("Swap");
            pm.getMenu().add("Delete");
pm.setOnMenuItemClickListener(item -> {
                String t = String.valueOf(item.getTitle());
                if (listener == null) return true;
                switch (t) {
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
