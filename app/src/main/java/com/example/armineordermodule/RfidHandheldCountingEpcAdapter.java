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

public class RfidHandheldCountingEpcAdapter extends RecyclerView.Adapter<RfidHandheldCountingEpcAdapter.ViewHolder> {

    /** Satirin cop kutusuna basilinca cagrilir. */
    public interface OnLineDeleteClickListener {
        void onLineDeleteClick(String item);
    }

    private final List<String> items = new ArrayList<>();

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

    public void submitList(List<String> epcs) {
        items.clear();
        if (epcs != null) {
            items.addAll(epcs);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_rfid_handheld_counting_epc, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String item = items.get(position);
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
        private final TextView tvEpc;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            btnDelete = itemView.findViewById(R.id.btnLineDelete);
            tvEpc = itemView.findViewById(R.id.tvEpc);
        }

        void bind(String epc) {
            tvEpc.setText(epc);
        }
    }
}
