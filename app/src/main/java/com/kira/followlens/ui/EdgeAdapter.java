package com.kira.followlens.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.kira.followlens.R;
import com.kira.followlens.data.EdgeEntity;

import java.util.ArrayList;
import java.util.List;

public class EdgeAdapter extends RecyclerView.Adapter<EdgeAdapter.ViewHolder> {

    private final List<EdgeEntity> items = new ArrayList<>();

    public void submit(List<EdgeEntity> edges) {
        items.clear();
        items.addAll(edges);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_edge, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.username.setText(items.get(position).username);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView username;

        ViewHolder(View view) {
            super(view);
            username = view.findViewById(R.id.username);
        }
    }
}
