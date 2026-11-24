package com.example.armineordermodule;

import android.content.Intent;
import android.content.res.ColorStateList;
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
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Siparis akisinin 2. adimi: musteri secildikten sonra depo + aciklama.
 * Lokal depo yoksa Depolarim ekranindaki "Varsayilan Depolari Guncelle" ile ayni
 * API cagirisini otomatik yapar. Bkz. docs/TOPTAN_SIPARIS.md.
 */
public class WholesaleOrderSetupActivity extends AppCompatActivity {

    public static final String EXTRA_CUSTOMER = "extra_customer";

    private CustomerItem customer;
    private WarehouseSelectAdapter warehouseAdapter;
    private TextInputEditText etDescription;
    private TextView tvSetupError;
    private TextView tvSetupWarehouseEmpty;
    private TextView tvSetupWarehouseHint;
    private ProgressBar progressWarehouses;
    private RecyclerView rvWarehouses;
    private MaterialButton btnContinue;
    private MaterialCardView cardStockControl;
    private View dotStockControl;
    private TextView tvStockControlState;
    private SwitchMaterial switchStockControl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wholesale_order_setup);

        Object customerExtra = getIntent() != null ? getIntent().getSerializableExtra(EXTRA_CUSTOMER) : null;
        if (!(customerExtra instanceof CustomerItem)) {
            finish();
            return;
        }
        customer = (CustomerItem) customerExtra;

        ImageButton btnBack = findViewById(R.id.btnBack);
        TextView tvSetupCustomer = findViewById(R.id.tvSetupCustomer);
        TextView tvSetupCustomerCode = findViewById(R.id.tvSetupCustomerCode);
        tvSetupWarehouseHint = findViewById(R.id.tvSetupWarehouseHint);
        tvSetupWarehouseEmpty = findViewById(R.id.tvSetupWarehouseEmpty);
        progressWarehouses = findViewById(R.id.progressSetupWarehouses);
        rvWarehouses = findViewById(R.id.rvSetupWarehouses);
        etDescription = findViewById(R.id.etSetupDescription);
        tvSetupError = findViewById(R.id.tvSetupError);
        btnContinue = findViewById(R.id.btnSetupContinue);

        tvSetupCustomer.setText(customer.displayName());
        if (!TextUtils.isEmpty(customer.currAccCode)) {
            tvSetupCustomerCode.setVisibility(View.VISIBLE);
            tvSetupCustomerCode.setText(customer.currAccCode);
        } else {
            tvSetupCustomerCode.setVisibility(View.GONE);
        }

        String userName = AuthPreferences.getUserName(this);
        etDescription.setText((TextUtils.isEmpty(userName) ? "" : userName)
                + " - " + customer.displayName());

        warehouseAdapter = new WarehouseSelectAdapter(warehouse -> clearError());
        rvWarehouses.setLayoutManager(new LinearLayoutManager(this));
        rvWarehouses.setAdapter(warehouseAdapter);

        cardStockControl = findViewById(R.id.cardStockControl);
        dotStockControl = findViewById(R.id.dotStockControl);
        tvStockControlState = findViewById(R.id.tvStockControlState);
        switchStockControl = findViewById(R.id.switchStockControl);
        switchStockControl.setChecked(WholesaleOrderPreferences.isStockControlEnabled(this));
        switchStockControl.setOnCheckedChangeListener((button, checked) -> {
            WholesaleOrderPreferences.setStockControlEnabled(this, checked);
            renderStockControl();
        });
        // Kartin tamamina dokunmak da anahtari cevirir (el terminalinde kucuk hedef olmasin).
        cardStockControl.setOnClickListener(v -> switchStockControl.toggle());
        renderStockControl();

        btnBack.setOnClickListener(v -> finish());
        btnContinue.setOnClickListener(v -> continueToScan());

        WarehousesResult cached = WarehousePreferences.get(this);
        if (cached.warehouses.isEmpty()) {
            refreshWarehousesFromApi();
        } else {
            applyWarehousesResult(cached);
        }
    }

    private void refreshWarehousesFromApi() {
        clearError();
        setWarehousesLoading(true);
        WarehouseSync.refresh(this, new WarehouseSync.Callback() {
                    @Override
                    public void onSuccess(WarehousesResult result) {
                        setWarehousesLoading(false);
                        if (result.warehouses.isEmpty()) {
                            showWarehouseLoadFailed(getString(R.string.wholesale_order_no_warehouses));
                            return;
                        }
                        applyWarehousesResult(result);
                    }

                    @Override
                    public void onError(String message) {
                        setWarehousesLoading(false);
                        showWarehouseLoadFailed(TextUtils.isEmpty(message)
                                ? getString(R.string.wholesale_order_setup_warehouses_load_failed)
                                : message);
                    }
                });
    }

    /** Stok kontrolu kartini renklendirir: acik = yesil, kapali = kirmizi. */
    private void renderStockControl() {
        boolean on = switchStockControl.isChecked();
        int accent = ContextCompat.getColor(this, on ? R.color.stock_control_on : R.color.stock_control_off);
        int background = ContextCompat.getColor(this,
                on ? R.color.stock_control_on_bg : R.color.stock_control_off_bg);

        cardStockControl.setStrokeColor(accent);
        cardStockControl.setCardBackgroundColor(background);
        dotStockControl.setBackgroundResource(on ? R.drawable.bg_dot_green : R.drawable.bg_dot_red);
        tvStockControlState.setText(on
                ? R.string.wholesale_order_stock_control_on
                : R.string.wholesale_order_stock_control_off);
        tvStockControlState.setTextColor(accent);

        switchStockControl.setThumbTintList(ColorStateList.valueOf(accent));
        switchStockControl.setTrackTintList(ColorStateList.valueOf(
                androidx.core.graphics.ColorUtils.setAlphaComponent(accent, 0x66)));
    }

    private void applyWarehousesResult(WarehousesResult result) {
        warehouseAdapter.submitList(result.warehouses);
        tvSetupWarehouseEmpty.setVisibility(View.GONE);
        rvWarehouses.setVisibility(View.VISIBLE);
        tvSetupWarehouseHint.setText(R.string.wholesale_order_setup_warehouse_hint);

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
            tvSetupWarehouseEmpty.setText(R.string.wholesale_order_setup_warehouses_loading);
            rvWarehouses.setVisibility(View.GONE);
        }
    }

    private void showWarehouseLoadFailed(String message) {
        tvSetupWarehouseEmpty.setVisibility(View.VISIBLE);
        tvSetupWarehouseEmpty.setTextColor(ContextCompat.getColor(this, R.color.login_error));
        tvSetupWarehouseEmpty.setText(R.string.wholesale_order_no_warehouses);
        rvWarehouses.setVisibility(View.GONE);
        showError(message);
    }

    private void continueToScan() {
        clearError();
        WarehouseItem selected = warehouseAdapter.getSelected();
        if (selected == null || TextUtils.isEmpty(selected.warehouseCode)
                || TextUtils.isEmpty(selected.officeCode)) {
            showError(getString(R.string.wholesale_order_warehouse_required));
            return;
        }

        String description = etDescription.getText() != null
                ? etDescription.getText().toString().trim() : "";

        Intent intent = new Intent(this, WholesaleOrderActivity.class);
        intent.putExtra(WholesaleOrderActivity.EXTRA_CUSTOMER, customer);
        intent.putExtra(WholesaleOrderActivity.EXTRA_OFFICE_CODE, selected.officeCode);
        intent.putExtra(WholesaleOrderActivity.EXTRA_WAREHOUSE_CODE, selected.warehouseCode);
        intent.putExtra(WholesaleOrderActivity.EXTRA_WAREHOUSE_DESCRIPTION, selected.warehouseDescription);
        intent.putExtra(WholesaleOrderActivity.EXTRA_DESCRIPTION, description);
        intent.putExtra(WholesaleOrderActivity.EXTRA_STOCK_CONTROL, switchStockControl.isChecked());
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
