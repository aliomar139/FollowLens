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

public class EdgeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SECTION = 0;
    private static final int TYPE_ACCOUNT = 1;

    /** Tints for the monogram, one per Monogram.TINT_COUNT slot. */
    private static final int[] TINTS = {
            R.color.tint_1, R.color.tint_2, R.color.tint_3,
            R.color.tint_4, R.color.tint_5, R.color.tint_6,
    };

    private final List<EdgeListItems.Item> items = new ArrayList<>();

    public void submit(List<EdgeRow> rows) {
        items.clear();
        items.addAll(EdgeListItems.build(rows));
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).isHeading() ? TYPE_SECTION : TYPE_ACCOUNT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_SECTION) {
            return new SectionHolder(inflater.inflate(R.layout.item_section, parent, false));
        }
        View view = inflater.inflate(R.layout.item_edge, parent, false);
        // Rows are controls too, and until now they were the only tappable thing
        // in the app without the press response every button has.
        Press.applyTo(view);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        EdgeListItems.Item item = items.get(position);
        if (holder instanceof SectionHolder) {
            ((SectionHolder) holder).label.setText(item.heading());
            return;
        }

        ViewHolder accountHolder = (ViewHolder) holder;
        EdgeRow row = item.row();
        Context context = holder.itemView.getContext();

        accountHolder.username.setText(row.username);

        accountHolder.monogram.setText(Monogram.initialOf(row.username));
        int tint = ContextCompat.getColor(context, TINTS[Monogram.tintIndexOf(row.userId)]);
        // mutate() first: monogram backgrounds share one constant state, so an
        // untinted copy would carry the last row's colour into every other row.
        accountHolder.monogram.getBackground().mutate()
                .setColorFilter(tint, PorterDuff.Mode.SRC_IN);

        accountHolder.badgeNew.setVisibility(row.isNew ? View.VISIBLE : View.GONE);

        if (row.mutual) {
            accountHolder.badge.setText(R.string.badge_mutual);
            accountHolder.badge.setVisibility(View.VISIBLE);
        } else {
            accountHolder.badge.setVisibility(View.GONE);
        }

        // Badges are part of the row's meaning, so they are announced in the
        // same order they are read.
        StringBuilder description = new StringBuilder(row.username);
        if (row.isNew) {
            description.append(", ").append(context.getString(R.string.badge_new_spoken));
        }
        if (row.mutual) {
            description.append(", ").append(context.getString(R.string.badge_mutual));
        }
        accountHolder.itemView.setContentDescription(description);
        accountHolder.itemView.setOnClickListener(v -> openProfile(context, row.username));
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

    static class SectionHolder extends RecyclerView.ViewHolder {
        final TextView label;

        SectionHolder(View view) {
            super(view);
            label = view.findViewById(R.id.section_label);
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView monogram;
        final TextView username;
        final TextView badge;
        final TextView badgeNew;

        ViewHolder(View view) {
            super(view);
            monogram = view.findViewById(R.id.monogram);
            username = view.findViewById(R.id.username);
            badge = view.findViewById(R.id.badge);
            badgeNew = view.findViewById(R.id.badge_new);
        }
    }
}
