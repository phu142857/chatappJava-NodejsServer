package com.example.chatappjava.utils;

import android.view.View;

import androidx.annotation.Nullable;

/**
 * List-loading placeholders matching {@code component_list_skeleton} geometry.
 * Use instead of centered {@code ProgressBar} on content areas (DESIGN.md / Impeccable product).
 */
public final class SkeletonHelper {

    private SkeletonHelper() {
    }

    public static void setListLoading(@Nullable View skeletonRoot, boolean loading) {
        if (skeletonRoot == null) {
            return;
        }
        skeletonRoot.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}
