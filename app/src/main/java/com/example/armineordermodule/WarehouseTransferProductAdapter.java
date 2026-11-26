package com.example.armineordermodule;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Depo transferi okutma listesi: EPC'ler barkoda gore gruplanmis tek satir
 * (urun adi, beden, okutulan adet, kaynak depodaki stok).
 */
public class WarehouseTransferProductAdapter
        extends RecyclerView.Adapter<WarehouseTransferProductAdapter.ViewHolder> {

    public enum LookupState {
        LOADING,
        LOADED,
        ERROR,
        /** Stok Hatalari listesi: urun listeden cikarildi, sebep errorMessage'da. */
        REMOVED
    }

    /** Bir barkodun ekrandaki satiri. */
    public static final class Row {
        public final String barcode;
        public final int qty;
        public final LookupState state;
        @Nullable
        public final WarehouseProductInfo product;
        @Nullable
        public final String errorMessage;

        public Row(String barcode, int qty, LookupState state,
                   @Nullable WarehouseProductInfo product, @Nullable String errorMessage) {
            this.barcode = barcode;
            this.qty = qty;
            this.state = state;
            this.product = product;
            this.errorMessage = errorMessage;
        }
    }

    public interface Listener {
        /** Cop kutusu: Iptal Barkod modalini acar. */
        void onDeleteClick(Row row);

        /** Stok bilgisi alinamamis satira dokunuldu: tekrar sorgula. */
        void onRetryClick(Row row);
    }

    private final List<Row> rows = new ArrayList<>();
    @Nullable
    private Listener listener;

    /** Verilmezse (Iptal Barkod modali) cop kutusu gizlenir, satir dokunusu bos kalir. */
    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
        notifyDataSetChanged();
    }

    public void submitList(List<Row> newRows) {
        rows.clear();
        if (newRows != null) {
            rows.addAll(newRows);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_warehouse_transfer_product, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Row row = rows.get(position);
        holder.bind(row);
        if (listener != null) {
            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteClick(row);
                }
            });
            if (row.state == LookupState.ERROR) {
                holder.itemView.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onRetryClick(row);
                    }
                });
            } else {
                holder.itemView.setOnClickListener(null);
                holder.itemView.setClickable(false);
            }
        } else {
            holder.btnDelete.setVisibility(View.GONE);
            holder.btnDelete.setOnClickListener(null);
            holder.itemView.setOnClickListener(null);
            holder.itemView.setClickable(false);
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageButton btnDelete;
        private final TextView tvName;
        private final TextView tvQty;
        private final TextView tvMeta;
        private final TextView tvStock;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            btnDelete = itemView.findViewById(R.id.btnLineDelete);
            tvName = itemView.findViewById(R.id.tvProductName);
            tvQty = itemView.findViewById(R.id.tvProductQty);
            tvMeta = itemView.findViewById(R.id.tvProductMeta);
            tvStock = itemView.findViewById(R.id.tvProductStock);
        }

        void bind(Row row) {
            Context context = itemView.getContext();
            tvQty.setText(context.getString(R.string.warehouse_transfer_row_qty, row.qty));

            int subtitleColor = ContextCompat.getColor(context, R.color.login_subtitle);
            int errorColor = ContextCompat.getColor(context, R.color.login_error);
            int okColor = ContextCompat.getColor(context, R.color.login_primary);

            WarehouseProductInfo product = row.product;
            tvName.setText(product != null && product.found ? product.displayName() : row.barcode);
            tvMeta.setText(buildMeta(context, row.barcode, product));
            tvStock.setVisibility(View.VISIBLE);

            switch (row.state) {
                case LOADING:
                    tvStock.setText(R.string.warehouse_transfer_row_stock_loading);
                    tvStock.setTextColor(subtitleColor);
                    break;
                case REMOVED:
                    tvStock.setText(row.errorMessage != null ? row.errorMessage : "");
                    tvStock.setTextColor(errorColor);
                    break;
                case ERROR:
                    tvStock.setText(context.getString(R.string.warehouse_transfer_row_stock_error,
                            row.errorMessage != null ? row.errorMessage : ""));
                    tvStock.setTextColor(errorColor);
                    break;
                case LOADED:
                default:
                    if (product == null || !product.found) {
                        tvStock.setText(R.string.warehouse_transfer_row_not_found);
                        tvStock.setTextColor(errorColor);
                    } else if (row.qty > product.availableQty) {
                        tvStock.setText(context.getString(R.string.warehouse_transfer_row_stock_short,
                                product.formattedAvailableQty()));
                        tvStock.setTextColor(errorColor);
                    } else {
                        tvStock.setText(context.getString(R.string.warehouse_transfer_row_stock,
                                product.formattedAvailableQty()));
                        tvStock.setTextColor(okColor);
                    }
                    break;
            }
        }

        private static String buildMeta(Context context, String barcode, WarehouseProductInfo product) {
            StringBuilder meta = new StringBuilder(barcode != null ? barcode : "");
            if (product != null && product.found && !TextUtils.isEmpty(product.itemMeasure)) {
                meta.append("  •  ").append(context.getString(
                        R.string.warehouse_transfer_row_size, product.itemMeasure));
            }
            return meta.toString();
        }
    }
}
