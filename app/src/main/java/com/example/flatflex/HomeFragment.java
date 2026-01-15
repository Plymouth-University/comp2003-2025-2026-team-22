package com.example.flatflex;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class HomeFragment extends Fragment {

    public HomeFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_home, container, false);

        // Add Chore button -> opens AddChoreActivity
        View addBtn = v.findViewById(R.id.btnAddChore);
        if (addBtn != null) {
            addBtn.setOnClickListener(view ->
                    startActivity(new Intent(requireContext(), AddChoreActivity.class))
            );
        }

        // Rotate Chores button -> opens RotateChoresActivity
        View rotateBtn = v.findViewById(R.id.btnRotateChores);
        if (rotateBtn != null) {
            rotateBtn.setOnClickListener(view ->
                    startActivity(new Intent(requireContext(), RotateChoresActivity.class))
            );
        }

        // Swap Chores button -> opens SwapChoresActivity
        View swapBtn = v.findViewById(R.id.btnSwapChores);
        if (swapBtn != null) {
            swapBtn.setOnClickListener(view ->
                    startActivity(new Intent(requireContext(), SwapChoresActivity.class))
            );
        }

        return v;
    }
}
