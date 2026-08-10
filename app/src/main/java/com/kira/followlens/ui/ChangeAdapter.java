package com.kira.followlens.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.kira.followlens.R;
import com.kira.followlens.data.ChangeDirection;
import com.kira.followlens.data.ChangeEventEntity;
import com.kira.followlens.data.ListKind;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChangeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CHANGE = 1;

    /** The account lists' tints, so one person keeps one colour app-wide. */
    private static final int[] TINTS = {
            R.color.tint_1, R.color.tint_2, R.color.tint_3,
            R.color.tint_4, R.color.tint_5, R.color.tint_6,
    };

    private final List<ChangeFeedItems.Item> items = new ArrayList<>();

    /**
     * What happened, without the name.
     *
     * The row shows the username on its own line above this, so repeating it
     * here would print it twice. Package-visible and static so it can be unit
     * tested without a View.
     */
    static String actionFor(ChangeEventEntity event) {
        boolean added = event.direction == ChangeDirection.ADDED;
        if (event.kind == ListKind.FOLLOWER) {
            return added ? "Started following you" : "Unfollowed you";
        }
        return added ? "You started following" : "You stopped following";
    }

    /** The glyph, not the colour, is what conveys direction. */
    static String signFor(ChangeEventEntity event) {
        return event.direction == ChangeDirection.ADDED ? "+" : "−";
    }

    public void submit(List<ChangeEventEntity> events) {
        items.clear();
        items.addAll(ChangeFeedItems.build(events));
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).isHeader() ? TYPE_HEADER : TYPE_CHANGE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inflater.inflate(R.layout.item_scan_header, parent, false));
        }
        return new ChangeHolder(inflater.inflate(R.layout.item_change, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChangeFeedItems.Item item = items.get(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).scanTime.setText(
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                            .format(new Date(item.occurredAt())));
            return;
        }
        ChangeHolder changeHolder = (ChangeHolder) holder;
        ChangeEventEntity event = item.change();
        boolean added = event.direction == ChangeDirection.ADDED;
        Context context = changeHolder.itemView.getContext();

        changeHolder.username.setText(event.username);
        changeHolder.label.setText(actionFor(event));
        changeHolder.sign.setText(signFor(event));

        // The same monogram the account lists draw, from the same helpers, so a
        // person keeps one colour and one letter everywhere in the app.
        changeHolder.monogram.setText(Monogram.initialOf(event.username));
        changeHolder.monogram.getBackground().mutate().setColorFilter(
                ContextCompat.getColor(context, TINTS[Monogram.tintIndexOf(event.userId)]),
                PorterDuff.Mode.SRC_IN);

        changeHolder.sign.setTextColor(ContextCompat.getColor(context,
                added ? R.color.positive : R.color.negative));
        // mutate() first: the disc is inflated from one shared constant state,
        // so tinting it without a private copy recolours every other row too.
        changeHolder.sign.getBackground().mutate().setColorFilter(
                ContextCompat.getColor(context,
                        added ? R.color.positive_dim : R.color.negative_dim),
                PorterDuff.Mode.SRC_IN);

        changeHolder.itemView.setContentDescription(
                event.username + ", " + actionFor(event).toLowerCase(Locale.getDefault()));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView scanTime;

        HeaderHolder(View view) {
            super(view);
            scanTime = view.findViewById(R.id.scan_time);
        }
    }

    static class ChangeHolder extends RecyclerView.ViewHolder {
        final TextView monogram;
        final TextView username;
        final TextView label;
        final TextView sign;

        ChangeHolder(View view) {
            super(view);
            monogram = view.findViewById(R.id.monogram);
            username = view.findViewById(R.id.username);
            label = view.findViewById(R.id.label);
            sign = view.findViewById(R.id.sign);
        }
    }
}
