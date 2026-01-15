package com.example.flatflex;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ChoreAdapter extends RecyclerView.Adapter<ChoreAdapter.VH> {

    private final List<Chore> chores;
    private final Context context;

    public ChoreAdapter(Context context, List<Chore> chores) {
        this.context = context;
        this.chores = chores;
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView title, assignedTo, status;
        Button markComplete;

        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.choreTitle);
            assignedTo = itemView.findViewById(R.id.choreAssignedTo);
            status = itemView.findViewById(R.id.choreStatus);
            markComplete = itemView.findViewById(R.id.btnMarkComplete);
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
        holder.assignedTo.setText("Assigned to: " + c.assignedTo);
        holder.status.setText("Status: " + (c.completed ? "Completed" : "Pending"));
        holder.markComplete.setEnabled(!c.completed);

        holder.markComplete.setOnClickListener(v -> {
            Intent i = new Intent(context, MarkChoreCompleteActivity.class);
            i.putExtra("CHORE_ID", c.id);
            context.startActivity(i);
        });
    }

    @Override
    public int getItemCount() {
        return chores.size();
    }
}
