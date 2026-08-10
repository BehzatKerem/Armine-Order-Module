package com.example.armineordermodule;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class InventoryListAdapter extends RecyclerView.Adapter<InventoryListAdapter.ViewHolder> {

    public interface Listener {
        void onOpenCount(InventoryCount count);

        void onTransferCount(InventoryCount count);

        void onDeleteCount(InventoryCount count);
    }

    private final List<InventoryCount> items = new ArrayList<>();
    private final Listener listener;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());

    public InventoryListAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<InventoryCount> counts) {
        items.clear();
        if (counts != null) {
            items.addAll(counts);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_inventory_count, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), listener, dateFormat);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvName;
        private final TextView tvMeta;
        private final TextView tvStatusBadge;
        private final MaterialButton btnOpen;
        private final MaterialButton btnTransfer;
        private final ImageButton btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvCountName);
            tvMeta = itemView.findViewById(R.id.tvCountMeta);
            tvStatusBadge = itemView.findViewById(R.id.tvCountStatusBadge);
            btnOpen = itemView.findViewById(R.id.btnOpenCount);
            btnTransfer = itemView.findViewById(R.id.btnTransferCount);
            btnDelete = itemView.findViewById(R.id.btnDeleteCount);
        }

        void bind(InventoryCount count, Listener listener, SimpleDateFormat dateFormat) {
            tvName.setText(TextUtils.isEmpty(count.name) ? "-" : count.name);

            String warehouse = !TextUtils.isEmpty(count.warehouseDescription)
                    ? count.warehouseDescription
                    : count.warehouseCode;
            String date = dateFormat.format(new Date(count.createdAtMillis));
            String meta = itemView.getContext().getString(
                    R.string.inventory_item_meta,
                    count.totalLocationCount(),
                    warehouse,
                    date);
            tvMeta.setText(meta);

            boolean pendingSend = InventoryCount.STATUS_PENDING_SEND.equals(count.status);
            tvStatusBadge.setText(pendingSend
                    ? R.string.inventory_status_pending_send
                    : R.string.inventory_status_draft);

            View.OnClickListener openClick = v -> {
                if (listener != null) {
                    listener.onOpenCount(count);
                }
            };
            tvName.setOnClickListener(openClick);
            btnOpen.setOnClickListener(openClick);
            btnTransfer.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTransferCount(count);
                }
            });
            btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteCount(count);
                }
            });
        }
    }
}
