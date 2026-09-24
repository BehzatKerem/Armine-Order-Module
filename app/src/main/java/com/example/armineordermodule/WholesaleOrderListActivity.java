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

import java.util.ArrayList;
import java.util.List;

/**
 * Toptan siparis listesi: ustte aktarilmamis/taslak, altta NebimV3 aktarilmis (kilitli).
 * Bkz. docs/TOPTAN_SIPARIS.md.
 */
public class WholesaleOrderListActivity extends AppCompatActivity
        implements WholesaleOrderListAdapter.Listener {

    private static final String TAG = "WholesaleOrderList";

    private TextView tvPendingBanner;
    private TextView tvTransferError;
    private TextView tvEmptyPendingHint;
    private TextView tvEmptySentHint;
    private WholesaleOrderListAdapter pendingAdapter;
    private WholesaleOrderSentListAdapter sentAdapter;
    private boolean sending = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wholesale_order_list);

        ImageButton btnBack = findViewById(R.id.btnBack);
        tvPendingBanner = findViewById(R.id.tvPendingBanner);
        tvTransferError = findViewById(R.id.tvTransferError);
        tvEmptyPendingHint = findViewById(R.id.tvEmptyPendingHint);
        tvEmptySentHint = findViewById(R.id.tvEmptySentHint);
        RecyclerView rvPendingOrders = findViewById(R.id.rvPendingOrders);
        RecyclerView rvSentOrders = findViewById(R.id.rvSentOrders);
        FloatingActionButton fabNewOrder = findViewById(R.id.fabNewOrder);

        pendingAdapter = new WholesaleOrderListAdapter(this);
        rvPendingOrders.setLayoutManager(new LinearLayoutManager(this));
        rvPendingOrders.setAdapter(pendingAdapter);
        rvPendingOrders.setNestedScrollingEnabled(false);

        sentAdapter = new WholesaleOrderSentListAdapter();
        rvSentOrders.setLayoutManager(new LinearLayoutManager(this));
        rvSentOrders.setAdapter(sentAdapter);
        rvSentOrders.setNestedScrollingEnabled(false);

        btnBack.setOnClickListener(v -> finish());
        fabNewOrder.setOnClickListener(v -> startNewOrder());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshOrders();
    }

    private void refreshOrders() {
        List<WholesaleOrder> all = WholesaleOrderStore.getAll(this);
        List<WholesaleOrder> pending = new ArrayList<>();
        List<WholesaleOrder> sent = new ArrayList<>();
        for (WholesaleOrder order : all) {
            if (order.isTransferred()) {
                sent.add(order);
            } else {
                pending.add(order);
            }
        }

        pendingAdapter.submitList(pending);
        sentAdapter.submitList(sent);

        tvEmptyPendingHint.setVisibility(pending.isEmpty() ? View.VISIBLE : View.GONE);
        tvEmptySentHint.setVisibility(sent.isEmpty() ? View.VISIBLE : View.GONE);
        tvPendingBanner.setVisibility(pending.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void startNewOrder() {
        Intent intent = new Intent(this, CustomersActivity.class);
        intent.putExtra(CustomersActivity.EXTRA_SELECT_MODE, true);
        startActivity(intent);
    }

    @Override
    public void onOpenOrder(WholesaleOrder order) {
        if (order == null || order.isTransferred()) {
            Toast.makeText(this, R.string.wholesale_order_sent_locked, Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(this, WholesaleOrderActivity.class);
        intent.putExtra(WholesaleOrderActivity.EXTRA_ORDER_LOCAL_ID, order.localId);
        startActivity(intent);
    }

    @Override
    public void onTransferOrder(WholesaleOrder order) {
        if (order == null || sending) {
            return;
        }
        if (order.isTransferred()) {
            Toast.makeText(this, R.string.wholesale_order_list_already_transferred, Toast.LENGTH_LONG)
                    .show();
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

        String serverUrl = ServerPreferences.getServerUrl(this);
        if (TextUtils.isEmpty(serverUrl)) {
            Toast.makeText(this, R.string.server_url_missing, Toast.LENGTH_LONG).show();
            return;
        }
        String token = AuthPreferences.getToken(this);
        if (TextUtils.isEmpty(token) || !AuthPreferences.hasValidSession(this)) {
            Toast.makeText(this, R.string.warehouses_session_required, Toast.LENGTH_LONG).show();
            return;
        }
        String databaseName = AuthPreferences.getDefaultDatabase(this);
        if (TextUtils.isEmpty(databaseName)) {
            Toast.makeText(this, R.string.warehouses_database_missing, Toast.LENGTH_LONG).show();
            return;
        }

        JSONObject apiJson;
        try {
            apiJson = order.toApiJson(databaseName);
        } catch (JSONException e) {
            Toast.makeText(this, R.string.wholesale_order_json_error, Toast.LENGTH_LONG).show();
            return;
        }
        Log.d(TAG, "Siparis JSON (WholesaleCreate): " + apiJson);

        sending = true;
        clearTransferError();
        Toast.makeText(this, R.string.wholesale_order_sending, Toast.LENGTH_SHORT).show();

        final String localId = order.localId;
        final String orderDescription = order.description;
        WholesaleOrderCreateApiClient.create(
                serverUrl,
                token,
                apiJson,
                new WholesaleOrderCreateApiClient.Callback() {
                    @Override
                    public void onSuccess(WholesaleOrderCreateApiClient.CreateResult result) {
                        sending = false;
                        WholesaleOrder current = WholesaleOrderStore.findById(
                                WholesaleOrderListActivity.this, localId);
                        if (current != null) {
                            current.markTransferred(
                                    result != null ? result.headerId : 0,
                                    result != null ? result.orderGuid : null,
                                    result == null || result.isNebimSend);
                            WholesaleOrderStore.upsert(WholesaleOrderListActivity.this, current);
                        }
                        clearTransferError();
                        Toast.makeText(
                                WholesaleOrderListActivity.this,
                                buildNebimSuccessMessage(result, orderDescription),
                                Toast.LENGTH_LONG).show();
                        refreshOrders();
                    }

                    @Override
                    public void onError(String message) {
                        sending = false;
                        WholesaleOrder current = WholesaleOrderStore.findById(
                                WholesaleOrderListActivity.this, localId);
                        if (current != null && !current.isTransferred()) {
                            current.status = WholesaleOrder.STATUS_PENDING_SEND;
                            WholesaleOrderStore.upsert(WholesaleOrderListActivity.this, current);
                        }
                        showTransferError(!TextUtils.isEmpty(message)
                                ? message
                                : getString(R.string.wholesale_order_send_failed));
                        refreshOrders();
                    }
                });
    }

    private String buildNebimSuccessMessage(
            WholesaleOrderCreateApiClient.CreateResult result, String fallbackDescription) {
        String description = "";
        if (result != null && !TextUtils.isEmpty(result.description)) {
            description = result.description;
        } else if (!TextUtils.isEmpty(fallbackDescription)) {
            description = fallbackDescription;
        }
        if (TextUtils.isEmpty(description)) {
            return getString(R.string.wholesale_order_sent_nebim_v3);
        }
        return getString(R.string.wholesale_order_sent_nebim_v3_with_desc, description);
    }

    private void showTransferError(String message) {
        if (tvTransferError == null) {
            return;
        }
        tvTransferError.setText(message);
        tvTransferError.setVisibility(View.VISIBLE);
    }

    private void clearTransferError() {
        if (tvTransferError != null) {
            tvTransferError.setVisibility(View.GONE);
            tvTransferError.setText("");
        }
    }

    @Override
    public void onDeleteOrder(WholesaleOrder order) {
        if (order == null) {
            return;
        }
        if (order.isTransferred()) {
            Toast.makeText(this, R.string.wholesale_order_sent_locked, Toast.LENGTH_LONG).show();
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
