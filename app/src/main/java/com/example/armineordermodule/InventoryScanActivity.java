package com.example.armineordermodule;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.armineordermodule.rfid.RfidConnectionManager;
import com.example.armineordermodule.rfid.RfidManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import kotlin.Unit;

/**
 * Sayim lokasyonu urun okutma ekrani. Mantik toptan siparis okutma ile ayni:
 * RFID tetik, manuel EPC, EPC ile silme. Geri donunce lokasyon kaydedilir.
 */
public class InventoryScanActivity extends AppCompatActivity {

    public static final String EXTRA_COUNT_LOCAL_ID = "extra_count_local_id";
    public static final String EXTRA_LOCATION_LOCAL_ID = "extra_location_local_id";

    private static final String TRIGGER_LOG_TAG = "RFID_TRIGGER_KEY";
    private static final Set<Integer> TRIGGER_KEY_CODES =
            new LinkedHashSet<>(Arrays.asList(305, 619, 621));
    private static final long DUPLICATE_WARNING_THROTTLE_MS = 2500L;

    private InventoryCount count;
    private InventoryLocation location;
    private RfidManager rfidManager;
    private InventoryLineAdapter lineAdapter;

    private TextView tvOrderCustomerName;
    private View dotRfidStatus;
    private TextView tvScanHint;
    private TextView tvLinesSummary;
    private TextView tvLinesEmptyHint;
    private TextView tvOrderError;
    private TextView tvTotalTagCount;
    private View rowSave;
    private View rowSaveAndSend;
    private FloatingActionButton fabOrderActions;

    private final Object scanLock = new Object();
    private final Set<String> acceptedEpcs = new HashSet<>();
    private final Map<String, Set<String>> epcsByBarcode = new HashMap<>();
    private boolean isScanning = false;
    private long lastDuplicateWarningAtMs = 0L;

    private boolean deleteScanMode = false;
    private String deleteTargetBarcode = "";
    private AlertDialog deleteScanDialog;
    private TextView tvDeleteScanStatus;
    private TextInputEditText etDeleteEpc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wholesale_order);

        rfidManager = RfidConnectionManager.getManager(this);

        if (!loadOrFail()) {
            finish();
            return;
        }

        bindViews();
        renderLocation();
        updateRfidUi();
        updateTotalTagCountUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateRfidUi();
        if (RfidPreferences.isRfidEnabled(this) && !rfidManager.isConnected()) {
            RfidConnectionManager.autoReconnectIfNeededAsync(getApplicationContext(), result -> {
                updateRfidUi();
                return Unit.INSTANCE;
            });
        }
    }

    @Override
    protected void onPause() {
        persistLocationQuietly();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (isScanning) {
            RfidConnectionManager.stopInventoryAsync(null);
        }
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.d(TRIGGER_LOG_TAG, "keyCode=" + keyCode
                + " repeatCount=" + (event != null ? event.getRepeatCount() : -1));
        if (TRIGGER_KEY_CODES.contains(keyCode)) {
            if (event != null && event.getRepeatCount() == 0 && rfidManager.isConnected()) {
                if (RfidPreferences.isPressAndHoldMode(this)) {
                    if (!isScanning) {
                        startScanning();
                    }
                } else {
                    toggleScanning();
                }
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (TRIGGER_KEY_CODES.contains(keyCode)) {
            if (isScanning && RfidPreferences.isPressAndHoldMode(this)) {
                stopScanning();
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private boolean loadOrFail() {
        String countId = getIntent() != null ? getIntent().getStringExtra(EXTRA_COUNT_LOCAL_ID) : null;
        String locationId = getIntent() != null
                ? getIntent().getStringExtra(EXTRA_LOCATION_LOCAL_ID) : null;
        count = InventoryStore.findById(this, countId);
        if (count == null) {
            return false;
        }
        location = count.findLocationById(locationId);
        if (location == null) {
            return false;
        }
        restoreAcceptedEpcsFromLocation();
        return true;
    }

    private void restoreAcceptedEpcsFromLocation() {
        synchronized (scanLock) {
            acceptedEpcs.clear();
            epcsByBarcode.clear();
            for (InventoryLine line : location.lines) {
                if (line.epcs.isEmpty()) {
                    continue;
                }
                Set<String> set = new HashSet<>();
                for (String epc : line.epcs) {
                    if (TextUtils.isEmpty(epc)) {
                        continue;
                    }
                    acceptedEpcs.add(epc);
                    set.add(epc);
                }
                if (!set.isEmpty()) {
                    epcsByBarcode.put(line.barcode, set);
                }
            }
        }
    }

    private void bindViews() {
        ImageButton btnBack = findViewById(R.id.btnBack);
        tvOrderCustomerName = findViewById(R.id.tvOrderCustomerName);
        dotRfidStatus = findViewById(R.id.dotRfidStatus);
        tvScanHint = findViewById(R.id.tvScanHint);
        tvLinesSummary = findViewById(R.id.tvLinesSummary);
        MaterialButton btnAddManualLine = findViewById(R.id.btnAddManualLine);
        tvOrderError = findViewById(R.id.tvOrderError);
        tvLinesEmptyHint = findViewById(R.id.tvLinesEmptyHint);
        tvTotalTagCount = findViewById(R.id.tvTotalTagCount);
        RecyclerView rvOrderLines = findViewById(R.id.rvOrderLines);
        fabOrderActions = findViewById(R.id.fabOrderActions);
        rowSave = findViewById(R.id.rowSave);
        rowSaveAndSend = findViewById(R.id.rowSaveAndSend);

        // Kaydet menusu lokasyonlar ekraninda; burada sadece geri ile kayit.
        fabOrderActions.setVisibility(View.GONE);
        rowSave.setVisibility(View.GONE);
        rowSaveAndSend.setVisibility(View.GONE);

        lineAdapter = new InventoryLineAdapter(this::showDeleteByScanDialog);
        rvOrderLines.setLayoutManager(new LinearLayoutManager(this));
        rvOrderLines.setAdapter(lineAdapter);

        tvScanHint.setText(R.string.inventory_scan_hint);
        tvLinesEmptyHint.setText(R.string.inventory_lines_empty_hint);
        btnAddManualLine.setText(R.string.inventory_add_manual_line);

        btnBack.setOnClickListener(v -> {
            persistLocationQuietly();
            finish();
        });
        btnAddManualLine.setOnClickListener(v -> showAddManualLineDialog());
    }

    private void renderLocation() {
        String title = !TextUtils.isEmpty(location.name)
                ? location.name
                : getString(R.string.inventory_locations_title);
        if (!TextUtils.isEmpty(count.name)) {
            title = count.name + " · " + title;
        }
        tvOrderCustomerName.setText(title);
        renderLines();
    }

    private void toggleScanning() {
        if (isScanning) {
            stopScanning();
            return;
        }
        clearError();
        if (rfidManager.isConnected()) {
            startScanning();
            return;
        }
        RfidConnectionManager.connectAsync(this, result -> {
            updateRfidUi();
            if (result instanceof com.example.armineordermodule.rfid.ConnectResult.Success) {
                startScanning();
            } else {
                showError(getString(R.string.inventory_rfid_connect_failed));
            }
            return Unit.INSTANCE;
        });
    }

    private void startScanning() {
        RfidConnectionManager.startInventoryAsync(
                this,
                tag -> {
                    String epc = tag.getEpcId();
                    if (!EpcBarcodeGrouping.isAcceptedEpc(epc)) {
                        if (deleteScanMode) {
                            runOnUiThread(() -> showDeleteScanStatus(
                                    getString(R.string.inventory_delete_invalid_tag)));
                        }
                        return true;
                    }
                    if (deleteScanMode) {
                        return handleDeleteScanTag(epc);
                    }
                    return handleLiveTagRead(epc);
                },
                () -> Unit.INSTANCE,
                started -> {
                    if (started) {
                        isScanning = true;
                    } else {
                        if (deleteScanMode) {
                            showDeleteScanStatus(getString(R.string.rfid_start_failed));
                        } else {
                            showError(getString(R.string.rfid_start_failed));
                        }
                    }
                    return Unit.INSTANCE;
                });
    }

    private boolean handleLiveTagRead(String epc) {
        synchronized (scanLock) {
            if (acceptedEpcs.contains(epc)) {
                return false;
            }
        }

        InventoryCount other = InventoryStore.findOtherContainingEpc(
                this, epc, count.localId, location.localId);
        if (other != null) {
            warnEpcAlreadyUsed(epc);
            return false;
        }

        final String barcode;
        synchronized (scanLock) {
            if (acceptedEpcs.contains(epc)) {
                return false;
            }
            barcode = EpcBarcodeGrouping.extractBarcode(epc);
            if (barcode == null) {
                return false;
            }
            acceptedEpcs.add(epc);
            Set<String> set = epcsByBarcode.get(barcode);
            if (set == null) {
                set = new HashSet<>();
                epcsByBarcode.put(barcode, set);
            }
            set.add(epc);
        }

        runOnUiThread(() -> {
            addOrIncrementLine(barcode, 1, epc);
            renderLines();
            updateTotalTagCountUi();
            clearError();
        });
        RfidConnectionManager.playBeep();
        return false;
    }

    private void warnEpcAlreadyUsed(String epc) {
        long now = System.currentTimeMillis();
        boolean shouldAlert;
        synchronized (scanLock) {
            shouldAlert = (now - lastDuplicateWarningAtMs) >= DUPLICATE_WARNING_THROTTLE_MS;
            if (shouldAlert) {
                lastDuplicateWarningAtMs = now;
            }
        }
        if (!shouldAlert) {
            return;
        }

        RfidConnectionManager.playDuplicateWarning();

        String where = InventoryStore.findLocationNameContainingEpc(
                this, epc, count.localId, location.localId);
        if (TextUtils.isEmpty(where)) {
            where = "-";
        }
        final String message = getString(R.string.inventory_epc_in_other, where);
        runOnUiThread(() -> {
            showError(message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    private void stopScanning() {
        RfidConnectionManager.stopInventoryAsync(() -> {
            isScanning = false;
            runOnUiThread(() -> tvScanHint.setText(R.string.inventory_scan_hint));
            return Unit.INSTANCE;
        });
    }

    private boolean handleDeleteScanTag(String epc) {
        runOnUiThread(() -> {
            if (etDeleteEpc != null) {
                etDeleteEpc.setText(epc);
            }
            tryDeleteByEpc(epc);
        });
        return false;
    }

    private void addOrIncrementLine(String barcode, int qty, String epc) {
        for (InventoryLine line : location.lines) {
            if (line.barcode.equals(barcode)) {
                line.qty += qty;
                if (!TextUtils.isEmpty(epc) && !line.epcs.contains(epc)) {
                    line.epcs.add(epc);
                }
                return;
            }
        }
        InventoryLine created = new InventoryLine(barcode, qty);
        if (!TextUtils.isEmpty(epc)) {
            created.epcs.add(epc);
        }
        location.lines.add(created);
    }

    private void showAddManualLineDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_order_line, null);
        TextInputEditText etEpc = dialogView.findViewById(R.id.etManualEpc);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.inventory_add_manual_line)
                .setView(dialogView)
                .setPositiveButton(R.string.inventory_manual_add_confirm, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String epc = etEpc.getText() != null ? etEpc.getText().toString().trim() : "";
                if (TextUtils.isEmpty(epc)) {
                    etEpc.setError(getString(R.string.inventory_manual_epc_empty));
                    etEpc.requestFocus();
                    return;
                }
                if (!EpcBarcodeGrouping.isValidManualEpc(epc)) {
                    etEpc.setError(getString(R.string.inventory_manual_epc_invalid));
                    showError(getString(R.string.inventory_manual_epc_invalid));
                    Toast.makeText(this, R.string.inventory_manual_epc_invalid, Toast.LENGTH_LONG).show();
                    etEpc.requestFocus();
                    etEpc.selectAll();
                    return;
                }

                boolean alreadyHere;
                synchronized (scanLock) {
                    alreadyHere = acceptedEpcs.contains(epc);
                }
                if (alreadyHere) {
                    RfidConnectionManager.playDuplicateWarning();
                    showError(getString(R.string.inventory_manual_epc_duplicate));
                    Toast.makeText(this, R.string.inventory_manual_epc_duplicate, Toast.LENGTH_LONG).show();
                    etEpc.setError(getString(R.string.inventory_manual_epc_duplicate));
                    etEpc.requestFocus();
                    etEpc.selectAll();
                    return;
                }

                InventoryCount other = InventoryStore.findOtherContainingEpc(
                        this, epc, count.localId, location.localId);
                if (other != null) {
                    warnEpcAlreadyUsed(epc);
                    etEpc.setError(getString(R.string.inventory_epc_in_other,
                            InventoryStore.findLocationNameContainingEpc(
                                    this, epc, count.localId, location.localId)));
                    etEpc.requestFocus();
                    etEpc.selectAll();
                    return;
                }

                String barcode = EpcBarcodeGrouping.extractBarcode(epc);
                if (barcode == null) {
                    etEpc.setError(getString(R.string.inventory_manual_epc_invalid));
                    return;
                }

                synchronized (scanLock) {
                    acceptedEpcs.add(epc);
                    Set<String> set = epcsByBarcode.get(barcode);
                    if (set == null) {
                        set = new HashSet<>();
                        epcsByBarcode.put(barcode, set);
                    }
                    set.add(epc);
                }

                addOrIncrementLine(barcode, 1, epc);
                renderLines();
                updateTotalTagCountUi();
                clearError();
                dialog.dismiss();
            });
        });

        dialog.show();
        etEpc.requestFocus();
    }

    private void showDeleteByScanDialog(InventoryLine line) {
        if (line == null || TextUtils.isEmpty(line.barcode)) {
            return;
        }
        if (deleteScanDialog != null && deleteScanDialog.isShowing()) {
            return;
        }

        if (isScanning) {
            deleteScanMode = true;
            RfidConnectionManager.stopInventoryAsync(() -> {
                isScanning = false;
                openDeleteScanDialog(line.barcode);
                return Unit.INSTANCE;
            });
            return;
        }

        openDeleteScanDialog(line.barcode);
    }

    private void openDeleteScanDialog(String barcode) {
        deleteScanMode = true;
        deleteTargetBarcode = barcode;

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_delete_order_line, null);
        TextView tvTarget = dialogView.findViewById(R.id.tvDeleteScanTarget);
        tvDeleteScanStatus = dialogView.findViewById(R.id.tvDeleteScanStatus);
        etDeleteEpc = dialogView.findViewById(R.id.etDeleteEpc);
        tvTarget.setText(getString(R.string.inventory_delete_scan_target, barcode));
        tvDeleteScanStatus.setVisibility(View.GONE);

        TextView tvMessage = dialogView.findViewById(R.id.tvDeleteScanMessage);
        TextView tvHint = dialogView.findViewById(R.id.tvDeleteScanHint);
        tvMessage.setText(R.string.inventory_delete_scan_message);
        tvHint.setText(R.string.inventory_delete_scan_hint);

        etDeleteEpc.setOnEditorActionListener((v, actionId, event) -> {
            boolean isDone = actionId == EditorInfo.IME_ACTION_DONE;
            boolean isEnter = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (isDone || isEnter) {
                submitDeleteEpcFromInput();
                return true;
            }
            return false;
        });

        deleteScanDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.inventory_delete_scan_title)
                .setView(dialogView)
                .setPositiveButton(R.string.inventory_delete_confirm, null)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dismissDeleteScanDialog(true))
                .setOnCancelListener(dialog -> dismissDeleteScanDialog(true))
                .setCancelable(true)
                .create();
        deleteScanDialog.setCanceledOnTouchOutside(false);
        deleteScanDialog.setOnShowListener(dialog -> {
            deleteScanDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                    v -> submitDeleteEpcFromInput());
            if (deleteScanDialog.getWindow() != null) {
                deleteScanDialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
            etDeleteEpc.requestFocus();
        });
        deleteScanDialog.show();
    }

    private void submitDeleteEpcFromInput() {
        if (etDeleteEpc == null) {
            return;
        }
        String raw = etDeleteEpc.getText() != null ? etDeleteEpc.getText().toString().trim() : "";
        if (TextUtils.isEmpty(raw)) {
            showDeleteScanStatus(getString(R.string.inventory_delete_empty));
            return;
        }
        tryDeleteByEpc(raw);
    }

    private void tryDeleteByEpc(String epcRaw) {
        if (!deleteScanMode) {
            return;
        }

        String epc = epcRaw != null ? epcRaw.trim() : "";
        if (TextUtils.isEmpty(epc)) {
            showDeleteScanStatus(getString(R.string.inventory_delete_empty));
            return;
        }

        String barcode = EpcBarcodeGrouping.extractBarcode(epc);
        if (barcode == null) {
            showDeleteScanStatus(getString(R.string.inventory_delete_invalid_tag));
            return;
        }

        if (!barcode.equals(deleteTargetBarcode)) {
            showDeleteScanStatus(getString(R.string.inventory_delete_wrong_product));
            if (etDeleteEpc != null) {
                etDeleteEpc.selectAll();
            }
            return;
        }

        boolean removed;
        synchronized (scanLock) {
            removed = acceptedEpcs.remove(epc);
            if (removed) {
                Set<String> set = epcsByBarcode.get(barcode);
                if (set != null) {
                    set.remove(epc);
                    if (set.isEmpty()) {
                        epcsByBarcode.remove(barcode);
                    }
                }
            }
        }

        if (!removed) {
            showDeleteScanStatus(getString(R.string.inventory_delete_epc_not_found));
            if (etDeleteEpc != null) {
                etDeleteEpc.selectAll();
            }
            return;
        }

        decrementLineQty(barcode, 1, epc);
        renderLines();
        updateTotalTagCountUi();
        Toast.makeText(this, R.string.inventory_delete_success, Toast.LENGTH_SHORT).show();

        if (isScanning) {
            RfidConnectionManager.stopInventoryAsync(() -> {
                isScanning = false;
                dismissDeleteScanDialog(false);
                return Unit.INSTANCE;
            });
        } else {
            dismissDeleteScanDialog(false);
        }
    }

    private void decrementLineQty(String barcode, int qty, String epc) {
        if (TextUtils.isEmpty(barcode) || location == null || qty <= 0) {
            return;
        }
        for (int i = 0; i < location.lines.size(); i++) {
            InventoryLine line = location.lines.get(i);
            if (!barcode.equals(line.barcode)) {
                continue;
            }
            if (!TextUtils.isEmpty(epc)) {
                line.epcs.remove(epc);
            }
            line.qty -= qty;
            if (line.qty <= 0) {
                location.lines.remove(i);
            }
            return;
        }
    }

    private void syncEpcsIntoLocationLines() {
        synchronized (scanLock) {
            for (InventoryLine line : location.lines) {
                line.epcs.clear();
                Set<String> set = epcsByBarcode.get(line.barcode);
                if (set != null) {
                    line.epcs.addAll(set);
                }
            }
        }
    }

    private void persistLocationQuietly() {
        if (count == null || location == null) {
            return;
        }
        syncEpcsIntoLocationLines();
        InventoryStore.upsert(this, count);
    }

    private void dismissDeleteScanDialog(boolean cancelled) {
        deleteScanMode = false;
        deleteTargetBarcode = "";
        tvDeleteScanStatus = null;
        etDeleteEpc = null;
        if (deleteScanDialog != null) {
            if (deleteScanDialog.isShowing()) {
                deleteScanDialog.dismiss();
            }
            deleteScanDialog = null;
        }
        if (isScanning) {
            RfidConnectionManager.stopInventoryAsync(() -> {
                isScanning = false;
                return Unit.INSTANCE;
            });
        }
        if (cancelled) {
            Toast.makeText(this, R.string.inventory_delete_cancelled, Toast.LENGTH_SHORT).show();
        }
    }

    private void showDeleteScanStatus(String message) {
        if (tvDeleteScanStatus == null) {
            return;
        }
        tvDeleteScanStatus.setText(message);
        tvDeleteScanStatus.setVisibility(View.VISIBLE);
    }

    private void renderLines() {
        lineAdapter.submitList(location.lines);
        tvLinesEmptyHint.setVisibility(location.lines.isEmpty() ? View.VISIBLE : View.GONE);
        tvLinesSummary.setText(getString(
                R.string.inventory_lines_summary,
                location.totalLineCount(),
                location.totalQty()));
    }

    private void updateTotalTagCountUi() {
        int total;
        synchronized (scanLock) {
            total = acceptedEpcs.size();
        }
        int lineQty = location != null ? location.totalQty() : 0;
        int display = Math.max(total, lineQty);
        tvTotalTagCount.setText(getString(R.string.inventory_total_tags_footer, display));
    }

    private void updateRfidUi() {
        boolean connected = rfidManager.isConnected();
        dotRfidStatus.setBackgroundResource(connected ? R.drawable.bg_dot_green : R.drawable.bg_dot_red);
        dotRfidStatus.setContentDescription(getString(
                connected ? R.string.main_rfid_status_connected : R.string.main_rfid_status_disconnected));
    }

    private void showError(String message) {
        tvOrderError.setText(message);
        tvOrderError.setVisibility(View.VISIBLE);
    }

    private void clearError() {
        tvOrderError.setVisibility(View.GONE);
        tvOrderError.setText("");
    }
}
