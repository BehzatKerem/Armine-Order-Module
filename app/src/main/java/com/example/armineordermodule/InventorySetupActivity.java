package com.example.armineordermodule;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
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

import java.text.SimpleDateFormat;
import java.util.Locale;

/** Yeni sayim: isim + depo secimi. */
public class InventorySetupActivity extends AppCompatActivity {

    private WarehouseSelectAdapter warehouseAdapter;
    private TextInputEditText etCountName;
    private TextView tvSetupError;
    private TextView tvSetupWarehouseEmpty;
    private TextView tvSetupWarehouseHint;
    private ProgressBar progressWarehouses;
    private RecyclerView rvWarehouses;
    private MaterialButton btnContinue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory_setup);

        ImageButton btnBack = findViewById(R.id.btnBack);
        tvSetupWarehouseHint = findViewById(R.id.tvSetupWarehouseHint);
        tvSetupWarehouseEmpty = findViewById(R.id.tvSetupWarehouseEmpty);
        progressWarehouses = findViewById(R.id.progressSetupWarehouses);
        rvWarehouses = findViewById(R.id.rvSetupWarehouses);
        etCountName = findViewById(R.id.etCountName);
        tvSetupError = findViewById(R.id.tvSetupError);
        btnContinue = findViewById(R.id.btnSetupContinue);

        warehouseAdapter = new WarehouseSelectAdapter(warehouse -> clearError());
        rvWarehouses.setLayoutManager(new LinearLayoutManager(this));
        rvWarehouses.setAdapter(warehouseAdapter);

        btnBack.setOnClickListener(v -> finish());
        btnContinue.setOnClickListener(v -> continueToLocations());

        WarehousesResult cached = WarehousePreferences.get(this);
        if (cached.warehouses.isEmpty()) {
            refreshWarehousesFromApi();
        } else {
            applyWarehousesResult(cached);
        }
    }

    private void refreshWarehousesFromApi() {
        clearError();

        String serverUrl = ServerPreferences.getServerUrl(this);
        if (TextUtils.isEmpty(serverUrl)) {
            showWarehouseLoadFailed(getString(R.string.server_url_missing));
            return;
        }

        String token = AuthPreferences.getToken(this);
        String databaseName = AuthPreferences.getDefaultDatabase(this);
        if (TextUtils.isEmpty(token) || !AuthPreferences.hasValidSession(this)) {
            showWarehouseLoadFailed(getString(R.string.warehouses_session_required));
            return;
        }
        if (TextUtils.isEmpty(databaseName)) {
            showWarehouseLoadFailed(getString(R.string.warehouses_database_missing));
            return;
        }

        setWarehousesLoading(true);
        WarehouseApiClient.getWarehouses(serverUrl, token, databaseName,
                new WarehouseApiClient.WarehousesCallback() {
                    @Override
                    public void onSuccess(WarehousesResult result) {
                        setWarehousesLoading(false);
                        WarehousePreferences.save(InventorySetupActivity.this, result);
                        if (result.warehouses.isEmpty()) {
                            showWarehouseLoadFailed(getString(R.string.inventory_no_warehouses));
                            return;
                        }
                        applyWarehousesResult(result);
                    }

                    @Override
                    public void onError(String message) {
                        setWarehousesLoading(false);
                        showWarehouseLoadFailed(TextUtils.isEmpty(message)
                                ? getString(R.string.inventory_setup_warehouses_load_failed)
                                : message);
                    }
                });
    }

    private void applyWarehousesResult(WarehousesResult result) {
        warehouseAdapter.submitList(result.warehouses);
        tvSetupWarehouseEmpty.setVisibility(View.GONE);
        rvWarehouses.setVisibility(View.VISIBLE);
        tvSetupWarehouseHint.setText(R.string.inventory_setup_warehouse_hint);

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
            warehouseAdapter.setSelected(preselect);
        }
        clearError();
    }

    private void setWarehousesLoading(boolean loading) {
        progressWarehouses.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnContinue.setEnabled(!loading);
        if (loading) {
            tvSetupWarehouseEmpty.setVisibility(View.VISIBLE);
            tvSetupWarehouseEmpty.setTextColor(ContextCompat.getColor(this, R.color.login_subtitle));
            tvSetupWarehouseEmpty.setText(R.string.inventory_setup_warehouses_loading);
            rvWarehouses.setVisibility(View.GONE);
        }
    }

    private void showWarehouseLoadFailed(String message) {
        tvSetupWarehouseEmpty.setVisibility(View.VISIBLE);
        tvSetupWarehouseEmpty.setTextColor(ContextCompat.getColor(this, R.color.login_error));
        tvSetupWarehouseEmpty.setText(R.string.inventory_no_warehouses);
        rvWarehouses.setVisibility(View.GONE);
        showError(message);
    }

    private void continueToLocations() {
        clearError();

        String name = etCountName.getText() != null
                ? etCountName.getText().toString().trim() : "";
        if (TextUtils.isEmpty(name)) {
            showError(getString(R.string.inventory_name_required));
            etCountName.requestFocus();
            return;
        }

        WarehouseItem selected = warehouseAdapter.getSelected();
        if (selected == null || TextUtils.isEmpty(selected.warehouseCode)
                || TextUtils.isEmpty(selected.officeCode)) {
            showError(getString(R.string.inventory_warehouse_required));
            return;
        }

        InventoryCount count = InventoryCount.createNew();
        count.name = name;
        count.companyCode = AuthPreferences.getCompanyCode(this);
        count.countDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date());
        count.officeCode = selected.officeCode;
        count.warehouseCode = selected.warehouseCode;
        count.warehouseDescription = selected.warehouseDescription != null
                ? selected.warehouseDescription : "";

        InventoryStore.upsert(this, count);

        Intent intent = new Intent(this, InventoryLocationsActivity.class);
        intent.putExtra(InventoryLocationsActivity.EXTRA_COUNT_LOCAL_ID, count.localId);
        startActivity(intent);
        finish();
    }

    private void showError(String message) {
        tvSetupError.setText(message);
        tvSetupError.setVisibility(View.VISIBLE);
    }

    private void clearError() {
        tvSetupError.setVisibility(View.GONE);
        tvSetupError.setText("");
    }
}
