package com.example.armineordermodule;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Okutulan EPC'lerin urun ozet ekrani.
 * Barkod (EPC ilk 13 hane) ile enrich; Kaydet / Kaydet ve Gonder burada.
 */
public class WholesaleOrderSummaryActivity extends AppCompatActivity {

    public static final String EXTRA_ORDER_LOCAL_ID = "extra_order_local_id";

    private static final String TAG = "WholesaleOrderSummary";

    private WholesaleOrder order;
    private WholesaleOrderSummaryAdapter adapter;

    private TextView tvSummaryCustomer;
    private TextView tvSummaryMeta;
    private TextView tvSummaryCount;
    private TextView tvStockWarning;
    private TextView tvSummaryError;
    private TextView tvSummaryLoading;
    private FloatingActionButton fabSummaryActions;
    private View rowSave;
    private View rowSaveAndSend;

    private boolean fabExpanded = false;
    private boolean sending = false;
    private final List<WholesaleOrderProductInfo> products = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wholesale_order_summary);

        if (!loadOrFail()) {
            finish();
            return;
        }

        bindViews();
        renderHeader();
        loadProductEnrichment();
    }

    private boolean loadOrFail() {
        String id = getIntent() != null
                ? getIntent().getStringExtra(EXTRA_ORDER_LOCAL_ID) : null;
        if (TextUtils.isEmpty(id)) {
            return false;
        }
        order = WholesaleOrderStore.findById(this, id);
        if (order == null || order.lines.isEmpty()) {
            return false;
        }
        if (order.isTransferred()) {
            Toast.makeText(this, R.string.wholesale_order_sent_locked, Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private void bindViews() {
        ImageButton btnBack = findViewById(R.id.btnBack);
        tvSummaryCustomer = findViewById(R.id.tvSummaryCustomer);
        tvSummaryMeta = findViewById(R.id.tvSummaryMeta);
        tvSummaryCount = findViewById(R.id.tvSummaryCount);
        tvStockWarning = findViewById(R.id.tvStockWarning);
        tvSummaryError = findViewById(R.id.tvSummaryError);
        tvSummaryLoading = findViewById(R.id.tvSummaryLoading);
        RecyclerView rvSummaryProducts = findViewById(R.id.rvSummaryProducts);
        fabSummaryActions = findViewById(R.id.fabSummaryActions);
        rowSave = findViewById(R.id.rowSave);
        rowSaveAndSend = findViewById(R.id.rowSaveAndSend);
        FloatingActionButton fabSave = findViewById(R.id.fabSave);
        FloatingActionButton fabSaveAndSend = findViewById(R.id.fabSaveAndSend);

        adapter = new WholesaleOrderSummaryAdapter();
        rvSummaryProducts.setLayoutManager(new LinearLayoutManager(this));
        rvSummaryProducts.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        fabSummaryActions.setOnClickListener(v -> setFabMenuExpanded(!fabExpanded));
        fabSave.setOnClickListener(v -> saveDraft());
        fabSaveAndSend.setOnClickListener(v -> saveAndSend());
    }

    private void renderHeader() {
        String customer = !TextUtils.isEmpty(order.customerDescription)
                ? order.customerDescription : order.customerCode;
        tvSummaryCustomer.setText(customer);

        String warehouse = !TextUtils.isEmpty(order.warehouseDescription)
                ? order.warehouseDescription
                : order.warehouseCode;
        tvSummaryMeta.setText(getString(
                R.string.wholesale_order_summary_meta,
                warehouse,
                order.officeCode,
                order.warehouseCode));

        int epcCount = order.collectAllEpcs().size();
        tvSummaryCount.setText(getString(R.string.wholesale_order_summary_count, epcCount));
    }

    private void loadProductEnrichment() {
        showLoading(true);
        clearError();

        List<String> epcs = order.collectAllEpcs();
        Map<String, Integer> qtyByBarcode =
                WholesaleOrderProductApiClient.groupEpcsByBarcode(epcs);

        String serverUrl = ServerPreferences.getServerUrl(this);
        if (TextUtils.isEmpty(serverUrl)) {
            showLoading(false);
            showError(getString(R.string.server_url_missing));
            return;
        }

        String token = AuthPreferences.getToken(this);
        if (TextUtils.isEmpty(token) || !AuthPreferences.hasValidSession(this)) {
            showLoading(false);
            showError(getString(R.string.warehouses_session_required));
            return;
        }

        String databaseName = AuthPreferences.getDefaultDatabase(this);
        if (TextUtils.isEmpty(databaseName)) {
            showLoading(false);
            showError(getString(R.string.warehouses_database_missing));
            return;
        }

        String userName = AuthPreferences.getUserName(this);

        WholesaleOrderProductApiClient.fetchProducts(
                serverUrl,
                token,
                qtyByBarcode.keySet(),
                qtyByBarcode,
                order.officeCode,
                order.warehouseCode,
                userName,
                databaseName,
                new WholesaleOrderProductApiClient.Callback() {
                    @Override
                    public void onSuccess(List<WholesaleOrderProductInfo> result) {
                        showLoading(false);
                        products.clear();
                        if (result != null) {
                            products.addAll(result);
                        }
                        adapter.submitList(products);
                        updateStockWarning();
                    }

                    @Override
                    public void onError(String message) {
                        showLoading(false);
                        // Enrich basarisiz olsa bile barkod+adet ile lokal satir goster.
                        products.clear();
                        for (Map.Entry<String, Integer> entry : qtyByBarcode.entrySet()) {
                            products.add(new WholesaleOrderProductInfo(
                                    entry.getKey(),
                                    "",
                                    "",
                                    "",
                                    "",
                                    null,
                                    entry.getValue(),
                                    false));
                        }
                        adapter.submitList(products);
                        updateStockWarning();
                        showError(message);
                    }
                });
    }

    private void updateStockWarning() {
        boolean anyIssue = false;
        for (WholesaleOrderProductInfo info : products) {
            if (info.hasStockIssue()) {
                anyIssue = true;
                break;
            }
        }
        tvStockWarning.setVisibility(anyIssue ? View.VISIBLE : View.GONE);
    }

    private void setFabMenuExpanded(boolean expanded) {
        fabExpanded = expanded;
        fabSummaryActions.animate().rotation(expanded ? 45f : 0f).setDuration(150).start();
        animateFabRow(rowSave, expanded);
        animateFabRow(rowSaveAndSend, expanded);
    }

    private void animateFabRow(View row, boolean expanded) {
        if (expanded) {
            row.setVisibility(View.VISIBLE);
            row.setAlpha(0f);
            row.setTranslationY(24f);
            row.animate().alpha(1f).translationY(0f).setDuration(150).start();
        } else {
            row.animate().alpha(0f).translationY(24f).setDuration(120)
                    .withEndAction(() -> row.setVisibility(View.GONE)).start();
        }
    }

    private void saveDraft() {
        setFabMenuExpanded(false);
        order.status = WholesaleOrder.STATUS_DRAFT;
        WholesaleOrderStore.upsert(this, order);
        Toast.makeText(this, R.string.wholesale_order_draft_saved_message, Toast.LENGTH_LONG).show();
        navigateToListAfterSave();
    }

    private void saveAndSend() {
        if (sending) {
            return;
        }
        setFabMenuExpanded(false);

        if (order.isTransferred()) {
            showError(getString(R.string.wholesale_order_list_already_transferred));
            return;
        }

        String serverUrl = ServerPreferences.getServerUrl(this);
        if (TextUtils.isEmpty(serverUrl)) {
            showError(getString(R.string.server_url_missing));
            return;
        }
        String token = AuthPreferences.getToken(this);
        if (TextUtils.isEmpty(token) || !AuthPreferences.hasValidSession(this)) {
            showError(getString(R.string.warehouses_session_required));
            return;
        }
        String databaseName = AuthPreferences.getDefaultDatabase(this);
        if (TextUtils.isEmpty(databaseName)) {
            showError(getString(R.string.warehouses_database_missing));
            return;
        }

        JSONObject apiJson;
        try {
            apiJson = order.toApiJson(databaseName);
        } catch (JSONException e) {
            showError(getString(R.string.wholesale_order_json_error));
            return;
        }
        Log.d(TAG, "Siparis JSON (WholesaleCreate): " + apiJson);

        sending = true;
        clearError();
        tvSummaryLoading.setText(R.string.wholesale_order_sending);
        showLoading(true);
        fabSummaryActions.setEnabled(false);

        WholesaleOrderCreateApiClient.create(
                serverUrl,
                token,
                apiJson,
                new WholesaleOrderCreateApiClient.Callback() {
                    @Override
                    public void onSuccess(WholesaleOrderCreateApiClient.CreateResult result) {
                        sending = false;
                        showLoading(false);
                        tvSummaryLoading.setText(R.string.wholesale_order_summary_loading);
                        order.markTransferred(
                                result != null ? result.headerId : 0,
                                result != null ? result.orderGuid : null,
                                result == null || result.isNebimSend);
                        WholesaleOrderStore.upsert(WholesaleOrderSummaryActivity.this, order);
                        Toast.makeText(
                                WholesaleOrderSummaryActivity.this,
                                buildNebimSuccessMessage(result),
                                Toast.LENGTH_LONG).show();
                        navigateToListAfterSave();
                    }

                    @Override
                    public void onError(String message) {
                        sending = false;
                        showLoading(false);
                        tvSummaryLoading.setText(R.string.wholesale_order_summary_loading);
                        fabSummaryActions.setEnabled(true);
                        if (!order.isTransferred()) {
                            order.status = WholesaleOrder.STATUS_PENDING_SEND;
                            WholesaleOrderStore.upsert(WholesaleOrderSummaryActivity.this, order);
                        }
                        showError(!TextUtils.isEmpty(message)
                                ? message
                                : getString(R.string.wholesale_order_send_failed));
                    }
                });
    }

    /** Description + "Sipariş başarıyla NebimV3 gönderildi" */
    private String buildNebimSuccessMessage(WholesaleOrderCreateApiClient.CreateResult result) {
        String description = "";
        if (result != null && !TextUtils.isEmpty(result.description)) {
            description = result.description;
        } else if (order != null && !TextUtils.isEmpty(order.description)) {
            description = order.description;
        }
        if (TextUtils.isEmpty(description)) {
            return getString(R.string.wholesale_order_sent_nebim_v3);
        }
        return getString(R.string.wholesale_order_sent_nebim_v3_with_desc, description);
    }

    private void navigateToListAfterSave() {
        Intent intent = new Intent(this, WholesaleOrderListActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void showLoading(boolean loading) {
        tvSummaryLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        tvSummaryError.setText(message);
        tvSummaryError.setVisibility(View.VISIBLE);
    }

    private void clearError() {
        tvSummaryError.setVisibility(View.GONE);
        tvSummaryError.setText("");
    }
}
