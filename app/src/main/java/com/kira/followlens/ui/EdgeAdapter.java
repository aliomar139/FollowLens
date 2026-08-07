package com.kira.followlens.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.kira.followlens.R;
import com.kira.followlens.data.EdgeRow;

import java.util.ArrayList;
import java.util.List;

public class EdgeAdapter extends RecyclerView.Adapter<EdgeAdapter.ViewHolder> {

    /** Tints for the monogram, one per Monogram.TINT_COUNT slot. */
    private static final int[] TINTS = {
            R.color.tint_1, R.color.tint_2, R.color.tint_3,
            R.color.tint_4, R.color.tint_5, R.color.tint_6,
    };

    private final List<EdgeRow> items = new ArrayList<>();

    public void submit(List<EdgeRow> rows) {
        items.clear();
        if (rows != null) {
            items.addAll(rows);
        }
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
        EdgeRow row = items.get(position);
        Context context = holder.itemView.getContext();

        holder.username.setText(row.username);

        holder.monogram.setText(Monogram.initialOf(row.username));
        int tint = ContextCompat.getColor(context, TINTS[Monogram.tintIndexOf(row.userId)]);
        holder.monogram.getBackground().setColorFilter(tint, PorterDuff.Mode.SRC_IN);

        if (row.mutual) {
            holder.badge.setText(R.string.badge_mutual);
            holder.badge.setVisibility(View.VISIBLE);
        } else {
            holder.badge.setVisibility(View.GONE);
        }

        holder.itemView.setContentDescription(row.username
                + (row.mutual ? ", " + context.getString(R.string.badge_mutual) : ""));
        holder.itemView.setOnClickListener(v -> openProfile(context, row.username));
    }

    /**
     * Opens the profile in whatever handles instagram.com — the installed app if
     * present, otherwise a browser. No API call, so this cannot contribute to
     * rate limiting.
     */
    private void openProfile(Context context, String username) {
        if (username == null || username.isEmpty()) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.instagram.com/" + username + "/"));
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.no_browser, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView monogram;
        final TextView username;
        final TextView badge;

        ViewHolder(View view) {
            super(view);
            monogram = view.findViewById(R.id.monogram);
            username = view.findViewById(R.id.username);
            badge = view.findViewById(R.id.badge);
        }
    }
}
