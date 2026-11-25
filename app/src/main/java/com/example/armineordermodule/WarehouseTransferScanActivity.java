package com.example.armineordermodule;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
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
import com.example.armineordermodule.rfid.RfidTriggerController;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;


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
    private static final long DUPLICATE_WARNING_THROTTLE_MS = 2500L;
    private long lastDuplicateWarningAtMs;
    private RfidTriggerController rfidTrigger;

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
    private String lastSendError = "";
    private long lastSendAttemptMillis;
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
        rfidTrigger = createRfidTrigger();

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
        // Satirdaki cop kutusu, FAB menusundeki "Iptal Barkod" ile ayni modali acar.
        epcAdapter.setOnLineDeleteClickListener(epc -> openCancelBarcodeFromLine());

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
        if (!TextUtils.isEmpty(lastSendError)) {
            Toast.makeText(this, R.string.warehouse_transfer_failed_locked, Toast.LENGTH_LONG).show();
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
        lastSendError = transfer.lastSendError != null ? transfer.lastSendError : "";
        lastSendAttemptMillis = transfer.lastSendAttemptMillis;
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
        transfer.lastSendError = lastSendError;
        transfer.lastSendAttemptMillis = lastSendAttemptMillis;
        synchronized (scanLock) {
            transfer.epcs.addAll(epcs);
        }
        return transfer;
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateRfidUi();
        if (RfidPreferences.isRfidEnabled(this) && !RfidConnectionManager.isConnected()) {
            RfidConnectionManager.autoReconnectIfNeededAsync(result -> {
                updateRfidUi();
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

    // --- Fiziksel tetik (cihaza gore davranis RfidTriggerController icinde) ---
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return rfidTrigger.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return rfidTrigger.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event);
    }

    private RfidTriggerController createRfidTrigger() {
        return new RfidTriggerController(this, new RfidTriggerController.Host() {
            @Override
            public boolean isScanning() {
                return isScanning;
            }

            @Override
            public void startScanning() {
                WarehouseTransferScanActivity.this.startScanning();
            }

            @Override
            public void stopScanning() {
                WarehouseTransferScanActivity.this.stopScanning();
            }

            @Override
            public boolean isScanLatched() {
                // Iptal Barkod diyalogunda da tetik ana ekrandaki gibi calisir.
                return false;
            }
        });
    }

    /**
     * Gonderim surerken ya da gonderim hata aldiktan sonra transfer degistirilemez.
     * Ilk istek sunucuya ulasmis olabilir; tekrar gonderimde alreadySent doner ve sonradan
     * eklenen urunler gitmez. Bu yuzden hata almis transfer sadece tekrar aktarilir.
     */
    private boolean isEditLocked() {
        return sending || !TextUtils.isEmpty(lastSendError);
    }

    private void startScanning() {
        if (isEditLocked()) {
            showError(getString(R.string.warehouse_transfer_failed_locked));
            return;
        }
        RfidConnectionManager.startInventoryAsync(
                tag -> {
                    if (cancelScanMode) {
                        handleCancelScanTag(tag.getEpcId());
                        return;
                    }
                    handleLiveTagRead(tag.getEpcId());
                },
                started -> {
                    if (started) {
                        isScanning = true;
                    } else {
                        showError(getString(R.string.rfid_start_failed));
                    }
                });
    }

    private boolean handleLiveTagRead(String epc) {
        if (isEditLocked()) {
            return false;
        }
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
            if (acceptedEpcs.contains(normalized)) {
                return false;
            }
        }

        WarehouseTransfer otherTransfer = WarehouseTransferStore.findOtherTransferContainingEpc(
                this, normalized, transferGuid);
        if (otherTransfer != null) {
            warnEpcAlreadyInOtherTransfer(otherTransfer);
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

    private String otherTransferEpcMessage(WarehouseTransfer otherTransfer) {
        if (!WarehouseTransferStore.isOwnedByCurrentUser(this, otherTransfer)) {
            return getString(R.string.warehouse_transfer_epc_in_other_user_transfer);
        }
        String route = getString(R.string.warehouse_transfer_scan_route,
                otherTransfer.sourceDescription, otherTransfer.targetDescription);
        return getString(R.string.warehouse_transfer_epc_in_other_transfer, route);
    }

    /** Okuyucu thread'inden cagrilir; surekli okumada uyari sesi/mesaji seyreltilir. */
    private void warnEpcAlreadyInOtherTransfer(WarehouseTransfer otherTransfer) {
        long now = System.currentTimeMillis();
        synchronized (scanLock) {
            if (now - lastDuplicateWarningAtMs < DUPLICATE_WARNING_THROTTLE_MS) {
                return;
            }
            lastDuplicateWarningAtMs = now;
        }
        RfidConnectionManager.playDuplicateWarning();
        final String message = otherTransferEpcMessage(otherTransfer);
        runOnUiThread(() -> {
            showError(message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    private void stopScanning() {
        RfidConnectionManager.stopInventoryAsync(() -> {
            isScanning = false;
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

    /** Satir sonundaki cop kutusu: hangi satir olursa olsun ayni Iptal Barkod modali. */
    private void openCancelBarcodeFromLine() {
        if (fabExpanded) {
            setFabMenuExpanded(false);
        }
        showCancelBarcodeDialog();
    }

    private void showCancelBarcodeDialog() {
        if (isEditLocked()) {
            showError(getString(R.string.warehouse_transfer_failed_locked));
            return;
        }
        if (cancelScanDialog != null && cancelScanDialog.isShowing()) {
            return;
        }
        cancelScanMode = true;
        pendingCancelEpcs.clear();
        if (isScanning) {
            RfidConnectionManager.stopInventoryAsync(() -> {
                isScanning = false;
                openCancelScanDialog();
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
        // Diyalog acikken tus olaylari Activity'ye degil diyalog penceresine gelir. BLD'de
        // tetik KeyEvent oldugu icin buradan tetik denetleyicisine aktarilir. Okuma diyalog
        // acilinca kendiliginden baslamaz; sadece tetikle baslar.
        cancelScanDialog.setOnKeyListener((dialog, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                return rfidTrigger.onKeyDown(keyCode, event);
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                return rfidTrigger.onKeyUp(keyCode, event);
            }
            return false;
        });
        cancelScanDialog.setOnShowListener(dialog ->
                cancelScanDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                        v -> confirmCancelDeletes()));
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
        if (isEditLocked()) {
            showError(getString(R.string.warehouse_transfer_failed_locked));
            return;
        }
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
        // Once cihaza kaydedilir: gonderim basarisiz olsa da okutmalar kaybolmaz.
        WarehouseTransfer transfer = saveLocalDraft();
        if (transfer == null) {
            return;
        }

        clearError();
        sending = true;
        Toast.makeText(this, R.string.warehouse_transfer_sending, Toast.LENGTH_SHORT).show();
        WarehouseTransferSender.send(this, transfer, new WarehouseTransferSender.Callback() {
            @Override
            public void onSuccess(WarehouseTransferCreateApiClient.CreateResult result,
                                  WarehouseTransfer saved) {
                sending = false;
                nebimSent = true;
                lastSendError = "";
                if (saved != null) {
                    headerId = saved.headerId;
                    lastSendAttemptMillis = saved.lastSendAttemptMillis;
                }
                if (isFinishing()) {
                    return;
                }
                String message = result != null && !TextUtils.isEmpty(result.message)
                        ? result.message
                        : getString(R.string.warehouse_transfer_sent_fallback);
                Toast.makeText(WarehouseTransferScanActivity.this, message, Toast.LENGTH_LONG).show();
                openTransferList();
            }

            @Override
            public void onError(String message, WarehouseTransfer saved) {
                sending = false;
                if (saved != null) {
                    lastSendError = saved.lastSendError;
                    lastSendAttemptMillis = saved.lastSendAttemptMillis;
                }
                if (isFinishing()) {
                    return;
                }
                showSendFailedDialog(message);
            }
        });
    }

    /**
     * Gonderim hatasi: transfer cihazda "gonderilemedi" olarak kayitli kalir.
     * Kullanici hemen tekrar deneyebilir ya da listeye donup sonra "Tekrar Aktar" yapar.
     * Transfer artik duzenlenemez (bkz. isEditLocked), bu yuzden ekranda kalma secenegi yok.
     */
    private void showSendFailedDialog(String message) {
        String reason = !TextUtils.isEmpty(message)
                ? message
                : getString(R.string.warehouse_transfer_send_failed);
        showError(reason);
        new AlertDialog.Builder(this)
                .setTitle(R.string.warehouse_transfer_send_failed_title)
                .setMessage(getString(R.string.warehouse_transfer_send_failed_saved_message, reason))
                .setPositiveButton(R.string.warehouse_transfer_retry_now, (dialog, which) -> saveAndSend())
                .setNegativeButton(R.string.warehouse_transfer_back_to_list,
                        (dialog, which) -> leaveScanScreen())
                .setCancelable(false)
                .show();
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
                openTransferList();
            });
            return;
        }
        openTransferList();
    }

    /**
     * Transfer listesine doner. Yeni transferde arada depo secim ekrani acik kalir;
     * CLEAR_TOP onu da kapatir, boylece geri tusu depo secimine degil listeye duser.
     */
    private void openTransferList() {
        Intent intent = new Intent(this, WarehouseTransferListActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
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
        boolean connected = RfidConnectionManager.isConnected();
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
