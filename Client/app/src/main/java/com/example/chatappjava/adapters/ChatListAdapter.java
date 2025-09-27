package com.example.chatappjava.adapters;

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
    
    private List<Chat> chats;
    private OnChatClickListener listener;
    private Context context;
    private static AvatarManager avatarManager;
    
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
    
    public static class ChatViewHolder extends RecyclerView.ViewHolder {
        private CircleImageView ivChatAvatar;
        private TextView tvChatName;
        private TextView tvLastMessage;
        private TextView tvLastMessageTime;
        private TextView tvUnreadCount;
        private ImageView ivChatType;
        private View itemView;
        
        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            this.itemView = itemView;
            ivChatAvatar = itemView.findViewById(R.id.iv_chat_avatar);
            tvChatName = itemView.findViewById(R.id.tv_chat_name);
            tvLastMessage = itemView.findViewById(R.id.tv_last_message);
            tvLastMessageTime = itemView.findViewById(R.id.tv_last_message_time);
            tvUnreadCount = itemView.findViewById(R.id.tv_unread_count);
            ivChatType = itemView.findViewById(R.id.iv_chat_type);
        }
        
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
            
            // Set chat type icon
            if (chat.isGroupChat()) {
                ivChatType.setImageResource(R.drawable.ic_group_chat);
                ivChatType.setVisibility(View.VISIBLE);
            } else {
                ivChatType.setVisibility(View.GONE);
            }
            
            // Load chat avatar
            if (chat.isGroupChat()) {
                // For group chats, load group avatar if available
                String avatarUrl = chat.getFullAvatarUrl();
                android.util.Log.d("ChatListAdapter", "Group chat avatar URL: " + avatarUrl);
                if (avatarUrl != null && !avatarUrl.isEmpty()) {
                    avatarManager.loadAvatar(avatarUrl, ivChatAvatar, R.drawable.ic_group_avatar);
                } else {
                    android.util.Log.d("ChatListAdapter", "No group chat avatar, using placeholder");
                    ivChatAvatar.setImageResource(R.drawable.ic_group_avatar);
                }
            } else {
                // For private chats, load the other participant's avatar
                if (chat.getOtherParticipant() != null && 
                    chat.getOtherParticipant().getAvatar() != null && 
                    !chat.getOtherParticipant().getAvatar().isEmpty()) {
                    
                    String avatarUrl = chat.getOtherParticipant().getAvatar();
                    android.util.Log.d("ChatListAdapter", "Loading private chat avatar: " + avatarUrl);
                    
                    // Construct full URL if needed (like private chat logic)
                    if (!avatarUrl.startsWith("http")) {
                        avatarUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + 
                                   ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + avatarUrl;
                        android.util.Log.d("ChatListAdapter", "Constructed full URL: " + avatarUrl);
                    }
                    
                    avatarManager.loadAvatar(
                        avatarUrl, 
                        ivChatAvatar, 
                        R.drawable.ic_profile_placeholder
                    );
                } else {
                    android.util.Log.d("ChatListAdapter", "No private chat avatar, using placeholder");
                    ivChatAvatar.setImageResource(R.drawable.ic_profile_placeholder);
                }
            }
            
            // Set click listeners
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        listener.onChatClick(chat);
                    }
                }
            });
            
            itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (listener != null) {
                        listener.onChatLongClick(chat);
                        return true;
                    }
                    return false;
                }
            });
        }
    }
    
    public void updateChats(List<Chat> newChats) {
        this.chats = newChats;
        notifyDataSetChanged();
    }
}
