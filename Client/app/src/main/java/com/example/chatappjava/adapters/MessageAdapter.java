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
        void onFileClick(String fileUrl, String fileName, String originalName, String mimeType, long fileSize);
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
        setHasStableIds(true);
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

    @Override
    public long getItemId(int position) {
        try {
            Message m = messages.get(position);
            String id = m.getId();
            if (id != null) return id.hashCode();
            String key = m.getTimestamp() + ":" +
                    (m.getSenderId() != null ? m.getSenderId() : "") + ":" +
                    (m.getContent() != null ? m.getContent() : "");
            return key.hashCode();
        } catch (Exception ignored) {
            return RecyclerView.NO_ID;
        }
    }

    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout llSentMessage;
        private final LinearLayout llReceivedMessage;
        private final LinearLayout llSentReplyPreview;
        private final ImageView ivSentReplyThumb;
        private final LinearLayout llReceivedReplyPreview;
        private final ImageView ivReceivedReplyThumb;
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
        private final ImageView ivSentImageReaction;
        private final ImageView ivReceivedImage;
        private final ImageView ivReceivedImageReaction;
        
        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            llSentMessage = itemView.findViewById(R.id.ll_sent_message);
            llReceivedMessage = itemView.findViewById(R.id.ll_received_message);
            llSentReplyPreview = itemView.findViewById(R.id.ll_sent_reply_preview);
            ivSentReplyThumb = itemView.findViewById(R.id.iv_sent_reply_thumb);
            llReceivedReplyPreview = itemView.findViewById(R.id.ll_received_reply_preview);
            ivReceivedReplyThumb = itemView.findViewById(R.id.iv_received_reply_thumb);
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
            ivSentImageReaction = itemView.findViewById(R.id.iv_sent_image_reaction);
            ivReceivedImage = itemView.findViewById(R.id.iv_received_image);
            ivReceivedImageReaction = itemView.findViewById(R.id.iv_received_image_reaction);
        }
        
        public void bind(Message message, String currentUserId, boolean isGroupChat, Context context) {
            // Format timestamp with date
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault());
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
                    // Show image thumb if this is reply to image
                    if (ivSentReplyThumb != null) {
                        String thumb = message.getReplyToImageThumb();
                        if ((thumb == null || thumb.isEmpty()) && message.getReplyToMessageId() != null) {
                            thumb = com.example.chatappjava.utils.ReplyPreviewCache.get(message.getReplyToMessageId());
                        }
                        if (thumb != null && !thumb.isEmpty()) {
                            ivSentReplyThumb.setVisibility(View.VISIBLE);
                            // prefer content/local if content is a content:// or file:// uri
                            String loadUrl = thumb;
                            if (!(thumb.startsWith("content://") || thumb.startsWith("file://"))) {
                                loadUrl = thumb.startsWith("http") ? thumb : ("http://" + ServerConfig.getServerIp() + ":" + ServerConfig.getServerPort() + thumb);
                            }
                            Picasso.get().cancelRequest(ivSentReplyThumb);
                            Picasso.get().load(loadUrl).noFade().fit().centerCrop().into(ivSentReplyThumb);
                        } else {
                            ivSentReplyThumb.setVisibility(View.GONE);
                        }
                    }
                    if (tvSentReplyAuthor != null) tvSentReplyAuthor.setText(
                        message.getReplyToSenderName() != null && !message.getReplyToSenderName().isEmpty() ? message.getReplyToSenderName() : "Reply"
                    );
                    if (tvSentReplyContent != null) tvSentReplyContent.setText(
                        message.getReplyToContent() != null ? message.getReplyToContent() : ""
                    );
                } else {
                    if (llSentReplyPreview != null) llSentReplyPreview.setVisibility(View.GONE);
                    if (ivSentReplyThumb != null) ivSentReplyThumb.setVisibility(View.GONE);
                }
                
                if (message.isImageMessage()) {
                    // Show image message
                    if (tvSentMessage != null) tvSentMessage.setVisibility(View.GONE);
                    if (ivSentImage != null) {
                        // ensure containers visibility
                        View container = itemView.findViewById(R.id.fl_sent_image_container);
                        if (container != null) container.setVisibility(View.VISIBLE);
                        ivSentImage.setVisibility(View.VISIBLE);
                        // Set dynamic image size
                        setImageSize(ivSentImage, context);
                        // Load image using Picasso
                        String serverUrlCandidate = getImageUrlFromMessage(message);
                        String localUri = message.getLocalImageUri();
                        String displayUrl;
                        // Prefer localUri to avoid flicker while upload completes
                        if (localUri != null && !localUri.isEmpty()) {
                            Picasso.get().cancelRequest(ivSentImage);
                            Picasso.get()
                                .load(localUri)
                                .noFade()
                                .fit()
                                .centerCrop()
                                .error(R.drawable.ic_profile_placeholder)
                                .into(ivSentImage);
                            displayUrl = (serverUrlCandidate != null && !serverUrlCandidate.isEmpty()) ?
                                (serverUrlCandidate.startsWith("http") ? serverUrlCandidate :
                                    ("http://" + ServerConfig.getServerIp() + ":" + ServerConfig.getServerPort() + serverUrlCandidate))
                                : localUri;
                        } else if (serverUrlCandidate != null && !serverUrlCandidate.isEmpty()) {
                            // Convert to full URL for display
                            displayUrl = serverUrlCandidate.startsWith("http") ? serverUrlCandidate :
                                ("http://" + ServerConfig.getServerIp() + ":" + ServerConfig.getServerPort() + serverUrlCandidate);
                            Picasso.get().cancelRequest(ivSentImage);
                            Picasso.get()
                                .load(displayUrl)
                                .noFade()
                                .fit()
                                .centerCrop()
                                .placeholder(R.drawable.ic_profile_placeholder)
                                .error(R.drawable.ic_profile_placeholder)
                                .into(ivSentImage);
                        } else {
                            displayUrl = null;
                        }

                        if (displayUrl != null) {
                            ivSentImage.setOnClickListener(v -> {
                                if (listener != null) {
                                    listener.onImageClick(displayUrl, message.getLocalImageUri());
                                }
                            });
                        }

                        // Use image reaction badge instead of text bubble badge
                        updateReactionIcon(ivSentImageReaction, message, context);
                        if (ivSentImageReaction != null) {
                            ivSentImageReaction.setOnClickListener(v -> showReactionsSheet(v.getContext(), message));
                        }
                        if (ivSentReactionImage != null) ivSentReactionImage.setVisibility(View.GONE);
                    }
                } else if (message.isFileMessage()) {
                    // Show file message
                    if (tvSentMessage != null) tvSentMessage.setVisibility(View.GONE);
                    if (ivSentImage != null) {
                        // Hide image container for file messages
                        View container = itemView.findViewById(R.id.fl_sent_image_container);
                        if (container != null) container.setVisibility(View.GONE);
                        ivSentImage.setVisibility(View.GONE);
                    }
                    
                    // Show file info + download/open icon inside image container
                    if (tvSentMessage != null) {
                        tvSentMessage.setVisibility(View.VISIBLE);
                        String fileInfo = getFileInfoFromMessage(message);
                        tvSentMessage.setText(fileInfo);
                    }
                    View container = itemView.findViewById(R.id.fl_sent_image_container);
                    if (container != null) container.setVisibility(View.VISIBLE);
                    if (ivSentImage != null) {
                        ivSentImage.setVisibility(View.VISIBLE);
                        // Force compact icon size instead of large image dimensions
                        try {
                            float d = context.getResources().getDisplayMetrics().density;
                            int size = (int) (24 * d);
                            ViewGroup.LayoutParams p = ivSentImage.getLayoutParams();
                            if (p != null) {
                                p.width = size;
                                p.height = size;
                                ivSentImage.setLayoutParams(p);
                            }
                            ivSentImage.setMinimumWidth(0);
                            ivSentImage.setMinimumHeight(0);
                            ivSentImage.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                            ivSentImage.setAdjustViewBounds(true);
                            ivSentImage.setPadding(0, 0, 0, 0);
                        } catch (Exception ignored) {}
                        boolean isDownloaded = isFileDownloaded(itemView.getContext(), message);
                        ivSentImage.setImageResource(isDownloaded ? R.drawable.ic_open : R.drawable.ic_download);
                        ivSentImage.setOnClickListener(v -> {
                            if (listener != null) {
                                String[] fileData = parseFileDataFromMessage(message);
                                if (fileData != null) {
                                    listener.onFileClick(fileData[0], fileData[1], fileData[2], fileData[3], Long.parseLong(fileData[4]));
                                }
                            }
                        });
                    }
                    
                    if (ivSentImageReaction != null) ivSentImageReaction.setVisibility(View.GONE);
                } else {
                    // Show text message
                    if (tvSentMessage != null) {
                        tvSentMessage.setVisibility(View.VISIBLE);
                        applyMentionStyling(tvSentMessage, message.getContent(), true);
                    }
                    if (ivSentImage != null) ivSentImage.setVisibility(View.GONE);
                    View container = itemView.findViewById(R.id.fl_sent_image_container);
                    if (container != null) container.setVisibility(View.GONE);
                    if (ivSentImageReaction != null) ivSentImageReaction.setVisibility(View.GONE);
                }
                
                if (tvSentTime != null) tvSentTime.setText(timeString);
                if (tvSentEdited != null) tvSentEdited.setVisibility(message.isEdited() ? View.VISIBLE : View.GONE);
                if (message.isImageMessage()) {
                    if (ivSentReactionImage != null) ivSentReactionImage.setVisibility(View.GONE);
                } else {
                    updateReactionIcon(ivSentReactionImage, message, context);
                    if (ivSentImageReaction != null) ivSentImageReaction.setVisibility(View.GONE);
                }
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
                    if (ivReceivedReplyThumb != null) {
                        String thumb = message.getReplyToImageThumb();
                        if ((thumb == null || thumb.isEmpty()) && message.getReplyToMessageId() != null) {
                            thumb = com.example.chatappjava.utils.ReplyPreviewCache.get(message.getReplyToMessageId());
                        }
                        if (thumb != null && !thumb.isEmpty()) {
                            ivReceivedReplyThumb.setVisibility(View.VISIBLE);
                            String loadUrl = thumb;
                            if (!(thumb.startsWith("content://") || thumb.startsWith("file://"))) {
                                loadUrl = thumb.startsWith("http") ? thumb : ("http://" + ServerConfig.getServerIp() + ":" + ServerConfig.getServerPort() + thumb);
                            }
                            Picasso.get().cancelRequest(ivReceivedReplyThumb);
                            Picasso.get().load(loadUrl).noFade().fit().centerCrop().into(ivReceivedReplyThumb);
                        } else {
                            ivReceivedReplyThumb.setVisibility(View.GONE);
                        }
                    }
                    if (tvReceivedReplyAuthor != null) tvReceivedReplyAuthor.setText(
                        message.getReplyToSenderName() != null && !message.getReplyToSenderName().isEmpty() ? message.getReplyToSenderName() : "Reply"
                    );
                    if (tvReceivedReplyContent != null) tvReceivedReplyContent.setText(
                        message.getReplyToContent() != null ? message.getReplyToContent() : ""
                    );
                } else {
                    if (llReceivedReplyPreview != null) llReceivedReplyPreview.setVisibility(View.GONE);
                    if (ivReceivedReplyThumb != null) ivReceivedReplyThumb.setVisibility(View.GONE);
                }
                
                if (message.isImageMessage()) {
                    // Show image message
                    if (tvReceivedMessage != null) tvReceivedMessage.setVisibility(View.GONE);
                    if (ivReceivedImage != null) {
                        View container = itemView.findViewById(R.id.fl_received_image_container);
                        if (container != null) container.setVisibility(View.VISIBLE);
                        ivReceivedImage.setVisibility(View.VISIBLE);
                        // Set dynamic image size
                        setImageSize(ivReceivedImage, context);
                        // Load image using Picasso
                        String serverUrlCandidate = getImageUrlFromMessage(message);
                        String localUri = message.getLocalImageUri();
                        String displayUrl;
                        if (localUri != null && !localUri.isEmpty()) {
                            Picasso.get().cancelRequest(ivReceivedImage);
                            Picasso.get()
                                .load(localUri)
                                .noFade()
                                .fit()
                                .centerCrop()
                                .error(R.drawable.ic_profile_placeholder)
                                .into(ivReceivedImage);
                            displayUrl = (serverUrlCandidate != null && !serverUrlCandidate.isEmpty()) ?
                                (serverUrlCandidate.startsWith("http") ? serverUrlCandidate :
                                    ("http://" + ServerConfig.getServerIp() + ":" + ServerConfig.getServerPort() + serverUrlCandidate))
                                : localUri;
                        } else if (serverUrlCandidate != null && !serverUrlCandidate.isEmpty()) {
                            displayUrl = serverUrlCandidate.startsWith("http") ? serverUrlCandidate :
                                ("http://" + ServerConfig.getServerIp() + ":" + ServerConfig.getServerPort() + serverUrlCandidate);
                            Picasso.get().cancelRequest(ivReceivedImage);
                            Picasso.get()
                                .load(displayUrl)
                                .noFade()
                                .fit()
                                .centerCrop()
                                .placeholder(R.drawable.ic_profile_placeholder)
                                .error(R.drawable.ic_profile_placeholder)
                                .into(ivReceivedImage);
                        } else {
                            displayUrl = null;
                        }

                        if (displayUrl != null) {
                            ivReceivedImage.setOnClickListener(v -> {
                                if (listener != null) {
                                    listener.onImageClick(displayUrl, message.getLocalImageUri());
                                }
                            });
                        }

                        updateReactionIcon(ivReceivedImageReaction, message, context);
                        if (ivReceivedImageReaction != null) {
                            ivReceivedImageReaction.setOnClickListener(v -> showReactionsSheet(v.getContext(), message));
                        }
                        if (ivReceivedReactionImage != null) ivReceivedReactionImage.setVisibility(View.GONE);
                    }
                } else if (message.isFileMessage()) {
                    // Show file message
                    if (tvReceivedMessage != null) tvReceivedMessage.setVisibility(View.GONE);
                    if (ivReceivedImage != null) {
                        // Hide image container for file messages
                        View container = itemView.findViewById(R.id.fl_received_image_container);
                        if (container != null) container.setVisibility(View.GONE);
                        ivReceivedImage.setVisibility(View.GONE);
                    }
                    
                    // Show file info + download/open icon inside image container
                    if (tvReceivedMessage != null) {
                        tvReceivedMessage.setVisibility(View.VISIBLE);
                        String fileInfo = getFileInfoFromMessage(message);
                        tvReceivedMessage.setText(fileInfo);
                    }
                    View recvContainer = itemView.findViewById(R.id.fl_received_image_container);
                    if (recvContainer != null) recvContainer.setVisibility(View.VISIBLE);
                    if (ivReceivedImage != null) {
                        ivReceivedImage.setVisibility(View.VISIBLE);
                        // Force compact icon size instead of large image dimensions
                        try {
                            float d = context.getResources().getDisplayMetrics().density;
                            int size = (int) (24 * d);
                            ViewGroup.LayoutParams p = ivReceivedImage.getLayoutParams();
                            if (p != null) {
                                p.width = size;
                                p.height = size;
                                ivReceivedImage.setLayoutParams(p);
                            }
                            ivReceivedImage.setMinimumWidth(0);
                            ivReceivedImage.setMinimumHeight(0);
                            ivReceivedImage.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                            ivReceivedImage.setAdjustViewBounds(true);
                            ivReceivedImage.setPadding(0, 0, 0, 0);
                        } catch (Exception ignored) {}
                        boolean isDownloaded = isFileDownloaded(itemView.getContext(), message);
                        ivReceivedImage.setImageResource(isDownloaded ? R.drawable.ic_open : R.drawable.ic_download);
                        ivReceivedImage.setOnClickListener(v -> {
                            if (listener != null) {
                                String[] fileData = parseFileDataFromMessage(message);
                                if (fileData != null) {
                                    listener.onFileClick(fileData[0], fileData[1], fileData[2], fileData[3], Long.parseLong(fileData[4]));
                                }
                            }
                        });
                    }
                    
                    if (ivReceivedImageReaction != null) ivReceivedImageReaction.setVisibility(View.GONE);
                } else {
                    // Show text message
                    if (tvReceivedMessage != null) {
                        tvReceivedMessage.setVisibility(View.VISIBLE);
                        applyMentionStyling(tvReceivedMessage, message.getContent(), false);
                    }
                    if (ivReceivedImage != null) ivReceivedImage.setVisibility(View.GONE);
                    View container = itemView.findViewById(R.id.fl_received_image_container);
                    if (container != null) container.setVisibility(View.GONE);
                    if (ivReceivedImageReaction != null) ivReceivedImageReaction.setVisibility(View.GONE);
                }
                
                if (tvReceivedTime != null) tvReceivedTime.setText(timeString);
                if (tvReceivedEdited != null) tvReceivedEdited.setVisibility(message.isEdited() ? View.VISIBLE : View.GONE);
                if (message.isImageMessage()) {
                    if (ivReceivedReactionImage != null) ivReceivedReactionImage.setVisibility(View.GONE);
                } else {
                    updateReactionIcon(ivReceivedReactionImage, message, context);
                    if (ivReceivedImageReaction != null) ivReceivedImageReaction.setVisibility(View.GONE);
                }

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
                    // Show custom reaction dialog; fall back to quick popup if any error
                    try {
                        showReactPicker(itemView.getContext(), message);
                    } catch (Exception ignored) {
                        showQuickReactions(itemView.getContext(), itemView, message);
                    }
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

        @SuppressLint("SetTextI18n")
        private void showReactPicker(Context context, Message message) {
            android.view.LayoutInflater inflater = android.view.LayoutInflater.from(context);
            android.view.View dialogView = inflater.inflate(com.example.chatappjava.R.layout.dialog_react_picker, null);

            android.widget.TextView title = dialogView.findViewById(com.example.chatappjava.R.id.tv_title);
            android.view.View btnRemove = dialogView.findViewById(com.example.chatappjava.R.id.btn_remove_react);
            int[] emojiIds = new int[]{
                com.example.chatappjava.R.id.emoji_1,
                com.example.chatappjava.R.id.emoji_2,
                com.example.chatappjava.R.id.emoji_3,
                com.example.chatappjava.R.id.emoji_4,
                com.example.chatappjava.R.id.emoji_5,
                com.example.chatappjava.R.id.emoji_6
            };

            android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(context)
                .setView(dialogView)
                .setCancelable(true)
                .create();

            if (title != null) title.setText("React");
            for (int id : emojiIds) {
                android.view.View v = dialogView.findViewById(id);
                if (v instanceof android.widget.TextView) {
                    v.setOnClickListener(x -> {
                        try {
                            CharSequence emoji = ((android.widget.TextView) v).getText();
                            if (emoji != null && listener != null) listener.onReactClick(message, emoji.toString());
                        } catch (Exception ignored2) {}
                        dlg.dismiss();
                    });
                }
            }
            if (btnRemove != null) btnRemove.setOnClickListener(v -> dlg.dismiss());
            if (dlg.getWindow() != null) {
                android.view.Window w = dlg.getWindow();
                w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            }
            dlg.show();
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
                android.graphics.Bitmap bmp = createEmojiBitmap(topEmoji, context);
                badgeView.setImageBitmap(bmp);
                // Optional background to mimic floating chip
                badgeView.setVisibility(View.VISIBLE);
            } catch (Exception ignored) {
                badgeView.setVisibility(View.GONE);
            }
        }

        private android.graphics.Bitmap createEmojiBitmap(String emoji, Context context) {
            float density = context.getResources().getDisplayMetrics().density;
            int sizePx = (int) (22 * density);
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
            paint.getTextBounds(emoji, 0, emoji.length(), bounds);
            float x = sizePx / 2f;
            float y = (sizePx / 2f) - (bounds.exactCenterY());
            // Draw shadow-like glow for better contrast
            paint.setShadowLayer(sizePx * 0.08f, 0, sizePx * 0.06f, 0x80000000);
            canvas.drawText(emoji, x, y, paint);
            return bitmap;
        }

        @SuppressLint("SetTextI18n")
        private void showReactionsSheet(Context context, Message message) {
            android.view.LayoutInflater inflater = android.view.LayoutInflater.from(context);
            android.view.View dialogView = inflater.inflate(com.example.chatappjava.R.layout.dialog_reactions, null);

            android.widget.TextView tvTitle = dialogView.findViewById(com.example.chatappjava.R.id.tv_title);
            android.widget.LinearLayout container = dialogView.findViewById(com.example.chatappjava.R.id.container_reactions);
            android.view.View btnClose = dialogView.findViewById(com.example.chatappjava.R.id.btn_close);

            if (tvTitle != null) tvTitle.setText("Reactions");

            // Build rows from reactions
            try {
                String raw = message.getReactionsRaw();
                if (raw != null && !raw.isEmpty() && container != null) {
                    org.json.JSONArray arr = new org.json.JSONArray(raw);
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject r = arr.getJSONObject(i);
                        String emoji = r.optString("emoji", "");
                        org.json.JSONObject userObj = r.optJSONObject("user");
                        String uname = userObj != null ? userObj.optString("username", "Unknown") : "Unknown";

                        android.widget.LinearLayout row = new android.widget.LinearLayout(context);
                        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                        row.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));
                        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

                        android.widget.TextView tvEmoji = new android.widget.TextView(context);
                        tvEmoji.setText(emoji);
                        tvEmoji.setTextSize(20);
                        tvEmoji.setPadding(0, 0, dp(context, 12), 0);
                        tvEmoji.setTextColor(android.graphics.Color.WHITE);

                        android.widget.TextView tvName = new android.widget.TextView(context);
                        tvName.setText(uname);
                        tvName.setTextSize(16);
                        tvName.setTextColor(android.graphics.Color.WHITE);

                        row.addView(tvEmoji);
                        row.addView(tvName);
                        container.addView(row);
                    }
                } else if (container != null) {
                    android.widget.TextView empty = new android.widget.TextView(context);
                    empty.setText("No reactions");
                    empty.setTextColor(0xFF888888);
                    empty.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));
                    container.addView(empty);
                }
            } catch (Exception e) {
                if (container != null) {
                    android.widget.TextView empty = new android.widget.TextView(context);
                    empty.setText("No reactions");
                    empty.setTextColor(0xFF888888);
                    empty.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));
                    container.addView(empty);
                }
            }

            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
            builder.setView(dialogView);
            builder.setCancelable(true);
            android.app.AlertDialog dlg = builder.create();
            if (btnClose != null) btnClose.setOnClickListener(v -> dlg.dismiss());
            if (dlg.getWindow() != null) {
                android.view.Window w = dlg.getWindow();
                w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            }
            dlg.show();
        }

        private int dp(Context ctx, int value) {
            float d = ctx.getResources().getDisplayMetrics().density;
            return (int) (value * d);
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
    
    // Helper methods for file messages
    private static String getFileInfoFromMessage(Message message) {
        try {
            if (message.getAttachments() != null && !message.getAttachments().isEmpty()) {
                org.json.JSONArray attachments = new org.json.JSONArray(message.getAttachments());
                if (attachments.length() > 0) {
                    org.json.JSONObject attachment = attachments.getJSONObject(0);
                    String originalName = attachment.optString("originalName", "Unknown File");
                    originalName = fixProbableMojibake(originalName);
                    long fileSize = attachment.optLong("size", 0);
                    String mimeType = attachment.optString("mimeType", "");
                    
                    String fileType = getFileTypeFromMime(mimeType);
                    String sizeStr = formatFileSize(fileSize);
                    
                    return "📄 " + originalName + "\n" + fileType + " • " + sizeStr;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "📄 File";
    }
    
    private static String[] parseFileDataFromMessage(Message message) {
        try {
            if (message.getAttachments() != null && !message.getAttachments().isEmpty()) {
                org.json.JSONArray attachments = new org.json.JSONArray(message.getAttachments());
                if (attachments.length() > 0) {
                    org.json.JSONObject attachment = attachments.getJSONObject(0);
                    String fileUrl = attachment.optString("url", "");
                    String fileName = attachment.optString("filename", "");
                    String originalName = attachment.optString("originalName", "");
                    originalName = fixProbableMojibake(originalName);
                    String mimeType = attachment.optString("mimeType", "");
                    long fileSize = attachment.optLong("size", 0);
                    
                    // Convert relative URL to full URL
                    if (!fileUrl.startsWith("http")) {
                        fileUrl = "http://" + ServerConfig.getServerIp() + ":" + ServerConfig.getServerPort() + fileUrl;
                    }
                    
                    return new String[]{fileUrl, fileName, originalName, mimeType, String.valueOf(fileSize)};
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    private static boolean looksLikeMojibake(String s) {
        // Heuristic: common UTF-8 -> ISO-8859-1 artifacts for Vietnamese
        if (s == null) return false;
        return s.contains("Ã") || s.contains("Â") || s.contains("â") || s.contains("ï»") || 
               s.contains("áº") || s.contains("á»") || s.contains("áº") || s.contains("áº") ||
               s.contains("áº") || s.contains("áº") || s.contains("áº") || s.contains("áº") ||
               s.contains("áº") || s.contains("áº") || s.contains("áº") || s.contains("áº");
    }

    private static String fixProbableMojibake(String s) {
        if (s == null || s.isEmpty()) return s;
        if (!looksLikeMojibake(s)) return s;
        try {
            // Try UTF-8 -> ISO-8859-1 -> UTF-8 conversion
            byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            String decoded = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            // If decoding produced replacement chars everywhere, keep original
            if (decoded.replace("\uFFFD", "").trim().isEmpty()) return s;
            return decoded;
        } catch (Exception ignored) {
            return s;
        }
    }
    
    private static String getFileTypeFromMime(String mimeType) {
        if (mimeType == null) return "File";
        
        if (mimeType.equals("application/pdf")) return "PDF Document";
        if (mimeType.equals("text/plain")) return "Text File";
        if (mimeType.contains("word")) return "Word Document";
        if (mimeType.contains("excel")) return "Excel Spreadsheet";
        if (mimeType.contains("powerpoint")) return "PowerPoint Presentation";
        
        return "File";
    }
    
    private static String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024.0));
        return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
    }

    private static boolean isFileDownloaded(Context context, Message message) {
        try {
            String[] fileData = parseFileDataFromMessage(message);
            if (fileData == null) return false;
            String originalName = fileData[2];
            java.io.File dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) return false;
            java.io.File f = new java.io.File(dir, originalName);
            return f.exists();
        } catch (Exception ignored) {}
        return false;
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
