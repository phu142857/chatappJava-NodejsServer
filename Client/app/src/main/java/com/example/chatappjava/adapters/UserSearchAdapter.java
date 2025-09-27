package com.example.chatappjava.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.chatappjava.R;
import com.example.chatappjava.models.User;
import com.example.chatappjava.utils.AvatarManager;
import de.hdodenhof.circleimageview.CircleImageView;
import java.util.List;

public class UserSearchAdapter extends RecyclerView.Adapter<UserSearchAdapter.UserViewHolder> {
    
    private List<User> users;
    private OnUserClickListener listener;
    private Context context;
    private AvatarManager avatarManager;
    private static String mode; // "add_members" or null for normal search
    private static List<String> currentGroupMemberIds; // Track current group members
    
    public interface OnUserClickListener {
        void onUserClick(User user);
        void onUserLongClick(User user);
        void onAddFriendClick(User user);
        void onStartChatClick(User user);
        void onRespondFriendRequest(User user, boolean accept);
    }
    
    public UserSearchAdapter(Context context, List<User> users, OnUserClickListener listener) {
        this.context = context;
        this.users = users;
        this.listener = listener;
        this.avatarManager = AvatarManager.getInstance(context);
        this.mode = null; // Default to normal search mode
    }
    
    public UserSearchAdapter(Context context, List<User> users, OnUserClickListener listener, String mode) {
        this.context = context;
        this.users = users;
        this.listener = listener;
        this.avatarManager = AvatarManager.getInstance(context);
        this.mode = mode;
        this.currentGroupMemberIds = null;
    }
    
    public UserSearchAdapter(Context context, List<User> users, OnUserClickListener listener, String mode, List<String> currentGroupMemberIds) {
        this.context = context;
        this.users = users;
        this.listener = listener;
        this.avatarManager = AvatarManager.getInstance(context);
        this.mode = mode;
        this.currentGroupMemberIds = currentGroupMemberIds;
    }
    
    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user_search, parent, false);
        return new UserViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = users.get(position);
        holder.bind(user, listener);
    }
    
    @Override
    public int getItemCount() {
        return users.size();
    }
    
    public static class UserViewHolder extends RecyclerView.ViewHolder {
        private CircleImageView ivProfilePicture;
        private TextView tvDisplayName;
        private TextView tvUsername;
        private TextView tvEmail;
        private TextView tvStatus;
        private ImageView ivStatusIndicator;
        private Button btnAddFriend;
        private Button btnSecondary;
        private ImageView ivAddChat;
        private View itemView;
        
        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            this.itemView = itemView;
            ivProfilePicture = itemView.findViewById(R.id.iv_profile_picture);
            tvDisplayName = itemView.findViewById(R.id.tv_display_name);
            tvUsername = itemView.findViewById(R.id.tv_username);
            tvEmail = itemView.findViewById(R.id.tv_email);
            tvStatus = itemView.findViewById(R.id.tv_status);
            ivStatusIndicator = itemView.findViewById(R.id.iv_status_indicator);
            btnAddFriend = itemView.findViewById(R.id.btn_add_friend);
            btnSecondary = itemView.findViewById(R.id.btn_secondary_action);
            ivAddChat = itemView.findViewById(R.id.iv_add_chat);
        }
        
        public void bind(User user, OnUserClickListener listener) {
            // Set display name
            tvDisplayName.setText(user.getDisplayName());
            
            // Set username (show if different from display name)
            if (!user.getUsername().equals(user.getDisplayName())) {
                tvUsername.setText("@" + user.getUsername());
                tvUsername.setVisibility(View.VISIBLE);
            } else {
                tvUsername.setVisibility(View.GONE);
            }
            
            // Set email
            tvEmail.setText(user.getEmail());
            
            // Set status
            tvStatus.setText(user.getStatusText());
            
            // Set status indicator
            if (user.isOnline()) {
                ivStatusIndicator.setImageResource(R.drawable.ic_status_online);
            } else {
                ivStatusIndicator.setImageResource(R.drawable.ic_status_offline);
            }
            
            // Load profile picture
            if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
                String avatarUrl = user.getAvatar();
                android.util.Log.d("UserSearchAdapter", "Loading user avatar: " + avatarUrl);
                
                // Construct full URL if needed (like other adapters)
                if (!avatarUrl.startsWith("http")) {
                    avatarUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + 
                               ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + avatarUrl;
                    android.util.Log.d("UserSearchAdapter", "Constructed full URL: " + avatarUrl);
                }
                
                // Use AvatarManager to load avatar with caching
                AvatarManager.getInstance(itemView.getContext()).loadAvatar(
                    avatarUrl, 
                    ivProfilePicture, 
                    R.drawable.ic_profile_placeholder
                );
            } else {
                android.util.Log.d("UserSearchAdapter", "No avatar URL, using placeholder");
                ivProfilePicture.setImageResource(R.drawable.ic_profile_placeholder);
            }
            
            // Configure actions per state
            String frStatus = user.getFriendRequestStatus();
            
            // Check if we're in add_members mode
            if ("add_members".equals(mode)) {
                // Check if user is already a member of the group
                boolean isAlreadyMember = currentGroupMemberIds != null && currentGroupMemberIds.contains(user.getId());
                
                if (isAlreadyMember) {
                    // User is already a member - hide add button and show status
                    btnAddFriend.setText("Already a Member");
                    btnAddFriend.setVisibility(View.VISIBLE);
                    btnAddFriend.setEnabled(false);
                    btnAddFriend.setAlpha(0.6f);
                    btnSecondary.setVisibility(View.GONE);
                    ivAddChat.setVisibility(View.GONE);
                } else {
                    // User is not a member - show add button
                    btnAddFriend.setText("Add to Group");
                    btnAddFriend.setVisibility(View.VISIBLE);
                    btnAddFriend.setEnabled(true);
                    btnAddFriend.setAlpha(1.0f);
                    btnSecondary.setVisibility(View.GONE);
                    ivAddChat.setVisibility(View.GONE);
                }
            } else {
                // Normal search mode - use original logic
                if (user.isFriend()) {
                    btnAddFriend.setText("Chat");
                    btnAddFriend.setVisibility(View.VISIBLE);
                    btnSecondary.setVisibility(View.GONE);
                    ivAddChat.setVisibility(View.GONE);
                } else if ("received".equals(frStatus)) {
                    btnAddFriend.setText("Accept");
                    btnSecondary.setText("Reject");
                    btnAddFriend.setVisibility(View.VISIBLE);
                    btnSecondary.setVisibility(View.VISIBLE);
                    ivAddChat.setVisibility(View.VISIBLE); // extra Chat
                } else if ("sent".equals(frStatus) || "pending".equals(frStatus)) {
                    // User has sent a friend request that is pending
                    btnAddFriend.setText("Pending");
                    btnAddFriend.setVisibility(View.VISIBLE);
                    btnSecondary.setVisibility(View.GONE);
                    ivAddChat.setVisibility(View.GONE);
                } else {
                    // No friend request or cancelled - show Add Friend button
                    btnAddFriend.setText("Chat");
                    btnSecondary.setText("Add Friend");
                    btnAddFriend.setVisibility(View.VISIBLE);
                    btnSecondary.setVisibility(View.VISIBLE);
                    ivAddChat.setVisibility(View.GONE);
                }
            }
            
            // Set click listeners
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        listener.onUserClick(user);
                    }
                }
            });
            
            itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (listener != null) {
                        listener.onUserLongClick(user);
                        return true;
                    }
                    return false;
                }
            });

            // Primary button
            btnAddFriend.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        // Check if we're in add_members mode
                        if ("add_members".equals(mode)) {
                            // Check if user is already a member
                            boolean isAlreadyMember = currentGroupMemberIds != null && currentGroupMemberIds.contains(user.getId());
                            if (!isAlreadyMember) {
                                // In add members mode, primary button adds user to group
                                listener.onUserClick(user);
                            }
                            // If already member, do nothing (button is disabled)
                        } else {
                            // Normal search mode - use original logic
                            String s = user.getFriendRequestStatus();
                            if (user.isFriend()) {
                                listener.onStartChatClick(user);
                            } else if ("received".equals(s)) {
                                listener.onRespondFriendRequest(user, true);
                            } else if ("sent".equals(s) || "pending".equals(s)) {
                                // Pending request - do nothing or show message
                                // Could show a toast or do nothing
                            } else {
                                // default primary acts as Chat
                                listener.onStartChatClick(user);
                            }
                        }
                    }
                }
            });

            // Secondary button
            btnSecondary.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        String s = user.getFriendRequestStatus();
                        if (user.isFriend()) {
                            // no-op
                        } else if ("received".equals(s)) {
                            listener.onRespondFriendRequest(user, false);
                        } else {
                            // default secondary acts as Add Friend
                            listener.onAddFriendClick(user);
                        }
                    }
                }
            });

            // Extra chat icon for received state
            ivAddChat.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        listener.onStartChatClick(user);
                    }
                }
            });
        }
    }
}
