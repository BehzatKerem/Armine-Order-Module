package com.example.armineordermodule;

import android.content.Intent;
import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.armineordermodule.rfid.RfidConnectionManager;
import com.google.android.material.button.MaterialButton;

public class ProfileActivity extends AppCompatActivity {

    /** Kullanici adina bu kadar basili tutunca gelistirici modu acilir/kapanir. */
    private static final long DEVELOPER_MODE_HOLD_MS = 5000L;

    private final Handler holdHandler = new Handler(Looper.getMainLooper());
    private final Runnable toggleDeveloperMode = this::toggleDeveloperMode;
    private TextView tvDeveloperMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        ImageButton btnBack = findViewById(R.id.btnBack);
        TextView tvUsername = findViewById(R.id.tvProfileUsername);
        TextView tvEmail = findViewById(R.id.tvProfileEmail);
        TextView tvDatabase = findViewById(R.id.tvProfileDatabase);
        TextView tvVersion = findViewById(R.id.tvProfileVersion);
        MaterialButton btnLogout = findViewById(R.id.btnLogout);
        tvDeveloperMode = findViewById(R.id.tvDeveloperMode);

        String username = AuthPreferences.getUserName(this);
        String email = AuthPreferences.getEmail(this);
        String database = AuthPreferences.getDefaultDatabase(this);

        tvUsername.setText(TextUtils.isEmpty(username) ? "-" : username);
        tvEmail.setText(TextUtils.isEmpty(email) ? "-" : email);
        tvDatabase.setText(TextUtils.isEmpty(database) ? "-" : database);
        tvVersion.setText(getString(R.string.profile_version, BuildConfig.VERSION_NAME));

        btnBack.setOnClickListener(v -> finish());
        setupDeveloperModeGesture(tvUsername);
        renderDeveloperMode();

        btnLogout.setOnClickListener(v -> {
            // Cikis yapan kullanicinin RFID okuyucuyu acik birakmamasi icin baglanti kesilir.
            // Bkz. RfidConnectionManager - uygulama genelinde tek paylasilan baglanti.
            if (RfidConnectionManager.isConnected()) {
                RfidConnectionManager.disconnectAsync();
            }

            AuthPreferences.clearSession(this);
            DeveloperModePreferences.setEnabled(this, false);
            WarehousePreferences.clear(this);
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        holdHandler.removeCallbacks(toggleDeveloperMode);
        super.onDestroy();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupDeveloperModeGesture(View target) {
        target.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    holdHandler.postDelayed(toggleDeveloperMode, DEVELOPER_MODE_HOLD_MS);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    holdHandler.removeCallbacks(toggleDeveloperMode);
                    return true;
                default:
                    return true;
            }
        });
    }

    private void toggleDeveloperMode() {
        boolean enabled = !DeveloperModePreferences.isEnabled(this);
        DeveloperModePreferences.setEnabled(this, enabled);
        renderDeveloperMode();
        Toast.makeText(this,
                enabled ? R.string.developer_mode_enabled : R.string.developer_mode_disabled,
                Toast.LENGTH_LONG).show();
    }

    private void renderDeveloperMode() {
        tvDeveloperMode.setVisibility(
                DeveloperModePreferences.isEnabled(this) ? View.VISIBLE : View.GONE);
    }
}
