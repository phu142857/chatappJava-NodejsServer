package com.example.chatappjava.adapters;

import android.annotation.SuppressLint;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.DisplayMetrics;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.chatappjava.R;
import com.example.chatappjava.models.Message;
import com.example.chatappjava.models.User;
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
        default void onReplyClick(String replyToMessageId) {}
    }
    
    private final List<Message> messages;
    private final String currentUserId;
    private boolean isGroupChat;
    private static OnMessageClickListener listener;
    private static AvatarManager avatarManager;
    
    public MessageAdapter(List<Message> messages, String currentUserId) {
        this.messages = messages;
        this.currentUserId = currentUserId;
    }

    public void setOnMessageClickListener(OnMessageClickListener listener) {
        MessageAdapter.listener = listener;
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

    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout llSentMessage;
        private final LinearLayout llReceivedMessage;
        private final LinearLayout llSentReplyPreview;
        private final LinearLayout llReceivedReplyPreview;
        private final TextView tvSentMessage;
        private final TextView tvSentTime;
        private final TextView tvSentEdited;
        private final ImageView ivSentReactionImage;
        private final TextView tvReceivedMessage;
        private final TextView tvReceivedTime;
        private final TextView tvReceivedEdited;
        private final ImageView ivReceivedReactionImage;
        private final TextView tvSenderName;
        private final TextView tvSentReplyAuthor;
        private final TextView tvSentReplyContent;
        private final TextView tvReceivedReplyAuthor;
        private final TextView tvReceivedReplyContent;
        private final ImageView ivSenderAvatar;
        private final ImageView ivSentImage;
        private final ImageView ivReceivedImage;
        
        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            llSentMessage = itemView.findViewById(R.id.ll_sent_message);
            llReceivedMessage = itemView.findViewById(R.id.ll_received_message);
            llSentReplyPreview = itemView.findViewById(R.id.ll_sent_reply_preview);
            llReceivedReplyPreview = itemView.findViewById(R.id.ll_received_reply_preview);
            tvSentMessage = itemView.findViewById(R.id.tv_sent_message);
            tvSentTime = itemView.findViewById(R.id.tv_sent_time);
            tvSentEdited = itemView.findViewById(R.id.tv_sent_edited);
            ivSentReactionImage = itemView.findViewById(R.id.iv_sent_reaction_image);
            tvReceivedMessage = itemView.findViewById(R.id.tv_received_message);
            tvReceivedTime = itemView.findViewById(R.id.tv_received_time);
            tvReceivedEdited = itemView.findViewById(R.id.tv_received_edited);
            ivReceivedReactionImage = itemView.findViewById(R.id.iv_received_reaction_image);
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
                    if (llSentReplyPreview != null) {
                        llSentReplyPreview.setVisibility(View.VISIBLE);
                        // Add click listener to scroll to original message
                        llSentReplyPreview.setOnClickListener(v -> {
                            if (listener != null) {
                                listener.onReplyClick(message.getReplyToMessageId());
                            }
                        });
                    }
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
                updateReactionIcon(ivSentReactionImage, message, context);
            } else {
                // Show received message layout
                llSentMessage.setVisibility(View.GONE);
                llReceivedMessage.setVisibility(View.VISIBLE);
                // Reply preview for received
                if (message.getReplyToMessageId() != null && !message.getReplyToMessageId().isEmpty()) {
                    if (llReceivedReplyPreview != null) {
                        llReceivedReplyPreview.setVisibility(View.VISIBLE);
                        // Add click listener to scroll to original message
                        llReceivedReplyPreview.setOnClickListener(v -> {
                            if (listener != null) {
                                listener.onReplyClick(message.getReplyToMessageId());
                            }
                        });
                    }
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
                updateReactionIcon(ivReceivedReactionImage, message, context);

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
                if (ivSentReactionImage != null) ivSentReactionImage.setOnClickListener(v -> showReactionsSheet(v.getContext(), message));
            } else if (llReceivedMessage.getVisibility() == View.VISIBLE) {
                llReceivedMessage.setOnLongClickListener(v -> { if (listener != null) { listener.onMessageLongClick(message); return true; } return false; });
                if (tvReceivedMessage != null) tvReceivedMessage.setOnLongClickListener(v -> { if (listener != null) { listener.onMessageLongClick(message); return true; } return false; });
                if (ivReceivedImage != null && ivReceivedImage.getVisibility() == View.VISIBLE)
                    ivReceivedImage.setOnLongClickListener(v -> { if (listener != null) { listener.onMessageLongClick(message); return true; } return false; });
                if (ivReceivedReactionImage != null) ivReceivedReactionImage.setOnClickListener(v -> showReactionsSheet(v.getContext(), message));
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
                @SuppressLint("DiscouragedPrivateApi") java.lang.reflect.Field mFieldPopup = menu.getClass().getDeclaredField("mPopup");
                mFieldPopup.setAccessible(true);
                Object mPopup = mFieldPopup.get(menu);
                assert mPopup != null;
                mPopup.getClass().getDeclaredMethod("setForceShowIcon", boolean.class).invoke(mPopup, true);
            } catch (Exception ignored) {}
            menu.show();
        }

        private void updateReactionIcon(ImageView badgeView, Message message, Context context) {
            if (badgeView == null) return;
            java.util.Map<String, Integer> sum = message.getReactionSummary();
            if (sum == null || sum.isEmpty()) {
                badgeView.setVisibility(View.GONE);
                return;
            }
            // Pick the most frequent emoji
            String topEmoji = null; int max = -1;
            for (java.util.Map.Entry<String, Integer> e : sum.entrySet()) {
                Integer c = e.getValue();
                if (c != null && c > max) { max = c; topEmoji = e.getKey(); }
            }
            if (topEmoji == null || topEmoji.isEmpty()) {
                badgeView.setVisibility(View.GONE);
                return;
            }
            try {
                android.graphics.Bitmap bmp = createEmojiBitmap(topEmoji, context, 22);
                if (bmp != null) {
                    badgeView.setImageBitmap(bmp);
                    // Optional background to mimic floating chip
                    badgeView.setBackgroundResource(com.example.chatappjava.R.drawable.ripple_reaction_floating);
                    badgeView.setVisibility(View.VISIBLE);
                } else {
                    badgeView.setVisibility(View.GONE);
                }
            } catch (Exception ignored) {
                badgeView.setVisibility(View.GONE);
            }
        }

        private android.graphics.Bitmap createEmojiBitmap(String emoji, Context context, int dpSize) {
            float density = context.getResources().getDisplayMetrics().density;
            int sizePx = (int) (dpSize * density);
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            // Clear fully transparent to keep original emoji edges without clipping
            canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR);
            android.text.TextPaint paint = new android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            paint.setTextSize(sizePx * 0.8f);
            paint.setColor(android.graphics.Color.WHITE);
            paint.setTextAlign(android.graphics.Paint.Align.CENTER);
            // Center baseline calculation
            android.graphics.Rect bounds = new android.graphics.Rect();
            String draw = emoji;
            paint.getTextBounds(draw, 0, draw.length(), bounds);
            float x = sizePx / 2f;
            float y = (sizePx / 2f) - (bounds.exactCenterY());
            // Draw shadow-like glow for better contrast
            paint.setShadowLayer(sizePx * 0.08f, 0, sizePx * 0.06f, 0x80000000);
            canvas.drawText(draw, x, y, paint);
            return bitmap;
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
            ClickableSpan clickableSpan = getClickableSpan(content, start, end);
            spannable.setSpan(clickableSpan, start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        textView.setText(spannable);
        textView.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
    }

    @NonNull
    private static ClickableSpan getClickableSpan(String content, int start, int end) {
        final String username = content.substring(start + 1, end);
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
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
    }

    public static class RequestsAdapter extends RecyclerView.Adapter<RequestsAdapter.VH> {
        public interface ActionListener {
            void onApprove(User u);
            void onReject(User u);
        }
        private final List<User> users;
        private final ActionListener listener;
        public RequestsAdapter(List<User> users, ActionListener listener) {
            this.users = users;
            this.listener = listener;
        }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_join_request, parent, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) { h.bind(users.get(pos)); }
        @Override public int getItemCount() { return users.size(); }

        class VH extends RecyclerView.ViewHolder {
            de.hdodenhof.circleimageview.CircleImageView avatar; TextView name; Button approve; Button reject;
            VH(View v) { super(v);
                avatar = v.findViewById(R.id.iv_avatar);
                name = v.findViewById(R.id.tv_name);
                approve = v.findViewById(R.id.btn_approve);
                reject = v.findViewById(R.id.btn_reject);
            }
            void bind(User u) {
                name.setText(u.getDisplayName());
                try {
                    String avatarUrl = u.getAvatar();
                    if (avatarUrl != null && !avatarUrl.isEmpty()) {
                        if (!(avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://"))) {
                            String path = avatarUrl.startsWith("/") ? avatarUrl : "/" + avatarUrl;
                            avatarUrl = "http://" + ServerConfig.getServerIp() + ":" + ServerConfig.getServerPort() + path;
                        }
                        try {
                            AvatarManager.getInstance(itemView.getContext()).loadAvatar(avatarUrl, avatar, R.drawable.ic_profile_placeholder);
                        } catch (Exception ignored) {
                            Picasso.get().load(avatarUrl).placeholder(R.drawable.ic_profile_placeholder).error(R.drawable.ic_profile_placeholder).into(avatar);
                        }
                    } else {
                        avatar.setImageResource(R.drawable.ic_profile_placeholder);
                    }
                } catch (Exception ignored) {}

                // Open profile when clicking avatar
                if (avatar != null) {
                    avatar.setOnClickListener(v -> {
                        try {
                            android.content.Intent intent = new android.content.Intent(v.getContext(), com.example.chatappjava.ui.theme.ProfileViewActivity.class);
                            intent.putExtra("user", u.toJson().toString());
                            v.getContext().startActivity(intent);
                        } catch (org.json.JSONException e) {
                            android.content.Intent intent = new android.content.Intent(v.getContext(), com.example.chatappjava.ui.theme.ProfileViewActivity.class);
                            intent.putExtra("userId", u.getId());
                            v.getContext().startActivity(intent);
                        }
                    });
                }
                if (approve != null) approve.setOnClickListener(v -> { if (listener != null) listener.onApprove(u); });
                if (reject != null) reject.setOnClickListener(v -> { if (listener != null) listener.onReject(u); });
            }
        }
    }
}
