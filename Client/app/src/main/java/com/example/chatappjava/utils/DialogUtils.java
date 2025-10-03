package com.example.chatappjava.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.example.chatappjava.R;

public final class DialogUtils {
    private DialogUtils() {}

    public static void showConfirm(Context context,
                                   String title,
                                   String message,
                                   String positive,
                                   String negative,
                                   Runnable onPositive,
                                   Runnable onNegative,
                                   boolean noDim) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_confirm, null);
        TextView tvTitle = view.findViewById(R.id.tv_title);
        TextView tvMsg = view.findViewById(R.id.tv_message);
        Button btnPos = view.findViewById(R.id.btn_positive);
        Button btnNeg = view.findViewById(R.id.btn_negative);

        tvTitle.setText(title != null ? title : "");
        tvMsg.setText(message != null ? message : "");
        btnPos.setText(positive != null ? positive : "OK");
        btnNeg.setText(negative != null ? negative : "Cancel");

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .create();

        btnPos.setOnClickListener(v -> {
            try { if (onPositive != null) onPositive.run(); } finally { dialog.dismiss(); }
        });
        btnNeg.setOnClickListener(v -> {
            try { if (onNegative != null) onNegative.run(); } finally { dialog.dismiss(); }
        });

        if (dialog.getWindow() != null) {
            Window window = dialog.getWindow();
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            if (noDim) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                WindowManager.LayoutParams lp = window.getAttributes();
                lp.dimAmount = 0f;
                window.setAttributes(lp);
            }
        }

        dialog.show();
    }
}


