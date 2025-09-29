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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {
    
    public interface OnMessageClickListener {
        void onMessageClick(Message message);
        void onMessageLongClick(Message message);
        void onImageClick(String imageUrl, String localImageUri);
        default void onReactClick(Message message, String emoji) {}
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
        private LinearLayout llSentReplyPreview;
        private LinearLayout llReceivedReplyPreview;
        private TextView tvSentMessage;
        private TextView tvSentTime;
        private TextView tvSentEdited;
        private TextView tvSentReactionsBadge;
        private TextView tvReceivedMessage;
        private TextView tvReceivedTime;
        private TextView tvReceivedEdited;
        private TextView tvReceivedReactionsBadge;
        private TextView tvSenderName;
        private TextView tvSentReplyAuthor;
        private TextView tvSentReplyContent;
        private TextView tvReceivedReplyAuthor;
        private TextView tvReceivedReplyContent;
        private ImageView ivSenderAvatar;
        private ImageView ivSentImage;
        private ImageView ivReceivedImage;
        
        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            llSentMessage = itemView.findViewById(R.id.ll_sent_message);
            llReceivedMessage = itemView.findViewById(R.id.ll_received_message);
            llSentReplyPreview = itemView.findViewById(R.id.ll_sent_reply_preview);
            llReceivedReplyPreview = itemView.findViewById(R.id.ll_received_reply_preview);
            tvSentMessage = itemView.findViewById(R.id.tv_sent_message);
            tvSentTime = itemView.findViewById(R.id.tv_sent_time);
            tvSentEdited = itemView.findViewById(R.id.tv_sent_edited);
            tvSentReactionsBadge = itemView.findViewById(R.id.tv_sent_reactions_badge);
            tvReceivedMessage = itemView.findViewById(R.id.tv_received_message);
            tvReceivedTime = itemView.findViewById(R.id.tv_received_time);
            tvReceivedEdited = itemView.findViewById(R.id.tv_received_edited);
            tvReceivedReactionsBadge = itemView.findViewById(R.id.tv_received_reactions_badge);
            tvSenderName = itemView.findViewById(R.id.tv_sender_name);
            tvSentReplyAuthor = itemView.findViewById(R.id.tv_sent_reply_author);
            tvSentReplyContent = itemView.findViewById(R.id.tv_sent_reply_content);
            tvReceivedReplyAuthor = itemView.findViewById(R.id.tv_received_reply_author);
            tvReceivedReplyContent = itemView.findViewById(R.id.tv_received_reply_content);
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
                // Reply preview for sent
                if (message.getReplyToMessageId() != null && !message.getReplyToMessageId().isEmpty()) {
                    if (llSentReplyPreview != null) llSentReplyPreview.setVisibility(View.VISIBLE);
                    if (tvSentReplyAuthor != null) tvSentReplyAuthor.setText(
                        message.getReplyToSenderName() != null && !message.getReplyToSenderName().isEmpty() ? message.getReplyToSenderName() : "Reply"
                    );
                    if (tvSentReplyContent != null) tvSentReplyContent.setText(
                        message.getReplyToContent() != null ? message.getReplyToContent() : ""
                    );
                } else {
                    if (llSentReplyPreview != null) llSentReplyPreview.setVisibility(View.GONE);
                }
                
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
                        applyMentionStyling(tvSentMessage, message.getContent(), true);
                    }
                    if (ivSentImage != null) ivSentImage.setVisibility(View.GONE);
                }
                
                if (tvSentTime != null) tvSentTime.setText(timeString);
                if (tvSentEdited != null) tvSentEdited.setVisibility(message.isEdited() ? View.VISIBLE : View.GONE);
                updateReactionsBadge(tvSentReactionsBadge, message);
            } else {
                // Show received message layout
                llSentMessage.setVisibility(View.GONE);
                llReceivedMessage.setVisibility(View.VISIBLE);
                // Reply preview for received
                if (message.getReplyToMessageId() != null && !message.getReplyToMessageId().isEmpty()) {
                    if (llReceivedReplyPreview != null) llReceivedReplyPreview.setVisibility(View.VISIBLE);
                    if (tvReceivedReplyAuthor != null) tvReceivedReplyAuthor.setText(
                        message.getReplyToSenderName() != null && !message.getReplyToSenderName().isEmpty() ? message.getReplyToSenderName() : "Reply"
                    );
                    if (tvReceivedReplyContent != null) tvReceivedReplyContent.setText(
                        message.getReplyToContent() != null ? message.getReplyToContent() : ""
                    );
                } else {
                    if (llReceivedReplyPreview != null) llReceivedReplyPreview.setVisibility(View.GONE);
                }
                
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
                        applyMentionStyling(tvReceivedMessage, message.getContent(), false);
                    }
                    if (ivReceivedImage != null) ivReceivedImage.setVisibility(View.GONE);
                }
                
                if (tvReceivedTime != null) tvReceivedTime.setText(timeString);
                if (tvReceivedEdited != null) tvReceivedEdited.setVisibility(message.isEdited() ? View.VISIBLE : View.GONE);
                updateReactionsBadge(tvReceivedReactionsBadge, message);

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

            // Forward click/long-click to listener
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onMessageClick(message);
            });
            itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onMessageLongClick(message);
                    // Quick reaction panel (lightweight)
                    showQuickReactions(itemView.getContext(), itemView, message);
                    return true;
                }
                return false;
            });

            // Also attach to visible bubble views for better UX
            if (llSentMessage.getVisibility() == View.VISIBLE) {
                llSentMessage.setOnLongClickListener(v -> { if (listener != null) { listener.onMessageLongClick(message); return true; } return false; });
                if (tvSentMessage != null) tvSentMessage.setOnLongClickListener(v -> { if (listener != null) { listener.onMessageLongClick(message); return true; } return false; });
                if (ivSentImage != null && ivSentImage.getVisibility() == View.VISIBLE)
                    ivSentImage.setOnLongClickListener(v -> { if (listener != null) { listener.onMessageLongClick(message); return true; } return false; });
                if (tvSentReactionsBadge != null) tvSentReactionsBadge.setOnClickListener(v -> showReactionsSheet(v.getContext(), message));
            } else if (llReceivedMessage.getVisibility() == View.VISIBLE) {
                llReceivedMessage.setOnLongClickListener(v -> { if (listener != null) { listener.onMessageLongClick(message); return true; } return false; });
                if (tvReceivedMessage != null) tvReceivedMessage.setOnLongClickListener(v -> { if (listener != null) { listener.onMessageLongClick(message); return true; } return false; });
                if (ivReceivedImage != null && ivReceivedImage.getVisibility() == View.VISIBLE)
                    ivReceivedImage.setOnLongClickListener(v -> { if (listener != null) { listener.onMessageLongClick(message); return true; } return false; });
                if (tvReceivedReactionsBadge != null) tvReceivedReactionsBadge.setOnClickListener(v -> showReactionsSheet(v.getContext(), message));
            }
        }

        private void showQuickReactions(Context context, View anchor, Message message) {
            String[] emojis = {"👍", "❤️", "😂", "😮", "😢", "🔥"};
            android.widget.PopupMenu menu = new android.widget.PopupMenu(context, anchor);
            for (int i = 0; i < emojis.length; i++) {
                menu.getMenu().add(0, 1000 + i, i, emojis[i]);
            }
            menu.setOnMenuItemClickListener(item -> {
                int idx = item.getItemId() - 1000;
                if (idx >= 0 && idx < emojis.length) {
                    String emoji = emojis[idx];
                    if (listener != null) listener.onReactClick(message, emoji);
                    return true;
                }
                return false;
            });
            // Show as icons row by forcing show
            try {
                java.lang.reflect.Field mFieldPopup = menu.getClass().getDeclaredField("mPopup");
                mFieldPopup.setAccessible(true);
                Object mPopup = mFieldPopup.get(menu);
                mPopup.getClass().getDeclaredMethod("setForceShowIcon", boolean.class).invoke(mPopup, true);
            } catch (Exception ignored) {}
            menu.show();
        }

        private void updateReactionsBadge(TextView badgeView, Message message) {
            if (badgeView == null) return;
            java.util.Map<String, Integer> sum = message.getReactionSummary();
            if (sum == null || sum.isEmpty()) {
                badgeView.setVisibility(View.GONE);
                return;
            }
            // Build compact badge like "👍 3  ❤️ 1"
            StringBuilder sb = new StringBuilder();
            int shown = 0;
            for (java.util.Map.Entry<String, Integer> e : sum.entrySet()) {
                if (e.getValue() == null || e.getValue() <= 0) continue;
                if (shown > 0) sb.append("  ");
                sb.append(e.getKey()).append(" ").append(e.getValue());
                shown++;
                if (shown >= 3) break; // cap to 3 types for compactness
            }
            if (sb.length() > 0) {
                badgeView.setText(sb.toString());
                badgeView.setVisibility(View.VISIBLE);
            } else {
                badgeView.setVisibility(View.GONE);
            }
        }

        private void showReactionsSheet(Context context, Message message) {
            // Parse raw reactions to show users list
            java.util.List<String> lines = new java.util.ArrayList<>();
            try {
                String raw = message.getReactionsRaw();
                if (raw != null && !raw.isEmpty()) {
                    org.json.JSONArray arr = new org.json.JSONArray(raw);
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject r = arr.getJSONObject(i);
                        String emoji = r.optString("emoji", "");
                        org.json.JSONObject userObj = r.optJSONObject("user");
                        String uname = userObj != null ? userObj.optString("username", "Unknown") : "Unknown";
                        lines.add(emoji + "  " + uname);
                    }
                }
            } catch (Exception ignored) {}

            if (lines.isEmpty()) {
                android.widget.Toast.makeText(context, "No reactions", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            String[] arr = lines.toArray(new String[0]);
            new android.app.AlertDialog.Builder(context)
                .setTitle("Reactions")
                .setItems(arr, null)
                .setPositiveButton("Close", null)
                .show();
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

    private static final Pattern MENTION_PATTERN = Pattern.compile("@([A-Za-z0-9_]+)");

    private static void applyMentionStyling(TextView textView, String content, boolean isSent) {
        if (content == null) {
            textView.setText("");
            return;
        }
        android.text.SpannableString spannable = new android.text.SpannableString(content);
        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            android.text.style.StyleSpan styleSpan = new android.text.style.StyleSpan(android.graphics.Typeface.BOLD);
            if (isSent) {
                // On sent bubble (usually colored background with white text), use a semi-transparent highlight
                android.text.style.BackgroundColorSpan bgSpan = new android.text.style.BackgroundColorSpan(0x66FFC107); // amber 40%
                spannable.setSpan(bgSpan, start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                // On received bubble (light background, dark text), use blue foreground
                android.text.style.ForegroundColorSpan colorSpan = new android.text.style.ForegroundColorSpan(0xFF1E88E5);
                spannable.setSpan(colorSpan, start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            spannable.setSpan(styleSpan, start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            // Clickable span to open profile
            final String username = content.substring(start + 1, end);
            android.text.style.ClickableSpan clickableSpan = new android.text.style.ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    Context ctx = widget.getContext();
                    android.content.Intent intent = new android.content.Intent(ctx, com.example.chatappjava.ui.theme.ProfileViewActivity.class);
                    // Best-effort: pass username; ProfileViewActivity should handle fetching by username
                    intent.putExtra("username", username);
                    // Optional: also pass minimal user object if we can map username from avatarManager cache later
                    ctx.startActivity(intent);
                }

                @Override
                public void updateDrawState(android.text.TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setUnderlineText(false);
                }
            };
            spannable.setSpan(clickableSpan, start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        textView.setText(spannable);
        textView.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
    }
}
