package com.kira.followlens.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.kira.followlens.R;
import com.kira.followlens.data.ChangeDirection;
import com.kira.followlens.data.ChangeEventEntity;
import com.kira.followlens.data.ListKind;

import java.util.ArrayList;
import java.util.List;

public class ChangeAdapter extends RecyclerView.Adapter<ChangeAdapter.ViewHolder> {

    private final List<ChangeEventEntity> items = new ArrayList<>();

    /** Package-visible and static so it can be unit tested without a View. */
    static String labelFor(ChangeEventEntity event) {
        boolean added = event.direction == ChangeDirection.ADDED;
        String sign = added ? "+ " : "- ";
        if (event.kind == ListKind.FOLLOWER) {
            return sign + event.username + (added ? " started following you" : " unfollowed you");
        }
        return sign + "you " + (added ? "started following " : "stopped following ")
                + event.username;
    }

    public void submit(List<ChangeEventEntity> events) {
        items.clear();
        items.addAll(events);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_change, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.label.setText(labelFor(items.get(position)));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView label;

        ViewHolder(View view) {
            super(view);
            label = view.findViewById(R.id.label);
        }
    }
}
