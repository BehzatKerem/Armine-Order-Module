package com.example.armineordermodule;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class ServerSettingsActivity extends AppCompatActivity {

    private TextInputLayout tilServerUrl;
    private TextInputEditText etServerUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_settings);

        tilServerUrl = findViewById(R.id.tilServerUrl);
        etServerUrl = findViewById(R.id.etServerUrl);
        MaterialButton btnSave = findViewById(R.id.btnSaveServerUrl);

        etServerUrl.setText(ServerPreferences.getServerUrl(this));

        btnSave.setOnClickListener(v -> saveServerUrl());
    }

    private void saveServerUrl() {
        tilServerUrl.setError(null);

        String serverUrl = etServerUrl.getText() != null
                ? etServerUrl.getText().toString().trim()
                : "";

        if (TextUtils.isEmpty(serverUrl)) {
            tilServerUrl.setError(getString(R.string.server_url_required));
            return;
        }

        ServerPreferences.saveServerUrl(this, serverUrl);
        Toast.makeText(this, R.string.server_url_saved, Toast.LENGTH_SHORT).show();
        finish();
    }
}
