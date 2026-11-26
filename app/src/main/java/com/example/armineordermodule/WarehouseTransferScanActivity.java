package com.example.armineordermodule;

import android.content.Intent;
import android.content.res.ColorStateList;
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
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.armineordermodule.rfid.RfidConnectionManager;
import com.example.armineordermodule.rfid.RfidTriggerController;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


/**
 * Depo transferi urun okutma.
 *
 * Transfer EPC olarak tutulur ve gonderilir; ekranda ise EPC'ler barkoda (ilk 13 hane)
 * gore gruplanip urun adi + adet olarak gosterilir. Her yeni barkod icin kaynak depodaki
 * urun/stok bilgisi bir kez sorgulanir (GetProductByBarcode). Urun tanimli ama stogu yoksa
 * o barkodun tum EPC'leri listeden cikarilir, guclu uyari sesi calar ve uyari diyalogu acilir;
 * ayni barkodun sonraki okumalari da eklenmez.
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

    private WarehouseTransferProductAdapter productAdapter;
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
    private WarehouseTransferProductAdapter cancelProductAdapter;
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
    private SendingDialog sendingDialog;
    private final List<String> epcs = new ArrayList<>();
    private final Set<String> acceptedEpcs = new LinkedHashSet<>();
    private final LinkedHashSet<String> pendingCancelEpcs = new LinkedHashSet<>();
    private boolean isScanning = false;
    private boolean cancelScanMode = false;
    private boolean fabExpanded = false;

    /** Barkod -> urun/stok sorgu durumu. Sadece ana thread'de okunur/yazilir. */
    private final Map<String, ProductLookup> lookups = new HashMap<>();
    /**
     * Bulunamadigi ya da stokta olmadigi icin cikarilan barkod -> tekrar okutulunca gosterilecek
     * mesaj. scanLock ile korunur (okuyucu thread'i okur).
     */
    private final Map<String, String> rejectedBarcodes = new HashMap<>();
    private long lastOutOfStockWarningAtMs;
    private long lastUndefinedTagWarningAtMs;
    /** Kayitli transfer acilirken ayiklanan tanimsiz etiket sayisi. */
    private int removedUndefinedOnLoad;
    private AlertDialog outOfStockDialog;
    private final List<String> outOfStockDialogNames = new ArrayList<>();
    /** Bu ekranda listeden cikarilan urunler (en yenisi ustte). Sadece ana thread. */
    private final List<WarehouseTransferProductAdapter.Row> stockErrors = new ArrayList<>();
    private MaterialButton btnStockErrors;

    private static final class ProductLookup {
        WarehouseTransferProductAdapter.LookupState state;
        WarehouseProductInfo product;
        String errorMessage;

        ProductLookup(WarehouseTransferProductAdapter.LookupState state) {
            this.state = state;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_warehouse_transfer_scan);
        rfidTrigger = createRfidTrigger();
        sendingDialog = new SendingDialog(this);

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
        btnStockErrors = findViewById(R.id.btnStockErrors);
        btnStockErrors.setOnClickListener(v -> showStockErrorsDialog());

        productAdapter = new WarehouseTransferProductAdapter();
        rvEpcs.setLayoutManager(new LinearLayoutManager(this));
        rvEpcs.setAdapter(productAdapter);
        productAdapter.setListener(new WarehouseTransferProductAdapter.Listener() {
            @Override
            public void onDeleteClick(WarehouseTransferProductAdapter.Row row) {
                // Satirdaki cop kutusu, FAB menusundeki "Iptal Barkod" ile ayni modali acar.
                openCancelBarcodeFromLine();
            }

            @Override
            public void onRetryClick(WarehouseTransferProductAdapter.Row row) {
                if (row.barcode != null) {
                    requestLookup(row.barcode);
                    renderEpcs();
                }
            }
        });

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
        if (removedUndefinedOnLoad > 0) {
            String message = getString(R.string.warehouse_transfer_undefined_removed_on_load,
                    removedUndefinedOnLoad);
            showError(message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            removedUndefinedOnLoad = 0;
        }
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
                if (TextUtils.isEmpty(epc)) {
                    continue;
                }
                // Eski surumde kabul edilmis tanimsiz etiketler kayittan ayiklanir.
                if (!isDefinedTag(epc)) {
                    removedUndefinedOnLoad++;
                    continue;
                }
                if (acceptedEpcs.add(epc)) {
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
        if (retryFailedLookups()) {
            renderEpcs();
        }
        if (RfidPreferences.isRfidEnabled(this) && !RfidConnectionManager.isConnected()) {
            RfidConnectionManager.autoReconnectIfNeededAsync(result -> {
                updateRfidUi();
            });
        }
    }

    @Override
    protected void onDestroy() {
        dismissCancelScanDialog(false);
        if (outOfStockDialog != null && outOfStockDialog.isShowing()) {
            outOfStockDialog.dismiss();
        }
        if (sendingDialog != null) {
            sendingDialog.dismiss();
        }
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

        if (!isDefinedTag(normalized)) {
            warnUndefinedTag();
            return false;
        }

        String barcode = EpcBarcodeGrouping.extractBarcode(normalized);
        String rejectMessage;
        synchronized (scanLock) {
            if (acceptedEpcs.contains(normalized)) {
                return false;
            }
            rejectMessage = barcode != null ? rejectedBarcodes.get(barcode) : null;
        }
        if (rejectMessage != null) {
            warnRejectedBarcode(rejectMessage);
            return false;
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

    /**
     * Gecerli urun etiketi: 24 hane, tamami rakam, 868 ile baslar (barkod + seri no).
     * E280... gibi kodlanmamis fabrika etiketleri ve diger her sey tanimsizdir, listeye girmez.
     */
    private static boolean isDefinedTag(String epc) {
        if (epc == null || epc.length() != EpcBarcodeGrouping.EPC_LENGTH) {
            return false;
        }
        for (int i = 0; i < epc.length(); i++) {
            char c = epc.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return EpcBarcodeGrouping.extractBarcode(epc) != null;
    }

    /**
     * Okuyucu thread'inden: tanimsiz etiket okundu, eklenmedi. Depoda etrafta kodlanmamis
     * etiket cok olabilir; bu yuzden sessizdir ve sadece ekrandaki hata satiri seyrek guncellenir.
     */
    private void warnUndefinedTag() {
        long now = System.currentTimeMillis();
        synchronized (scanLock) {
            if (now - lastUndefinedTagWarningAtMs < DUPLICATE_WARNING_THROTTLE_MS) {
                return;
            }
            lastUndefinedTagWarningAtMs = now;
        }
        runOnUiThread(() -> showError(getString(R.string.warehouse_transfer_undefined_tag_rejected)));
    }

    /** Okuyucu thread'inden: daha once bulunamayan/stoksuz cikan barkod tekrar okundu. Seyreltilir. */
    private void warnRejectedBarcode(String message) {
        long now = System.currentTimeMillis();
        synchronized (scanLock) {
            if (now - lastOutOfStockWarningAtMs < DUPLICATE_WARNING_THROTTLE_MS) {
                return;
            }
            lastOutOfStockWarningAtMs = now;
        }
        RfidConnectionManager.playOutOfStockWarning();
        runOnUiThread(() -> {
            showError(message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    // --- Urun / stok sorgusu (ana thread) ---

    /** Listede olup sorgusu hic baslatilmamis barkodlar icin sorgu acar. */
    private void ensureLookups(Set<String> barcodes) {
        for (String barcode : barcodes) {
            if (!lookups.containsKey(barcode)) {
                requestLookup(barcode);
            }
        }
    }

    /** Hata almis sorgulari tekrar baslatir. Tekrar baslatilan varsa true. */
    private boolean retryFailedLookups() {
        List<String> failed = new ArrayList<>();
        for (Map.Entry<String, ProductLookup> entry : lookups.entrySet()) {
            if (entry.getValue().state == WarehouseTransferProductAdapter.LookupState.ERROR) {
                failed.add(entry.getKey());
            }
        }
        for (String barcode : failed) {
            requestLookup(barcode);
        }
        return !failed.isEmpty();
    }

    private void requestLookup(String barcode) {
        ProductLookup existing = lookups.get(barcode);
        if (existing != null && existing.state != WarehouseTransferProductAdapter.LookupState.ERROR) {
            return;
        }
        ProductLookup lookup = new ProductLookup(WarehouseTransferProductAdapter.LookupState.LOADING);
        lookups.put(barcode, lookup);

        String serverUrl = ServerPreferences.getServerUrl(this);
        String token = AuthPreferences.getToken(this);
        String databaseName = AuthPreferences.getDefaultDatabase(this);
        if (TextUtils.isEmpty(serverUrl)) {
            failLookup(lookup, getString(R.string.server_url_missing));
            return;
        }
        if (TextUtils.isEmpty(token) || !AuthPreferences.hasValidSession(this)) {
            failLookup(lookup, getString(R.string.warehouses_session_required));
            return;
        }
        if (TextUtils.isEmpty(databaseName)) {
            failLookup(lookup, getString(R.string.warehouses_database_missing));
            return;
        }

        // Stok, urunun ciktigi kaynak depoda kontrol edilir.
        WarehouseProductApiClient.getProductByBarcode(serverUrl, token, databaseName, barcode,
                sourceOffice, sourceWarehouse, new WarehouseProductApiClient.Callback() {
                    @Override
                    public void onSuccess(WarehouseProductInfo product) {
                        if (isDestroyed() || lookups.get(barcode) != lookup) {
                            return;
                        }
                        lookup.state = WarehouseTransferProductAdapter.LookupState.LOADED;
                        lookup.product = product;
                        lookup.errorMessage = null;
                        if (product.shouldRemoveFromTransfer()) {
                            removeRejectedProduct(barcode, product);
                        }
                        renderEpcs();
                    }

                    @Override
                    public void onError(String message) {
                        if (isDestroyed() || lookups.get(barcode) != lookup) {
                            return;
                        }
                        failLookup(lookup, message);
                        renderEpcs();
                    }
                });
    }

    private static void failLookup(ProductLookup lookup, String message) {
        lookup.state = WarehouseTransferProductAdapter.LookupState.ERROR;
        lookup.product = null;
        lookup.errorMessage = message;
    }

    /**
     * Bulunamayan ya da stoksuz urunun tum EPC'lerini listeden (ve Iptal Barkod modalindan)
     * cikarir, sonraki okumalarda eklenmemesi icin barkodu isaretler ve depocuyu uyarir.
     * Bulunamayan urunun adi yoktur; mesajda barkodu yazilir.
     */
    private void removeRejectedProduct(String barcode, WarehouseProductInfo product) {
        String removedMessage;
        String rejectMessage;
        if (!product.found) {
            removedMessage = getString(R.string.warehouse_transfer_not_found_message, barcode);
            rejectMessage = getString(R.string.warehouse_transfer_not_found_rejected, barcode);
        } else {
            String name = product.displayName();
            removedMessage = getString(R.string.warehouse_transfer_out_of_stock_message, name);
            rejectMessage = getString(R.string.warehouse_transfer_out_of_stock_rejected, name);
        }
        int removed = 0;
        synchronized (scanLock) {
            rejectedBarcodes.put(barcode, rejectMessage);
            Iterator<String> it = epcs.iterator();
            while (it.hasNext()) {
                String epc = it.next();
                if (barcode.equals(EpcBarcodeGrouping.extractBarcode(epc))) {
                    it.remove();
                    acceptedEpcs.remove(epc);
                    removed++;
                }
            }
        }
        if (removed <= 0) {
            return;
        }
        boolean cancelListChanged = false;
        Iterator<String> pending = pendingCancelEpcs.iterator();
        while (pending.hasNext()) {
            if (barcode.equals(EpcBarcodeGrouping.extractBarcode(pending.next()))) {
                pending.remove();
                cancelListChanged = true;
            }
        }
        if (cancelListChanged) {
            renderCancelList();
        }
        String qty = String.valueOf(removed);
        String reason = product.found
                ? getString(R.string.warehouse_transfer_stock_error_reason_out_of_stock, qty)
                : getString(R.string.warehouse_transfer_stock_error_reason_not_found, qty);
        stockErrors.add(0, new WarehouseTransferProductAdapter.Row(barcode, removed,
                WarehouseTransferProductAdapter.LookupState.REMOVED, product, reason));
        updateStockErrorsButton();
        showRemovedProductAlert(removedMessage);
    }

    /** Sayac: hata varsa buton kirmizi ve adetli, yoksa gri. */
    private void updateStockErrorsButton() {
        int count = stockErrors.size();
        int color = ContextCompat.getColor(this, count > 0 ? R.color.login_error : R.color.login_subtitle);
        btnStockErrors.setText(count > 0
                ? getString(R.string.warehouse_transfer_stock_errors_count, count)
                : getString(R.string.warehouse_transfer_stock_errors));
        btnStockErrors.setTextColor(color);
        btnStockErrors.setStrokeColor(ColorStateList.valueOf(color));
    }

    /** Sol alttaki "Stok Hatalari": listeden cikarilan urunler ve sebepleri. */
    private void showStockErrorsDialog() {
        if (fabExpanded) {
            setFabMenuExpanded(false);
        }
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_warehouse_transfer_stock_errors, null);
        RecyclerView rvStockErrors = dialogView.findViewById(R.id.rvStockErrors);
        TextView tvEmpty = dialogView.findViewById(R.id.tvStockErrorsEmpty);
        TextView tvMessage = dialogView.findViewById(R.id.tvStockErrorsMessage);
        boolean empty = stockErrors.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        tvMessage.setVisibility(empty ? View.GONE : View.VISIBLE);
        rvStockErrors.setVisibility(empty ? View.GONE : View.VISIBLE);

        WarehouseTransferProductAdapter adapter = new WarehouseTransferProductAdapter();
        rvStockErrors.setLayoutManager(new LinearLayoutManager(this));
        rvStockErrors.setAdapter(adapter);
        adapter.submitList(new ArrayList<>(stockErrors));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.warehouse_transfer_stock_errors)
                .setView(dialogView)
                .setPositiveButton(R.string.warehouse_transfer_stock_errors_close, null)
                .create();
        // Diyalog acikken tus olaylari diyaloga gelir; BLD'de tetik birakilinca okuma dursun.
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                return rfidTrigger.onKeyDown(keyCode, event);
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                return rfidTrigger.onKeyUp(keyCode, event);
            }
            return false;
        });
        dialog.show();
    }

    /** Ayni anda birden fazla urun cikarilirsa tek diyalogda alt alta listelenir. */
    private void showRemovedProductAlert(String line) {
        RfidConnectionManager.playOutOfStockWarning();
        showError(line);
        outOfStockDialogNames.add(line);
        String message = TextUtils.join("\n\n", outOfStockDialogNames);

        if (outOfStockDialog != null && outOfStockDialog.isShowing()) {
            outOfStockDialog.setMessage(message);
            return;
        }
        outOfStockDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.warehouse_transfer_out_of_stock_title)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage(message)
                .setPositiveButton(R.string.warehouse_transfer_out_of_stock_ok, null)
                .setOnDismissListener(dialog -> {
                    outOfStockDialogNames.clear();
                    outOfStockDialog = null;
                })
                .create();
        // Diyalog acikken tus olaylari diyaloga gelir; BLD'de tetik birakilinca okuma dursun.
        outOfStockDialog.setOnKeyListener((dialog, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                return rfidTrigger.onKeyDown(keyCode, event);
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                return rfidTrigger.onKeyUp(keyCode, event);
            }
            return false;
        });
        outOfStockDialog.show();
    }

    /** EPC listesini barkoda gore satirlara cevirir (sira: en son okunan urun ustte). */
    private List<WarehouseTransferProductAdapter.Row> buildRows(List<String> epcList) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String epc : epcList) {
            // Listeye sadece isDefinedTag'den gecen etiketler girer; barkod her zaman vardir.
            String barcode = EpcBarcodeGrouping.extractBarcode(epc);
            if (barcode == null) {
                continue;
            }
            Integer current = counts.get(barcode);
            counts.put(barcode, current == null ? 1 : current + 1);
        }
        List<WarehouseTransferProductAdapter.Row> rows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            ProductLookup lookup = lookups.get(entry.getKey());
            WarehouseTransferProductAdapter.LookupState state = lookup != null
                    ? lookup.state
                    : WarehouseTransferProductAdapter.LookupState.LOADING;
            rows.add(new WarehouseTransferProductAdapter.Row(entry.getKey(), entry.getValue(), state,
                    lookup != null ? lookup.product : null,
                    lookup != null ? lookup.errorMessage : null));
        }
        return rows;
    }

    /**
     * Gonderimden once tum barkodlarin stok kontrolu bitmis olmali; yoksa stoksuz urun
     * gonderilebilir. Bitmemisse hata almis sorgular tekrar baslatilir.
     */
    private boolean isStockCheckComplete() {
        Set<String> barcodes = new LinkedHashSet<>();
        synchronized (scanLock) {
            for (String epc : epcs) {
                String barcode = EpcBarcodeGrouping.extractBarcode(epc);
                if (barcode != null) {
                    barcodes.add(barcode);
                }
            }
        }
        boolean complete = true;
        for (String barcode : barcodes) {
            ProductLookup lookup = lookups.get(barcode);
            if (lookup == null || lookup.state != WarehouseTransferProductAdapter.LookupState.LOADED) {
                complete = false;
                requestLookup(barcode);
            }
        }
        if (!complete) {
            renderEpcs();
        }
        return complete;
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

        cancelProductAdapter = new WarehouseTransferProductAdapter();
        rvCancelEpcs.setLayoutManager(new LinearLayoutManager(this));
        rvCancelEpcs.setAdapter(cancelProductAdapter);
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
                getString(R.string.warehouse_transfer_delete_success) + " ("
                        + getString(R.string.warehouse_transfer_row_qty, removedCount) + ")",
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
        if (cancelProductAdapter == null) {
            return;
        }
        cancelProductAdapter.submitList(buildRows(new ArrayList<>(pendingCancelEpcs)));
        if (tvCancelEpcEmpty != null) {
            tvCancelEpcEmpty.setVisibility(pendingCancelEpcs.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void dismissCancelScanDialog(boolean cancelled) {
        cancelScanMode = false;
        pendingCancelEpcs.clear();
        tvCancelEpcStatus = null;
        tvCancelEpcEmpty = null;
        cancelProductAdapter = null;
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
        List<WarehouseTransferProductAdapter.Row> rows = buildRows(snapshot);
        Set<String> barcodes = new LinkedHashSet<>();
        for (WarehouseTransferProductAdapter.Row row : rows) {
            if (row.barcode != null) {
                barcodes.add(row.barcode);
            }
        }
        ensureLookups(barcodes);
        // ensureLookups durum degistirmis olabilir (ör. oturum yok -> hata); satirlari tazele.
        productAdapter.submitList(buildRows(snapshot));
        tvEpcEmptyHint.setVisibility(snapshot.isEmpty() ? View.VISIBLE : View.GONE);
        tvTotalProductCount.setText(getString(R.string.warehouse_transfer_total_products,
                snapshot.size(), rows.size()));
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
        if (hasProducts() && !isStockCheckComplete()) {
            showError(getString(R.string.warehouse_transfer_stock_check_pending));
            Toast.makeText(this, R.string.warehouse_transfer_stock_check_pending, Toast.LENGTH_LONG).show();
            return;
        }
        // Once cihaza kaydedilir: gonderim basarisiz olsa da okutmalar kaybolmaz.
        WarehouseTransfer transfer = saveLocalDraft();
        if (transfer == null) {
            return;
        }

        clearError();
        sending = true;
        if (isScanning) {
            stopScanning();
        }
        sendingDialog.show(R.string.warehouse_transfer_sending);
        WarehouseTransferSender.send(this, transfer, new WarehouseTransferSender.Callback() {
            @Override
            public void onSuccess(WarehouseTransferCreateApiClient.CreateResult result,
                                  WarehouseTransfer saved) {
                sending = false;
                sendingDialog.dismiss();
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
                sendingDialog.dismiss();
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
