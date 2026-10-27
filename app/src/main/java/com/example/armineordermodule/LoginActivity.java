package com.example.armineordermodule;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilUsername;
    private TextInputLayout tilPassword;
    private TextInputEditText etUsername;
    private TextInputEditText etPassword;
    private MaterialButton btnLogin;
    private TextView tvLoginError;
    private TextView tvServerUrl;
    private MaterialButton btnAppUpdate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (AuthPreferences.hasValidSession(this)) {
            openMainScreen();
            return;
        }

        setContentView(R.layout.activity_login);

        tilUsername = findViewById(R.id.tilUsername);
        tilPassword = findViewById(R.id.tilPassword);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvLoginError = findViewById(R.id.tvLoginError);
        tvServerUrl = findViewById(R.id.tvServerUrl);
        btnAppUpdate = findViewById(R.id.btnAppUpdate);
        ImageButton btnServerSettings = findViewById(R.id.btnServerSettings);

        btnLogin.setOnClickListener(v -> attemptLogin());
        btnServerSettings.setOnClickListener(v ->
                startActivity(new Intent(this, ServerSettingsActivity.class)));
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (btnAppUpdate != null) {
            AppUpdateButton.attach(this, btnAppUpdate);
        }
    }

    @Override
    protected void onStop() {
        if (btnAppUpdate != null) {
            AppUpdateButton.detach(btnAppUpdate);
        }
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        showSavedServerUrl();
    }

    private void showSavedServerUrl() {
        if (tvServerUrl == null) {
            return;
        }
        String serverUrl = ServerPreferences.getServerUrl(this);
        if (TextUtils.isEmpty(serverUrl)) {
            tvServerUrl.setText("");
            tvServerUrl.setVisibility(View.GONE);
        } else {
            tvServerUrl.setText(serverUrl);
            tvServerUrl.setVisibility(View.VISIBLE);
        }
    }

    private void attemptLogin() {
        clearErrors();

        String username = etUsername.getText() != null
                ? etUsername.getText().toString().trim()
                : "";
        String password = etPassword.getText() != null
                ? etPassword.getText().toString()
                : "";

        boolean hasError = false;

        if (TextUtils.isEmpty(username)) {
            tilUsername.setError(getString(R.string.login_username_required));
            hasError = true;
        }

        if (TextUtils.isEmpty(password)) {
            tilPassword.setError(getString(R.string.login_password_required));
            hasError = true;
        }

        if (hasError) {
            return;
        }

        String serverUrl = ServerPreferences.getServerUrl(this);
        if (TextUtils.isEmpty(serverUrl)) {
            showError(getString(R.string.server_url_missing));
            return;
        }

        setLoading(true);
        LoginApiClient.login(serverUrl, username, password, new LoginApiClient.LoginCallback() {
            @Override
            public void onSuccess(LoginResult result) {
                setLoading(false);
                AuthPreferences.saveSession(LoginActivity.this, result);
                openMainScreen();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showError(message);
            }
        });
    }

    private void setLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        btnLogin.setText(loading ? R.string.login_loading : R.string.login_button);
    }

    private void showError(String message) {
        tvLoginError.setText(message);
        tvLoginError.setVisibility(View.VISIBLE);
    }

    private void clearErrors() {
        tilUsername.setError(null);
        tilPassword.setError(null);
        tvLoginError.setVisibility(View.GONE);
    }

    private void openMainScreen() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
