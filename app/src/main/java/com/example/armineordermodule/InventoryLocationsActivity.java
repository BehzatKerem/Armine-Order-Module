package com.example.armineordermodule;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Sayim lokasyon detaylari.
 * Ilk seferde lokasyon adedi ile toplu olusturma; sonrasinda "Lokasyon Ekle"
 * ile tek tek ekleme. Lokasyona tiklaninca urun okutma acilir.
 */
public class InventoryLocationsActivity extends AppCompatActivity {

    public static final String EXTRA_COUNT_LOCAL_ID = "extra_count_local_id";

    private static final String TAG = "InventoryLocations";
    private static final int MAX_LOCATIONS = 99;

    private InventoryCount count;
    private TextView tvCountTitle;
    private TextView tvWarehouseInfo;
    private TextView tvLocationsSection;
    private TextView tvLocationsError;
    private View panelInitialCount;
    private TextInputEditText etLocationCount;
    private MaterialButton btnAddLocation;
    private View panelAddLocation;
    private TextInputEditText etNewLocationName;
    private LinearLayout containerLocations;
    private FloatingActionButton fabCountActions;
    private View rowSave;
    private View rowSaveAndSend;

    private boolean fabExpanded = false;
    private final List<LocationRow> locationRows = new ArrayList<>();

    private static final class LocationRow {
        final String locationId;
        final TextInputEditText etName;
        final TextView tvQty;

        LocationRow(String locationId, TextInputEditText etName, TextView tvQty) {
            this.locationId = locationId;
            this.etName = etName;
            this.tvQty = tvQty;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory_locations);

        String countId = getIntent() != null
                ? getIntent().getStringExtra(EXTRA_COUNT_LOCAL_ID) : null;
        count = InventoryStore.findById(this, countId);
        if (count == null) {
            finish();
            return;
        }

        ImageButton btnBack = findViewById(R.id.btnBack);
        tvCountTitle = findViewById(R.id.tvCountTitle);
        tvWarehouseInfo = findViewById(R.id.tvWarehouseInfo);
        tvLocationsSection = findViewById(R.id.tvLocationsSection);
        tvLocationsError = findViewById(R.id.tvLocationsError);
        panelInitialCount = findViewById(R.id.panelInitialCount);
        etLocationCount = findViewById(R.id.etLocationCount);
        MaterialButton btnCreateLocations = findViewById(R.id.btnCreateLocations);
        btnAddLocation = findViewById(R.id.btnAddLocation);
        panelAddLocation = findViewById(R.id.panelAddLocation);
        etNewLocationName = findViewById(R.id.etNewLocationName);
        MaterialButton btnConfirmAddLocation = findViewById(R.id.btnConfirmAddLocation);
        containerLocations = findViewById(R.id.containerLocations);
        fabCountActions = findViewById(R.id.fabCountActions);
        rowSave = findViewById(R.id.rowSave);
        rowSaveAndSend = findViewById(R.id.rowSaveAndSend);
        FloatingActionButton fabSave = findViewById(R.id.fabSave);
        FloatingActionButton fabSaveAndSend = findViewById(R.id.fabSaveAndSend);

        tvCountTitle.setText(TextUtils.isEmpty(count.name)
                ? getString(R.string.inventory_locations_title) : count.name);
        String warehouse = !TextUtils.isEmpty(count.warehouseDescription)
                ? count.warehouseDescription : count.warehouseCode;
        tvWarehouseInfo.setText(getString(R.string.inventory_locations_warehouse, warehouse));

        btnBack.setOnClickListener(v -> {
            persistLocationStructure();
            finish();
        });
        fabCountActions.setOnClickListener(v -> setFabMenuExpanded(!fabExpanded));
        fabSave.setOnClickListener(v -> saveDraft());
        fabSaveAndSend.setOnClickListener(v -> saveAndSend());
        btnCreateLocations.setOnClickListener(v -> createInitialLocations());
        btnAddLocation.setOnClickListener(v -> showAddLocationPanel());
        btnConfirmAddLocation.setOnClickListener(v -> confirmAddLocation());

        if (count.locations.isEmpty()) {
            showInitialCountMode();
        } else {
            renderAllLocationRows();
            showAddMode();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        count = InventoryStore.findById(this, count.localId);
        if (count == null) {
            finish();
            return;
        }
        // Okutmadan donunce satirlar/miktarlar guncel kalsin.
        if (!count.locations.isEmpty()) {
            syncNamesFromUiIntoCount();
            renderAllLocationRows();
            showAddMode();
        }
        refreshLocationQtyLabels();
    }

    private void showInitialCountMode() {
        panelInitialCount.setVisibility(View.VISIBLE);
        btnAddLocation.setVisibility(View.GONE);
        panelAddLocation.setVisibility(View.GONE);
        tvLocationsSection.setVisibility(View.GONE);
    }

    private void showAddMode() {
        panelInitialCount.setVisibility(View.GONE);
        btnAddLocation.setVisibility(View.VISIBLE);
        tvLocationsSection.setVisibility(count.locations.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showAddLocationPanel() {
        clearError();
        if (count.locations.size() >= MAX_LOCATIONS) {
            showError(getString(R.string.inventory_location_max_reached, MAX_LOCATIONS));
            return;
        }
        panelAddLocation.setVisibility(View.VISIBLE);
        String defaultName = getString(
                R.string.inventory_location_default_name, count.locations.size() + 1);
        etNewLocationName.setText(defaultName);
        etNewLocationName.requestFocus();
        etNewLocationName.selectAll();
    }

    private void createInitialLocations() {
        clearError();
        String raw = etLocationCount.getText() != null
                ? etLocationCount.getText().toString().trim() : "";
        if (TextUtils.isEmpty(raw)) {
            showError(getString(R.string.inventory_location_count_required));
            etLocationCount.requestFocus();
            return;
        }

        int desired;
        try {
            desired = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            showError(getString(R.string.inventory_location_count_required));
            return;
        }
        if (desired <= 0) {
            showError(getString(R.string.inventory_location_count_required));
            return;
        }
        if (desired > MAX_LOCATIONS) {
            desired = MAX_LOCATIONS;
            etLocationCount.setText(String.valueOf(MAX_LOCATIONS));
        }

        count.locations.clear();
        for (int i = 0; i < desired; i++) {
            count.locations.add(InventoryLocation.createNew(
                    getString(R.string.inventory_location_default_name, i + 1)));
        }

        InventoryStore.upsert(this, count);
        renderAllLocationRows();
        showAddMode();
        panelAddLocation.setVisibility(View.GONE);
    }

    private void confirmAddLocation() {
        clearError();
        if (count.locations.size() >= MAX_LOCATIONS) {
            showError(getString(R.string.inventory_location_max_reached, MAX_LOCATIONS));
            return;
        }

        syncNamesFromUiIntoCount();

        String name = etNewLocationName.getText() != null
                ? etNewLocationName.getText().toString().trim() : "";
        if (TextUtils.isEmpty(name)) {
            name = getString(R.string.inventory_location_default_name, count.locations.size() + 1);
        }

        count.locations.add(InventoryLocation.createNew(name));
        InventoryStore.upsert(this, count);

        renderAllLocationRows();
        showAddMode();
        panelAddLocation.setVisibility(View.GONE);
        etNewLocationName.setText("");
    }

    private void renderAllLocationRows() {
        containerLocations.removeAllViews();
        locationRows.clear();

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < count.locations.size(); i++) {
            InventoryLocation location = count.locations.get(i);
            View row = inflater.inflate(R.layout.item_inventory_location_row, containerLocations, false);
            TextInputEditText etName = row.findViewById(R.id.etLocationName);
            TextView tvQty = row.findViewById(R.id.tvLocationQty);
            View btnOpen = row.findViewById(R.id.btnOpenLocation);

            String displayName = !TextUtils.isEmpty(location.name)
                    ? location.name
                    : getString(R.string.inventory_location_default_name, i + 1);
            etName.setText(displayName);
            etName.setHint(getString(R.string.inventory_location_default_name, i + 1));
            tvQty.setText(getString(R.string.inventory_location_qty, location.totalQty()));

            final String locationId = location.localId;
            View.OnClickListener openScan = v -> openLocationScan(locationId);
            btnOpen.setOnClickListener(openScan);
            tvQty.setOnClickListener(openScan);

            containerLocations.addView(row);
            locationRows.add(new LocationRow(locationId, etName, tvQty));
        }

        tvLocationsSection.setVisibility(count.locations.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void refreshLocationQtyLabels() {
        for (LocationRow row : locationRows) {
            InventoryLocation location = count.findLocationById(row.locationId);
            int qty = location != null ? location.totalQty() : 0;
            row.tvQty.setText(getString(R.string.inventory_location_qty, qty));
        }
    }

    private void syncNamesFromUiIntoCount() {
        for (LocationRow row : locationRows) {
            InventoryLocation location = count.findLocationById(row.locationId);
            if (location == null) {
                continue;
            }
            String name = row.etName.getText() != null
                    ? row.etName.getText().toString().trim() : "";
            location.name = name;
        }
    }

    private void persistLocationStructure() {
        syncNamesFromUiIntoCount();
        InventoryStore.upsert(this, count);
    }

    private void openLocationScan(String locationId) {
        clearError();
        syncNamesFromUiIntoCount();

        InventoryLocation location = count.findLocationById(locationId);
        if (location == null) {
            return;
        }
        if (TextUtils.isEmpty(location.name)) {
            showError(getString(R.string.inventory_location_name_required));
            return;
        }

        InventoryStore.upsert(this, count);

        Intent intent = new Intent(this, InventoryScanActivity.class);
        intent.putExtra(InventoryScanActivity.EXTRA_COUNT_LOCAL_ID, count.localId);
        intent.putExtra(InventoryScanActivity.EXTRA_LOCATION_LOCAL_ID, locationId);
        startActivity(intent);
    }

    private void setFabMenuExpanded(boolean expanded) {
        fabExpanded = expanded;
        fabCountActions.animate().rotation(expanded ? 45f : 0f).setDuration(150).start();
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
        if (!validateForSave(false)) {
            return;
        }
        count.status = InventoryCount.STATUS_DRAFT;
        InventoryStore.upsert(this, count);
        Toast.makeText(this, R.string.inventory_draft_saved_message, Toast.LENGTH_LONG).show();
        finish();
    }

    private void saveAndSend() {
        setFabMenuExpanded(false);
        if (!validateForSave(true)) {
            return;
        }

        JSONObject apiJson;
        try {
            apiJson = count.toApiJson();
        } catch (JSONException e) {
            showError(getString(R.string.inventory_json_error));
            return;
        }
        Log.d(TAG, "Sayim JSON (gonderim bekliyor): " + apiJson);

        count.status = InventoryCount.STATUS_PENDING_SEND;
        InventoryStore.upsert(this, count);
        Toast.makeText(this, R.string.inventory_pending_send_saved_message, Toast.LENGTH_LONG).show();
        finish();
    }

    private boolean validateForSave(boolean requireProducts) {
        clearError();
        syncNamesFromUiIntoCount();

        if (TextUtils.isEmpty(count.warehouseCode) || TextUtils.isEmpty(count.officeCode)) {
            showError(getString(R.string.inventory_warehouse_required));
            return false;
        }
        if (count.locations.isEmpty()) {
            showError(getString(R.string.inventory_locations_required));
            return false;
        }
        for (int i = 0; i < count.locations.size(); i++) {
            InventoryLocation location = count.locations.get(i);
            if (TextUtils.isEmpty(location.name)) {
                showError(getString(R.string.inventory_location_name_required));
                return false;
            }
        }
        if (requireProducts && count.totalQty() <= 0) {
            showError(getString(R.string.inventory_lines_required));
            return false;
        }
        return true;
    }

    private void showError(String message) {
        tvLocationsError.setText(message);
        tvLocationsError.setVisibility(View.VISIBLE);
    }

    private void clearError() {
        tvLocationsError.setVisibility(View.GONE);
        tvLocationsError.setText("");
    }
}
