package com.example.armineordermodule;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class WarehouseListAdapter extends RecyclerView.Adapter<WarehouseListAdapter.ViewHolder> {

    private final List<WarehouseItem> items = new ArrayList<>();

    public void submitList(List<WarehouseItem> warehouses) {
        items.clear();
        items.addAll(WarehouseItem.sortedForDisplay(warehouses));
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_warehouse, parent, false);
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
        private final TextView tvName;
        private final TextView tvCode;
        private final TextView tvDefaultBadge;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvWarehouseName);
            tvCode = itemView.findViewById(R.id.tvWarehouseCode);
            tvDefaultBadge = itemView.findViewById(R.id.tvWarehouseDefaultBadge);
        }

        void bind(WarehouseItem item) {
            String title = !TextUtils.isEmpty(item.warehouseDescription)
                    ? item.warehouseDescription
                    : item.officeWarehouseCode();
            tvName.setText(TextUtils.isEmpty(title) ? "-" : title);

            String subtitle = item.officeWarehouseCode();
            if (!TextUtils.isEmpty(subtitle) && !subtitle.equals(title)) {
                tvCode.setVisibility(View.VISIBLE);
                tvCode.setText(subtitle);
            } else {
                tvCode.setVisibility(View.GONE);
            }

            tvDefaultBadge.setVisibility(item.isDefault ? View.VISIBLE : View.GONE);
        }
    }
}
