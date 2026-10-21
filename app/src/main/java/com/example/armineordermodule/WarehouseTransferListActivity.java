package com.example.armineordermodule;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

/** Kaydedilmis depo transferleri. Yeni transfer + ile acilir. */
public class WarehouseTransferListActivity extends AppCompatActivity
        implements WarehouseTransferListAdapter.Listener {

    private TextView tvEmptyHint;
    private WarehouseTransferListAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_warehouse_transfer_list);

        ImageButton btnBack = findViewById(R.id.btnBack);
        tvEmptyHint = findViewById(R.id.tvEmptyHint);
        RecyclerView rvTransfers = findViewById(R.id.rvTransfers);
        FloatingActionButton fabNewTransfer = findViewById(R.id.fabNewTransfer);

        adapter = new WarehouseTransferListAdapter(this);
        rvTransfers.setLayoutManager(new LinearLayoutManager(this));
        rvTransfers.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        fabNewTransfer.setOnClickListener(v ->
                startActivity(new Intent(this, WarehouseTransferActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshTransfers();
    }

    private void refreshTransfers() {
        List<WarehouseTransfer> transfers = WarehouseTransferStore.getAll(this);
        adapter.submitList(transfers);
        tvEmptyHint.setVisibility(transfers.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onOpenTransfer(WarehouseTransfer transfer) {
        if (transfer == null || TextUtils.isEmpty(transfer.transferGuid)) {
            return;
        }
        if (transfer.isNebimSend) {
            Toast.makeText(this, R.string.warehouse_transfer_sent_locked, Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(this, WarehouseTransferScanActivity.class);
        intent.putExtra(WarehouseTransferScanActivity.EXTRA_TRANSFER_GUID, transfer.transferGuid);
        startActivity(intent);
    }

    @Override
    public void onDeleteTransfer(WarehouseTransfer transfer) {
        if (transfer == null || TextUtils.isEmpty(transfer.transferGuid)) {
            return;
        }
        if (transfer.isNebimSend) {
            Toast.makeText(this, R.string.warehouse_transfer_sent_locked, Toast.LENGTH_LONG).show();
            return;
        }
        String source = !TextUtils.isEmpty(transfer.sourceDescription)
                ? transfer.sourceDescription : transfer.warehouseCode;
        String target = !TextUtils.isEmpty(transfer.targetDescription)
                ? transfer.targetDescription : transfer.toWarehouseCode;
        String route = getString(R.string.warehouse_transfer_scan_route, source, target);
        new AlertDialog.Builder(this)
                .setTitle(R.string.warehouse_transfer_list_delete_title)
                .setMessage(getString(R.string.warehouse_transfer_list_delete_message, route))
                .setPositiveButton(R.string.warehouse_transfer_list_delete, (dialog, which) -> {
                    WarehouseTransferStore.delete(this, transfer.transferGuid);
                    Toast.makeText(this, R.string.warehouse_transfer_list_deleted, Toast.LENGTH_SHORT).show();
                    refreshTransfers();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
