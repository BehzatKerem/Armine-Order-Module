package com.example.armineordermodule;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

/**
 * Gonderim surerken ekrani kilitleyen, kapatilamayan "gonderiliyor" dialogu.
 * Geri tusu ve disariya dokunma dialogu kapatmaz; istek bitince dismiss() cagrilir.
 */
public final class SendingDialog {

    private final Activity activity;
    private AlertDialog dialog;

    public SendingDialog(Activity activity) {
        this.activity = activity;
    }

    public void show(@StringRes int messageRes) {
        dismiss();
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_sending, null);
        TextView tvMessage = view.findViewById(R.id.tvSendingMessage);
        tvMessage.setText(messageRes);
        dialog = new AlertDialog.Builder(activity)
                .setView(view)
                .setCancelable(false)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    public void dismiss() {
        if (dialog != null) {
            if (dialog.isShowing() && !activity.isDestroyed()) {
                dialog.dismiss();
            }
            dialog = null;
        }
    }
}
