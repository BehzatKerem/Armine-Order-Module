package com.example.armineordermodule;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

final class AppUpdateInstaller {

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.MINUTES)
            .build();

    private AppUpdateInstaller() {
    }

    static void downloadAndInstall(Activity activity, MaterialButton button) {
        String url = AppUpdateChecker.apkUrl();
        if (url == null || url.isEmpty()) {
            Toast.makeText(activity, R.string.update_failed, Toast.LENGTH_LONG).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            settings.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(settings);
            Toast.makeText(activity, R.string.update_install_permission, Toast.LENGTH_LONG).show();
            return;
        }

        CharSequence previousLabel = button.getText();
        button.setEnabled(false);
        button.setText(R.string.update_downloading);

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "ArmineOrderModule")
                .build();

        CLIENT.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                activity.runOnUiThread(() -> finishFailed(activity, button, previousLabel));
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) {
                File apk = new File(activity.getCacheDir(), "updates/update.apk");
                boolean saved = false;
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        File parent = apk.getParentFile();
                        if (parent != null && (parent.exists() || parent.mkdirs())) {
                            try (InputStream in = body.byteStream();
                                 OutputStream out = new FileOutputStream(apk)) {
                                byte[] buffer = new byte[8192];
                                int read;
                                while ((read = in.read(buffer)) != -1) {
                                    out.write(buffer, 0, read);
                                }
                            }
                            saved = apk.length() > 0;
                        }
                    }
                } catch (Exception ignored) {
                    saved = false;
                }

                boolean ready = saved;
                activity.runOnUiThread(() -> {
                    button.setEnabled(true);
                    button.setText(previousLabel);
                    if (!ready || activity.isFinishing() || activity.isDestroyed()) {
                        if (!activity.isFinishing()) {
                            Toast.makeText(activity, R.string.update_failed, Toast.LENGTH_LONG).show();
                        }
                        return;
                    }
                    openInstaller(activity, apk);
                });
            }
        });
    }

    private static void finishFailed(Activity activity, MaterialButton button, CharSequence previousLabel) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        button.setEnabled(true);
        button.setText(previousLabel);
        Toast.makeText(activity, R.string.update_failed, Toast.LENGTH_LONG).show();
    }

    private static void openInstaller(Activity activity, File apk) {
        Uri uri = FileProvider.getUriForFile(
                activity,
                activity.getPackageName() + ".fileprovider",
                apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(intent);
    }
}
