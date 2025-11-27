package com.example.chatappjava.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.chatappjava.R;
import com.example.chatappjava.models.Chat;
import com.example.chatappjava.utils.AvatarManager;
import de.hdodenhof.circleimageview.CircleImageView;
import java.util.List;

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
            tvUnreadCount = itemView.findViewById(R.id.tv_unread_count);;
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
                tvLastMessage.setText("No messages yet");
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

            // Load chat avatar - AvatarManager will handle preventing reload if URL unchanged
            String avatarUrl = null;
            if (chat.isGroupChat()) {
                // For group chats, load group avatar if available
                avatarUrl = chat.getFullAvatarUrl();
            } else {
                // For private chats, load the other participant's avatar
                if (chat.getOtherParticipant() != null && 
                    chat.getOtherParticipant().getAvatar() != null && 
                    !chat.getOtherParticipant().getAvatar().isEmpty()) {
                    avatarUrl = chat.getOtherParticipant().getAvatar();
                }
            }
            
            // Construct full URL if needed (exactly like CallListAdapter)
            if (avatarUrl != null && !avatarUrl.isEmpty() && !avatarUrl.startsWith("http")) {
                avatarUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + 
                           ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + avatarUrl;
            }
            
            // Use appropriate placeholder based on chat type
            int placeholderResId = chat.isGroupChat() 
                ? R.drawable.ic_group_avatar 
                : R.drawable.ic_profile_placeholder;
            
            // Load avatar - AvatarManager will check if already loaded to prevent flickering
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                avatarManager.loadAvatar(avatarUrl, ivChatAvatar, placeholderResId);
            } else {
                // No avatar URL, use default placeholder
                ivChatAvatar.setImageResource(placeholderResId);
                ivChatAvatar.setTag(null); // Clear tag
            }
            
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
    
    @SuppressLint("NotifyDataSetChanged")
    public void updateChats(List<Chat> newChats) {
        // Only update if data actually changed (size or content) to prevent unnecessary reloads
        boolean shouldUpdate = false;
        
        if (chats == null || chats.size() != newChats.size()) {
            shouldUpdate = true;
        } else {
            // Compare by IDs to check if content changed
            for (int i = 0; i < chats.size(); i++) {
                if (i >= newChats.size() || 
                    !chats.get(i).getId().equals(newChats.get(i).getId())) {
                    shouldUpdate = true;
                    break;
                }
            }
        }
        
        if (shouldUpdate) {
            this.chats = newChats;
            notifyDataSetChanged();
        } else {
            // Data unchanged, skip update to prevent flickering
            android.util.Log.d("ChatListAdapter", "Chat list unchanged, skipping update");
        }
    }
}
