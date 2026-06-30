package com.example.chatappjava.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.chatappjava.R;

/**
 * Canonical emoji glyphs for chat/post reactions. Labels like "Luv" or "+1" are legacy
 * display values and are normalized back to emoji for badges and pickers.
 */
public final class ReactionEmojis {

    public static final String[] MESSAGE_PICKER = {"👍", "❤️", "😂", "😮", "😢", "🔥"};

    private ReactionEmojis() {
    }

    @NonNull
    public static String fromType(@Nullable String type) {
        if (type == null) {
            return "👍";
        }
        switch (type.toLowerCase()) {
            case "like":
                return "👍";
            case "love":
                return "❤️";
            case "haha":
                return "😂";
            case "wow":
                return "😮";
            case "sad":
                return "😢";
            case "angry":
                return "😠";
            case "fire":
                return "🔥";
            default:
                return normalize(type);
        }
    }

    @NonNull
    public static String normalize(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if ("+1".equals(value) || "Like".equals(value) || "Liked".equals(value)) {
            return "👍";
        }
        if ("Luv".equals(value) || "love".equals(value)) {
            return "❤️";
        }
        if ("Haha".equals(value) || "haha".equals(value)) {
            return "😂";
        }
        if ("Wow".equals(value) || "wow".equals(value)) {
            return "😮";
        }
        if ("Sad".equals(value) || "sad".equals(value)) {
            return "😢";
        }
        if ("Angry".equals(value) || "angry".equals(value)) {
            return "😠";
        }
        if ("Fire".equals(value) || "fire".equals(value)) {
            return "🔥";
        }
        return value;
    }

    public static int accessibilityLabelForEmoji(@NonNull Context context, @NonNull String emoji) {
        String normalized = normalize(emoji);
        if ("👍".equals(normalized)) {
            return R.string.reaction_like_cd;
        }
        if ("❤️".equals(normalized)) {
            return R.string.reaction_love_cd;
        }
        if ("😂".equals(normalized)) {
            return R.string.reaction_haha_cd;
        }
        if ("😮".equals(normalized)) {
            return R.string.reaction_wow_cd;
        }
        if ("😢".equals(normalized)) {
            return R.string.reaction_sad_cd;
        }
        if ("😠".equals(normalized)) {
            return R.string.reaction_angry_cd;
        }
        if ("🔥".equals(normalized)) {
            return R.string.reaction_fire_cd;
        }
        return R.string.reaction_picker_title;
    }
}
