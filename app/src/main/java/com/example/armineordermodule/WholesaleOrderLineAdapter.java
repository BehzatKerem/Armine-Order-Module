package com.example.armineordermodule;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class WholesaleOrderLineAdapter extends RecyclerView.Adapter<WholesaleOrderLineAdapter.ViewHolder> {

    /** Satirin cop kutusuna basilinca cagrilir. */
    public interface OnLineDeleteClickListener {
        void onLineDeleteClick(WholesaleOrderLine item);
    }

    private final List<WholesaleOrderLine> items = new ArrayList<>();

    @Nullable
    private OnLineDeleteClickListener deleteClickListener;

    /**
     * Verilirse her satirin sonunda cop kutusu gorunur. Verilmezse (ör. Iptal Barkod
     * modalindaki liste) satirlar eskisi gibi sadece metin gosterir.
     */
    public void setOnLineDeleteClickListener(@Nullable OnLineDeleteClickListener listener) {
        deleteClickListener = listener;
        notifyDataSetChanged();
    }

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
        WholesaleOrderLine item = items.get(position);
        holder.bind(item);
        if (deleteClickListener != null) {
            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.btnDelete.setOnClickListener(v -> {
                if (deleteClickListener != null) {
                    deleteClickListener.onLineDeleteClick(item);
                }
            });
        } else {
            holder.btnDelete.setVisibility(View.GONE);
            holder.btnDelete.setOnClickListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageButton btnDelete;
        private final TextView tvBarcode;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            btnDelete = itemView.findViewById(R.id.btnLineDelete);
            tvBarcode = itemView.findViewById(R.id.tvLineBarcode);
        }

        void bind(WholesaleOrderLine line) {
            // Satir kimligi artik barkod degil; ham EPC gosterilir.
            tvBarcode.setText(line.barcode);
        }
    }
}
