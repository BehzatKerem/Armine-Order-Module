package com.example.armineordermodule;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

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
        MaterialButton btnLogout = findViewById(R.id.btnLogout);

        String username = AuthPreferences.getUserName(this);
        String email = AuthPreferences.getEmail(this);
        String database = AuthPreferences.getDefaultDatabase(this);

        tvUsername.setText(TextUtils.isEmpty(username) ? "-" : username);
        tvEmail.setText(TextUtils.isEmpty(email) ? "-" : email);
        tvDatabase.setText(TextUtils.isEmpty(database) ? "-" : database);

        btnBack.setOnClickListener(v -> finish());

        btnLogout.setOnClickListener(v -> {
            AuthPreferences.clearSession(this);
            WarehousePreferences.clear(this);
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}
