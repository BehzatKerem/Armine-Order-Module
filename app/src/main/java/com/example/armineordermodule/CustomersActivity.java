package com.example.armineordermodule;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Collections;

public class CustomersActivity extends AppCompatActivity {

    /** true ise: musteriye dokununca detay yerine WholesaleOrderActivity acilir (siparis akisi). */
    public static final String EXTRA_SELECT_MODE = "extra_select_mode";

    private static final int PAGE_SIZE = 200;
    private static final int MIN_SEARCH_LENGTH = 3;
    private static final long SEARCH_DEBOUNCE_MS = 350L;

    private boolean selectMode;
    private TextInputLayout tilSearch;
    private TextInputEditText etSearch;
    private TextView tvCustomersStatus;
    private TextView tvCustomersError;
    private ProgressBar progressCustomers;
    private CustomerListAdapter adapter;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private int searchRequestId = 0;
    private String lastSearchedQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customers);

        selectMode = getIntent() != null && getIntent().getBooleanExtra(EXTRA_SELECT_MODE, false);

        ImageButton btnBack = findViewById(R.id.btnBack);
        TextView tvTitle = findViewById(R.id.tvCustomersTitle);
        tilSearch = findViewById(R.id.tilCustomerSearch);
        etSearch = findViewById(R.id.etCustomerSearch);
        tvCustomersStatus = findViewById(R.id.tvCustomersStatus);
        tvCustomersError = findViewById(R.id.tvCustomersError);
        progressCustomers = findViewById(R.id.progressCustomers);
        RecyclerView rvCustomers = findViewById(R.id.rvCustomers);

        adapter = new CustomerListAdapter(this::onCustomerClicked);
        rvCustomers.setLayoutManager(new LinearLayoutManager(this));
        rvCustomers.setAdapter(adapter);

        if (selectMode) {
            tvTitle.setText(R.string.customers_select_title);
        }

        btnBack.setOnClickListener(v -> finish());
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                onSearchTextChanged(s != null ? s.toString() : "");
            }
        });

        tvCustomersStatus.setText(R.string.customers_search_hint);
    }

    @Override
    protected void onDestroy() {
        cancelPendingSearch();
        super.onDestroy();
    }

    private void onSearchTextChanged(String raw) {
        tilSearch.setError(null);
        clearError();
        cancelPendingSearch();

        String query = raw.trim();
        if (query.length() < MIN_SEARCH_LENGTH) {
            searchRequestId++;
            lastSearchedQuery = "";
            adapter.submitList(Collections.emptyList());
            setLoading(false);
            tvCustomersStatus.setText(R.string.customers_search_hint);
            return;
        }

        pendingSearch = () -> searchCustomers(query);
        searchHandler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS);
    }

    private void cancelPendingSearch() {
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
            pendingSearch = null;
        }
    }

    private void searchCustomers(String query) {
        if (query.equals(lastSearchedQuery)) {
            return;
        }

        String serverUrl = ServerPreferences.getServerUrl(this);
        if (TextUtils.isEmpty(serverUrl)) {
            showError(getString(R.string.server_url_missing));
            return;
        }

        String token = AuthPreferences.getToken(this);
        if (TextUtils.isEmpty(token) || !AuthPreferences.hasValidSession(this)) {
            showError(getString(R.string.customers_session_required));
            return;
        }

        final int requestId = ++searchRequestId;
        lastSearchedQuery = query;
        setLoading(true);
        CustomerApiClient.search(serverUrl, token, query, 0, PAGE_SIZE,
                new CustomerApiClient.SearchCallback() {
                    @Override
                    public void onSuccess(CustomersResult result) {
                        if (requestId != searchRequestId) {
                            return;
                        }
                        setLoading(false);
                        adapter.submitList(result.customers);
                        if (result.customers.isEmpty()) {
                            tvCustomersStatus.setText(R.string.customers_empty);
                        } else {
                            tvCustomersStatus.setText(getString(
                                    R.string.customers_result_count,
                                    result.customers.size(),
                                    result.totalCount));
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (requestId != searchRequestId) {
                            return;
                        }
                        setLoading(false);
                        lastSearchedQuery = "";
                        showError(message);
                    }
                });
    }

    private void onCustomerClicked(CustomerItem customer) {
        if (selectMode) {
            Intent intent = new Intent(this, WholesaleOrderSetupActivity.class);
            intent.putExtra(WholesaleOrderSetupActivity.EXTRA_CUSTOMER, customer);
            startActivity(intent);
            // Musteri secildi; geri tusu arama ekranina degil dogrudan siparis listesine donsun.
            finish();
            return;
        }
        Intent intent = new Intent(this, CustomerDetailActivity.class);
        intent.putExtra(CustomerDetailActivity.EXTRA_CUSTOMER, customer);
        startActivity(intent);
    }

    private void setLoading(boolean loading) {
        progressCustomers.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            tvCustomersStatus.setText(R.string.customers_searching);
        }
    }

    private void showError(String message) {
        tvCustomersError.setText(message);
        tvCustomersError.setVisibility(View.VISIBLE);
    }

    private void clearError() {
        tvCustomersError.setVisibility(View.GONE);
        tvCustomersError.setText("");
    }
}
