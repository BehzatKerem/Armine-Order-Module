package com.example.armineordermodule;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Depo transferini Nebim'e gonderir ve sonucu cihazdaki kayda yazar.
 * Okutma ekranindaki "Kaydet ve Gonder" ile listedeki "Tekrar Aktar" ayni yolu kullanir.
 *
 * Transfer gondermeden once cihazda kayitli olmalidir. Hata olursa kayit silinmez;
 * hata mesaji {@link WarehouseTransfer#lastSendError} alanina yazilir ve transfer listede
 * "gonderilemedi" olarak gorunur. Tekrar gonderimde ayni transferGuid kullanilir; sunucu
 * ilk istegi islemis ama yanit gelmemisse (internet koptu) alreadySent doner ve transfer
 * ikinci kez olusmaz.
 */
public final class WarehouseTransferSender {

    public interface Callback {
        void onSuccess(WarehouseTransferCreateApiClient.CreateResult result, WarehouseTransfer saved);

        void onError(String message, WarehouseTransfer saved);
    }

    private WarehouseTransferSender() {
    }

    public static void send(Context context, WarehouseTransfer transfer, Callback callback) {
        Context app = context.getApplicationContext();
        if (transfer == null || TextUtils.isEmpty(transfer.transferGuid)) {
            callback.onError(app.getString(R.string.warehouse_transfer_send_failed), transfer);
            return;
        }
        final String guid = transfer.transferGuid;

        if (transfer.isNebimSend) {
            callback.onError(app.getString(R.string.warehouse_transfer_sent_locked), transfer);
            return;
        }
        if (transfer.epcs.isEmpty()) {
            fail(app, guid, app.getString(R.string.warehouse_transfer_products_required), callback);
            return;
        }

        String serverUrl = ServerPreferences.getServerUrl(app);
        if (TextUtils.isEmpty(serverUrl)) {
            fail(app, guid, app.getString(R.string.server_url_missing), callback);
            return;
        }
        String token = AuthPreferences.getToken(app);
        if (TextUtils.isEmpty(token) || !AuthPreferences.hasValidSession(app)) {
            fail(app, guid, app.getString(R.string.warehouses_session_required), callback);
            return;
        }
        String databaseName = AuthPreferences.getDefaultDatabase(app);
        if (TextUtils.isEmpty(databaseName)) {
            fail(app, guid, app.getString(R.string.warehouses_database_missing), callback);
            return;
        }
        String companyCode = AuthPreferences.getCompanyCode(app);
        if (TextUtils.isEmpty(companyCode)) {
            fail(app, guid, app.getString(R.string.warehouse_transfer_company_missing), callback);
            return;
        }

        JSONObject apiJson;
        try {
            apiJson = WarehouseTransferRequest.toApiJson(
                    guid,
                    companyCode,
                    "",
                    transfer.officeCode,
                    transfer.warehouseCode,
                    transfer.toOfficeCode,
                    transfer.toWarehouseCode,
                    databaseName,
                    transfer.epcs);
        } catch (JSONException e) {
            fail(app, guid, app.getString(R.string.warehouse_transfer_json_failed), callback);
            return;
        }

        WarehouseTransferCreateApiClient.create(serverUrl, token, apiJson,
                new WarehouseTransferCreateApiClient.Callback() {
                    @Override
                    public void onSuccess(WarehouseTransferCreateApiClient.CreateResult result) {
                        WarehouseTransfer saved = WarehouseTransferStore.findByGuid(app, guid);
                        if (saved != null) {
                            saved.isNebimSend = true;
                            saved.lastSendError = "";
                            saved.lastSendAttemptMillis = System.currentTimeMillis();
                            if (result != null && result.headerId > 0) {
                                saved.headerId = result.headerId;
                            }
                            WarehouseTransferStore.upsert(app, saved);
                        }
                        callback.onSuccess(result, saved);
                    }

                    @Override
                    public void onError(String message) {
                        fail(app, guid, !TextUtils.isEmpty(message)
                                ? message
                                : app.getString(R.string.warehouse_transfer_send_failed), callback);
                    }
                });
    }

    private static void fail(Context app, String guid, String message, Callback callback) {
        WarehouseTransfer saved = WarehouseTransferStore.findByGuid(app, guid);
        if (saved != null && !saved.isNebimSend) {
            saved.lastSendError = message;
            saved.lastSendAttemptMillis = System.currentTimeMillis();
            WarehouseTransferStore.upsert(app, saved);
        }
        callback.onError(message, saved);
    }
}
