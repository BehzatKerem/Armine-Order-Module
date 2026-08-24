package com.example.armineordermodule;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Gravity;
import android.graphics.Typeface;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONException;

/**
 * RFID sayim lokasyon listesi: lokasyon ekle, okut, sil, kaydet / kaydet gonder.
 */
public class RfidHandheldCountingDetailActivity extends AppCompatActivity {

    public static final String EXTRA_COUNTING_CODE = "extra_counting_code";
    private static final String TAG = "RfidCountingDetail";

    private RfidHandheldCounting counting;
    private TextView tvDetailTitle;
    private TextView tvBatchName;
    private TextView tvCountingMeta;
    private TextView tvCountingSummary;
    private TextView tvDetailError;
    private LinearLayout containerLocations;
    private FloatingActionButton fabCountActions;
    private View rowAddLocation;
    private View rowSave;
    private View rowSaveAndSend;
    private View rowPreviewSendJson;
    private boolean fabExpanded = false;
    private boolean isSending = false;
    private AlertDialog sendProgressDialog;
    private TextView tvSendProgress;
    private FloatingActionButton fabPreviewSendJson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rfid_handheld_counting_detail);

        if (!loadOrFail()) {
            finish();
            return;
        }

        ImageButton btnBack = findViewById(R.id.btnBack);
        tvDetailTitle = findViewById(R.id.tvDetailTitle);
        tvBatchName = findViewById(R.id.tvBatchName);
        tvCountingMeta = findViewById(R.id.tvCountingMeta);
        tvCountingSummary = findViewById(R.id.tvCountingSummary);
        tvDetailError = findViewById(R.id.tvDetailError);
        containerLocations = findViewById(R.id.containerLocations);
        fabCountActions = findViewById(R.id.fabCountActions);
        rowAddLocation = findViewById(R.id.rowAddLocation);
        rowSave = findViewById(R.id.rowSave);
        rowSaveAndSend = findViewById(R.id.rowSaveAndSend);
        rowPreviewSendJson = findViewById(R.id.rowPreviewSendJson);
        FloatingActionButton fabAddLocation = findViewById(R.id.fabAddLocation);
        FloatingActionButton fabSave = findViewById(R.id.fabSave);
        FloatingActionButton fabSaveAndSend = findViewById(R.id.fabSaveAndSend);
        fabPreviewSendJson = findViewById(R.id.fabPreviewSendJson);

        btnBack.setOnClickListener(v -> {
            if (isSending) {
                return;
            }
            RfidHandheldCountingStore.upsert(this, counting);
            finish();
        });
        fabCountActions.setOnClickListener(v -> setFabMenuExpanded(!fabExpanded));
        fabAddLocation.setOnClickListener(v -> {
            setFabMenuExpanded(false);
            showAddLocationDialog();
        });
        fabSave.setOnClickListener(v -> saveDraft());
        fabSaveAndSend.setOnClickListener(v -> saveAndSend());
        fabPreviewSendJson.setOnClickListener(v -> showPreviewSendJson());

        renderAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isSending) {
            reloadCounting();
            renderAll();
        }
    }

    @Override
    protected void onDestroy() {
        dismissSendProgressDialog();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (isSending) {
            return;
        }
        super.onBackPressed();
    }

    private boolean loadOrFail() {
        String code = getIntent() != null
                ? getIntent().getStringExtra(EXTRA_COUNTING_CODE) : null;
        counting = RfidHandheldCountingStore.findByCode(this, code);
        return counting != null;
    }

    private void reloadCounting() {
        if (counting == null) {
            return;
        }
        RfidHandheldCounting fresh = RfidHandheldCountingStore.findByCode(this, counting.countingCode);
        if (fresh != null) {
            counting = fresh;
        }
    }

    private void renderAll() {
        String title = !TextUtils.isEmpty(counting.batchName)
                ? counting.batchName
                : getString(R.string.rfid_counting_detail_title);
        tvDetailTitle.setText(title);
        tvBatchName.setText(title);

        String warehouse = !TextUtils.isEmpty(counting.warehouseCode)
                ? counting.warehouseCode : "-";
        String office = !TextUtils.isEmpty(counting.officeCode)
                ? counting.officeCode : "-";
        tvCountingMeta.setText(getString(
                R.string.rfid_counting_detail_meta,
                counting.countingCode, office, warehouse));

        int locCount = counting.locations.size();
        int sentCount = counting.sentEpcCount();
        if (sentCount > 0) {
            tvCountingSummary.setText(getString(
                    R.string.rfid_counting_detail_summary_epc_sent,
                    locCount, counting.totalScannedEpcCount(), sentCount));
        } else {
            tvCountingSummary.setText(getString(
                    R.string.rfid_counting_detail_summary_epc,
                    locCount, counting.totalScannedEpcCount()));
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        containerLocations.removeAllViews();
        for (RfidHandheldCountingLocation location : counting.locations) {
            View row = inflater.inflate(
                    R.layout.item_rfid_handheld_counting_location, containerLocations, false);
            TextView tvName = row.findViewById(R.id.tvLocationName);
            TextView tvMeta = row.findViewById(R.id.tvLocationMeta);
            MaterialButton btnOpen = row.findViewById(R.id.btnOpenLocation);
            ImageButton btnDelete = row.findViewById(R.id.btnDeleteLocation);

            String name = !TextUtils.isEmpty(location.locationName)
                    ? location.locationName
                    : getString(R.string.rfid_counting_location_unnamed, location.locationId);
            tvName.setText(name);
            tvMeta.setText(getString(
                    R.string.rfid_counting_location_epc_meta, location.scannedEpcCount()));

            final String guid = location.localGuid;
            btnOpen.setOnClickListener(v -> openLocationScan(guid));
            tvName.setOnClickListener(v -> openLocationScan(guid));
            tvMeta.setOnClickListener(v -> openLocationScan(guid));
            btnDelete.setOnClickListener(v -> showDeleteLocationDialog(guid, name));
            containerLocations.addView(row);
        }
    }

    private void showAddLocationDialog() {
        clearError();
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_rfid_location, null);
        TextInputEditText etName = dialogView.findViewById(R.id.etLocationName);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.rfid_counting_location_add)
                .setView(dialogView)
                .setPositiveButton(R.string.rfid_counting_location_add_confirm, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = etName.getText() != null ? etName.getText().toString().trim() : "";
            if (TextUtils.isEmpty(name)) {
                TextInputLayout til = dialogView.findViewById(R.id.tilLocationName);
                if (til != null) {
                    til.setError(getString(R.string.rfid_counting_location_name_required));
                }
                etName.requestFocus();
                return;
            }
            RfidHandheldCountingLocation location = RfidHandheldCountingLocation.createLocal(name);
            counting.locations.add(location);
            counting.locationCount = counting.locations.size();
            RfidHandheldCountingStore.upsert(this, counting);
            renderAll();
            dialog.dismiss();
            openLocationScan(location.localGuid);
        }));

        dialog.show();
        etName.requestFocus();
    }

    private void showDeleteLocationDialog(String locationGuid, String locationName) {
        clearError();
        View dialogView = getLayoutInflater().inflate(
                R.layout.dialog_rfid_counting_delete_location, null);
        TextInputEditText etConfirm = dialogView.findViewById(R.id.etDeleteConfirm);
        TextView tvError = dialogView.findViewById(R.id.tvDeleteConfirmError);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.rfid_counting_location_delete_title)
                .setView(dialogView)
                .setPositiveButton(R.string.rfid_counting_location_delete_confirm, null)
                .setNegativeButton(R.string.rfid_counting_location_delete_back, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String typed = etConfirm.getText() != null
                    ? etConfirm.getText().toString().trim() : "";
            if (!"E".equalsIgnoreCase(typed)) {
                tvError.setText(R.string.rfid_counting_location_delete_wrong);
                tvError.setVisibility(View.VISIBLE);
                etConfirm.requestFocus();
                etConfirm.selectAll();
                return;
            }
            deleteLocation(locationGuid);
            dialog.dismiss();
            Toast.makeText(this,
                    getString(R.string.rfid_counting_location_deleted)
                            + (TextUtils.isEmpty(locationName) ? "" : ": " + locationName),
                    Toast.LENGTH_SHORT).show();
        }));

        dialog.show();
        etConfirm.requestFocus();
    }

    private void deleteLocation(String locationGuid) {
        if (TextUtils.isEmpty(locationGuid) || counting == null) {
            return;
        }
        for (int i = 0; i < counting.locations.size(); i++) {
            if (locationGuid.equals(counting.locations.get(i).localGuid)) {
                counting.locations.remove(i);
                break;
            }
        }
        counting.locationCount = counting.locations.size();
        RfidHandheldCountingStore.upsert(this, counting);
        renderAll();
    }

    private void openLocationScan(String locationGuid) {
        clearError();
        Intent intent = new Intent(this, RfidHandheldCountingScanActivity.class);
        intent.putExtra(RfidHandheldCountingScanActivity.EXTRA_COUNTING_CODE, counting.countingCode);
        intent.putExtra(RfidHandheldCountingScanActivity.EXTRA_LOCATION_GUID, locationGuid);
        startActivity(intent);
    }

    private void setFabMenuExpanded(boolean expanded) {
        fabExpanded = expanded;
        fabCountActions.animate().rotation(expanded ? 45f : 0f).setDuration(150).start();
        animateFabRow(rowAddLocation, expanded);
        animateFabRow(rowSave, expanded);
        animateFabRow(rowSaveAndSend, expanded);
        animateFabRow(rowPreviewSendJson, expanded);
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

    private void showPreviewSendJson() {
        if (isSending) {
            return;
        }
        if (counting == null) {
            return;
        }
        if (counting.unsentEpcCount() <= 0) {
            showError(getString(R.string.rfid_counting_preview_send_json_not_ready));
            return;
        }

        final RfidHandheldCountingSendChunker.Chunk chunk;
        try {
            // Sadece önizleme: parça indeksini ilerletmez; gönderim başlayınca aynı parça ile devam eder.
            chunk = RfidHandheldCountingSendChunker.takeNext(counting);
        } catch (JSONException e) {
            showError(getString(R.string.rfid_counting_json_error));
            return;
        }

        if (chunk == null || chunk.json == null) {
            showError(getString(R.string.rfid_counting_all_epcs_sent));
            return;
        }

        TextView tv = new TextView(this);
        tv.setTextIsSelectable(true);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setPadding(24, 16, 24, 16);
        tv.setTextSize(12f);

        String json;
        try {
            json = chunk.json.toString(2);
        } catch (Exception ignored) {
            json = chunk.json.toString();
        }

        tv.setText(json);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(tv);
        scrollView.setFillViewport(true);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.rfid_counting_preview_send_json_title)
                        + " (" + chunk.chunkIndex + "/" + chunk.chunkCount + ")")
                .setView(scrollView)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.show();
    }

    private void saveDraft() {
        setFabMenuExpanded(false);
        if (!validateForSave(false)) {
            return;
        }
        counting.status = RfidHandheldCounting.STATUS_DRAFT;
        RfidHandheldCountingStore.upsert(this, counting);
        Toast.makeText(this, R.string.rfid_counting_draft_saved, Toast.LENGTH_LONG).show();
        finish();
    }

    private void saveAndSend() {
        setFabMenuExpanded(false);
        if (isSending) {
            return;
        }
        if (!validateForSave(true)) {
            return;
        }
        if (counting.unsentEpcCount() <= 0) {
            finishSendSuccess();
            return;
        }

        String serverUrl = ServerPreferences.getServerUrl(this);
        if (TextUtils.isEmpty(serverUrl)) {
            showError(getString(R.string.server_url_required));
            return;
        }
        String token = AuthPreferences.getToken(this);
        if (TextUtils.isEmpty(token) || !AuthPreferences.hasValidSession(this)) {
            showError(getString(R.string.rfid_counting_session_required));
            return;
        }

        counting.status = RfidHandheldCounting.STATUS_PENDING_SEND;
        counting.beginSendSessionIfNeeded();
        RfidHandheldCountingStore.upsert(this, counting);

        isSending = true;
        fabCountActions.setEnabled(false);
        showSendProgressDialog();
        sendNextChunk(serverUrl, token);
    }

    private void sendNextChunk(String serverUrl, String token) {
        if (isFinishing() || isDestroyed()) {
            isSending = false;
            return;
        }
        if (counting.unsentEpcCount() <= 0) {
            finishSendSuccess();
            return;
        }

        final RfidHandheldCountingSendChunker.Chunk chunk;
        try {
            chunk = RfidHandheldCountingSendChunker.takeNext(counting);
        } catch (JSONException e) {
            failSend(getString(R.string.rfid_counting_json_error));
            return;
        }
        if (chunk == null) {
            finishSendSuccess();
            return;
        }

        Log.d(TAG, "RFID Sayim parca " + chunk.chunkIndex + "/" + chunk.chunkCount
                + " (" + chunk.epcCount + " EPC): " + chunk.json);
        updateSendProgress(chunk);

        RfidHandheldCountingApiClient.sendChunk(serverUrl, token, counting.countingCode,
                chunk.json, new RfidHandheldCountingApiClient.SendCallback() {
                    @Override
                    public void onSuccess() {
                        counting.markChunkSent(chunk.epcsByLocationGuid);
                        if (counting.unsentEpcCount() <= 0) {
                            counting.status = RfidHandheldCounting.STATUS_SENT;
                            counting.clearSendSession();
                        }
                        RfidHandheldCountingStore.upsert(
                                RfidHandheldCountingDetailActivity.this, counting);
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        sendNextChunk(serverUrl, token);
                    }

                    @Override
                    public void onError(String message) {
                        failSend(message);
                    }
                });
    }

    private void showSendProgressDialog() {
        dismissSendProgressDialog();
        tvSendProgress = new TextView(this);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        tvSendProgress.setPadding(pad, pad, pad, pad);
        tvSendProgress.setText(R.string.rfid_counting_sending_title);
        sendProgressDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.rfid_counting_sending_title)
                .setView(tvSendProgress)
                .setCancelable(false)
                .create();
        sendProgressDialog.show();
    }

    private void updateSendProgress(RfidHandheldCountingSendChunker.Chunk chunk) {
        if (tvSendProgress == null) {
            return;
        }
        int total = counting.totalScannedEpcCount();
        int sentSoFar = counting.sentEpcCount() + chunk.epcCount;
        tvSendProgress.setText(getString(
                R.string.rfid_counting_sending_progress,
                chunk.chunkIndex, chunk.chunkCount, sentSoFar, total));
    }

    private void finishSendSuccess() {
        // Basariyla gonderildi; lokali temizle
        RfidHandheldCountingStore.deleteByCode(this, counting.countingCode);
        isSending = false;
        fabCountActions.setEnabled(true);
        dismissSendProgressDialog();
        Toast.makeText(this, R.string.rfid_counting_send_success, Toast.LENGTH_LONG).show();
        finish();
    }

    private void failSend(String message) {
        isSending = false;
        counting.status = RfidHandheldCounting.STATUS_PENDING_SEND;
        RfidHandheldCountingStore.upsert(this, counting);
        if (isFinishing() || isDestroyed()) {
            return;
        }
        fabCountActions.setEnabled(true);
        dismissSendProgressDialog();
        String detail = TextUtils.isEmpty(message)
                ? getString(R.string.rfid_counting_send_failed)
                : message;
        showError(getString(R.string.rfid_counting_send_partial, counting.sentEpcCount(), detail));
    }

    private void dismissSendProgressDialog() {
        if (sendProgressDialog != null) {
            sendProgressDialog.dismiss();
            sendProgressDialog = null;
        }
        tvSendProgress = null;
    }

    private boolean validateForSave(boolean requireEpcs) {
        clearError();
        if (counting.locations.isEmpty()) {
            showError(getString(R.string.rfid_counting_locations_required));
            return false;
        }
        if (requireEpcs && counting.totalScannedEpcCount() <= 0) {
            showError(getString(R.string.rfid_counting_epcs_required));
            return false;
        }
        return true;
    }

    private void showError(String message) {
        tvDetailError.setText(message);
        tvDetailError.setVisibility(View.VISIBLE);
    }

    private void clearError() {
        tvDetailError.setVisibility(View.GONE);
        tvDetailError.setText("");
    }
}
