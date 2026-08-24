package com.example.armineordermodule;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.armineordermodule.rfid.RfidConnectionManager;
import com.example.armineordermodule.rfid.RfidManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import kotlin.Unit;

/**
 * RFID sayim lokasyonu: yalnizca ham EPC okutma (parse / miktar yok).
 * Silme: FAB > Iptal Barkod ile modal; okunanlar modal listesine duser, Sil ile silinir.
 */
public class RfidHandheldCountingScanActivity extends AppCompatActivity {

    public static final String EXTRA_COUNTING_CODE = "extra_counting_code";
    public static final String EXTRA_LOCATION_GUID = "extra_location_guid";

    private static final String TRIGGER_LOG_TAG = "RFID_TRIGGER_KEY";
    private static final Set<Integer> TRIGGER_KEY_CODES =
            new LinkedHashSet<>(Arrays.asList(305, 619, 621));

    private RfidHandheldCounting counting;
    private RfidHandheldCountingLocation location;
    private RfidManager rfidManager;
    private RfidHandheldCountingEpcAdapter epcAdapter;

    private TextView tvLocationTitle;
    private View dotRfidStatus;
    private TextView tvEpcSummary;
    private TextView tvScanError;
    private TextView tvEpcEmptyHint;
    private TextView tvTotalEpcCount;
    private FloatingActionButton fabScanActions;
    private View rowCancelBarcode;

    private final Object scanLock = new Object();
    private final Set<String> acceptedEpcs = new LinkedHashSet<>();
    private boolean isScanning = false;
    private boolean fabExpanded = false;

    private boolean cancelScanMode = false;
    private AlertDialog cancelScanDialog;
    private TextView tvCancelEpcStatus;
    private TextView tvCancelEpcEmpty;
    private RfidHandheldCountingEpcAdapter cancelEpcAdapter;
    /** Modalda silinmek uzere okutulan EPC'ler (henuz ana listeden dusmedi). */
    private final LinkedHashSet<String> pendingCancelEpcs = new LinkedHashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rfid_handheld_counting_scan);

        rfidManager = RfidConnectionManager.getManager(this);
        if (!loadOrFail()) {
            finish();
            return;
        }

        ImageButton btnBack = findViewById(R.id.btnBack);
        tvLocationTitle = findViewById(R.id.tvLocationTitle);
        dotRfidStatus = findViewById(R.id.dotRfidStatus);
        tvEpcSummary = findViewById(R.id.tvEpcSummary);
        tvScanError = findViewById(R.id.tvScanError);
        tvEpcEmptyHint = findViewById(R.id.tvEpcEmptyHint);
        tvTotalEpcCount = findViewById(R.id.tvTotalEpcCount);
        RecyclerView rvEpcs = findViewById(R.id.rvEpcs);
        fabScanActions = findViewById(R.id.fabScanActions);
        rowCancelBarcode = findViewById(R.id.rowCancelBarcode);
        FloatingActionButton fabCancelBarcode = findViewById(R.id.fabCancelBarcode);

        epcAdapter = new RfidHandheldCountingEpcAdapter();
        rvEpcs.setLayoutManager(new LinearLayoutManager(this));
        rvEpcs.setAdapter(epcAdapter);

        btnBack.setOnClickListener(v -> {
            persistQuietly();
            finish();
        });
        fabScanActions.setOnClickListener(v -> setFabMenuExpanded(!fabExpanded));
        fabCancelBarcode.setOnClickListener(v -> {
            setFabMenuExpanded(false);
            showCancelBarcodeDialog();
        });

        restoreEpcsFromLocation();
        renderEpcs();
        updateRfidUi();
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
        persistQuietly();
        super.onPause();
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

    private boolean loadOrFail() {
        String code = getIntent() != null
                ? getIntent().getStringExtra(EXTRA_COUNTING_CODE) : null;
        String guid = getIntent() != null
                ? getIntent().getStringExtra(EXTRA_LOCATION_GUID) : null;
        counting = RfidHandheldCountingStore.findByCode(this, code);
        if (counting == null) {
            return false;
        }
        location = counting.findLocationByGuid(guid);
        return location != null;
    }

    private void restoreEpcsFromLocation() {
        synchronized (scanLock) {
            acceptedEpcs.clear();
            for (String epc : location.epcs) {
                if (!TextUtils.isEmpty(epc)) {
                    acceptedEpcs.add(epc.trim());
                }
            }
        }
    }

    private void setFabMenuExpanded(boolean expanded) {
        fabExpanded = expanded;
        fabScanActions.animate().rotation(expanded ? 45f : 0f).setDuration(150).start();
        if (expanded) {
            rowCancelBarcode.setVisibility(View.VISIBLE);
            rowCancelBarcode.setAlpha(0f);
            rowCancelBarcode.setTranslationY(24f);
            rowCancelBarcode.animate().alpha(1f).translationY(0f).setDuration(150).start();
        } else {
            rowCancelBarcode.animate().alpha(0f).translationY(24f).setDuration(120)
                    .withEndAction(() -> rowCancelBarcode.setVisibility(View.GONE)).start();
        }
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
                showError(getString(R.string.rfid_counting_rfid_connect_failed));
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
                    } else if (cancelScanMode) {
                        showCancelScanStatus(getString(R.string.rfid_start_failed));
                    } else {
                        showError(getString(R.string.rfid_start_failed));
                    }
                    return Unit.INSTANCE;
                });
    }

    private boolean handleLiveTagRead(String epc) {
        if (cancelScanMode) {
            return false;
        }
        if (TextUtils.isEmpty(epc)) {
            return false;
        }
        final String normalized = epc.trim();
        if (normalized.isEmpty()) {
            return false;
        }

        synchronized (scanLock) {
            if (cancelScanMode || acceptedEpcs.contains(normalized)) {
                return false;
            }
            acceptedEpcs.add(normalized);
        }

        runOnUiThread(() -> {
            if (cancelScanMode) {
                synchronized (scanLock) {
                    acceptedEpcs.remove(normalized);
                }
                location.epcs.remove(normalized);
                return;
            }
            if (!location.epcs.contains(normalized)) {
                location.epcs.add(normalized);
            }
            renderEpcs();
            clearError();
        });
        RfidConnectionManager.playBeep();
        return false;
    }

    /** Iptal modalinda okunan EPC modal listesine eklenir (ana listeden henuz silinmez). */
    private boolean handleCancelScanTag(String epc) {
        runOnUiThread(() -> addToPendingCancelList(epc));
        return false;
    }

    private void addToPendingCancelList(String epcRaw) {
        if (!cancelScanMode) {
            return;
        }
        String epc = epcRaw != null ? epcRaw.trim() : "";
        if (TextUtils.isEmpty(epc)) {
            return;
        }

        if (pendingCancelEpcs.contains(epc)) {
            return;
        }

        boolean inMainList;
        synchronized (scanLock) {
            inMainList = acceptedEpcs.contains(epc) || location.epcs.contains(epc);
        }
        if (!inMainList) {
            showCancelScanStatus(getString(R.string.rfid_counting_delete_not_found));
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

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_rfid_counting_cancel_epc, null);
        tvCancelEpcStatus = dialogView.findViewById(R.id.tvCancelEpcStatus);
        tvCancelEpcEmpty = dialogView.findViewById(R.id.tvCancelEpcEmpty);
        RecyclerView rvCancelEpcs = dialogView.findViewById(R.id.rvCancelEpcs);

        cancelEpcAdapter = new RfidHandheldCountingEpcAdapter();
        rvCancelEpcs.setLayoutManager(new LinearLayoutManager(this));
        rvCancelEpcs.setAdapter(cancelEpcAdapter);
        renderCancelList();

        cancelScanDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.rfid_counting_cancel_barcode)
                .setView(dialogView)
                .setPositiveButton(R.string.rfid_counting_cancel_barcode_confirm, null)
                .setNegativeButton(R.string.rfid_counting_cancel_barcode_back,
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
            showCancelScanStatus(getString(R.string.rfid_counting_cancel_barcode_delete_empty));
            return;
        }

        int removedCount = 0;
        synchronized (scanLock) {
            for (String epc : pendingCancelEpcs) {
                if (acceptedEpcs.remove(epc)) {
                    location.removeEpc(epc);
                    removedCount++;
                }
            }
        }

        if (removedCount <= 0) {
            showCancelScanStatus(getString(R.string.rfid_counting_delete_not_found));
            return;
        }

        pendingCancelEpcs.clear();
        renderEpcs();
        persistQuietly();
        Toast.makeText(this,
                getString(R.string.rfid_counting_delete_success) + " (" + removedCount + ")",
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
            Toast.makeText(this, R.string.rfid_counting_delete_cancelled, Toast.LENGTH_SHORT).show();
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

    private void stopScanning() {
        RfidConnectionManager.stopInventoryAsync(() -> {
            isScanning = false;
            return Unit.INSTANCE;
        });
    }

    private void renderEpcs() {
        String locName = !TextUtils.isEmpty(location.locationName)
                ? location.locationName
                : getString(R.string.rfid_counting_detail_title);
        if (!TextUtils.isEmpty(counting.batchName)) {
            tvLocationTitle.setText(counting.batchName + " · " + locName);
        } else {
            tvLocationTitle.setText(locName);
        }

        epcAdapter.submitList(new ArrayList<>(location.epcs));
        tvEpcEmptyHint.setVisibility(location.epcs.isEmpty() ? View.VISIBLE : View.GONE);
        tvEpcSummary.setText(getString(R.string.rfid_counting_epc_summary, location.epcs.size()));
        tvTotalEpcCount.setText(getString(R.string.rfid_counting_total_epc_footer, location.epcs.size()));
    }

    private void persistQuietly() {
        if (counting == null || location == null) {
            return;
        }
        synchronized (scanLock) {
            location.epcs.clear();
            location.epcs.addAll(acceptedEpcs);
        }
        counting.refreshStatusAfterScanChange();
        RfidHandheldCountingStore.upsert(this, counting);
    }

    private void updateRfidUi() {
        boolean connected = rfidManager.isConnected();
        dotRfidStatus.setBackgroundResource(connected ? R.drawable.bg_dot_green : R.drawable.bg_dot_red);
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
