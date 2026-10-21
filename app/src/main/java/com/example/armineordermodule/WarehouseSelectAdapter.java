package com.example.armineordermodule;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class WarehouseSelectAdapter extends RecyclerView.Adapter<WarehouseSelectAdapter.ViewHolder> {

    public interface OnWarehouseSelectedListener {
        void onWarehouseSelected(WarehouseItem warehouse);
    }

    private final List<WarehouseItem> items = new ArrayList<>();
    private final OnWarehouseSelectedListener listener;
    private int selectedIndex = -1;

    public WarehouseSelectAdapter(OnWarehouseSelectedListener listener) {
        this.listener = listener;
    }

    public void submitList(List<WarehouseItem> warehouses) {
        replaceItems(warehouses, true);
    }

    /** Arama sonucu: sunucunun dondurdugu sira korunur. */
    public void submitSearchResults(List<WarehouseItem> warehouses) {
        replaceItems(warehouses, false);
    }

    private void replaceItems(List<WarehouseItem> warehouses, boolean sort) {
        WarehouseItem selected = getSelected();
        items.clear();
        selectedIndex = -1;
        if (warehouses != null) {
            if (sort) {
                items.addAll(WarehouseItem.sortedForDisplay(warehouses));
            } else {
                items.addAll(warehouses);
            }
        }
        if (selected != null) {
            for (int i = 0; i < items.size(); i++) {
                if (equalsWarehouse(items.get(i), selected)) {
                    selectedIndex = i;
                    break;
                }
            }
        }
        notifyDataSetChanged();
    }

    public void setSelected(WarehouseItem warehouse) {
        selectedIndex = -1;
        if (warehouse == null) {
            notifyDataSetChanged();
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            WarehouseItem item = items.get(i);
            if (equalsWarehouse(item, warehouse)) {
                selectedIndex = i;
                break;
            }
        }
        notifyDataSetChanged();
    }

    public WarehouseItem getSelected() {
        if (selectedIndex < 0 || selectedIndex >= items.size()) {
            return null;
        }
        return items.get(selectedIndex);
    }

    private static boolean equalsWarehouse(WarehouseItem a, WarehouseItem b) {
        return a.officeCode.equals(b.officeCode) && a.warehouseCode.equals(b.warehouseCode);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_warehouse_selectable, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), position == selectedIndex);
        holder.itemView.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION) {
                return;
            }
            selectedIndex = adapterPosition;
            notifyDataSetChanged();
            if (listener != null) {
                listener.onWarehouseSelected(items.get(adapterPosition));
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvName;
        private final TextView tvCode;
        private final TextView tvDefaultBadge;
        private final TextView tvSelectedMark;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvWarehouseName);
            tvCode = itemView.findViewById(R.id.tvWarehouseCode);
            tvDefaultBadge = itemView.findViewById(R.id.tvWarehouseDefaultBadge);
            tvSelectedMark = itemView.findViewById(R.id.tvWarehouseSelectedMark);
        }

        void bind(WarehouseItem item, boolean selected) {
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
            tvSelectedMark.setVisibility(selected ? View.VISIBLE : View.GONE);
            itemView.setAlpha(1f);
            itemView.setBackgroundColor(ContextCompat.getColor(
                    itemView.getContext(),
                    selected ? R.color.menu_card_stroke : R.color.white));
        }
    }
}
