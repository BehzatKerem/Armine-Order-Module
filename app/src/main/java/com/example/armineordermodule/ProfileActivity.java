package com.example.armineordermodule;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.armineordermodule.rfid.RfidConnectionManager;
import com.google.android.material.button.MaterialButton;

public class ProfileActivity extends AppCompatActivity {

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

        String username = AuthPreferences.getUserName(this);
        String email = AuthPreferences.getEmail(this);
        String database = AuthPreferences.getDefaultDatabase(this);

        tvUsername.setText(TextUtils.isEmpty(username) ? "-" : username);
        tvEmail.setText(TextUtils.isEmpty(email) ? "-" : email);
        tvDatabase.setText(TextUtils.isEmpty(database) ? "-" : database);
        tvVersion.setText(getString(R.string.profile_version, BuildConfig.VERSION_NAME));

        btnBack.setOnClickListener(v -> finish());

        btnLogout.setOnClickListener(v -> {
            // Cikis yapan kullanicinin RFID okuyucuyu acik birakmamasi icin baglanti kesilir.
            // Bkz. RfidConnectionManager - uygulama genelinde tek paylasilan baglanti.
            if (RfidConnectionManager.isConnected()) {
                RfidConnectionManager.disconnectAsync();
            }

            AuthPreferences.clearSession(this);
            WarehousePreferences.clear(this);
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}
