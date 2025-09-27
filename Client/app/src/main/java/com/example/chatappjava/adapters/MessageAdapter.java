package com.example.chatappjava.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.DisplayMetrics;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.chatappjava.R;
import com.example.chatappjava.models.Message;
import com.example.chatappjava.utils.AvatarManager;
import com.example.chatappjava.config.ServerConfig;
import com.squareup.picasso.Picasso;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {
    
    public interface OnMessageClickListener {
        void onMessageClick(Message message);
        void onMessageLongClick(Message message);
        void onImageClick(String imageUrl, String localImageUri);
    }
    
    private List<Message> messages;
    private String currentUserId;
    private boolean isGroupChat;
    private static OnMessageClickListener listener;
    private static AvatarManager avatarManager;
    
    public MessageAdapter(List<Message> messages, String currentUserId) {
        this.messages = messages;
        this.currentUserId = currentUserId;
    }
    
    public MessageAdapter(List<Message> messages, OnMessageClickListener listener) {
        this.messages = messages;
        this.listener = listener;
    }
    
    public MessageAdapter(List<Message> messages, String currentUserId, AvatarManager avatarManager) {
        this.messages = messages;
        this.currentUserId = currentUserId;
        this.avatarManager = avatarManager;
    }
    
    public void setOnMessageClickListener(OnMessageClickListener listener) {
        this.listener = listener;
    }

    public void setGroupChat(boolean groupChat) {
        this.isGroupChat = groupChat;
    }
    
    public void setAvatarManager(AvatarManager avatarManager) {
        MessageAdapter.avatarManager = avatarManager;
        android.util.Log.d("MessageAdapter", "Static AvatarManager set: " + (avatarManager != null));
    }
    
    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);
        return new MessageViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = messages.get(position);
        holder.bind(message, currentUserId, isGroupChat, holder.itemView.getContext());
    }
    
    @Override
    public int getItemCount() {
        return messages.size();
    }
    
    public void updateMessages(List<Message> newMessages) {
        this.messages = newMessages;
        notifyDataSetChanged();
    }
    
    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        private LinearLayout llSentMessage;
        private LinearLayout llReceivedMessage;
        private TextView tvSentMessage;
        private TextView tvSentTime;
        private TextView tvReceivedMessage;
        private TextView tvReceivedTime;
        private TextView tvSenderName;
        private ImageView ivSenderAvatar;
        private ImageView ivSentImage;
        private ImageView ivReceivedImage;
        
        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            llSentMessage = itemView.findViewById(R.id.ll_sent_message);
            llReceivedMessage = itemView.findViewById(R.id.ll_received_message);
            tvSentMessage = itemView.findViewById(R.id.tv_sent_message);
            tvSentTime = itemView.findViewById(R.id.tv_sent_time);
            tvReceivedMessage = itemView.findViewById(R.id.tv_received_message);
            tvReceivedTime = itemView.findViewById(R.id.tv_received_time);
            tvSenderName = itemView.findViewById(R.id.tv_sender_name);
            ivSenderAvatar = itemView.findViewById(R.id.iv_sender_avatar);
            ivSentImage = itemView.findViewById(R.id.iv_sent_image);
            ivReceivedImage = itemView.findViewById(R.id.iv_received_image);
        }
        
        public void bind(Message message, String currentUserId, boolean isGroupChat, Context context) {
            // Format timestamp
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            String timeString = sdf.format(new Date(message.getTimestamp()));
            
            // Check if message is from current user
            boolean isFromCurrentUser = message.getSenderId() != null && 
                message.getSenderId().equals(currentUserId);
            
            android.util.Log.d("MessageAdapter", "Message check - senderId: " + message.getSenderId() + 
                ", currentUserId: " + currentUserId + 
                ", isFromCurrentUser: " + isFromCurrentUser);
            
            if (isFromCurrentUser) {
                // Show sent message layout
                llSentMessage.setVisibility(View.VISIBLE);
                llReceivedMessage.setVisibility(View.GONE);
                
                if (message.isImageMessage()) {
                    // Show image message
                    if (tvSentMessage != null) tvSentMessage.setVisibility(View.GONE);
                    if (ivSentImage != null) {
                        ivSentImage.setVisibility(View.VISIBLE);
                        // Set dynamic image size
                        setImageSize(ivSentImage, context);
                        // Load image using Picasso
                        String imageUrl = getImageUrlFromMessage(message);
                        if (imageUrl != null && !imageUrl.isEmpty()) {
                            // Convert to full URL for display
                            String displayUrl;
                            if (!imageUrl.startsWith("http")) {
                                displayUrl = "http://" + ServerConfig.getServerIp() + 
                                           ":" + ServerConfig.getServerPort() + imageUrl;
                            } else {
                                displayUrl = imageUrl;
                            }

                            if (imageUrl.startsWith("content://") || imageUrl.startsWith("file://")) {
                                // Local URI - load directly
                                Picasso.get()
                                    .load(imageUrl)
                                    .placeholder(R.drawable.ic_profile_placeholder)
                                    .error(R.drawable.ic_profile_placeholder)
                                    .into(ivSentImage);
                            } else {
                                // Server URL
                                Picasso.get()
                                    .load(displayUrl)
                                    .placeholder(R.drawable.ic_profile_placeholder)
                                    .error(R.drawable.ic_profile_placeholder)
                                    .into(ivSentImage);
                            }
                            
                            // Add click listener for image zoom
                            ivSentImage.setOnClickListener(v -> {
                                if (listener != null) {
                                    listener.onImageClick(displayUrl, message.getLocalImageUri());
                                }
                            });
                        }
                    }
                } else {
                    // Show text message
                    if (tvSentMessage != null) {
                        tvSentMessage.setVisibility(View.VISIBLE);
                        tvSentMessage.setText(message.getContent());
                    }
                    if (ivSentImage != null) ivSentImage.setVisibility(View.GONE);
                }
                
                if (tvSentTime != null) tvSentTime.setText(timeString);
            } else {
                // Show received message layout
                llSentMessage.setVisibility(View.GONE);
                llReceivedMessage.setVisibility(View.VISIBLE);
                
                if (message.isImageMessage()) {
                    // Show image message
                    if (tvReceivedMessage != null) tvReceivedMessage.setVisibility(View.GONE);
                    if (ivReceivedImage != null) {
                        ivReceivedImage.setVisibility(View.VISIBLE);
                        // Set dynamic image size
                        setImageSize(ivReceivedImage, context);
                        // Load image using Picasso
                        String imageUrl = getImageUrlFromMessage(message);
                        if (imageUrl != null && !imageUrl.isEmpty()) {
                            // Convert to full URL for display
                            String displayUrl;
                            if (!imageUrl.startsWith("http")) {
                                displayUrl = "http://" + ServerConfig.getServerIp() + 
                                           ":" + ServerConfig.getServerPort() + imageUrl;
                            } else {
                                displayUrl = imageUrl;
                            }

                            if (imageUrl.startsWith("content://") || imageUrl.startsWith("file://")) {
                                // Local URI - load directly
                                Picasso.get()
                                    .load(imageUrl)
                                    .placeholder(R.drawable.ic_profile_placeholder)
                                    .error(R.drawable.ic_profile_placeholder)
                                    .into(ivReceivedImage);
                            } else {
                                // Server URL
                                Picasso.get()
                                    .load(displayUrl)
                                    .placeholder(R.drawable.ic_profile_placeholder)
                                    .error(R.drawable.ic_profile_placeholder)
                                    .into(ivReceivedImage);
                            }
                            
                            // Add click listener for image zoom
                            ivReceivedImage.setOnClickListener(v -> {
                                if (listener != null) {
                                    listener.onImageClick(displayUrl, message.getLocalImageUri());
                                }
                            });
                        }
                    }
                } else {
                    // Show text message
                    if (tvReceivedMessage != null) {
                        tvReceivedMessage.setVisibility(View.VISIBLE);
                        tvReceivedMessage.setText(message.getContent());
                    }
                    if (ivReceivedImage != null) ivReceivedImage.setVisibility(View.GONE);
                }
                
                if (tvReceivedTime != null) tvReceivedTime.setText(timeString);

                // For group chats, show sender name and avatar
                boolean shouldShowAvatar = isGroupChat || message.isGroupChat();
                android.util.Log.d("MessageAdapter", "Should show avatar: " + shouldShowAvatar +
                    ", isGroupChat: " + isGroupChat +
                    ", message.isGroupChat(): " + message.isGroupChat() +
                    ", message.chatType: " + message.getChatType());
                
                if (shouldShowAvatar) {
                    if (tvSenderName != null) {
                        tvSenderName.setVisibility(View.VISIBLE);
                        String username = message.getSenderUsername();
                        android.util.Log.d("MessageAdapter", "Setting username: " + username);
                        tvSenderName.setText(username != null && !username.isEmpty() ? username : "Unknown User");
                    }
                    if (ivSenderAvatar != null) {
                        ivSenderAvatar.setVisibility(View.VISIBLE);
                        String avatarUrl = message.getSenderAvatar();
                        android.util.Log.d("MessageAdapter", "Setting avatar URL: " + avatarUrl);
                        if (avatarUrl != null && !avatarUrl.isEmpty()) {
                            android.util.Log.d("MessageAdapter", "Loading user avatar: " + avatarUrl);
                            
                            // Construct full URL if needed (like ChatListAdapter logic)
                            if (!avatarUrl.startsWith("http")) {
                                avatarUrl = "http://" + ServerConfig.getServerIp() + 
                                           ":" + ServerConfig.getServerPort() + avatarUrl;
                                android.util.Log.d("MessageAdapter", "Constructed full URL: " + avatarUrl);
                            }
                            
                            // Use AvatarManager if available, otherwise use Picasso directly
                            if (avatarManager != null) {
                                android.util.Log.d("MessageAdapter", "Using AvatarManager to load avatar");
                                avatarManager.loadAvatar(
                                    avatarUrl, 
                                    ivSenderAvatar, 
                                    R.drawable.ic_profile_placeholder
                                );
                            } else {
                                android.util.Log.d("MessageAdapter", "AvatarManager not available, using Picasso directly");
                                Picasso.get()
                                    .load(avatarUrl)
                                    .placeholder(R.drawable.ic_profile_placeholder)
                                    .error(R.drawable.ic_profile_placeholder)
                                    .into(ivSenderAvatar);
                            }
                        } else {
                            android.util.Log.d("MessageAdapter", "No avatar URL, using default");
                            // No avatar, use default
                            ivSenderAvatar.setImageResource(R.drawable.ic_profile_placeholder);
                        }
                    }
                } else {
                    android.util.Log.d("MessageAdapter", "Hiding avatar and username for private chat");
                    if (tvSenderName != null) tvSenderName.setVisibility(View.GONE);
                    if (ivSenderAvatar != null) ivSenderAvatar.setVisibility(View.GONE);
                }
            }
        }
    }
    
    private static String getImageUrlFromMessage(Message message) {
        // For image messages, try to get URL from attachments first, then fallback to content
        if (message.isImageMessage()) {
            String attachments = message.getAttachments();
            if (attachments != null && !attachments.isEmpty()) {
                try {
                    org.json.JSONArray attachmentsArray = new org.json.JSONArray(attachments);
                    if (attachmentsArray.length() > 0) {
                        org.json.JSONObject attachment = attachmentsArray.getJSONObject(0);
                        return attachment.optString("url", "");
                    }
                } catch (org.json.JSONException e) {
                    e.printStackTrace();
                }
            }
            // Fallback to content field for backward compatibility
            return message.getContent();
        }
        return null;
    }

    private static void setImageSize(ImageView imageView, Context context) {
        // Get screen width
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        int screenWidth = displayMetrics.widthPixels;
        
        // Calculate image size (max 60% of screen width, min 120dp)
        int maxWidth = (int) (screenWidth * 0.6);
        int minWidth = (int) (120 * displayMetrics.density);
        
        // Set layout parameters
        ViewGroup.LayoutParams params = imageView.getLayoutParams();
        if (params != null) {
            params.width = Math.max(minWidth, Math.min(maxWidth, 250));
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            imageView.setLayoutParams(params);
        }
    }
}
