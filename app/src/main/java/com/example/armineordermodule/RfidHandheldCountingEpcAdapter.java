package com.example.armineordermodule;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class RfidHandheldCountingEpcAdapter extends RecyclerView.Adapter<RfidHandheldCountingEpcAdapter.ViewHolder> {

    private final List<String> items = new ArrayList<>();

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
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvEpc;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEpc = itemView.findViewById(R.id.tvEpc);
        }

        void bind(String epc) {
            tvEpc.setText(epc);
        }
    }
}
