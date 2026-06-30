package com.example.chatappjava.utils;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.example.chatappjava.R;

public final class EmptyStateHelper {

    private EmptyStateHelper() {
    }

    public static void bind(
            View emptyStateRoot,
            @StringRes int titleRes,
            @StringRes int subtitleRes
    ) {
        bind(emptyStateRoot, titleRes, subtitleRes, 0);
    }

    public static void bind(
            View emptyStateRoot,
            @StringRes int titleRes,
            @StringRes int subtitleRes,
            @DrawableRes int iconRes
    ) {
        if (emptyStateRoot == null) {
            return;
        }

        TextView titleView = emptyStateRoot.findViewById(R.id.tv_empty_title);
        TextView subtitleView = emptyStateRoot.findViewById(R.id.tv_empty_subtitle);
        ImageView iconView = emptyStateRoot.findViewById(R.id.iv_empty_icon);
        View iconShell = emptyStateRoot.findViewById(R.id.empty_icon_shell);

        if (titleView != null) {
            titleView.setText(titleRes);
        }
        if (subtitleView != null) {
            subtitleView.setText(subtitleRes);
        }
        if (iconRes != 0) {
            if (iconView != null) {
                iconView.setImageResource(iconRes);
                iconView.setVisibility(View.VISIBLE);
                ImageViewCompat.setImageTintList(
                        iconView,
                        ContextCompat.getColorStateList(iconView.getContext(), R.color.palette_crimson)
                );
            }
            if (iconShell != null) {
                iconShell.setVisibility(View.VISIBLE);
            }
        } else {
            if (iconView != null) {
                iconView.setVisibility(View.GONE);
            }
            if (iconShell != null) {
                iconShell.setVisibility(View.GONE);
            }
        }
    }

    public static void bindAndReveal(
            android.content.Context context,
            View emptyStateRoot,
            @StringRes int titleRes,
            @StringRes int subtitleRes,
            @DrawableRes int iconRes
    ) {
        bind(emptyStateRoot, titleRes, subtitleRes, iconRes);
        MotionUtils.revealView(context, emptyStateRoot);
    }
}
