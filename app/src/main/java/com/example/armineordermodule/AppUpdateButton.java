package com.example.armineordermodule;

import android.app.Activity;
import android.view.View;

import com.google.android.material.button.MaterialButton;

final class AppUpdateButton {

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
        });
        button.setTag(listener);
        button.setOnClickListener(v -> AppUpdateInstaller.downloadAndInstall(activity, button));
        AppUpdateChecker.listen(listener);
    }

    static void detach(MaterialButton button) {
        Object tag = button.getTag();
        if (tag instanceof AppUpdateChecker.Listener) {
            AppUpdateChecker.unlisten((AppUpdateChecker.Listener) tag);
            button.setTag(null);
        }
    }
}
