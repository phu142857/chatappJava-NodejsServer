package com.example.chatappjava.ui.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.chatappjava.R;

public class ReactionPickerDialog extends Dialog {

    public interface OnReactionSelectedListener {
        void onReactionSelected(String reactionType);
    }

    private OnReactionSelectedListener listener;
    private String currentReaction; // To highlight current reaction if any

    public ReactionPickerDialog(@NonNull Context context, OnReactionSelectedListener listener) {
        super(context, android.R.style.Theme_Translucent_NoTitleBar);
        this.listener = listener;
    }

    public void setCurrentReaction(String reactionType) {
        this.currentReaction = reactionType;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_reaction_picker);

        Window window = getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }

        setupReactionButtons();
    }

    private void setupReactionButtons() {
        TextView tvLike = findViewById(R.id.tv_reaction_like_picker);
        TextView tvLove = findViewById(R.id.tv_reaction_love_picker);
        TextView tvHaha = findViewById(R.id.tv_reaction_haha_picker);
        TextView tvWow = findViewById(R.id.tv_reaction_wow_picker);
        TextView tvSad = findViewById(R.id.tv_reaction_sad_picker);
        TextView tvAngry = findViewById(R.id.tv_reaction_angry_picker);

        // Highlight current reaction
        if (currentReaction != null) {
            switch (currentReaction) {
                case "like":
                    if (tvLike != null) tvLike.setAlpha(1.0f);
                    break;
                case "love":
                    if (tvLove != null) tvLove.setAlpha(1.0f);
                    break;
                case "haha":
                    if (tvHaha != null) tvHaha.setAlpha(1.0f);
                    break;
                case "wow":
                    if (tvWow != null) tvWow.setAlpha(1.0f);
                    break;
                case "sad":
                    if (tvSad != null) tvSad.setAlpha(1.0f);
                    break;
                case "angry":
                    if (tvAngry != null) tvAngry.setAlpha(1.0f);
                    break;
            }
        }

        if (tvLike != null) {
            tvLike.setOnClickListener(v -> {
                if (listener != null) listener.onReactionSelected("like");
                dismiss();
            });
        }

        if (tvLove != null) {
            tvLove.setOnClickListener(v -> {
                if (listener != null) listener.onReactionSelected("love");
                dismiss();
            });
        }

        if (tvHaha != null) {
            tvHaha.setOnClickListener(v -> {
                if (listener != null) listener.onReactionSelected("haha");
                dismiss();
            });
        }

        if (tvWow != null) {
            tvWow.setOnClickListener(v -> {
                if (listener != null) listener.onReactionSelected("wow");
                dismiss();
            });
        }

        if (tvSad != null) {
            tvSad.setOnClickListener(v -> {
                if (listener != null) listener.onReactionSelected("sad");
                dismiss();
            });
        }

        if (tvAngry != null) {
            tvAngry.setOnClickListener(v -> {
                if (listener != null) listener.onReactionSelected("angry");
                dismiss();
            });
        }
    }
}

