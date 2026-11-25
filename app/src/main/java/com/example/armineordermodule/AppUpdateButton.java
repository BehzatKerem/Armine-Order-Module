package com.example.armineordermodule;

import android.app.Activity;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;

/**
 * Yeni surum varsa ekrandaki Guncelle butonunu gosterir ve bir kez "indirmek ister misiniz?"
 * diye sorar. Soru her surum icin uygulama acilisi basina bir kez cikar; "Sonra" denirse
 * buton ekranda kalir.
 */
final class AppUpdateButton {

    /** Bu acilista hangi surum icin soruldu (Login ve ana ekran ayni soruyu tekrar sormasin). */
    private static String promptedVersion;

    private AppUpdateButton() {
    }

    static void attach(Activity activity, MaterialButton button) {
        detach(button);
        AppUpdateChecker.Listener listener = version -> activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            button.setText(activity.getString(R.string.update_button, version));
            button.setVisibility(View.VISIBLE);
            promptOnce(activity, button, version);
        });
        button.setTag(listener);
        button.setOnClickListener(v -> AppUpdateInstaller.downloadAndInstall(activity, button));
        AppUpdateChecker.listen(listener);
    }

    private static void promptOnce(Activity activity, MaterialButton button, String version) {
        if (version.equals(promptedVersion)) {
            return;
        }
        promptedVersion = version;
        new AlertDialog.Builder(activity)
                .setTitle(R.string.update_prompt_title)
                .setMessage(activity.getString(
                        R.string.update_prompt_message, version, BuildConfig.VERSION_NAME))
                .setPositiveButton(R.string.update_prompt_download,
                        (dialog, which) -> AppUpdateInstaller.downloadAndInstall(activity, button))
                .setNegativeButton(R.string.update_prompt_later, null)
                .show();
    }

    static void detach(MaterialButton button) {
        Object tag = button.getTag();
        if (tag instanceof AppUpdateChecker.Listener) {
            AppUpdateChecker.unlisten((AppUpdateChecker.Listener) tag);
            button.setTag(null);
        }
    }
}
