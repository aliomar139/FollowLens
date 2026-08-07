package com.kira.followlens.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.kira.followlens.R;
import com.kira.followlens.data.ScanEntity;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** One row per scan, newest first, each showing its change from the scan before. */
public class ScanAdapter extends RecyclerView.Adapter<ScanAdapter.ViewHolder> {

    private final List<ScanEntity> items = new ArrayList<>();

    public void submit(List<ScanEntity> scans) {
        items.clear();
        if (scans != null) {
            items.addAll(scans);
        }
        notifyDataSetChanged();
    }

    /**
     * Signed change in followers against the next-older scan.
     *
     * Package-visible and static so the formatting is testable without a View.
     */
    static String deltaLabel(int current, Integer previous) {
        if (previous == null) {
            return "—";
        }
        int delta = current - previous;
        if (delta == 0) {
            return "0";
        }
        return (delta > 0 ? "+" : "−") + Math.abs(delta);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_scan, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ScanEntity scan = items.get(position);

        holder.when.setText(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(scan.finishedAt)));
        holder.counts.setText(holder.itemView.getContext()
                .getString(R.string.history_counts_format,
                        scan.followersCount, scan.followingCount));

        // The list is newest-first, so the previous scan is the next index along.
        Integer previous = position + 1 < items.size()
                ? items.get(position + 1).followersCount : null;
        int delta = previous == null ? 0 : scan.followersCount - previous;

        holder.delta.setText(deltaLabel(scan.followersCount, previous));
        int colour = delta > 0 ? R.color.positive
                : delta < 0 ? R.color.negative : R.color.text_disabled;
        holder.delta.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), colour));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView when;
        final TextView counts;
        final TextView delta;

        ViewHolder(View view) {
            super(view);
            when = view.findViewById(R.id.scan_when);
            counts = view.findViewById(R.id.scan_counts);
            delta = view.findViewById(R.id.scan_delta);
        }
    }
}
