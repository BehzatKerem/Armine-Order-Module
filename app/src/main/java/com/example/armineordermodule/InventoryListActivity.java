package com.example.armineordermodule;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/** Sayim listesi: mevcut sayimlar + yeni sayim FAB. */
public class InventoryListActivity extends AppCompatActivity
        implements InventoryListAdapter.Listener {

    private static final String TAG = "InventoryList";

    private TextView tvPendingBanner;
    private TextView tvEmptyHint;
    private InventoryListAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory_list);

        ImageButton btnBack = findViewById(R.id.btnBack);
        tvPendingBanner = findViewById(R.id.tvPendingBanner);
        tvEmptyHint = findViewById(R.id.tvEmptyHint);
        RecyclerView rvCounts = findViewById(R.id.rvCounts);
        FloatingActionButton fabNewCount = findViewById(R.id.fabNewCount);

        adapter = new InventoryListAdapter(this);
        rvCounts.setLayoutManager(new LinearLayoutManager(this));
        rvCounts.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        fabNewCount.setOnClickListener(v ->
                startActivity(new Intent(this, InventorySetupActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCounts();
    }

    private void refreshCounts() {
        List<InventoryCount> counts = InventoryStore.getAll(this);
        adapter.submitList(counts);
        tvEmptyHint.setVisibility(counts.isEmpty() ? View.VISIBLE : View.GONE);
        tvPendingBanner.setVisibility(counts.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onOpenCount(InventoryCount count) {
        Intent intent = new Intent(this, InventoryLocationsActivity.class);
        intent.putExtra(InventoryLocationsActivity.EXTRA_COUNT_LOCAL_ID, count.localId);
        startActivity(intent);
    }

    @Override
    public void onTransferCount(InventoryCount count) {
        if (count == null) {
            return;
        }
        if (TextUtils.isEmpty(count.warehouseCode) || TextUtils.isEmpty(count.officeCode)) {
            Toast.makeText(this, R.string.inventory_list_transfer_warehouse, Toast.LENGTH_LONG).show();
            return;
        }
        if (count.totalQty() <= 0) {
            Toast.makeText(this, R.string.inventory_list_transfer_empty, Toast.LENGTH_LONG).show();
            return;
        }

        JSONObject apiJson;
        try {
            apiJson = count.toApiJson();
        } catch (JSONException e) {
            Toast.makeText(this, R.string.inventory_json_error, Toast.LENGTH_LONG).show();
            return;
        }
        Log.d(TAG, "Sayim JSON (aktarim bekliyor): " + apiJson);

        count.status = InventoryCount.STATUS_PENDING_SEND;
        InventoryStore.upsert(this, count);
        Toast.makeText(this, R.string.inventory_pending_send_saved_message, Toast.LENGTH_LONG).show();
        refreshCounts();
    }

    @Override
    public void onDeleteCount(InventoryCount count) {
        if (count == null) {
            return;
        }
        String name = !TextUtils.isEmpty(count.name) ? count.name : "-";
        new AlertDialog.Builder(this)
                .setTitle(R.string.inventory_list_delete_title)
                .setMessage(getString(R.string.inventory_list_delete_message) + "\n\n" + name)
                .setPositiveButton(R.string.inventory_list_delete, (dialog, which) -> {
                    InventoryStore.delete(this, count.localId);
                    Toast.makeText(this, R.string.inventory_list_deleted, Toast.LENGTH_SHORT).show();
                    refreshCounts();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
