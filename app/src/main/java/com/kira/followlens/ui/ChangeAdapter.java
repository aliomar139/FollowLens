package com.kira.followlens.ui;

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

public class ChangeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CHANGE = 1;

    private final List<ChangeFeedItems.Item> items = new ArrayList<>();

    /** Package-visible and static so it can be unit tested without a View. */
    static String labelFor(ChangeEventEntity event) {
        boolean added = event.direction == ChangeDirection.ADDED;
        if (event.kind == ListKind.FOLLOWER) {
            return event.username + (added ? " started following you" : " unfollowed you");
        }
        return "you " + (added ? "started following " : "stopped following ") + event.username;
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
        changeHolder.label.setText(labelFor(event));
        changeHolder.sign.setText(signFor(event));
        changeHolder.sign.setTextColor(ContextCompat.getColor(
                changeHolder.itemView.getContext(),
                event.direction == ChangeDirection.ADDED
                        ? R.color.positive : R.color.negative));
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
        final TextView label;
        final TextView sign;

        ChangeHolder(View view) {
            super(view);
            label = view.findViewById(R.id.label);
            sign = view.findViewById(R.id.sign);
        }
    }
}
