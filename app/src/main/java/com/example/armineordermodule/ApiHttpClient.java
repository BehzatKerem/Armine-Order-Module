package com.example.armineordermodule;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;

final class ApiHttpClient {

    private static final OkHttpClient CLIENT = createClient();

    private ApiHttpClient() {
    }

    static OkHttpClient get() {
        return CLIENT;
    }

    private static OkHttpClient createClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS);

        if (BuildConfig.DEBUG) {
            try {
                X509TrustManager trustAll = new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                };
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[]{trustAll}, new SecureRandom());
                builder.sslSocketFactory(sslContext.getSocketFactory(), trustAll);
                builder.hostnameVerifier((hostname, session) -> true);
            } catch (Exception ignored) {
                // Debug trust kurulamazsa varsayilan client kullanilir.
            }
        }

        return builder.build();
    }
}
