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

/**
 * Aktarilmamis / taslak siparisler (Aç / Aktar / Sil).
 */
public class WholesaleOrderListAdapter extends RecyclerView.Adapter<WholesaleOrderListAdapter.ViewHolder> {

    public interface Listener {
        void onOpenOrder(WholesaleOrder order);

        void onTransferOrder(WholesaleOrder order);

        void onDeleteOrder(WholesaleOrder order);
    }

    private final List<WholesaleOrder> items = new ArrayList<>();
    private final Listener listener;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());

    public WholesaleOrderListAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<WholesaleOrder> orders) {
        items.clear();
        if (orders != null) {
            items.addAll(orders);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_wholesale_order, parent, false);
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
        private final TextView tvCustomer;
        private final TextView tvMeta;
        private final TextView tvStatusBadge;
        private final MaterialButton btnOpen;
        private final MaterialButton btnTransfer;
        private final ImageButton btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCustomer = itemView.findViewById(R.id.tvOrderCustomer);
            tvMeta = itemView.findViewById(R.id.tvOrderMeta);
            tvStatusBadge = itemView.findViewById(R.id.tvOrderStatusBadge);
            btnOpen = itemView.findViewById(R.id.btnOpenOrder);
            btnTransfer = itemView.findViewById(R.id.btnTransferOrder);
            btnDelete = itemView.findViewById(R.id.btnDeleteOrder);
        }

        void bind(WholesaleOrder order, Listener listener, SimpleDateFormat dateFormat) {
            String customer = !TextUtils.isEmpty(order.customerDescription)
                    ? order.customerDescription
                    : order.customerCode;
            tvCustomer.setText(TextUtils.isEmpty(customer) ? "-" : customer);

            String warehouse = !TextUtils.isEmpty(order.warehouseDescription)
                    ? order.warehouseDescription
                    : order.warehouseCode;
            String date = dateFormat.format(new Date(order.createdAtMillis));
            tvMeta.setText(itemView.getContext().getString(
                    R.string.wholesale_order_item_meta, order.totalLineCount(), warehouse, date));

            boolean pendingSend = WholesaleOrder.STATUS_PENDING_SEND.equals(order.status);
            tvStatusBadge.setText(pendingSend
                    ? R.string.wholesale_order_status_pending_send
                    : R.string.wholesale_order_status_draft);

            View.OnClickListener openClick = v -> {
                if (listener != null && !order.isTransferred()) {
                    listener.onOpenOrder(order);
                }
            };
            tvCustomer.setOnClickListener(openClick);
            btnOpen.setOnClickListener(openClick);
            btnTransfer.setOnClickListener(v -> {
                if (listener != null && !order.isTransferred()) {
                    listener.onTransferOrder(order);
                }
            });
            btnDelete.setOnClickListener(v -> {
                if (listener != null && !order.isTransferred()) {
                    listener.onDeleteOrder(order);
                }
            });
        }
    }
}
