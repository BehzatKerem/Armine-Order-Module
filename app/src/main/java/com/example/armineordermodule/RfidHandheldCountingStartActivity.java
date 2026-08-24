package com.example.armineordermodule;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/**
 * RFID Sayim baslangic: sayim kodunu okut / gir.
 * API'ye gitmez; lokal kayit varsa direk acar, yoksa yeni bos sayim olusturur.
 */
public class RfidHandheldCountingStartActivity extends AppCompatActivity {

    private static final long AUTO_START_DELAY_MS = 450L;

    private final Handler autoStartHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoStartRunnable = this::openOrCreate;

    private TextInputEditText etCountingCode;
    private TextView tvCountingError;
    private MaterialButton btnStartCounting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rfid_handheld_counting_start);

        ImageButton btnBack = findViewById(R.id.btnBack);
        etCountingCode = findViewById(R.id.etCountingCode);
        tvCountingError = findViewById(R.id.tvCountingError);
        btnStartCounting = findViewById(R.id.btnStartCounting);

        // ProgressBar artik kullanilmiyor; gizle
        View progress = findViewById(R.id.progressCounting);
        if (progress != null) {
            progress.setVisibility(View.GONE);
        }

        btnBack.setOnClickListener(v -> finish());
        btnStartCounting.setOnClickListener(v -> openOrCreate());

        etCountingCode.setOnEditorActionListener((v, actionId, event) -> {
            boolean isDone = actionId == EditorInfo.IME_ACTION_DONE;
            boolean isEnter = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (isDone || isEnter) {
                cancelAutoStart();
                openOrCreate();
                return true;
            }
            return false;
        });

        etCountingCode.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                cancelAutoStart();
                String code = s != null ? s.toString() : "";
                if (code.contains("\n") || code.contains("\r")) {
                    code = code.replace("\r", "").replace("\n", "").trim();
                    etCountingCode.removeTextChangedListener(this);
                    etCountingCode.setText(code);
                    etCountingCode.setSelection(code.length());
                    etCountingCode.addTextChangedListener(this);
                    if (!TextUtils.isEmpty(code)) {
                        openOrCreate();
                    }
                    return;
                }
                if (!TextUtils.isEmpty(code.trim())) {
                    autoStartHandler.postDelayed(autoStartRunnable, AUTO_START_DELAY_MS);
                }
            }
        });

        etCountingCode.requestFocus();
    }

    @Override
    protected void onDestroy() {
        cancelAutoStart();
        super.onDestroy();
    }

    private void cancelAutoStart() {
        autoStartHandler.removeCallbacks(autoStartRunnable);
    }

    private void openOrCreate() {
        cancelAutoStart();
        clearError();

        String code = etCountingCode.getText() != null
                ? etCountingCode.getText().toString().replace("\r", "").replace("\n", "").trim()
                : "";

        if (TextUtils.isEmpty(code)) {
            showError(getString(R.string.rfid_counting_code_required));
            etCountingCode.requestFocus();
            return;
        }

        // Lokal kayit varsa ac, yoksa yeni bos sayim olustur
        RfidHandheldCounting counting = RfidHandheldCountingStore.findByCode(this, code);
        if (counting == null) {
            counting = RfidHandheldCounting.createLocal(code);
            RfidHandheldCountingStore.upsert(this, counting);
            Toast.makeText(this, R.string.rfid_counting_created, Toast.LENGTH_SHORT).show();
        }

        Intent intent = new Intent(this, RfidHandheldCountingDetailActivity.class);
        intent.putExtra(RfidHandheldCountingDetailActivity.EXTRA_COUNTING_CODE, counting.countingCode);
        startActivity(intent);
        finish();
    }

    private void showError(String message) {
        tvCountingError.setText(message);
        tvCountingError.setVisibility(View.VISIBLE);
    }

    private void clearError() {
        tvCountingError.setVisibility(View.GONE);
        tvCountingError.setText("");
    }
}
