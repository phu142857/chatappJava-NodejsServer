package com.example.chatappjava.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.chatappjava.models.Chat;
import com.example.chatappjava.models.User;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central avatar sync: persists updates locally and notifies UI listeners.
 */
public final class AvatarSyncCoordinator {

    public interface Listener {
        default void onUserAvatarChanged(String userId, String avatarPath) {}

        default void onGroupAvatarChanged(String chatId, String avatarPath) {}
    }

    private static AvatarSyncCoordinator instance;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private AvatarSyncCoordinator(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static synchronized AvatarSyncCoordinator getInstance(Context context) {
        if (instance == null) {
            instance = new AvatarSyncCoordinator(context);
        }
        return instance;
    }

    public void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void handleUserAvatarChanged(String userId, String avatarPath) {
        if (userId == null || userId.isEmpty()) {
            return;
        }
        String path = avatarPath != null ? avatarPath : "";

        invalidateAvatarCaches(path);

        DatabaseManager databaseManager = new DatabaseManager(appContext);
        if (userId.equals(databaseManager.getUserId())) {
            databaseManager.updateUserAvatar(path);
        }

        ConversationRepository conversationRepository = new ConversationRepository(appContext);
        conversationRepository.updatePeerAvatarForUser(userId, path);

        MessageRepository messageRepository = new MessageRepository(appContext);
        messageRepository.updateSenderAvatarForUser(userId, path);

        dispatchOnMain(() -> {
            for (Listener listener : listeners) {
                listener.onUserAvatarChanged(userId, path);
            }
        });
    }

    public void handleGroupAvatarChanged(String chatId, String avatarPath) {
        if (chatId == null || chatId.isEmpty()) {
            return;
        }
        String path = avatarPath != null ? avatarPath : "";

        invalidateAvatarCaches(path);

        ConversationRepository conversationRepository = new ConversationRepository(appContext);
        conversationRepository.updateGroupAvatar(chatId, path);

        dispatchOnMain(() -> {
            for (Listener listener : listeners) {
                listener.onGroupAvatarChanged(chatId, path);
            }
        });
    }

    /**
     * Apply a user avatar update to an in-memory private chat model.
     */
    public static void applyUserAvatarToChat(Chat chat, String userId, String avatarPath) {
        if (chat == null || chat.isGroupChat() || userId == null || userId.isEmpty()) {
            return;
        }
        User other = chat.getOtherParticipant();
        if (other != null && userId.equals(other.getId())) {
            other.setAvatar(avatarPath);
        }
        chat.setAvatar(avatarPath);
    }

    private void invalidateAvatarCaches(String avatarPath) {
        AvatarManager avatarManager = AvatarManager.getInstance(appContext);
        avatarManager.invalidateAvatarUrl(avatarPath);
        String fullUrl = UrlUtils.getFullAvatarUrl(avatarPath);
        if (fullUrl != null) {
            avatarManager.invalidateAvatarUrl(fullUrl);
        }
    }

    private void dispatchOnMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            mainHandler.post(action);
        }
    }
}
