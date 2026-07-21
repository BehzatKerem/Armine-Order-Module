package com.example.armineordermodule;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

public class WarehousesActivity extends AppCompatActivity {

    private MaterialButton btnUpdateDefaultWarehouses;
    private TextView tvWarehousesStatus;
    private TextView tvWarehousesError;
    private WarehouseListAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_warehouses);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnUpdateDefaultWarehouses = findViewById(R.id.btnUpdateDefaultWarehouses);
        tvWarehousesStatus = findViewById(R.id.tvWarehousesStatus);
        tvWarehousesError = findViewById(R.id.tvWarehousesError);
        RecyclerView rvWarehouses = findViewById(R.id.rvWarehouses);

        adapter = new WarehouseListAdapter();
        rvWarehouses.setLayoutManager(new LinearLayoutManager(this));
        rvWarehouses.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        btnUpdateDefaultWarehouses.setOnClickListener(v -> refreshWarehouses());

        showCachedWarehouses();
    }

    private void showCachedWarehouses() {
        WarehousesResult cached = WarehousePreferences.get(this);
        adapter.submitList(cached.warehouses);
        if (cached.warehouses.isEmpty()) {
            tvWarehousesStatus.setText(R.string.warehouses_empty_hint);
        } else {
            tvWarehousesStatus.setText(getString(
                    R.string.warehouses_cached_count, cached.warehouses.size()));
        }
    }

    private void refreshWarehouses() {
        clearError();

        String serverUrl = ServerPreferences.getServerUrl(this);
        if (TextUtils.isEmpty(serverUrl)) {
            showError(getString(R.string.server_url_missing));
            return;
        }

        String token = AuthPreferences.getToken(this);
        String databaseName = AuthPreferences.getDefaultDatabase(this);

        if (TextUtils.isEmpty(token) || !AuthPreferences.hasValidSession(this)) {
            showError(getString(R.string.warehouses_session_required));
            return;
        }
        if (TextUtils.isEmpty(databaseName)) {
            showError(getString(R.string.warehouses_database_missing));
            return;
        }

        setLoading(true);
        WarehouseApiClient.getWarehouses(serverUrl, token, databaseName,
                new WarehouseApiClient.WarehousesCallback() {
                    @Override
                    public void onSuccess(WarehousesResult result) {
                        setLoading(false);
                        WarehousePreferences.save(WarehousesActivity.this, result);
                        adapter.submitList(result.warehouses);
                        tvWarehousesStatus.setText(getString(
                                R.string.warehouses_updated_count, result.warehouses.size()));
                        Toast.makeText(WarehousesActivity.this,
                                R.string.warehouses_update_success, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        showError(message);
                    }
                });
    }

    private void setLoading(boolean loading) {
        btnUpdateDefaultWarehouses.setEnabled(!loading);
        btnUpdateDefaultWarehouses.setText(loading
                ? R.string.warehouses_updating
                : R.string.warehouses_update_defaults);
    }

    private void showError(String message) {
        tvWarehousesError.setText(message);
        tvWarehousesError.setVisibility(View.VISIBLE);
    }

    private void clearError() {
        tvWarehousesError.setVisibility(View.GONE);
        tvWarehousesError.setText("");
    }
}
