package com.example.armineordermodule;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.armineordermodule.rfid.RfidConnectionManager;
import com.example.armineordermodule.rfid.RfidManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import kotlin.Unit;

/**
 * Depo transferi urun okutma: tetik ile gelen EPC'ler oldugu gibi listelenir.
 */
public class WarehouseTransferScanActivity extends AppCompatActivity {

    public static final String EXTRA_SOURCE_OFFICE = "extra_source_office";
    public static final String EXTRA_SOURCE_WAREHOUSE = "extra_source_warehouse";
    public static final String EXTRA_SOURCE_DESCRIPTION = "extra_source_description";
    public static final String EXTRA_TARGET_OFFICE = "extra_target_office";
    public static final String EXTRA_TARGET_WAREHOUSE = "extra_target_warehouse";
    public static final String EXTRA_TARGET_DESCRIPTION = "extra_target_description";
    public static final String EXTRA_TRANSFER_GUID = "extra_transfer_guid";

    private static final String STATE_TRANSFER = "state_transfer";
    private static final String JSON_LOG_TAG = "WAREHOUSE_TRANSFER";
    private static final String TRIGGER_LOG_TAG = "RFID_TRIGGER_KEY";
    private static final Set<Integer> TRIGGER_KEY_CODES =
            new LinkedHashSet<>(Arrays.asList(305, 619, 621));

    private RfidManager rfidManager;
    private RfidHandheldCountingEpcAdapter epcAdapter;
    private RecyclerView rvEpcs;
    private TextView tvScanError;
    private TextView tvEpcEmptyHint;
    private TextView tvTotalProductCount;
    private View dotRfidStatus;
    private FloatingActionButton fabTransferActions;
    private View rowSave;
    private View rowSaveAndSend;
    private View rowCancelBarcode;
    private AlertDialog cancelScanDialog;
    private RfidHandheldCountingEpcAdapter cancelEpcAdapter;
    private TextView tvCancelEpcStatus;
    private TextView tvCancelEpcEmpty;

    private final Object scanLock = new Object();
    private String transferGuid;
    private String sourceOffice = "";
    private String sourceWarehouse = "";
    private String sourceDescription = "";
    private String targetOffice = "";
    private String targetWarehouse = "";
    private String targetDescription = "";
    private long createdAtMillis;
    private int headerId;
    private boolean nebimSent;
    private boolean sending;
    private final List<String> epcs = new ArrayList<>();
    private final Set<String> acceptedEpcs = new LinkedHashSet<>();
    private final LinkedHashSet<String> pendingCancelEpcs = new LinkedHashSet<>();
    private boolean isScanning = false;
    private boolean cancelScanMode = false;
    private boolean fabExpanded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_warehouse_transfer_scan);

        rfidManager = RfidConnectionManager.getManager(this);

        ImageButton btnBack = findViewById(R.id.btnBack);
        TextView tvTransferRoute = findViewById(R.id.tvTransferRoute);
        dotRfidStatus = findViewById(R.id.dotRfidStatus);
        tvScanError = findViewById(R.id.tvScanError);
        tvEpcEmptyHint = findViewById(R.id.tvEpcEmptyHint);
        tvTotalProductCount = findViewById(R.id.tvTotalProductCount);
        rvEpcs = findViewById(R.id.rvEpcs);
        fabTransferActions = findViewById(R.id.fabTransferActions);
        rowSave = findViewById(R.id.rowSave);
        rowSaveAndSend = findViewById(R.id.rowSaveAndSend);
        rowCancelBarcode = findViewById(R.id.rowCancelBarcode);
        FloatingActionButton fabSave = findViewById(R.id.fabSave);
        FloatingActionButton fabSaveAndSend = findViewById(R.id.fabSaveAndSend);
        FloatingActionButton fabCancelBarcode = findViewById(R.id.fabCancelBarcode);

        epcAdapter = new RfidHandheldCountingEpcAdapter();
        rvEpcs.setLayoutManager(new LinearLayoutManager(this));
        rvEpcs.setAdapter(epcAdapter);

        if (!bindSession(savedInstanceState)) {
            Toast.makeText(this, R.string.warehouse_transfer_not_found, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (nebimSent) {
            Toast.makeText(this, R.string.warehouse_transfer_sent_locked, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        tvTransferRoute.setText(getString(
                R.string.warehouse_transfer_scan_route, sourceDescription, targetDescription));

        btnBack.setOnClickListener(v -> requestLeaveScanScreen());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                requestLeaveScanScreen();
            }
        });
        fabTransferActions.setOnClickListener(v -> setFabMenuExpanded(!fabExpanded));
        fabSave.setOnClickListener(v -> saveDraft());
        fabSaveAndSend.setOnClickListener(v -> saveAndSend());
        fabCancelBarcode.setOnClickListener(v -> {
            setFabMenuExpanded(false);
            showCancelBarcodeDialog();
        });

        renderEpcs();
        updateRfidUi();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        try {
            outState.putString(STATE_TRANSFER, currentTransfer().toStorageJson().toString());
        } catch (JSONException ignored) {
        }
    }

    private boolean bindSession(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            String saved = savedInstanceState.getString(STATE_TRANSFER);
            if (!TextUtils.isEmpty(saved)) {
                try {
                    applyTransfer(WarehouseTransfer.fromStorageJson(new JSONObject(saved)));
                    return !TextUtils.isEmpty(transferGuid);
                } catch (JSONException ignored) {
                }
            }
        }

        String existingGuid = textExtra(EXTRA_TRANSFER_GUID);
        if (!TextUtils.isEmpty(existingGuid)) {
            WarehouseTransfer stored = WarehouseTransferStore.findByGuid(this, existingGuid);
            if (stored == null) {
                return false;
            }
            applyTransfer(stored);
            return true;
        }

        transferGuid = UUID.randomUUID().toString();
        sourceOffice = textExtra(EXTRA_SOURCE_OFFICE);
        sourceWarehouse = textExtra(EXTRA_SOURCE_WAREHOUSE);
        sourceDescription = textExtra(EXTRA_SOURCE_DESCRIPTION);
        targetOffice = textExtra(EXTRA_TARGET_OFFICE);
        targetWarehouse = textExtra(EXTRA_TARGET_WAREHOUSE);
        targetDescription = textExtra(EXTRA_TARGET_DESCRIPTION);
        createdAtMillis = System.currentTimeMillis();
        return true;
    }

    private void applyTransfer(WarehouseTransfer transfer) {
        transferGuid = transfer.transferGuid;
        sourceOffice = transfer.officeCode;
        sourceWarehouse = transfer.warehouseCode;
        sourceDescription = transfer.sourceDescription;
        targetOffice = transfer.toOfficeCode;
        targetWarehouse = transfer.toWarehouseCode;
        targetDescription = transfer.targetDescription;
        createdAtMillis = transfer.createdAtMillis;
        headerId = transfer.headerId;
        nebimSent = transfer.isNebimSend;
        synchronized (scanLock) {
            epcs.clear();
            acceptedEpcs.clear();
            for (String epc : transfer.epcs) {
                if (!TextUtils.isEmpty(epc) && acceptedEpcs.add(epc)) {
                    epcs.add(epc);
                }
            }
        }
    }

    private WarehouseTransfer currentTransfer() {
        WarehouseTransfer transfer = new WarehouseTransfer();
        transfer.transferGuid = transferGuid;
        transfer.officeCode = sourceOffice;
        transfer.warehouseCode = sourceWarehouse;
        transfer.sourceDescription = sourceDescription;
        transfer.toOfficeCode = targetOffice;
        transfer.toWarehouseCode = targetWarehouse;
        transfer.targetDescription = targetDescription;
        transfer.createdAtMillis = createdAtMillis;
        transfer.headerId = headerId;
        transfer.isNebimSend = nebimSent;
        synchronized (scanLock) {
            transfer.epcs.addAll(epcs);
        }
        return transfer;
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
    protected void onDestroy() {
        dismissCancelScanDialog(false);
        if (isScanning) {
            RfidConnectionManager.stopInventoryAsync(null);
        }
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.d(TRIGGER_LOG_TAG, "keyCode=" + keyCode);
        if (TRIGGER_KEY_CODES.contains(keyCode)) {
            if (event != null && event.getRepeatCount() == 0 && rfidManager.isConnected()) {
                if (cancelScanMode) {
                    if (!isScanning) {
                        startScanning();
                    }
                } else if (RfidPreferences.isPressAndHoldMode(this)) {
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
            if (isScanning && !cancelScanMode && RfidPreferences.isPressAndHoldMode(this)) {
                stopScanning();
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void toggleScanning() {
        if (cancelScanMode) {
            return;
        }
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
                if (!cancelScanMode) {
                    startScanning();
                }
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
                    if (cancelScanMode) {
                        return handleCancelScanTag(tag.getEpcId());
                    }
                    return handleLiveTagRead(tag.getEpcId());
                },
                () -> Unit.INSTANCE,
                started -> {
                    if (started) {
                        isScanning = true;
                    } else {
                        showError(getString(R.string.rfid_start_failed));
                    }
                    return Unit.INSTANCE;
                });
    }

    private boolean handleLiveTagRead(String epc) {
        if (cancelScanMode) {
            return handleCancelScanTag(epc);
        }
        if (TextUtils.isEmpty(epc)) {
            return false;
        }
        final String normalized = epc.trim();
        if (normalized.isEmpty()) {
            return false;
        }

        synchronized (scanLock) {
            if (!acceptedEpcs.add(normalized)) {
                return false;
            }
            epcs.add(0, normalized);
        }

        runOnUiThread(() -> {
            renderEpcs();
            rvEpcs.scrollToPosition(0);
            clearError();
        });
        RfidConnectionManager.playBeep();
        return false;
    }

    private void stopScanning() {
        RfidConnectionManager.stopInventoryAsync(() -> {
            isScanning = false;
            return Unit.INSTANCE;
        });
    }

    private boolean handleCancelScanTag(String epc) {
        runOnUiThread(() -> addToPendingCancelList(epc));
        return false;
    }

    private void addToPendingCancelList(String epcRaw) {
        if (!cancelScanMode) {
            return;
        }
        String epc = epcRaw != null ? epcRaw.trim() : "";
        if (TextUtils.isEmpty(epc) || pendingCancelEpcs.contains(epc)) {
            return;
        }

        boolean inList;
        synchronized (scanLock) {
            inList = acceptedEpcs.contains(epc);
        }
        if (!inList) {
            showCancelScanStatus(getString(R.string.warehouse_transfer_delete_not_found));
            return;
        }

        pendingCancelEpcs.add(epc);
        renderCancelList();
        clearCancelScanStatus();
        RfidConnectionManager.playBeep();
    }

    private void showCancelBarcodeDialog() {
        if (cancelScanDialog != null && cancelScanDialog.isShowing()) {
            return;
        }
        cancelScanMode = true;
        pendingCancelEpcs.clear();
        if (isScanning) {
            RfidConnectionManager.stopInventoryAsync(() -> {
                isScanning = false;
                openCancelScanDialog();
                return Unit.INSTANCE;
            });
            return;
        }
        openCancelScanDialog();
    }

    private void openCancelScanDialog() {
        cancelScanMode = true;
        pendingCancelEpcs.clear();

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_wholesale_order_cancel_epc, null);
        tvCancelEpcStatus = dialogView.findViewById(R.id.tvCancelEpcStatus);
        tvCancelEpcEmpty = dialogView.findViewById(R.id.tvCancelEpcEmpty);
        RecyclerView rvCancelEpcs = dialogView.findViewById(R.id.rvCancelEpcs);

        cancelEpcAdapter = new RfidHandheldCountingEpcAdapter();
        rvCancelEpcs.setLayoutManager(new LinearLayoutManager(this));
        rvCancelEpcs.setAdapter(cancelEpcAdapter);
        renderCancelList();

        cancelScanDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.warehouse_transfer_cancel_barcode)
                .setView(dialogView)
                .setPositiveButton(R.string.warehouse_transfer_cancel_barcode_confirm, null)
                .setNegativeButton(R.string.warehouse_transfer_cancel_barcode_back,
                        (dialog, which) -> dismissCancelScanDialog(true))
                .setOnCancelListener(dialog -> dismissCancelScanDialog(true))
                .setCancelable(true)
                .create();
        cancelScanDialog.setCanceledOnTouchOutside(false);
        cancelScanDialog.setOnShowListener(dialog -> {
            cancelScanDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                    v -> confirmCancelDeletes());
            if (rfidManager.isConnected() && !isScanning) {
                startScanning();
            }
        });
        cancelScanDialog.show();
    }

    private void confirmCancelDeletes() {
        if (!cancelScanMode) {
            return;
        }
        if (pendingCancelEpcs.isEmpty()) {
            showCancelScanStatus(getString(R.string.warehouse_transfer_cancel_barcode_delete_empty));
            return;
        }

        int removedCount = 0;
        synchronized (scanLock) {
            for (String epc : pendingCancelEpcs) {
                if (acceptedEpcs.remove(epc)) {
                    epcs.remove(epc);
                    removedCount++;
                }
            }
        }
        if (removedCount <= 0) {
            showCancelScanStatus(getString(R.string.warehouse_transfer_delete_not_found));
            return;
        }

        pendingCancelEpcs.clear();
        renderEpcs();
        Toast.makeText(this,
                getString(R.string.warehouse_transfer_delete_success) + " (" + removedCount + ")",
                Toast.LENGTH_SHORT).show();

        if (isScanning) {
            RfidConnectionManager.stopInventoryAsync(() -> {
                isScanning = false;
                dismissCancelScanDialog(false);
                return Unit.INSTANCE;
            });
        } else {
            dismissCancelScanDialog(false);
        }
    }

    private void renderCancelList() {
        if (cancelEpcAdapter == null) {
            return;
        }
        cancelEpcAdapter.submitList(new ArrayList<>(pendingCancelEpcs));
        if (tvCancelEpcEmpty != null) {
            tvCancelEpcEmpty.setVisibility(pendingCancelEpcs.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void dismissCancelScanDialog(boolean cancelled) {
        cancelScanMode = false;
        pendingCancelEpcs.clear();
        tvCancelEpcStatus = null;
        tvCancelEpcEmpty = null;
        cancelEpcAdapter = null;
        if (cancelScanDialog != null) {
            if (cancelScanDialog.isShowing()) {
                cancelScanDialog.dismiss();
            }
            cancelScanDialog = null;
        }
        if (isScanning) {
            RfidConnectionManager.stopInventoryAsync(() -> {
                isScanning = false;
                return Unit.INSTANCE;
            });
        }
        if (cancelled) {
            Toast.makeText(this, R.string.warehouse_transfer_delete_cancelled, Toast.LENGTH_SHORT).show();
        }
    }

    private void showCancelScanStatus(String message) {
        if (tvCancelEpcStatus == null) {
            return;
        }
        tvCancelEpcStatus.setText(message);
        tvCancelEpcStatus.setVisibility(View.VISIBLE);
    }

    private void clearCancelScanStatus() {
        if (tvCancelEpcStatus != null) {
            tvCancelEpcStatus.setVisibility(View.GONE);
            tvCancelEpcStatus.setText("");
        }
    }

    private void renderEpcs() {
        List<String> snapshot;
        synchronized (scanLock) {
            snapshot = new ArrayList<>(epcs);
        }
        epcAdapter.submitList(snapshot);
        tvEpcEmptyHint.setVisibility(snapshot.isEmpty() ? View.VISIBLE : View.GONE);
        tvTotalProductCount.setText(getString(R.string.warehouse_transfer_total_products, snapshot.size()));
    }

    private void saveDraft() {
        setFabMenuExpanded(false);
        if (saveLocalDraft() == null) {
            return;
        }
        showError(getString(R.string.warehouse_transfer_saved));
        Toast.makeText(this, R.string.warehouse_transfer_saved, Toast.LENGTH_LONG).show();
    }

    private void saveAndSend() {
        if (sending) {
            return;
        }
        setFabMenuExpanded(false);
        if (nebimSent) {
            showError(getString(R.string.warehouse_transfer_sent_locked));
            return;
        }
        WarehouseTransfer transfer = saveLocalDraft();
        if (transfer == null) {
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
        String companyCode = AuthPreferences.getCompanyCode(this);
        if (TextUtils.isEmpty(companyCode)) {
            showError(getString(R.string.warehouse_transfer_company_missing));
            return;
        }

        JSONObject apiJson;
        try {
            apiJson = WarehouseTransferRequest.toApiJson(
                    transfer.transferGuid,
                    companyCode,
                    "",
                    transfer.officeCode,
                    transfer.warehouseCode,
                    transfer.toOfficeCode,
                    transfer.toWarehouseCode,
                    databaseName,
                    transfer.epcs);
        } catch (JSONException e) {
            showError(getString(R.string.warehouse_transfer_json_failed));
            return;
        }

        clearError();
        sending = true;
        Toast.makeText(this, R.string.warehouse_transfer_sending, Toast.LENGTH_SHORT).show();
        WarehouseTransferCreateApiClient.create(serverUrl, token, apiJson,
                new WarehouseTransferCreateApiClient.Callback() {
                    @Override
                    public void onSuccess(WarehouseTransferCreateApiClient.CreateResult result) {
                        sending = false;
                        nebimSent = true;
                        if (result != null && result.headerId > 0) {
                            headerId = result.headerId;
                        }
                        if (result != null && !TextUtils.isEmpty(result.transferGuid)) {
                            transferGuid = result.transferGuid;
                        }
                        WarehouseTransferStore.upsert(WarehouseTransferScanActivity.this, currentTransfer());
                        if (isFinishing()) {
                            return;
                        }
                        String message = result != null && !TextUtils.isEmpty(result.message)
                                ? result.message
                                : getString(R.string.warehouse_transfer_sent_fallback);
                        Intent intent = new Intent(
                                WarehouseTransferScanActivity.this, WarehouseTransferListActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        sending = false;
                        if (isFinishing()) {
                            return;
                        }
                        showError(!TextUtils.isEmpty(message)
                                ? message
                                : getString(R.string.warehouse_transfer_send_failed));
                    }
                });
    }

    /** Cihaz kaydı. API gövdesi üretmez; IsCompleted burada yazılmaz. */
    private WarehouseTransfer saveLocalDraft() {
        if (!hasProducts()) {
            showError(getString(R.string.warehouse_transfer_products_required));
            return null;
        }
        if (TextUtils.isEmpty(transferGuid)) {
            transferGuid = UUID.randomUUID().toString();
        }
        if (createdAtMillis <= 0L) {
            createdAtMillis = System.currentTimeMillis();
        }
        WarehouseTransfer transfer = currentTransfer();
        WarehouseTransferStore.upsert(this, transfer);
        return transfer;
    }

    private boolean hasProducts() {
        synchronized (scanLock) {
            return !epcs.isEmpty();
        }
    }

    private void requestLeaveScanScreen() {
        if (hasUnsavedScans()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.warehouse_transfer_unsaved_title)
                    .setMessage(R.string.warehouse_transfer_unsaved_message)
                    .setPositiveButton(R.string.warehouse_transfer_unsaved_stay, null)
                    .setNegativeButton(R.string.warehouse_transfer_unsaved_leave,
                            (dialog, which) -> leaveScanScreen())
                    .show();
            return;
        }
        leaveScanScreen();
    }

    /** Kaydedilmiş liste ile ekrandaki okutma aynıysa çıkışta veri kaybı yoktur. */
    private boolean hasUnsavedScans() {
        List<String> current;
        synchronized (scanLock) {
            if (epcs.isEmpty()) {
                return false;
            }
            current = new ArrayList<>(epcs);
        }
        WarehouseTransfer stored = WarehouseTransferStore.findByGuid(this, transferGuid);
        if (stored == null) {
            return true;
        }
        return !stored.epcs.equals(current);
    }

    private void leaveScanScreen() {
        if (isScanning) {
            RfidConnectionManager.stopInventoryAsync(() -> {
                isScanning = false;
                runOnUiThread(this::finish);
                return Unit.INSTANCE;
            });
            return;
        }
        finish();
    }

    private void setFabMenuExpanded(boolean expanded) {
        fabExpanded = expanded;
        fabTransferActions.animate().rotation(expanded ? 45f : 0f).setDuration(150).start();
        animateFabRow(rowSave, expanded);
        animateFabRow(rowSaveAndSend, expanded);
        animateFabRow(rowCancelBarcode, expanded);
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

    private void updateRfidUi() {
        boolean connected = rfidManager.isConnected();
        dotRfidStatus.setBackgroundResource(connected ? R.drawable.bg_dot_green : R.drawable.bg_dot_red);
    }

    private String textExtra(String key) {
        String value = getIntent() != null ? getIntent().getStringExtra(key) : null;
        return value != null ? value : "";
    }

    private void showError(String message) {
        tvScanError.setText(message);
        tvScanError.setVisibility(View.VISIBLE);
    }

    private void clearError() {
        tvScanError.setVisibility(View.GONE);
        tvScanError.setText("");
    }
}
