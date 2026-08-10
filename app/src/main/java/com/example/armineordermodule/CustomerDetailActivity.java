package com.example.armineordermodule;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class CustomerDetailActivity extends AppCompatActivity {

    public static final String EXTRA_CUSTOMER = "extra_customer";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_detail);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        CustomerItem customer = null;
        if (getIntent() != null && getIntent().hasExtra(EXTRA_CUSTOMER)) {
            Object extra = getIntent().getSerializableExtra(EXTRA_CUSTOMER);
            if (extra instanceof CustomerItem) {
                customer = (CustomerItem) extra;
            }
        }

        if (customer == null) {
            finish();
            return;
        }

        TextView tvTitle = findViewById(R.id.tvCustomerDetailTitle);
        tvTitle.setText(customer.displayName());

        bindField(R.id.tvCustomerCodeValue, customer.currAccCode);
        bindField(R.id.tvCustomerNameValue, customer.currAccDescription);
        bindField(R.id.tvCustomerDistrictValue, customer.districtDescription);
        bindField(R.id.tvCustomerCityValue, customer.cityDescription);
        bindField(R.id.tvCustomerIdentityValue, customer.identityNum);
        bindField(R.id.tvCustomerTaxValue, customer.taxNumber);
    }

    private void bindField(int viewId, String value) {
        TextView view = findViewById(viewId);
        view.setText(TextUtils.isEmpty(value) ? "-" : value);
    }
}
