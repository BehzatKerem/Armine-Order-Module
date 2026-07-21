package com.example.armineordermodule;

import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ImageButton btnProfile = findViewById(R.id.btnProfile);
        MaterialButton btnProducts = findViewById(R.id.btnProducts);
        MaterialButton btnCustomers = findViewById(R.id.btnCustomers);
        MaterialButton btnWarehouses = findViewById(R.id.btnWarehouses);
        MaterialButton btnWholesaleOrder = findViewById(R.id.btnWholesaleOrder);

        btnProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        setupPressableMenuButton(btnProducts);
        setupPressableMenuButton(btnCustomers);
        setupPressableMenuButton(btnWarehouses);
        setupPressableMenuButton(btnWholesaleOrder);

        btnProducts.setOnClickListener(v -> showComingSoon());
        btnCustomers.setOnClickListener(v -> showComingSoon());
        btnWarehouses.setOnClickListener(v ->
                startActivity(new Intent(this, WarehousesActivity.class)));
        btnWholesaleOrder.setOnClickListener(v -> showComingSoon());
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
