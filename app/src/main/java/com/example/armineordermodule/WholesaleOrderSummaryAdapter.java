package com.example.armineordermodule;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class WholesaleOrderSummaryAdapter
        extends RecyclerView.Adapter<WholesaleOrderSummaryAdapter.ViewHolder> {

    private final List<WholesaleOrderProductInfo> items = new ArrayList<>();

    public void submitList(List<WholesaleOrderProductInfo> products) {
        items.clear();
        if (products != null) {
            items.addAll(products);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_wholesale_order_summary_product, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvProductName;
        private final TextView tvProductQty;
        private final TextView tvProductColor;
        private final TextView tvProductMeta;
        private final TextView tvProductStock;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProductName = itemView.findViewById(R.id.tvProductName);
            tvProductQty = itemView.findViewById(R.id.tvProductQty);
            tvProductColor = itemView.findViewById(R.id.tvProductColor);
            tvProductMeta = itemView.findViewById(R.id.tvProductMeta);
            tvProductStock = itemView.findViewById(R.id.tvProductStock);
        }

        void bind(WholesaleOrderProductInfo info) {
            tvProductName.setText(info.displayTitle());
            tvProductQty.setText(itemView.getContext().getString(
                    R.string.wholesale_order_summary_qty, info.qty));

            String colorLine = formatColor(info);
            if (!colorLine.isEmpty()) {
                tvProductColor.setVisibility(View.VISIBLE);
                tvProductColor.setText(colorLine);
            } else {
                tvProductColor.setVisibility(View.GONE);
            }

            StringBuilder meta = new StringBuilder();
            if (!info.itemCode.isEmpty()) {
                meta.append(itemView.getContext().getString(
                        R.string.wholesale_order_summary_item_code, info.itemCode));
            }
            if (!info.barcode.isEmpty()) {
                if (meta.length() > 0) {
                    meta.append("  ·  ");
                }
                meta.append(info.barcode);
            }
            if (meta.length() > 0) {
                tvProductMeta.setVisibility(View.VISIBLE);
                tvProductMeta.setText(meta.toString());
            } else {
                tvProductMeta.setVisibility(View.GONE);
            }

            int errorColor = ContextCompat.getColor(itemView.getContext(), R.color.login_error);
            int okColor = ContextCompat.getColor(itemView.getContext(), R.color.login_primary);

            if (!info.found) {
                tvProductStock.setText(R.string.wholesale_order_summary_stock_not_found);
                tvProductStock.setTextColor(errorColor);
            } else if (info.stock == null) {
                tvProductStock.setText(R.string.wholesale_order_summary_stock_unknown);
                tvProductStock.setTextColor(errorColor);
            } else if (info.stock < info.qty) {
                tvProductStock.setText(itemView.getContext().getString(
                        R.string.wholesale_order_summary_stock_short, info.stock));
                tvProductStock.setTextColor(errorColor);
            } else {
                tvProductStock.setText(itemView.getContext().getString(
                        R.string.wholesale_order_summary_stock_ok, info.stock));
                tvProductStock.setTextColor(okColor);
            }
        }

        private String formatColor(WholesaleOrderProductInfo info) {
            boolean hasDescription = !info.colorDescription.isEmpty();
            boolean hasCode = !info.colorCode.isEmpty();
            if (hasDescription && hasCode && !info.colorDescription.equals(info.colorCode)) {
                return itemView.getContext().getString(
                        R.string.wholesale_order_summary_color,
                        info.colorDescription,
                        info.colorCode);
            }
            if (hasDescription) {
                return itemView.getContext().getString(
                        R.string.wholesale_order_summary_color_single, info.colorDescription);
            }
            if (hasCode) {
                return itemView.getContext().getString(
                        R.string.wholesale_order_summary_color_single, info.colorCode);
            }
            return "";
        }
    }
}
