package com.example.armineordermodule;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * NebimV3'e aktarilmis siparisler — salt goruntuleme (Ac / Aktar / Sil yok).
 */
public class WholesaleOrderSentListAdapter
        extends RecyclerView.Adapter<WholesaleOrderSentListAdapter.ViewHolder> {

    private final List<WholesaleOrder> items = new ArrayList<>();
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());

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
        holder.bind(items.get(position), dateFormat);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvCustomer;
        private final TextView tvMeta;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCustomer = itemView.findViewById(R.id.tvOrderCustomer);
            tvMeta = itemView.findViewById(R.id.tvOrderMeta);
            // Click yok — aktarilmis fis kilitli.
            itemView.setClickable(false);
            itemView.setLongClickable(false);
        }

        void bind(WholesaleOrder order, SimpleDateFormat dateFormat) {
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
