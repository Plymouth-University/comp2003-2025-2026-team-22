package com.example.flatflex;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * Chores screen: holds all chore actions (moved from Home) and will later host
 * the full chore list UI.
 */
public class ChoresFragment extends Fragment {

    public ChoresFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_chores, container, false);

        View addBtn = v.findViewById(R.id.btnAddChore);
        if (addBtn != null) {
            addBtn.setOnClickListener(view ->
                    startActivity(new Intent(requireContext(), AddChoreActivity.class))
            );
        }

        View rotateBtn = v.findViewById(R.id.btnRotateChores);
        if (rotateBtn != null) {
            rotateBtn.setOnClickListener(view ->
                    startActivity(new Intent(requireContext(), RotateChoresActivity.class))
            );
        }

        View swapBtn = v.findViewById(R.id.btnSwapChores);
        if (swapBtn != null) {
            swapBtn.setOnClickListener(view ->
                    startActivity(new Intent(requireContext(), SwapChoresActivity.class))
            );
        }

        View markBtn = v.findViewById(R.id.btnMarkComplete);
        if (markBtn != null) {
            markBtn.setOnClickListener(view ->
                    startActivity(new Intent(requireContext(), MarkChoreCompleteActivity.class))
            );
        }

        return v;
    }
}
