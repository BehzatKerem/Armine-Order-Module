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
        MaterialButton btnLogout = findViewById(R.id.btnLogout);

        String username = AuthPreferences.getUserName(this);
        String email = AuthPreferences.getEmail(this);

        tvUsername.setText(TextUtils.isEmpty(username) ? "-" : username);
        tvEmail.setText(TextUtils.isEmpty(email) ? "-" : email);

        btnBack.setOnClickListener(v -> finish());

        btnLogout.setOnClickListener(v -> {
            AuthPreferences.clearSession(this);
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}
