package com.example.armineordermodule;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class InventoryLineAdapter extends RecyclerView.Adapter<InventoryLineAdapter.ViewHolder> {

    public interface OnLineDeleteRequestListener {
        void onLineDeleteRequest(InventoryLine line);
    }

    private final List<InventoryLine> items = new ArrayList<>();
    private final OnLineDeleteRequestListener listener;

    public InventoryLineAdapter(OnLineDeleteRequestListener listener) {
        this.listener = listener;
    }

    public void submitList(List<InventoryLine> lines) {
        items.clear();
        if (lines != null) {
            items.addAll(lines);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_inventory_line, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InventoryLine line = items.get(position);
        holder.bind(line);
        holder.btnRemove.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION && listener != null) {
                listener.onLineDeleteRequest(items.get(adapterPosition));
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvBarcode;
        private final TextView tvQty;
        private final ImageButton btnRemove;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBarcode = itemView.findViewById(R.id.tvLineBarcode);
            tvQty = itemView.findViewById(R.id.tvLineQty);
            btnRemove = itemView.findViewById(R.id.btnRemoveLine);
        }

        void bind(InventoryLine line) {
            tvBarcode.setText(line.barcode);
            tvQty.setText(itemView.getContext().getString(R.string.inventory_line_qty, line.qty));
        }
    }
}
