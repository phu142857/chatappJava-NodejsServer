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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

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

    public static void showEditDialog(Context context,
                                     String title,
                                     String initialText,
                                     String positive,
                                     String negative,
                                     java.util.function.Consumer<String> onPositive,
                                     Runnable onNegative,
                                     boolean noDim) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_confirm, null);
        TextView tvTitle = view.findViewById(R.id.tv_title);
        TextView tvMsg = view.findViewById(R.id.tv_message);
        Button btnPos = view.findViewById(R.id.btn_positive);
        Button btnNeg = view.findViewById(R.id.btn_negative);

        // Hide the message TextView and add EditText instead
        tvMsg.setVisibility(View.GONE);
        
        // Create EditText
        EditText editText = new EditText(context);
        editText.setText(initialText);
        editText.setSelection(editText.getText().length()); // Place cursor at end
        editText.setHint("Enter new message content...");
        editText.setPadding(50, 20, 50, 20);
        editText.setTextColor(context.getResources().getColor(android.R.color.white));
        editText.setHintTextColor(context.getResources().getColor(android.R.color.white));
        editText.setBackground(context.getResources().getDrawable(com.example.chatappjava.R.drawable.rounded_container));
        
        // Set layout parameters
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(20, 10, 20, 20);
        editText.setLayoutParams(params);
        
        // Add EditText to the layout
        CardView cardView = (CardView) view;
        android.widget.LinearLayout layout = (android.widget.LinearLayout) cardView.getChildAt(0);
        layout.addView(editText, 1); // Insert at position 1 (after title, before buttons)

        tvTitle.setText(title != null ? title : "");
        btnPos.setText(positive != null ? positive : "Save");
        btnNeg.setText(negative != null ? negative : "Cancel");

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .create();

        btnPos.setOnClickListener(v -> {
            try { 
                if (onPositive != null) {
                    String newText = editText.getText().toString().trim();
                    onPositive.accept(newText);
                }
            } finally { dialog.dismiss(); }
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
        
        // Focus on EditText and show keyboard
        editText.requestFocus();
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) 
            context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }
    }
}