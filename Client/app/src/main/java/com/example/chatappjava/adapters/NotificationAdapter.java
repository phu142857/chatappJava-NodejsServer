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
import com.example.chatappjava.models.Notification;
import com.example.chatappjava.utils.AvatarManager;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {
    
    private final Context context;
    private final List<Notification> notificationList;
    private OnNotificationClickListener onNotificationClickListener;
    private final AvatarManager avatarManager;
    
    public interface OnNotificationClickListener {
        void onNotificationClick(Notification notification);
    }
    
    public NotificationAdapter(Context context) {
        this.context = context;
        this.notificationList = new ArrayList<>();
        this.avatarManager = AvatarManager.getInstance(context);
    }
    
    public void setOnNotificationClickListener(OnNotificationClickListener listener) {
        this.onNotificationClickListener = listener;
    }
    
    @android.annotation.SuppressLint("NotifyDataSetChanged")
    public void updateNotifications(List<Notification> newNotificationList) {
        this.notificationList.clear();
        this.notificationList.addAll(newNotificationList);
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        Notification notification = notificationList.get(position);
        holder.bind(notification);
    }
    
    @Override
    public int getItemCount() {
        return notificationList.size();
    }
    
    class NotificationViewHolder extends RecyclerView.ViewHolder {
        private final CircleImageView ivNotificationAvatar;
        private final TextView tvNotificationText;
        private final TextView tvNotificationTime;
        private final ImageView ivNotificationPreview;
        private final View viewUnreadIndicator;
        
        public NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            
            ivNotificationAvatar = itemView.findViewById(R.id.iv_notification_avatar);
            tvNotificationText = itemView.findViewById(R.id.tv_notification_text);
            tvNotificationTime = itemView.findViewById(R.id.tv_notification_time);
            ivNotificationPreview = itemView.findViewById(R.id.iv_notification_preview);
            viewUnreadIndicator = itemView.findViewById(R.id.view_unread_indicator);
            
            itemView.setOnClickListener(v -> {
                if (onNotificationClickListener != null) {
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        onNotificationClickListener.onNotificationClick(notificationList.get(position));
                    }
                }
            });
        }
        
        public void bind(Notification notification) {
            tvNotificationText.setText(notification.getNotificationText());
            tvNotificationTime.setText(notification.getFormattedTime());
            
            // Show/hide unread indicator
            viewUnreadIndicator.setVisibility(notification.isRead() ? View.GONE : View.VISIBLE);
            
            // Load actor avatar
            String avatarUrl = notification.getActorAvatar();
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                if (!avatarUrl.startsWith("http")) {
                    avatarUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + 
                               ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + avatarUrl;
                }
                avatarManager.loadAvatar(avatarUrl, ivNotificationAvatar, R.drawable.ic_profile_placeholder);
            } else {
                ivNotificationAvatar.setImageResource(R.drawable.ic_profile_placeholder);
            }
            
            // Load post preview image if available
            String previewImage = notification.getPostPreviewImage();
            if (previewImage != null && !previewImage.isEmpty()) {
                if (!previewImage.startsWith("http")) {
                    previewImage = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + 
                                  ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + previewImage;
                }
                ivNotificationPreview.setVisibility(View.VISIBLE);
                Picasso.get()
                    .load(previewImage)
                    .placeholder(R.drawable.ic_image)
                    .error(R.drawable.ic_image)
                    .resize(48, 48)
                    .centerCrop()
                    .into(ivNotificationPreview);
            } else {
                ivNotificationPreview.setVisibility(View.GONE);
            }
        }
    }
}

