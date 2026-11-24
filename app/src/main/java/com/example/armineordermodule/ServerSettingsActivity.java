package com.example.armineordermodule;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.Nullable;

import com.example.armineordermodule.rfid.ConnectResult;
import com.example.armineordermodule.rfid.RfidConnectionManager;
import com.example.armineordermodule.rfid.RfidDeviceDetector;
import com.example.armineordermodule.rfid.RfidReaderType;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;


public class ServerSettingsActivity extends AppCompatActivity {

    private TextInputLayout tilServerUrl;
    private TextInputEditText etServerUrl;
    private SwitchMaterial switchRfid;
    private SwitchMaterial switchTriggerMode;
    private boolean initialRfidEnabled;
    private RadioGroup rgReaderType;
    private TextView tvReaderTypeHelp;
    private TextView tvTriggerModeHelp;
    @Nullable
    private String initialReaderTypeOverride;
    private RfidReaderType detectedReaderType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_settings);

        tilServerUrl = findViewById(R.id.tilServerUrl);
        etServerUrl = findViewById(R.id.etServerUrl);
        switchRfid = findViewById(R.id.switchRfid);
        switchTriggerMode = findViewById(R.id.switchTriggerMode);
        rgReaderType = findViewById(R.id.rgRfidReaderType);
        tvReaderTypeHelp = findViewById(R.id.tvRfidReaderTypeHelp);
        tvTriggerModeHelp = findViewById(R.id.tvTriggerModeHelp);
        MaterialButton btnSave = findViewById(R.id.btnSaveServerUrl);

        etServerUrl.setText(ServerPreferences.getServerUrl(this));
        initialRfidEnabled = RfidPreferences.isRfidEnabled(this);
        switchRfid.setChecked(initialRfidEnabled);
        switchTriggerMode.setChecked(RfidPreferences.isPressAndHoldMode(this));

        detectedReaderType = RfidDeviceDetector.detect(this);
        initialReaderTypeOverride = RfidPreferences.getReaderTypeOverride(this);
        rgReaderType.check(radioIdFor(RfidReaderType.fromPrefValue(initialReaderTypeOverride)));
        rgReaderType.setOnCheckedChangeListener((group, checkedId) -> renderReaderTypeState());
        renderReaderTypeState();

        switchRfid.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                Toast.makeText(this, R.string.server_rfid_enable_warning, Toast.LENGTH_LONG).show();
            }
        });

        // "Kaydet"in altinda: Kaydet akisina bagli degil, secim aninda kalici olarak saklanir
        // (hicbir hardware/baglanti islemi tetiklemez, sadece RfidScanActivity'nin tetik tusu
        // davranisini belirler).
        switchTriggerMode.setOnCheckedChangeListener((buttonView, isChecked) ->
                RfidPreferences.setPressAndHoldMode(this, isChecked));

        btnSave.setOnClickListener(v -> saveSettings());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (hasUnsavedRfidEnable()) {
                    showUnsavedRfidEnableDialog();
                } else {
                    finish();
                }
            }
        });
    }

    /**
     * RFID anahtari bu ekrana girildigindeki durumdan farkli olarak ACIK konuma
     * getirilmis ama henuz Kaydet ile onaylanmamissa true doner (geri tusuna
     * basildiginda kullaniciyi uyarmak icin kullanilir).
     */
    private boolean hasUnsavedRfidEnable() {
        return switchRfid.isChecked() && switchRfid.isChecked() != initialRfidEnabled;
    }

    private void showUnsavedRfidEnableDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.server_rfid_unsaved_title)
                .setMessage(R.string.server_rfid_unsaved_message)
                .setPositiveButton(R.string.server_rfid_unsaved_save, (dialog, which) -> saveSettings())
                .setNegativeButton(R.string.server_rfid_unsaved_discard, (dialog, which) -> finish())
                .setNeutralButton(R.string.server_rfid_unsaved_cancel, null)
                .show();
    }

    private void saveSettings() {
        tilServerUrl.setError(null);

        String serverUrl = etServerUrl.getText() != null
                ? etServerUrl.getText().toString().trim()
                : "";

        if (TextUtils.isEmpty(serverUrl)) {
            tilServerUrl.setError(getString(R.string.server_url_required));
            return;
        }

        if (!ServerPreferences.isValidServerUrl(serverUrl)) {
            tilServerUrl.setError(getString(R.string.server_url_invalid));
            return;
        }

        String normalized = ServerPreferences.normalizeForStorage(serverUrl);
        ServerPreferences.saveServerUrl(this, normalized);
        etServerUrl.setText(normalized);

        boolean rfidEnabled = switchRfid.isChecked();
        RfidPreferences.setRfidEnabled(this, rfidEnabled);

        String readerTypeOverride = selectedReaderTypeOverride();
        if (!TextUtils.equals(readerTypeOverride, initialReaderTypeOverride)) {
            RfidPreferences.setReaderTypeOverride(this, readerTypeOverride);
            // Eski surucu kapatilir; asagidaki baglanti istegi ayni sirali thread'de
            // bundan sonra calistigi icin yeni tiple baglanir.
            RfidConnectionManager.reloadReaderAsync();
        }

        Toast.makeText(this, R.string.server_url_saved, Toast.LENGTH_SHORT).show();
        applyRfidConnectionState(rfidEnabled);
        finish();
    }

    /**
     * RFID anahtari acikken kaydedilirse baglanmayi, kapaliyken kaydedilirse (baglanmis
     * durumdaysa) baglantiyi kesmeyi arka planda dener. Bu ekran hemen kapanacagi icin
     * sonuc bildirimleri {@link Context#getApplicationContext()} ile gosterilir.
     */
    // --- RFID cihaz tipi ---

    private static int radioIdFor(@Nullable RfidReaderType type) {
        if (type == RfidReaderType.BLD) {
            return R.id.rbReaderTypeBld;
        }
        if (type == RfidReaderType.CHAINWAY) {
            return R.id.rbReaderTypeChainway;
        }
        return R.id.rbReaderTypeAuto;
    }

    /** {@code null} = otomatik tespit. */
    @Nullable
    private String selectedReaderTypeOverride() {
        int checked = rgReaderType.getCheckedRadioButtonId();
        if (checked == R.id.rbReaderTypeBld) {
            return RfidReaderType.BLD.getPrefValue();
        }
        if (checked == R.id.rbReaderTypeChainway) {
            return RfidReaderType.CHAINWAY.getPrefValue();
        }
        return null;
    }

    private RfidReaderType selectedEffectiveReaderType() {
        RfidReaderType selected = RfidReaderType.fromPrefValue(selectedReaderTypeOverride());
        return selected != null ? selected : detectedReaderType;
    }

    private String readerTypeLabel(RfidReaderType type) {
        return getString(type == RfidReaderType.CHAINWAY
                ? R.string.rfid_reader_type_chainway
                : R.string.rfid_reader_type_bld);
    }

    /**
     * Secilen cihaza gore yardim metnini ve tetik modu anahtarini gunceller. Chainway'de
     * tetigi Keyboard Emulator yonettigi icin Toggle / Bas-Cek secimi uygulanmaz.
     */
    private void renderReaderTypeState() {
        RfidReaderType effective = selectedEffectiveReaderType();
        String help = getString(R.string.server_rfid_reader_type_detected,
                readerTypeLabel(detectedReaderType));
        if (effective == RfidReaderType.CHAINWAY) {
            help = help + "\n" + getString(R.string.server_rfid_reader_type_chainway_help);
        }
        tvReaderTypeHelp.setText(help);

        boolean holdOnly = effective == RfidReaderType.CHAINWAY;
        switchTriggerMode.setEnabled(!holdOnly);
        tvTriggerModeHelp.setText(holdOnly
                ? R.string.server_trigger_mode_hold_only_help
                : R.string.server_trigger_mode_switch_help);
    }

    private void applyRfidConnectionState(boolean rfidEnabled) {
        Context appContext = getApplicationContext();

        if (rfidEnabled) {
            Toast.makeText(appContext, R.string.server_rfid_connecting, Toast.LENGTH_SHORT).show();
            RfidConnectionManager.connectWithRetryAsync(result -> {
                if (result instanceof ConnectResult.Success) {
                    Toast.makeText(appContext, R.string.server_rfid_connected, Toast.LENGTH_SHORT).show();
                } else if (result instanceof ConnectResult.Failure) {
                    String reason = ((ConnectResult.Failure) result).getReason();
                    Toast.makeText(appContext,
                            getString(R.string.server_rfid_connect_failed, reason),
                            Toast.LENGTH_LONG).show();
                }
            });
        } else if (RfidConnectionManager.isConnected()) {
            RfidConnectionManager.disconnectAsync(() -> {
                Toast.makeText(appContext, R.string.server_rfid_disconnected, Toast.LENGTH_SHORT).show();
            });
        }
    }
}
