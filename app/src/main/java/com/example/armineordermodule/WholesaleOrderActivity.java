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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import android.content.Intent;

import androidx.activity.OnBackPressedCallback;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import kotlin.Unit;

/**
 * Toptan siparis urun okutma ekrani (akisin 3. adimi).
 * Depo + aciklama onceki adimda ({@link WholesaleOrderSetupActivity}) secilir;
 * bu ekranda yalnizca ham EPC okutma (parse / miktar yok) ve kaydet islemleri vardir.
 */
public class WholesaleOrderActivity extends AppCompatActivity {

    public static final String EXTRA_CUSTOMER = "extra_customer";
    public static final String EXTRA_ORDER_LOCAL_ID = "extra_order_local_id";
    public static final String EXTRA_OFFICE_CODE = "extra_office_code";
    public static final String EXTRA_WAREHOUSE_CODE = "extra_warehouse_code";
    public static final String EXTRA_WAREHOUSE_DESCRIPTION = "extra_warehouse_description";
    public static final String EXTRA_DESCRIPTION = "extra_description";

    private static final String TRIGGER_LOG_TAG = "RFID_TRIGGER_KEY";
    private static final Set<Integer> TRIGGER_KEY_CODES =
            new LinkedHashSet<>(Arrays.asList(305, 619, 621));
    /** Ayni anda coklu cakisan etiket icin tek uyari penceresi. */
    private static final long DUPLICATE_WARNING_THROTTLE_MS = 2500L;

    private WholesaleOrder order;
    private RfidManager rfidManager;
    private WholesaleOrderLineAdapter lineAdapter;

    private TextView tvOrderCustomerName;
    private View dotRfidStatus;
    private TextView tvScanHint;
    private TextView tvLinesSummary;
    private TextView tvLinesEmptyHint;
    private TextView tvOrderError;
    private TextView tvTotalTagCount;
    private FloatingActionButton fabOrderActions;
    private View rowCancelBarcode;

    private final Object scanLock = new Object();
    /** Siparise kabul edilmis tum EPC'ler (tekrar okunamaz; silinince serbest kalir). */
    private final Set<String> acceptedEpcs = new HashSet<>();
    private boolean isScanning = false;
    private boolean fabExpanded = false;
    private long lastDuplicateWarningAtMs = 0L;

    /** Iptal Barkod diyaloğu açıkken RFID okuması ana listeye eklemek yerine pending'e gider. */
    private boolean cancelScanMode = false;
    private AlertDialog cancelScanDialog;
    private TextView tvCancelEpcStatus;
    private TextView tvCancelEpcEmpty;
    private RfidHandheldCountingEpcAdapter cancelEpcAdapter;
    private final LinkedHashSet<String> pendingCancelEpcs = new LinkedHashSet<>();

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
        renderOrder();
        updateRfidUi();
        updateTotalTagCountUi();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                requestLeaveScanScreen();
            }
        });
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

    // --- Fiziksel tetik tusu ---
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.d(TRIGGER_LOG_TAG, "keyCode=" + keyCode
                + " repeatCount=" + (event != null ? event.getRepeatCount() : -1));
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
        String existingId = getIntent() != null ? getIntent().getStringExtra(EXTRA_ORDER_LOCAL_ID) : null;
        if (!TextUtils.isEmpty(existingId)) {
            order = WholesaleOrderStore.findById(this, existingId);
            if (order == null) {
                return false;
            }
            if (order.isTransferred()) {
                Toast.makeText(this, R.string.wholesale_order_sent_locked, Toast.LENGTH_LONG).show();
                return false;
            }
            normalizeOrderLinesToEpcs();
            restoreAcceptedEpcsFromOrder();
            return true;
        }

        Object customerExtra = getIntent() != null ? getIntent().getSerializableExtra(EXTRA_CUSTOMER) : null;
        if (!(customerExtra instanceof CustomerItem)) {
            return false;
        }
        CustomerItem customer = (CustomerItem) customerExtra;

        String officeCode = getIntent().getStringExtra(EXTRA_OFFICE_CODE);
        String warehouseCode = getIntent().getStringExtra(EXTRA_WAREHOUSE_CODE);
        String warehouseDescription = getIntent().getStringExtra(EXTRA_WAREHOUSE_DESCRIPTION);
        String description = getIntent().getStringExtra(EXTRA_DESCRIPTION);
        if (TextUtils.isEmpty(officeCode) || TextUtils.isEmpty(warehouseCode)) {
            return false;
        }

        order = WholesaleOrder.createNew();
        order.customerCode = customer.currAccCode;
        order.customerDescription = customer.displayName();
        order.companyCode = AuthPreferences.getCompanyCode(this);
        order.orderDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date());
        order.officeCode = officeCode;
        order.warehouseCode = warehouseCode;
        order.warehouseDescription = warehouseDescription != null ? warehouseDescription : "";
        order.description = description != null ? description : "";
        return true;
    }

    /**
     * Eski barkod+miktar satırlarını ham EPC satırlarına açar (geriye uyumluluk).
     * Her satir = tek EPC; {@code line.barcode} alaninda ham EPC tutulur.
     */
    private void normalizeOrderLinesToEpcs() {
        if (order == null || order.lines.isEmpty()) {
            return;
        }
        ArrayList<WholesaleOrderLine> expanded = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (WholesaleOrderLine line : order.lines) {
            if (line.epcs != null && !line.epcs.isEmpty()) {
                for (String epcRaw : line.epcs) {
                    String epc = epcRaw != null ? epcRaw.trim() : "";
                    if (TextUtils.isEmpty(epc) || !seen.add(epc)) {
                        continue;
                    }
                    WholesaleOrderLine created = new WholesaleOrderLine(epc, 1);
                    created.epcs.add(epc);
                    expanded.add(created);
                }
            } else if (!TextUtils.isEmpty(line.barcode) && seen.add(line.barcode.trim())) {
                String epc = line.barcode.trim();
                WholesaleOrderLine created = new WholesaleOrderLine(epc, 1);
                created.epcs.add(epc);
                expanded.add(created);
            }
        }
        order.lines.clear();
        order.lines.addAll(expanded);
    }

    private void restoreAcceptedEpcsFromOrder() {
        synchronized (scanLock) {
            acceptedEpcs.clear();
            for (WholesaleOrderLine line : order.lines) {
                String epc = !TextUtils.isEmpty(line.barcode) ? line.barcode.trim() : "";
                if (!TextUtils.isEmpty(epc)) {
                    acceptedEpcs.add(epc);
                }
                for (String stored : line.epcs) {
                    if (!TextUtils.isEmpty(stored)) {
                        acceptedEpcs.add(stored.trim());
                    }
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
        rowCancelBarcode = findViewById(R.id.rowCancelBarcode);
        FloatingActionButton fabCancelBarcode = findViewById(R.id.fabCancelBarcode);
        MaterialButton btnGoToSummary = findViewById(R.id.btnGoToSummary);

        lineAdapter = new WholesaleOrderLineAdapter();
        rvOrderLines.setLayoutManager(new LinearLayoutManager(this));
        rvOrderLines.setAdapter(lineAdapter);

        btnBack.setOnClickListener(v -> requestLeaveScanScreen());
        btnAddManualLine.setOnClickListener(v -> showAddManualLineDialog());
        fabOrderActions.setOnClickListener(v -> setFabMenuExpanded(!fabExpanded));
        fabCancelBarcode.setOnClickListener(v -> {
            setFabMenuExpanded(false);
            showCancelBarcodeDialog();
        });
        btnGoToSummary.setOnClickListener(v -> goToSummary());
    }

    private void renderOrder() {
        tvOrderCustomerName.setText(TextUtils.isEmpty(order.customerDescription)
                ? order.customerCode : order.customerDescription);
        renderLines();
    }

    // --- RFID tarama (yalnizca fiziksel tetik) ---

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
                showError(getString(R.string.wholesale_order_rfid_connect_failed));
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

    /**
     * Okunan her yeni EPC aninda listeye yansir (parse / miktar yok).
     * Donus: {@code false} = surekli SDK ses dongusu kapali.
     */
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
        }

        WholesaleOrder otherOrder = WholesaleOrderStore.findOtherOrderContainingEpc(
                this, normalized, order != null ? order.localId : null);
        if (otherOrder != null) {
            warnEpcAlreadyInOtherOrder(otherOrder);
            return false;
        }

        synchronized (scanLock) {
            if (acceptedEpcs.contains(normalized)) {
                return false;
            }
            acceptedEpcs.add(normalized);
        }

        runOnUiThread(() -> {
            if (cancelScanMode) {
                synchronized (scanLock) {
                    acceptedEpcs.remove(normalized);
                }
                return;
            }
            addEpcLine(normalized);
            renderLines();
            updateTotalTagCountUi();
            clearError();
        });
        RfidConnectionManager.playBeep();
        return false;
    }

    private void warnEpcAlreadyInOtherOrder(WholesaleOrder otherOrder) {
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

        String customer = !TextUtils.isEmpty(otherOrder.customerDescription)
                ? otherOrder.customerDescription
                : otherOrder.customerCode;
        if (TextUtils.isEmpty(customer)) {
            customer = "-";
        }
        final String message = getString(R.string.wholesale_order_epc_in_other_order, customer);
        runOnUiThread(() -> {
            showError(message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    private void stopScanning() {
        RfidConnectionManager.stopInventoryAsync(() -> {
            isScanning = false;
            runOnUiThread(() -> tvScanHint.setText(R.string.wholesale_order_scan_hint));
            return Unit.INSTANCE;
        });
    }

    private void addEpcLine(String epc) {
        if (TextUtils.isEmpty(epc) || order == null) {
            return;
        }
        for (WholesaleOrderLine line : order.lines) {
            if (epc.equals(line.barcode)) {
                return;
            }
        }
        WholesaleOrderLine created = new WholesaleOrderLine(epc, 1);
        created.epcs.add(epc);
        order.lines.add(created);
    }

    private void removeEpcLine(String epc) {
        if (TextUtils.isEmpty(epc) || order == null) {
            return;
        }
        for (int i = 0; i < order.lines.size(); i++) {
            WholesaleOrderLine line = order.lines.get(i);
            if (epc.equals(line.barcode) || line.epcs.contains(epc)) {
                order.lines.remove(i);
                return;
            }
        }
    }

    private void showAddManualLineDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_order_line, null);
        TextInputEditText etEpc = dialogView.findViewById(R.id.etManualEpc);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.wholesale_order_add_manual_line)
                .setView(dialogView)
                .setPositiveButton(R.string.wholesale_order_manual_add_confirm, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String epc = etEpc.getText() != null ? etEpc.getText().toString().trim() : "";
                if (TextUtils.isEmpty(epc)) {
                    etEpc.setError(getString(R.string.wholesale_order_manual_epc_empty));
                    etEpc.requestFocus();
                    return;
                }

                boolean alreadyInThisOrder;
                synchronized (scanLock) {
                    alreadyInThisOrder = acceptedEpcs.contains(epc);
                }
                if (alreadyInThisOrder) {
                    RfidConnectionManager.playDuplicateWarning();
                    showError(getString(R.string.wholesale_order_manual_epc_duplicate));
                    Toast.makeText(this, R.string.wholesale_order_manual_epc_duplicate, Toast.LENGTH_LONG).show();
                    etEpc.setError(getString(R.string.wholesale_order_manual_epc_duplicate));
                    etEpc.requestFocus();
                    etEpc.selectAll();
                    return;
                }

                WholesaleOrder otherOrder = WholesaleOrderStore.findOtherOrderContainingEpc(
                        this, epc, order != null ? order.localId : null);
                if (otherOrder != null) {
                    warnEpcAlreadyInOtherOrder(otherOrder);
                    etEpc.setError(getString(R.string.wholesale_order_epc_in_other_order,
                            !TextUtils.isEmpty(otherOrder.customerDescription)
                                    ? otherOrder.customerDescription
                                    : otherOrder.customerCode));
                    etEpc.requestFocus();
                    etEpc.selectAll();
                    return;
                }

                synchronized (scanLock) {
                    acceptedEpcs.add(epc);
                }

                addEpcLine(epc);
                renderLines();
                updateTotalTagCountUi();
                clearError();
                dialog.dismiss();
            });
        });

        dialog.show();
        etEpc.requestFocus();
    }

    // --- Iptal Barkod (RFID sayim ile ayni mantik) ---

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
            inMainList = acceptedEpcs.contains(epc);
        }
        if (!inMainList) {
            for (WholesaleOrderLine line : order.lines) {
                if (epc.equals(line.barcode) || line.epcs.contains(epc)) {
                    inMainList = true;
                    break;
                }
            }
        }
        if (!inMainList) {
            showCancelScanStatus(getString(R.string.wholesale_order_delete_not_found));
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
                .setTitle(R.string.wholesale_order_cancel_barcode)
                .setView(dialogView)
                .setPositiveButton(R.string.wholesale_order_cancel_barcode_confirm, null)
                .setNegativeButton(R.string.wholesale_order_cancel_barcode_back,
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
            showCancelScanStatus(getString(R.string.wholesale_order_cancel_barcode_delete_empty));
            return;
        }

        int removedCount = 0;
        synchronized (scanLock) {
            for (String epc : pendingCancelEpcs) {
                if (acceptedEpcs.remove(epc)) {
                    removeEpcLine(epc);
                    removedCount++;
                } else {
                    // accepted set'te yoksa yine satirdan dusurmeyi dene
                    int before = order.lines.size();
                    removeEpcLine(epc);
                    if (order.lines.size() < before) {
                        removedCount++;
                    }
                }
            }
        }

        if (removedCount <= 0) {
            showCancelScanStatus(getString(R.string.wholesale_order_delete_not_found));
            return;
        }

        pendingCancelEpcs.clear();
        renderLines();
        updateTotalTagCountUi();
        Toast.makeText(this,
                getString(R.string.wholesale_order_delete_success) + " (" + removedCount + ")",
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
            Toast.makeText(this, R.string.wholesale_order_delete_cancelled, Toast.LENGTH_SHORT).show();
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

    /** Kaydetmeden once her satirin EPC listesini kimlikle senkronlar. */
    private void syncEpcsIntoOrderLines() {
        for (WholesaleOrderLine line : order.lines) {
            line.epcs.clear();
            if (!TextUtils.isEmpty(line.barcode)) {
                line.epcs.add(line.barcode);
            }
            line.qty = 1;
        }
    }

    private void renderLines() {
        lineAdapter.submitList(order.lines);
        tvLinesEmptyHint.setVisibility(order.lines.isEmpty() ? View.VISIBLE : View.GONE);
        tvLinesSummary.setText(getString(R.string.wholesale_order_lines_summary, order.totalLineCount()));
    }

    private void updateTotalTagCountUi() {
        int total;
        synchronized (scanLock) {
            total = acceptedEpcs.size();
        }
        int display = Math.max(total, order != null ? order.totalLineCount() : 0);
        tvTotalTagCount.setText(getString(R.string.wholesale_order_total_tags_footer, display));
    }

    private void updateRfidUi() {
        boolean connected = rfidManager.isConnected();
        dotRfidStatus.setBackgroundResource(connected ? R.drawable.bg_dot_green : R.drawable.bg_dot_red);
        dotRfidStatus.setContentDescription(getString(
                connected ? R.string.main_rfid_status_connected : R.string.main_rfid_status_disconnected));
    }

    // --- FAB (Iptal Barkod) ---

    private void setFabMenuExpanded(boolean expanded) {
        fabExpanded = expanded;
        fabOrderActions.animate().rotation(expanded ? 45f : 0f).setDuration(150).start();
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

    /** Okutulan EPC'leri kaydeder ve urun ozet ekranina gider. */
    private void goToSummary() {
        setFabMenuExpanded(false);
        clearError();
        if (TextUtils.isEmpty(order.warehouseCode) || TextUtils.isEmpty(order.officeCode)) {
            showError(getString(R.string.wholesale_order_warehouse_required));
            return;
        }
        if (order.lines.isEmpty()) {
            showError(getString(R.string.wholesale_order_lines_required));
            return;
        }
        if (isScanning) {
            RfidConnectionManager.stopInventoryAsync(() -> {
                isScanning = false;
                runOnUiThread(this::persistAndOpenSummary);
                return Unit.INSTANCE;
            });
            return;
        }
        persistAndOpenSummary();
    }

    /**
     * Okutulmus EPC varken geri cikis: kaydetmeden ayrilmayi onaylatir.
     * (Kaydet ozet ekranindadir; burada yalnizca uyarilir.)
     */
    private void requestLeaveScanScreen() {
        if (hasScannedEpcs()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.wholesale_order_unsaved_title)
                    .setMessage(R.string.wholesale_order_unsaved_message)
                    .setPositiveButton(R.string.wholesale_order_unsaved_stay, null)
                    .setNegativeButton(R.string.wholesale_order_unsaved_leave,
                            (dialog, which) -> leaveScanScreen())
                    .show();
            return;
        }
        leaveScanScreen();
    }

    private boolean hasScannedEpcs() {
        return order != null && !order.lines.isEmpty();
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

    private void persistAndOpenSummary() {
        syncEpcsIntoOrderLines();
        order.status = WholesaleOrder.STATUS_DRAFT;
        WholesaleOrderStore.upsert(this, order);

        Intent intent = new Intent(this, WholesaleOrderSummaryActivity.class);
        intent.putExtra(WholesaleOrderSummaryActivity.EXTRA_ORDER_LOCAL_ID, order.localId);
        startActivity(intent);
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
