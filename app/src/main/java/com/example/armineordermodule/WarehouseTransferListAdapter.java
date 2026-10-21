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

public class WarehouseTransferListAdapter extends RecyclerView.Adapter<WarehouseTransferListAdapter.ViewHolder> {

    public interface Listener {
        void onOpenTransfer(WarehouseTransfer transfer);

        void onDeleteTransfer(WarehouseTransfer transfer);
    }

    private final List<WarehouseTransfer> items = new ArrayList<>();
    private final Listener listener;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());

    public WarehouseTransferListAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<WarehouseTransfer> transfers) {
        items.clear();
        if (transfers != null) {
            items.addAll(transfers);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_warehouse_transfer, parent, false);
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
        private final TextView tvRoute;
        private final TextView tvMeta;
        private final TextView tvSent;
        private final MaterialButton btnOpen;
        private final ImageButton btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRoute = itemView.findViewById(R.id.tvTransferRoute);
            tvMeta = itemView.findViewById(R.id.tvTransferMeta);
            tvSent = itemView.findViewById(R.id.tvTransferSent);
            btnOpen = itemView.findViewById(R.id.btnOpenTransfer);
            btnDelete = itemView.findViewById(R.id.btnDeleteTransfer);
        }

        void bind(WarehouseTransfer transfer, Listener listener, SimpleDateFormat dateFormat) {
            String source = label(transfer.sourceDescription, transfer.warehouseCode);
            String target = label(transfer.targetDescription, transfer.toWarehouseCode);
            tvRoute.setText(itemView.getContext().getString(
                    R.string.warehouse_transfer_scan_route, source, target));

            String date = transfer.createdAtMillis > 0
                    ? dateFormat.format(new Date(transfer.createdAtMillis))
                    : "";
            tvMeta.setText(itemView.getContext().getString(
                    R.string.warehouse_transfer_item_meta, transfer.productCount(), date));
            boolean sent = transfer.isNebimSend;
            tvSent.setVisibility(sent ? View.VISIBLE : View.GONE);
            btnOpen.setVisibility(sent ? View.GONE : View.VISIBLE);
            btnDelete.setVisibility(sent ? View.GONE : View.VISIBLE);

            View.OnClickListener openClick = v -> {
                if (listener != null) {
                    listener.onOpenTransfer(transfer);
                }
            };
            tvRoute.setOnClickListener(openClick);
            btnOpen.setOnClickListener(openClick);
            btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteTransfer(transfer);
                }
            });
        }

        private static String label(String description, String code) {
            if (!TextUtils.isEmpty(description)) {
                return description;
            }
            return code != null ? code : "";
        }
    }
}
