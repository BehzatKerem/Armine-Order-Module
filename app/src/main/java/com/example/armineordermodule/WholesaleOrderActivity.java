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

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import kotlin.Unit;

/**
 * Toptan siparis urun okutma ekrani (akisin 3. adimi).
 * Depo + aciklama onceki adimda ({@link WholesaleOrderSetupActivity}) secilir;
 * bu ekranda yalnizca RFID / manuel satır ekleme ve kaydet islemleri vardir.
 * Bkz. docs/TOPTAN_SIPARIS.md.
 */
public class WholesaleOrderActivity extends AppCompatActivity {

    public static final String EXTRA_CUSTOMER = "extra_customer";
    public static final String EXTRA_ORDER_LOCAL_ID = "extra_order_local_id";
    public static final String EXTRA_OFFICE_CODE = "extra_office_code";
    public static final String EXTRA_WAREHOUSE_CODE = "extra_warehouse_code";
    public static final String EXTRA_WAREHOUSE_DESCRIPTION = "extra_warehouse_description";
    public static final String EXTRA_DESCRIPTION = "extra_description";

    private static final String TAG = "WholesaleOrderActivity";
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
    private View rowSave;
    private View rowSaveAndSend;

    private final Object scanLock = new Object();
    /** Siparise kabul edilmis tum EPC'ler (tekrar okunamaz; satir silinince serbest kalir). */
    private final Set<String> acceptedEpcs = new HashSet<>();
    /** Barkod -> o satira ait EPC'ler (silmede serbest birakmak icin). */
    private final Map<String, Set<String>> epcsByBarcode = new HashMap<>();
    private boolean isScanning = false;
    private boolean fabExpanded = false;
    private long lastDuplicateWarningAtMs = 0L;

    /** Silme diyaloğu açıkken RFID okuması ürüne eklemek yerine silme onayı için kullanılır. */
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
        renderOrder();
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
    protected void onDestroy() {
        if (isScanning) {
            RfidConnectionManager.stopInventoryAsync(null);
        }
        super.onDestroy();
    }

    // --- Fiziksel tetik tusu ---
    // RFID Tarama (Test) ekraniyla (RfidScanActivity) ayni mantik: bkz.
    // docs/RFID_SDK_ENTEGRASYON.md Bolum 6. Iki mod (Sunucu Ayarlari > "Tetik Bas-Cek"):
    // - Toggle (varsayilan): her basis taramayi ac/kapa yapar.
    // - Bas-Cek / momentary: tus basili tutulunca taramaya baslar, birakinca durdurup gruplar.
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
        String existingId = getIntent() != null ? getIntent().getStringExtra(EXTRA_ORDER_LOCAL_ID) : null;
        if (!TextUtils.isEmpty(existingId)) {
            order = WholesaleOrderStore.findById(this, existingId);
            if (order == null) {
                return false;
            }
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

    /** Kayitli siparisteki EPC'leri tekrar okunmasin diye accepted set'e yukler. */
    private void restoreAcceptedEpcsFromOrder() {
        synchronized (scanLock) {
            acceptedEpcs.clear();
            epcsByBarcode.clear();
            for (WholesaleOrderLine line : order.lines) {
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
        FloatingActionButton fabSave = findViewById(R.id.fabSave);
        FloatingActionButton fabSaveAndSend = findViewById(R.id.fabSaveAndSend);

        lineAdapter = new WholesaleOrderLineAdapter(this::showDeleteByScanDialog);
        rvOrderLines.setLayoutManager(new LinearLayoutManager(this));
        rvOrderLines.setAdapter(lineAdapter);

        btnBack.setOnClickListener(v -> finish());
        btnAddManualLine.setOnClickListener(v -> showAddManualLineDialog());
        fabOrderActions.setOnClickListener(v -> setFabMenuExpanded(!fabExpanded));
        fabSave.setOnClickListener(v -> saveDraft());
        fabSaveAndSend.setOnClickListener(v -> saveAndSend());
    }

    private void renderOrder() {
        tvOrderCustomerName.setText(TextUtils.isEmpty(order.customerDescription)
                ? order.customerCode : order.customerDescription);
        renderLines();
    }

    // --- RFID tarama (yalnizca fiziksel tetik) ---

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
                showError(getString(R.string.wholesale_order_rfid_connect_failed));
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
                                    getString(R.string.wholesale_order_delete_invalid_tag)));
                        }
                        // 868 disi / gecersiz — yeni etiket sesi calmasin (tekrar sesi)
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

    /**
     * Okunan her yeni EPC aninda satira yansir (tetik birakilmasini beklemez).
     * Donus: {@code false} = surekli SDK ses dongusu kapali.
     * Yeni urun bip'i ayri [RfidConnectionManager.playBeep] ile calinir
     * (SDK `beginSound` tek basina ses uretmez; SoundPool gerekir).
     * Baska siparisteki EPC yuksek sesli uyarilir ve eklenmez.
     */
    private boolean handleLiveTagRead(String epc) {
        synchronized (scanLock) {
            if (acceptedEpcs.contains(epc)) {
                // false: SDK surekli ses dongusunu ACMA (tekrar okumada takili ses olmasin)
                return false;
            }
        }

        WholesaleOrder otherOrder = WholesaleOrderStore.findOtherOrderContainingEpc(
                this, epc, order != null ? order.localId : null);
        if (otherOrder != null) {
            warnEpcAlreadyInOtherOrder(otherOrder);
            // false: cakisan etiketlerde surekli SDK sesi acilmasin
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
        // Tek bip: SDK beginSound bayrak degil, SoundPool.playSound gerekir.
        RfidConnectionManager.playBeep();
        // false => surekli ses dongusunu kapali tut (ayri playBeep yeterli)
        return false;
    }

    private void warnEpcAlreadyInOtherOrder(WholesaleOrder otherOrder) {
        long now = System.currentTimeMillis();
        boolean shouldAlert;
        synchronized (scanLock) {
            // EPC farketmez: ayni tarama demetinde tek uyari
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

    /**
     * Silme modunda okunan EPC, kutu uzerinden islenir (tek EPC silme).
     */
    private boolean handleDeleteScanTag(String epc) {
        runOnUiThread(() -> {
            if (etDeleteEpc != null) {
                etDeleteEpc.setText(epc);
            }
            tryDeleteByEpc(epc);
        });
        return false;
    }

    private void addOrIncrementLine(String barcode, int qty) {
        addOrIncrementLine(barcode, qty, null);
    }

    private void addOrIncrementLine(String barcode, int qty, String epc) {
        for (WholesaleOrderLine line : order.lines) {
            if (line.barcode.equals(barcode)) {
                line.qty += qty;
                if (!TextUtils.isEmpty(epc) && !line.epcs.contains(epc)) {
                    line.epcs.add(epc);
                }
                return;
            }
        }
        WholesaleOrderLine created = new WholesaleOrderLine(barcode, qty);
        if (!TextUtils.isEmpty(epc)) {
            created.epcs.add(epc);
        }
        order.lines.add(created);
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
                if (!EpcBarcodeGrouping.isValidManualEpc(epc)) {
                    etEpc.setError(getString(R.string.wholesale_order_manual_epc_invalid));
                    showError(getString(R.string.wholesale_order_manual_epc_invalid));
                    Toast.makeText(this, R.string.wholesale_order_manual_epc_invalid, Toast.LENGTH_LONG).show();
                    etEpc.requestFocus();
                    etEpc.selectAll();
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

                String barcode = EpcBarcodeGrouping.extractBarcode(epc);
                if (barcode == null) {
                    etEpc.setError(getString(R.string.wholesale_order_manual_epc_invalid));
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

    private void showDeleteByScanDialog(WholesaleOrderLine line) {
        if (line == null || TextUtils.isEmpty(line.barcode)) {
            return;
        }
        if (deleteScanDialog != null && deleteScanDialog.isShowing()) {
            return;
        }

        // Normal urun ekleme taramasi aciksa once durdur.
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
        tvTarget.setText(getString(R.string.wholesale_order_delete_scan_target, barcode));
        tvDeleteScanStatus.setVisibility(View.GONE);

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
                .setTitle(R.string.wholesale_order_delete_scan_title)
                .setView(dialogView)
                .setPositiveButton(R.string.wholesale_order_delete_confirm, null)
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
            showDeleteScanStatus(getString(R.string.wholesale_order_delete_empty));
            return;
        }
        tryDeleteByEpc(raw);
    }

    /**
     * Etiketteki karekod / RFID EPC degeri ile silme.
     * Sadece okutulan o EPC dusurulur (miktar -1); satir ancak miktar 0 olunca kalkar.
     */
    private void tryDeleteByEpc(String epcRaw) {
        if (!deleteScanMode) {
            return;
        }

        String epc = epcRaw != null ? epcRaw.trim() : "";
        if (TextUtils.isEmpty(epc)) {
            showDeleteScanStatus(getString(R.string.wholesale_order_delete_empty));
            return;
        }

        String barcode = EpcBarcodeGrouping.extractBarcode(epc);
        if (barcode == null) {
            showDeleteScanStatus(getString(R.string.wholesale_order_delete_invalid_tag));
            return;
        }

        if (!barcode.equals(deleteTargetBarcode)) {
            showDeleteScanStatus(getString(R.string.wholesale_order_delete_wrong_product));
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
            showDeleteScanStatus(getString(R.string.wholesale_order_delete_epc_not_in_order));
            if (etDeleteEpc != null) {
                etDeleteEpc.selectAll();
            }
            return;
        }

        decrementLineQty(barcode, 1, epc);
        renderLines();
        updateTotalTagCountUi();
        Toast.makeText(this, R.string.wholesale_order_delete_success, Toast.LENGTH_SHORT).show();

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

    /** Belirli barkod satirinin miktarini azaltir; ilgili EPC'yi satirdan dusurur. */
    private void decrementLineQty(String barcode, int qty, String epc) {
        if (TextUtils.isEmpty(barcode) || order == null || qty <= 0) {
            return;
        }
        for (int i = 0; i < order.lines.size(); i++) {
            WholesaleOrderLine line = order.lines.get(i);
            if (!barcode.equals(line.barcode)) {
                continue;
            }
            if (!TextUtils.isEmpty(epc)) {
                line.epcs.remove(epc);
            }
            line.qty -= qty;
            if (line.qty <= 0) {
                order.lines.remove(i);
            }
            return;
        }
    }

    /** Kaydetmeden once runtime EPC set'ini satir listesine senkronlar. */
    private void syncEpcsIntoOrderLines() {
        synchronized (scanLock) {
            for (WholesaleOrderLine line : order.lines) {
                line.epcs.clear();
                Set<String> set = epcsByBarcode.get(line.barcode);
                if (set != null) {
                    line.epcs.addAll(set);
                }
            }
        }
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
            Toast.makeText(this, R.string.wholesale_order_delete_cancelled, Toast.LENGTH_SHORT).show();
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
        lineAdapter.submitList(order.lines);
        tvLinesEmptyHint.setVisibility(order.lines.isEmpty() ? View.VISIBLE : View.GONE);
        tvLinesSummary.setText(getString(
                R.string.wholesale_order_lines_summary, order.totalLineCount(), order.totalQty()));
    }

    private void updateTotalTagCountUi() {
        int total;
        synchronized (scanLock) {
            total = acceptedEpcs.size();
        }
        // Manuel satirlardaki miktar RFID EPC'si olmadan eklenebilir; footer RFID benzersiz sayisini gosterir.
        // Manuel eklemeler icin totalQty ile max alarak tutarli gosterim:
        int lineQty = order != null ? order.totalQty() : 0;
        int display = Math.max(total, lineQty);
        tvTotalTagCount.setText(getString(R.string.wholesale_order_total_tags_footer, display));
    }

    private void updateRfidUi() {
        boolean connected = rfidManager.isConnected();
        dotRfidStatus.setBackgroundResource(connected ? R.drawable.bg_dot_green : R.drawable.bg_dot_red);
        dotRfidStatus.setContentDescription(getString(
                connected ? R.string.main_rfid_status_connected : R.string.main_rfid_status_disconnected));
    }

    // --- FAB (Kaydet / Kaydet ve Gonder) ---

    private void setFabMenuExpanded(boolean expanded) {
        fabExpanded = expanded;
        fabOrderActions.animate().rotation(expanded ? 45f : 0f).setDuration(150).start();
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

    // --- Kaydet / Kaydet ve Gonder ---

    private void saveDraft() {
        setFabMenuExpanded(false);
        if (!validateOrder()) {
            return;
        }
        syncEpcsIntoOrderLines();
        order.status = WholesaleOrder.STATUS_DRAFT;
        WholesaleOrderStore.upsert(this, order);
        Toast.makeText(this, R.string.wholesale_order_draft_saved_message, Toast.LENGTH_LONG).show();
        finish();
    }

    private void saveAndSend() {
        setFabMenuExpanded(false);
        if (!validateOrder()) {
            return;
        }

        syncEpcsIntoOrderLines();
        JSONObject apiJson;
        try {
            apiJson = order.toApiJson();
        } catch (JSONException e) {
            showError(getString(R.string.wholesale_order_json_error));
            return;
        }
        Log.d(TAG, "Siparis JSON (gonderim bekliyor): " + apiJson);

        order.status = WholesaleOrder.STATUS_PENDING_SEND;
        WholesaleOrderStore.upsert(this, order);
        Toast.makeText(this, R.string.wholesale_order_pending_send_saved_message, Toast.LENGTH_LONG).show();
        finish();
    }

    private boolean validateOrder() {
        clearError();
        if (TextUtils.isEmpty(order.warehouseCode) || TextUtils.isEmpty(order.officeCode)) {
            showError(getString(R.string.wholesale_order_warehouse_required));
            return false;
        }
        if (order.lines.isEmpty()) {
            showError(getString(R.string.wholesale_order_lines_required));
            return false;
        }
        return true;
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
