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

public class CustomerListAdapter extends RecyclerView.Adapter<CustomerListAdapter.ViewHolder> {

    public interface OnCustomerClickListener {
        void onCustomerClick(CustomerItem customer);
    }

    private final List<CustomerItem> items = new ArrayList<>();
    private final OnCustomerClickListener listener;

    public CustomerListAdapter(OnCustomerClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<CustomerItem> customers) {
        items.clear();
        if (customers != null) {
            items.addAll(customers);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_customer, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvName;
        private final TextView tvCode;
        private final TextView tvLocation;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvCustomerName);
            tvCode = itemView.findViewById(R.id.tvCustomerCode);
            tvLocation = itemView.findViewById(R.id.tvCustomerLocation);
        }

        void bind(CustomerItem item, OnCustomerClickListener listener) {
            tvName.setText(item.displayName());

            if (!TextUtils.isEmpty(item.currAccCode)) {
                tvCode.setVisibility(View.VISIBLE);
                tvCode.setText(item.currAccCode);
            } else {
                tvCode.setVisibility(View.GONE);
            }

            String location = item.locationLine();
            if (!TextUtils.isEmpty(location)) {
                tvLocation.setVisibility(View.VISIBLE);
                tvLocation.setText(location);
            } else {
                tvLocation.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCustomerClick(item);
                }
            });
        }
    }
}
