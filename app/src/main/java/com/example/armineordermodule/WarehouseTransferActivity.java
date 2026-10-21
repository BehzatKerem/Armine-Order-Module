package com.example.armineordermodule;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Collections;
import java.util.List;

import okhttp3.Call;

/**
 * Depo transferi: kaynak depo, toptan siparisteki ayni depo listesinden secilir.
 * Hedef depo, aciklamaya gore SearchWarehouse ile aranir.
 */
public class WarehouseTransferActivity extends AppCompatActivity {

    private static final int MIN_TARGET_SEARCH_LENGTH = 3;

    private WarehouseSelectAdapter sourceAdapter;
    private WarehouseSelectAdapter targetAdapter;
    private TextView tvSourceEmpty;
    private TextView tvTargetStatus;
    private ProgressBar progressSourceWarehouses;
    private ProgressBar progressTargetWarehouses;
    private View sourceSection;
    private RecyclerView rvSourceWarehouses;
    private RecyclerView rvTargetWarehouses;
    private MaterialButton btnStartTransfer;
    private TextInputEditText etTargetSearch;

    private Call targetSearchCall;
    private int targetSearchRequestId;
    private String lastTargetQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_warehouse_transfer);

        ImageButton btnBack = findViewById(R.id.btnBack);
        etTargetSearch = findViewById(R.id.etTargetSearch);
        sourceSection = findViewById(R.id.sourceSection);
        tvSourceEmpty = findViewById(R.id.tvSourceEmpty);
        tvTargetStatus = findViewById(R.id.tvTargetStatus);
        progressSourceWarehouses = findViewById(R.id.progressSourceWarehouses);
        progressTargetWarehouses = findViewById(R.id.progressTargetWarehouses);
        rvSourceWarehouses = findViewById(R.id.rvSourceWarehouses);
        rvTargetWarehouses = findViewById(R.id.rvTargetWarehouses);
        btnStartTransfer = findViewById(R.id.btnStartTransfer);

        sourceAdapter = new WarehouseSelectAdapter(warehouse -> {
            tvSourceEmpty.setVisibility(View.GONE);
            if (sameWarehouse(warehouse, targetAdapter.getSelected())) {
                targetAdapter.setSelected(null);
                showTargetMessage(getString(R.string.warehouse_transfer_same_warehouse), true);
            }
            refreshStartButton();
        });
        rvSourceWarehouses.setLayoutManager(new LinearLayoutManager(this));
        rvSourceWarehouses.setAdapter(sourceAdapter);

        targetAdapter = new WarehouseSelectAdapter(warehouse -> {
            if (sameWarehouse(warehouse, sourceAdapter.getSelected())) {
                targetAdapter.setSelected(null);
                showTargetMessage(getString(R.string.warehouse_transfer_same_warehouse), true);
                refreshStartButton();
                return;
            }
            hideTargetKeyboard();
            showTargetMessage(getString(
                    R.string.warehouse_transfer_target_result_count, targetAdapter.getItemCount()), false);
            refreshStartButton();
        });
        rvTargetWarehouses.setLayoutManager(new LinearLayoutManager(this));
        rvTargetWarehouses.setAdapter(targetAdapter);

        etTargetSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                onTargetSearchChanged(s != null ? s.toString() : "");
            }
        });

        etTargetSearch.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                setTargetSearchChrome(true);
            }
        });
        btnBack.setOnClickListener(v -> finish());
        btnStartTransfer.setOnClickListener(v -> startTransferScan());
        keepTargetResultsAboveKeyboard(findViewById(R.id.transferRoot));

        WarehousesResult cached = WarehousePreferences.get(this);
        if (cached.warehouses.isEmpty()) {
            refreshSourceWarehouses();
        } else {
            applySourceWarehouses(cached);
        }
    }

    @Override
    protected void onDestroy() {
        cancelTargetSearch();
        super.onDestroy();
    }

    private void onTargetSearchChanged(String raw) {
        String query = raw.trim();
        if (query.length() < MIN_TARGET_SEARCH_LENGTH) {
            targetSearchRequestId++;
            cancelTargetSearch();
            lastTargetQuery = "";
            targetAdapter.submitSearchResults(Collections.emptyList());
            showTargetMessage(getString(R.string.warehouse_transfer_target_search_hint), false);
            rvTargetWarehouses.setVisibility(View.GONE);
            refreshStartButton();
            return;
        }
        if (query.equals(lastTargetQuery)) {
            return;
        }
        searchTargetWarehouses(query);
    }

    private void searchTargetWarehouses(String query) {
        String serverUrl = ServerPreferences.getServerUrl(this);
        if (TextUtils.isEmpty(serverUrl)) {
            showTargetMessage(getString(R.string.server_url_missing), true);
            return;
        }

        String token = AuthPreferences.getToken(this);
        String databaseName = AuthPreferences.getDefaultDatabase(this);
        if (TextUtils.isEmpty(token) || !AuthPreferences.hasValidSession(this)) {
            showTargetMessage(getString(R.string.warehouses_session_required), true);
            return;
        }
        if (TextUtils.isEmpty(databaseName)) {
            showTargetMessage(getString(R.string.warehouses_database_missing), true);
            return;
        }

        cancelTargetSearch();
        final int requestId = ++targetSearchRequestId;
        lastTargetQuery = query;
        progressTargetWarehouses.setVisibility(View.VISIBLE);
        showTargetMessage(getString(R.string.warehouse_transfer_target_searching), false);

        targetSearchCall = WarehouseApiClient.searchWarehouses(serverUrl, token, databaseName, query,
                new WarehouseApiClient.WarehouseSearchCallback() {
                    @Override
                    public void onSuccess(List<WarehouseItem> warehouses) {
                        if (requestId != targetSearchRequestId) {
                            return;
                        }
                        progressTargetWarehouses.setVisibility(View.GONE);
                        targetAdapter.submitSearchResults(warehouses);
                        if (warehouses.isEmpty()) {
                            rvTargetWarehouses.setVisibility(View.GONE);
                            showTargetMessage(getString(R.string.warehouse_transfer_target_empty), false);
                        } else {
                            rvTargetWarehouses.setVisibility(View.VISIBLE);
                            rvTargetWarehouses.scrollToPosition(0);
                            showTargetMessage(getString(
                                    R.string.warehouse_transfer_target_result_count, warehouses.size()), false);
                        }
                        refreshStartButton();
                    }

                    @Override
                    public void onError(String message) {
                        if (requestId != targetSearchRequestId) {
                            return;
                        }
                        progressTargetWarehouses.setVisibility(View.GONE);
                        lastTargetQuery = "";
                        targetAdapter.submitSearchResults(Collections.emptyList());
                        rvTargetWarehouses.setVisibility(View.GONE);
                        showTargetMessage(TextUtils.isEmpty(message)
                                ? getString(R.string.wholesale_order_setup_warehouses_load_failed)
                                : message, true);
                        refreshStartButton();
                    }
                });
    }

    /** Klavye acikken kaynak depo listesi sonuclari ortuyor; arama sirasinda onu kapat. */
    private void setTargetSearchChrome(boolean searching) {
        int visibility = searching ? View.GONE : View.VISIBLE;
        if (sourceSection.getVisibility() != visibility) {
            sourceSection.setVisibility(visibility);
        }
        if (btnStartTransfer.getVisibility() != visibility) {
            btnStartTransfer.setVisibility(visibility);
        }
    }

    private void keepTargetResultsAboveKeyboard(View transferRoot) {
        transferRoot.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect visible = new Rect();
            transferRoot.getWindowVisibleDisplayFrame(visible);
            int[] location = new int[2];
            transferRoot.getLocationOnScreen(location);
            int rootBottom = location[1] + transferRoot.getHeight();
            int covered = Math.max(0, rootBottom - visible.bottom);
            int threshold = (int) (80 * transferRoot.getResources().getDisplayMetrics().density);
            boolean imeOpen = covered > threshold || etTargetSearch.hasFocus();
            setTargetSearchChrome(imeOpen);
            int pad = covered > threshold ? covered : 0;
            if (transferRoot.getPaddingBottom() != pad) {
                transferRoot.setPadding(
                        transferRoot.getPaddingLeft(),
                        transferRoot.getPaddingTop(),
                        transferRoot.getPaddingRight(),
                        pad);
            }
        });
    }

    private void hideTargetKeyboard() {
        if (etTargetSearch == null) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etTargetSearch.getWindowToken(), 0);
        }
        etTargetSearch.clearFocus();
    }

    private void cancelTargetSearch() {
        if (targetSearchCall != null) {
            targetSearchCall.cancel();
            targetSearchCall = null;
        }
        progressTargetWarehouses.setVisibility(View.GONE);
    }

    private void showTargetMessage(String message, boolean error) {
        tvTargetStatus.setText(message);
        tvTargetStatus.setTextColor(ContextCompat.getColor(
                this, error ? R.color.login_error : R.color.login_subtitle));
    }

    private void refreshSourceWarehouses() {
        String serverUrl = ServerPreferences.getServerUrl(this);
        if (TextUtils.isEmpty(serverUrl)) {
            showSourceLoadFailed(getString(R.string.server_url_missing));
            return;
        }

        String token = AuthPreferences.getToken(this);
        String databaseName = AuthPreferences.getDefaultDatabase(this);
        if (TextUtils.isEmpty(token) || !AuthPreferences.hasValidSession(this)) {
            showSourceLoadFailed(getString(R.string.warehouses_session_required));
            return;
        }
        if (TextUtils.isEmpty(databaseName)) {
            showSourceLoadFailed(getString(R.string.warehouses_database_missing));
            return;
        }

        setSourceLoading(true);
        WarehouseApiClient.getWarehouses(serverUrl, token, databaseName,
                new WarehouseApiClient.WarehousesCallback() {
                    @Override
                    public void onSuccess(WarehousesResult result) {
                        setSourceLoading(false);
                        WarehousePreferences.save(WarehouseTransferActivity.this, result);
                        if (result.warehouses.isEmpty()) {
                            showSourceLoadFailed(getString(R.string.wholesale_order_no_warehouses));
                            return;
                        }
                        applySourceWarehouses(result);
                    }

                    @Override
                    public void onError(String message) {
                        setSourceLoading(false);
                        showSourceLoadFailed(TextUtils.isEmpty(message)
                                ? getString(R.string.wholesale_order_setup_warehouses_load_failed)
                                : message);
                    }
                });
    }

    private void applySourceWarehouses(WarehousesResult result) {
        sourceAdapter.submitList(result.warehouses);
        tvSourceEmpty.setVisibility(View.GONE);
        rvSourceWarehouses.setVisibility(View.VISIBLE);

        WarehouseItem preselect = result.defaultWarehouse;
        if (preselect == null) {
            for (WarehouseItem item : result.warehouses) {
                if (item.isDefault) {
                    preselect = item;
                    break;
                }
            }
        }
        if (preselect == null && !result.warehouses.isEmpty()) {
            preselect = WarehouseItem.sortedForDisplay(result.warehouses).get(0);
        }
        if (preselect != null) {
            sourceAdapter.setSelected(preselect);
        }
        refreshStartButton();
    }

    private void startTransferScan() {
        WarehouseItem source = sourceAdapter.getSelected();
        WarehouseItem target = targetAdapter.getSelected();
        if (source == null || TextUtils.isEmpty(source.warehouseCode)) {
            Toast.makeText(this, R.string.warehouse_transfer_source_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (target == null || TextUtils.isEmpty(target.warehouseCode)) {
            refreshStartButton();
            return;
        }
        if (sameWarehouse(source, target)) {
            targetAdapter.setSelected(null);
            showTargetMessage(getString(R.string.warehouse_transfer_same_warehouse), true);
            refreshStartButton();
            return;
        }

        Intent intent = new Intent(this, WarehouseTransferScanActivity.class);
        intent.putExtra(WarehouseTransferScanActivity.EXTRA_SOURCE_OFFICE, source.officeCode);
        intent.putExtra(WarehouseTransferScanActivity.EXTRA_SOURCE_WAREHOUSE, source.warehouseCode);
        intent.putExtra(WarehouseTransferScanActivity.EXTRA_SOURCE_DESCRIPTION, sourceLabel(source));
        intent.putExtra(WarehouseTransferScanActivity.EXTRA_TARGET_OFFICE, target.officeCode);
        intent.putExtra(WarehouseTransferScanActivity.EXTRA_TARGET_WAREHOUSE, target.warehouseCode);
        intent.putExtra(WarehouseTransferScanActivity.EXTRA_TARGET_DESCRIPTION, sourceLabel(target));
        startActivity(intent);
    }

    private static String sourceLabel(WarehouseItem item) {
        if (!TextUtils.isEmpty(item.warehouseDescription)) {
            return item.warehouseDescription;
        }
        String code = item.officeWarehouseCode();
        return TextUtils.isEmpty(code) ? item.warehouseCode : code;
    }

    private void refreshStartButton() {
        WarehouseItem source = sourceAdapter.getSelected();
        WarehouseItem target = targetAdapter.getSelected();
        boolean ready = target != null
                && !TextUtils.isEmpty(target.warehouseCode)
                && !sameWarehouse(source, target);
        btnStartTransfer.setEnabled(ready);
        btnStartTransfer.setAlpha(ready ? 1f : 0.4f);
    }

    private void setSourceLoading(boolean loading) {
        progressSourceWarehouses.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            tvSourceEmpty.setVisibility(View.VISIBLE);
            tvSourceEmpty.setTextColor(ContextCompat.getColor(this, R.color.login_subtitle));
            tvSourceEmpty.setText(R.string.wholesale_order_setup_warehouses_loading);
            rvSourceWarehouses.setVisibility(View.GONE);
        }
    }

    private void showSourceLoadFailed(String message) {
        tvSourceEmpty.setVisibility(View.VISIBLE);
        tvSourceEmpty.setTextColor(ContextCompat.getColor(this, R.color.login_error));
        tvSourceEmpty.setText(message);
        rvSourceWarehouses.setVisibility(View.GONE);
    }

    private static boolean sameWarehouse(WarehouseItem a, WarehouseItem b) {
        if (a == null || b == null) {
            return false;
        }
        return a.officeCode.equals(b.officeCode) && a.warehouseCode.equals(b.warehouseCode);
    }
}
