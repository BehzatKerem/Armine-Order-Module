package com.example.armineordermodule;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Toptan siparis (fis) taslaklarinin listesi. Yeni siparis olusturmak icin musteri
 * secim ekranina (CustomersActivity, secim modunda) yonlendirir. Bkz. docs/TOPTAN_SIPARIS.md.
 */
public class WholesaleOrderListActivity extends AppCompatActivity
        implements WholesaleOrderListAdapter.Listener {

    private static final String TAG = "WholesaleOrderList";

    private TextView tvPendingBanner;
    private TextView tvEmptyHint;
    private WholesaleOrderListAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wholesale_order_list);

        ImageButton btnBack = findViewById(R.id.btnBack);
        tvPendingBanner = findViewById(R.id.tvPendingBanner);
        tvEmptyHint = findViewById(R.id.tvEmptyHint);
        RecyclerView rvOrders = findViewById(R.id.rvOrders);
        FloatingActionButton fabNewOrder = findViewById(R.id.fabNewOrder);

        adapter = new WholesaleOrderListAdapter(this);
        rvOrders.setLayoutManager(new LinearLayoutManager(this));
        rvOrders.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        fabNewOrder.setOnClickListener(v -> startNewOrder());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshOrders();
    }

    private void refreshOrders() {
        List<WholesaleOrder> orders = WholesaleOrderStore.getAll(this);
        adapter.submitList(orders);
        tvEmptyHint.setVisibility(orders.isEmpty() ? View.VISIBLE : View.GONE);
        tvPendingBanner.setVisibility(orders.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void startNewOrder() {
        Intent intent = new Intent(this, CustomersActivity.class);
        intent.putExtra(CustomersActivity.EXTRA_SELECT_MODE, true);
        startActivity(intent);
    }

    @Override
    public void onOpenOrder(WholesaleOrder order) {
        Intent intent = new Intent(this, WholesaleOrderActivity.class);
        intent.putExtra(WholesaleOrderActivity.EXTRA_ORDER_LOCAL_ID, order.localId);
        startActivity(intent);
    }

    @Override
    public void onTransferOrder(WholesaleOrder order) {
        if (order == null) {
            return;
        }
        if (TextUtils.isEmpty(order.warehouseCode) || TextUtils.isEmpty(order.officeCode)) {
            Toast.makeText(this, R.string.wholesale_order_list_transfer_warehouse, Toast.LENGTH_LONG).show();
            return;
        }
        if (order.lines.isEmpty()) {
            Toast.makeText(this, R.string.wholesale_order_list_transfer_empty, Toast.LENGTH_LONG).show();
            return;
        }

        JSONObject apiJson;
        try {
            apiJson = order.toApiJson();
        } catch (JSONException e) {
            Toast.makeText(this, R.string.wholesale_order_json_error, Toast.LENGTH_LONG).show();
            return;
        }
        Log.d(TAG, "Siparis JSON (aktarim bekliyor): " + apiJson);

        // Endpoint henuz yok; gonderime hazir olarak isaretlenir.
        order.status = WholesaleOrder.STATUS_PENDING_SEND;
        WholesaleOrderStore.upsert(this, order);
        Toast.makeText(this, R.string.wholesale_order_pending_send_saved_message, Toast.LENGTH_LONG).show();
        refreshOrders();
    }

    @Override
    public void onDeleteOrder(WholesaleOrder order) {
        if (order == null) {
            return;
        }
        String customer = !TextUtils.isEmpty(order.customerDescription)
                ? order.customerDescription
                : order.customerCode;
        new AlertDialog.Builder(this)
                .setTitle(R.string.wholesale_order_list_delete_title)
                .setMessage(getString(R.string.wholesale_order_list_delete_message)
                        + (TextUtils.isEmpty(customer) ? "" : "\n\n" + customer))
                .setPositiveButton(R.string.wholesale_order_list_delete, (dialog, which) -> {
                    WholesaleOrderStore.delete(this, order.localId);
                    Toast.makeText(this, R.string.wholesale_order_list_deleted, Toast.LENGTH_SHORT).show();
                    refreshOrders();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
