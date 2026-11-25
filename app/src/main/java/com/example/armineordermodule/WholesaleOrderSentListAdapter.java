package com.example.armineordermodule;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * NebimV3'e aktarilmis siparisler — salt goruntuleme (Ac / Aktar yok).
 * Sil butonu sadece gelistirici modunda gorunur ve cihazdaki kaydi siler.
 */
public class WholesaleOrderSentListAdapter
        extends RecyclerView.Adapter<WholesaleOrderSentListAdapter.ViewHolder> {

    public interface Listener {
        void onDeleteSentOrder(WholesaleOrder order);
    }

    private final List<WholesaleOrder> items = new ArrayList<>();
    private final Listener listener;
    private boolean developerMode;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());

    public WholesaleOrderSentListAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setDeveloperMode(boolean enabled) {
        if (developerMode != enabled) {
            developerMode = enabled;
            notifyDataSetChanged();
        }
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
                .inflate(R.layout.item_wholesale_order_sent, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), dateFormat, listener, developerMode);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvCustomer;
        private final TextView tvMeta;
        private final ImageButton btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCustomer = itemView.findViewById(R.id.tvOrderCustomer);
            tvMeta = itemView.findViewById(R.id.tvOrderMeta);
            btnDelete = itemView.findViewById(R.id.btnDeleteSentOrder);
            // Click yok — aktarilmis fis kilitli.
            itemView.setClickable(false);
            itemView.setLongClickable(false);
        }

        void bind(WholesaleOrder order, SimpleDateFormat dateFormat,
                  Listener listener, boolean developerMode) {
            btnDelete.setVisibility(developerMode ? View.VISIBLE : View.GONE);
            btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteSentOrder(order);
                }
            });

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
        }
    }
}
