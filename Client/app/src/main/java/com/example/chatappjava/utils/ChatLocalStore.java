package com.example.chatappjava.utils;

import android.content.Context;
import android.util.Log;

/**
 * Clears locally cached chat data when a conversation is deleted on the server.
 */
public final class ChatLocalStore {

    private static final String TAG = "ChatLocalStore";

    private ChatLocalStore() {
    }

    public static void clearChatLocally(Context context, String chatId) {
        if (context == null || chatId == null || chatId.isEmpty()) {
            return;
        }
        try {
            new ConversationRepository(context).deleteConversation(chatId);
            new MessageRepository(context).deleteAllMessagesForChat(chatId);
            Log.d(TAG, "Cleared local cache for chat: " + chatId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear local cache for chat: " + chatId, e);
        }
    }
}
