package com.example.chatappjava.utils;

import android.content.Context;
import android.text.TextUtils;

import com.example.chatappjava.R;
import com.example.chatappjava.models.Chat;
import com.example.chatappjava.models.Message;

/**
 * Keeps conversation list preview (last message line) in sync with chat realtime events.
 */
public final class ConversationPreviewHelper {

    private ConversationPreviewHelper() {
    }

    public static String buildPreviewText(Context context, Message message) {
        if (message == null) {
            return "";
        }
        String type = message.getType() != null ? message.getType() : "text";
        switch (type) {
            case "image":
                return context.getString(R.string.home_post_photo);
            case "file":
                return context.getString(R.string.chat_preview_file);
            case "voice":
            case "audio":
                return context.getString(R.string.chat_preview_voice);
            case "video":
                return context.getString(R.string.chat_preview_video);
            default:
                String content = message.getContent();
                return content != null ? content : "";
        }
    }

    public static String buildListPreview(Context context, Message message, boolean isGroupChat, boolean incoming) {
        String preview = buildPreviewText(context, message);
        if (isGroupChat && incoming) {
            String sender = message.getSenderDisplayName();
            if (TextUtils.isEmpty(sender)) {
                sender = message.getSenderUsername();
            }
            if (!TextUtils.isEmpty(sender) && !TextUtils.isEmpty(preview)) {
                return sender + ": " + preview;
            }
        }
        return preview;
    }

    public static long resolveTimestamp(Message message) {
        if (message == null) {
            return System.currentTimeMillis();
        }
        return message.getTimestamp() > 0 ? message.getTimestamp() : System.currentTimeMillis();
    }

    /**
     * Persist preview to SQLite and optionally bump unread count.
     */
    public static void persistPreview(
            ConversationRepository repository,
            String chatId,
            String preview,
            long timestamp,
            boolean incrementUnread
    ) {
        if (repository == null || TextUtils.isEmpty(chatId) || TextUtils.isEmpty(preview)) {
            return;
        }
        repository.updateLastMessage(chatId, preview, timestamp);
        if (incrementUnread) {
            Chat existing = repository.getConversationById(chatId);
            int unread = existing != null ? existing.getUnreadCount() : 0;
            repository.updateUnreadCount(chatId, unread + 1);
        }
    }

    public static void applyMessagePreview(
            Context context,
            ConversationRepository repository,
            Message message,
            String currentUserId,
            boolean incrementUnreadWhenIncoming
    ) {
        if (repository == null || message == null) {
            return;
        }
        String chatId = message.getChatId();
        if (TextUtils.isEmpty(chatId)) {
            return;
        }
        boolean incoming = currentUserId != null
                && message.getSenderId() != null
                && !currentUserId.equals(message.getSenderId());
        boolean isGroup = message.isGroupChat()
                || "group".equalsIgnoreCase(message.getChatType());
        String preview = buildListPreview(context, message, isGroup, incoming);
        if (TextUtils.isEmpty(preview)) {
            return;
        }
        boolean increment = incrementUnreadWhenIncoming && incoming;
        persistPreview(repository, chatId, preview, resolveTimestamp(message), increment);
    }

    public static void clearUnread(ConversationRepository repository, String chatId) {
        if (repository == null || TextUtils.isEmpty(chatId)) {
            return;
        }
        repository.updateUnreadCount(chatId, 0);
    }
}
