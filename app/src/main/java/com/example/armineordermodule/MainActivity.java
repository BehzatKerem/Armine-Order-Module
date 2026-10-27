package com.example.armineordermodule;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.armineordermodule.rfid.RfidConnectionManager;
import com.example.armineordermodule.rfid.RfidScanActivity;
import com.google.android.material.button.MaterialButton;

import kotlin.Unit;

public class MainActivity extends AppCompatActivity {

    /** RFID baglantisi arka planda (baska ekrandan) degisirse ekranda yansitmak icin. */
    private static final long RFID_STATUS_POLL_INTERVAL_MS = 2000L;

    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusPoller = new Runnable() {
        @Override
        public void run() {
            refreshRfidStatusUi();
            statusHandler.postDelayed(this, RFID_STATUS_POLL_INTERVAL_MS);
        }
    };

    private View dotRfidStatus;
    private TextView tvRfidStatus;
    private MaterialButton btnAppUpdate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ImageButton btnProfile = findViewById(R.id.btnProfile);
        MaterialButton btnProducts = findViewById(R.id.btnProducts);
        MaterialButton btnCustomers = findViewById(R.id.btnCustomers);
        MaterialButton btnWarehouses = findViewById(R.id.btnWarehouses);
        MaterialButton btnWholesaleOrder = findViewById(R.id.btnWholesaleOrder);
        MaterialButton btnWarehouseTransfer = findViewById(R.id.btnWarehouseTransfer);
        MaterialButton btnRfidCounting = findViewById(R.id.btnRfidCounting);
        MaterialButton btnRfidScan = findViewById(R.id.btnRfidScan);
        dotRfidStatus = findViewById(R.id.dotRfidStatus);
        tvRfidStatus = findViewById(R.id.tvRfidStatus);
        btnAppUpdate = findViewById(R.id.btnAppUpdate);

        btnProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        setupPressableMenuButton(btnProducts);
        setupPressableMenuButton(btnCustomers);
        setupPressableMenuButton(btnWarehouses);
        setupPressableMenuButton(btnWholesaleOrder);
        setupPressableMenuButton(btnWarehouseTransfer);
        setupPressableMenuButton(btnRfidCounting);
        setupPressableMenuButton(btnRfidScan);

        btnProducts.setOnClickListener(v -> showComingSoon());
        btnCustomers.setOnClickListener(v ->
                startActivity(new Intent(this, CustomersActivity.class)));
        btnWarehouses.setOnClickListener(v ->
                startActivity(new Intent(this, WarehousesActivity.class)));
        btnWholesaleOrder.setOnClickListener(v ->
                startActivity(new Intent(this, WholesaleOrderListActivity.class)));
        btnWarehouseTransfer.setOnClickListener(v ->
                startActivity(new Intent(this, WarehouseTransferListActivity.class)));
        btnRfidCounting.setOnClickListener(v ->
                startActivity(new Intent(this, RfidHandheldCountingStartActivity.class)));
        btnRfidScan.setOnClickListener(v ->
                startActivity(new Intent(this, RfidScanActivity.class)));
    }

    @Override
    protected void onStart() {
        super.onStart();
        AppUpdateButton.attach(this, btnAppUpdate);
    }

    @Override
    protected void onStop() {
        AppUpdateButton.detach(btnAppUpdate);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRfidStatusUi();
        maybeAutoReconnectRfid();
        statusHandler.post(statusPoller);
    }

    @Override
    protected void onPause() {
        super.onPause();
        statusHandler.removeCallbacks(statusPoller);
    }

    /**
     * Uygulama yeniden acildiginda, cikis yapip tekrar giris yapildiginda ya da bu ekrana
     * baska bir yerden geri donuldugunde RFID anahtari acik olmasina ragmen baglanti
     * kopmus olabilir (bkz. ArmineApplication - Application.onCreate() sadece process
     * basina bir kez calisir). Bu yuzden ekran her on plana geldiginde kontrol edilir.
     */
    private void maybeAutoReconnectRfid() {
        if (RfidPreferences.isRfidEnabled(this) && !RfidConnectionManager.isConnected()) {
            RfidConnectionManager.autoReconnectIfNeededAsync(getApplicationContext(), result -> {
                refreshRfidStatusUi();
                return Unit.INSTANCE;
            });
        }
    }

    private void refreshRfidStatusUi() {
        boolean connected = RfidConnectionManager.isConnected();
        dotRfidStatus.setBackgroundResource(connected ? R.drawable.bg_dot_green : R.drawable.bg_dot_red);
        tvRfidStatus.setText(connected ? R.string.main_rfid_status_connected : R.string.main_rfid_status_disconnected);
    }

    private void setupPressableMenuButton(View button) {
        button.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate()
                            .scaleX(0.95f)
                            .scaleY(0.95f)
                            .translationY(4f)
                            .setDuration(70)
                            .start();
                    break;
                case MotionEvent.ACTION_UP:
                    v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .translationY(0f)
                            .setDuration(100)
                            .start();
                    v.performClick();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .translationY(0f)
                            .setDuration(100)
                            .start();
                    break;
                default:
                    break;
            }
            return true;
        });
    }

    private void showComingSoon() {
        Toast.makeText(this, R.string.menu_coming_soon, Toast.LENGTH_SHORT).show();
    }
}
