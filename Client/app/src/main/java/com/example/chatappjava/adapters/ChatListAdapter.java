package com.example.chatappjava.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.example.chatappjava.R;
import com.example.chatappjava.models.Chat;
import com.example.chatappjava.utils.AvatarManager;
import com.example.chatappjava.utils.MotionUtils;
import de.hdodenhof.circleimageview.CircleImageView;
import java.util.List;
import java.util.Objects;

public class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.ChatViewHolder> {
    
    private final Context context;
    private List<Chat> chats;
    private final OnChatClickListener listener;
    private final AvatarManager avatarManager;
    
    public interface OnChatClickListener {
        void onChatClick(Chat chat);
        void onChatLongClick(Chat chat);
    }
    
    public ChatListAdapter(Context context, List<Chat> chats, OnChatClickListener listener) {
        this.context = context;
        this.chats = chats;
        this.listener = listener;
        this.avatarManager = AvatarManager.getInstance(context);
    }
    
    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat, parent, false);
        return new ChatViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        Chat chat = chats.get(position);
        holder.bind(chat, listener);
    }
    
    @Override
    public int getItemCount() {
        return chats.size();
    }
    
    class ChatViewHolder extends RecyclerView.ViewHolder {
        private final CircleImageView ivChatAvatar;
        private final TextView tvChatName;
        private final TextView tvLastMessage;
        private final TextView tvLastMessageTime;
        private final TextView tvUnreadCount;
        private final View itemView;
        
        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            this.itemView = itemView;
            ivChatAvatar = itemView.findViewById(R.id.iv_chat_avatar);
            tvChatName = itemView.findViewById(R.id.tv_chat_name);
            tvLastMessage = itemView.findViewById(R.id.tv_last_message);
            tvLastMessageTime = itemView.findViewById(R.id.tv_last_message_time);
            tvUnreadCount = itemView.findViewById(R.id.tv_unread_count);
            MotionUtils.attachPressFeedback(context, itemView);
        }
        
        @SuppressLint("SetTextI18n")
        public void bind(Chat chat, OnChatClickListener listener) {
            // Set chat name
            tvChatName.setText(chat.getDisplayName());
            
            // Set last message
            if (chat.getLastMessage() != null && !chat.getLastMessage().isEmpty()) {
                tvLastMessage.setText(chat.getLastMessage());
                tvLastMessage.setVisibility(View.VISIBLE);
            } else {
                tvLastMessage.setText(R.string.chat_empty_title);
                tvLastMessage.setVisibility(View.VISIBLE);
            }
            
            // Set last message time
            String timeText = chat.getLastMessageTimeFormatted();
            if (!timeText.isEmpty()) {
                tvLastMessageTime.setText(timeText);
                tvLastMessageTime.setVisibility(View.VISIBLE);
            } else {
                tvLastMessageTime.setVisibility(View.GONE);
            }
            
            // Set unread count
            if (chat.hasUnreadMessages()) {
                tvUnreadCount.setText(String.valueOf(chat.getUnreadCount()));
                tvUnreadCount.setVisibility(View.VISIBLE);
            } else {
                tvUnreadCount.setVisibility(View.GONE);
            }

            // Load chat avatar
            String avatarUrl = chat.getListAvatarUrl();
            int placeholderResId = chat.isGroupChat()
                    ? R.drawable.ic_group_avatar
                    : R.drawable.ic_profile_placeholder;

            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                android.util.Log.d("ChatListAdapter", "Loading chat avatar: " + avatarUrl);
                avatarManager.loadAvatar(avatarUrl, ivChatAvatar, placeholderResId);
            } else {
                android.util.Log.d("ChatListAdapter", "No avatar URL, using default");
                ivChatAvatar.setTag(null);
                ivChatAvatar.setImageResource(placeholderResId);
            }
            
            String rowDescription = context.getString(R.string.chat_row_cd, chat.getDisplayName());
            if (chat.hasUnreadMessages()) {
                rowDescription += context.getString(R.string.chat_row_unread_cd, chat.getUnreadCount());
            }
            itemView.setContentDescription(rowDescription);

            // Set click listeners
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onChatClick(chat);
                }
            });
            
            itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onChatLongClick(chat);
                    return true;
                }
                return false;
            });
        }
    }
    
    public void updateChats(List<Chat> newChats) {
        if (newChats == null) {
            newChats = new java.util.ArrayList<>();
        }
        final List<Chat> oldChats = chats != null ? new java.util.ArrayList<>(chats) : new java.util.ArrayList<>();
        final List<Chat> nextChats = new java.util.ArrayList<>(newChats);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new ChatDiffCallback(oldChats, nextChats));
        this.chats = nextChats;
        diffResult.dispatchUpdatesTo(this);
    }

    private static class ChatDiffCallback extends DiffUtil.Callback {
        private final List<Chat> oldChats;
        private final List<Chat> newChats;

        ChatDiffCallback(List<Chat> oldChats, List<Chat> newChats) {
            this.oldChats = oldChats;
            this.newChats = newChats;
        }

        @Override
        public int getOldListSize() {
            return oldChats.size();
        }

        @Override
        public int getNewListSize() {
            return newChats.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return Objects.equals(oldChats.get(oldItemPosition).getId(), newChats.get(newItemPosition).getId());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Chat oldChat = oldChats.get(oldItemPosition);
            Chat newChat = newChats.get(newItemPosition);
            return Objects.equals(oldChat.getDisplayName(), newChat.getDisplayName())
                    && Objects.equals(oldChat.getLastMessage(), newChat.getLastMessage())
                    && oldChat.getLastMessageTime() == newChat.getLastMessageTime()
                    && oldChat.getUnreadCount() == newChat.getUnreadCount()
                    && oldChat.isGroupChat() == newChat.isGroupChat()
                    && Objects.equals(oldChat.getListAvatarUrl(), newChat.getListAvatarUrl());
        }
    }
}
