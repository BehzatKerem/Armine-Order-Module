package com.example.armineordermodule;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class WholesaleOrderLineAdapter extends RecyclerView.Adapter<WholesaleOrderLineAdapter.ViewHolder> {

    private final List<WholesaleOrderLine> items = new ArrayList<>();

    public void submitList(List<WholesaleOrderLine> lines) {
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
                .inflate(R.layout.item_wholesale_order_line, parent, false);
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
        private final TextView tvBarcode;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBarcode = itemView.findViewById(R.id.tvLineBarcode);
        }

        void bind(WholesaleOrderLine line) {
            // Satir kimligi artik barkod degil; ham EPC gosterilir.
            tvBarcode.setText(line.barcode);
        }
    }
}
