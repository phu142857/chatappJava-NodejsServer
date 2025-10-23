package com.example.chatappjava.adapters;

import android.annotation.SuppressLint;
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
    
    private final List<User> users;
    private final OnUserClickListener listener;
    private static String mode; // "add_members" or null for normal search
    private static List<String> currentGroupMemberIds; // Track current group members

    public interface OnUserClickListener {
        void onUserClick(User user);
        void onUserLongClick(User user);
        void onAddFriendClick(User user);
        void onStartChatClick(User user);
        void onRespondFriendRequest(User user, boolean accept);
    }

    public UserSearchAdapter(Context context, List<User> users, OnUserClickListener listener, String mode, List<String> currentGroupMemberIds) {
        this.users = users;
        this.listener = listener;
        AvatarManager avatarManager = AvatarManager.getInstance(context);
        UserSearchAdapter.mode = mode;
        UserSearchAdapter.currentGroupMemberIds = currentGroupMemberIds;
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
        private final CircleImageView ivProfilePicture;
        private final TextView tvDisplayName;
        private final TextView tvUsername;
        private final TextView tvEmail;
        private final Button btnAddFriend;
        private final Button btnSecondary;
        private final View itemView;
        
        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            this.itemView = itemView;
            ivProfilePicture = itemView.findViewById(R.id.iv_profile_picture);
            tvDisplayName = itemView.findViewById(R.id.tv_display_name);
            tvUsername = itemView.findViewById(R.id.tv_username);
            tvEmail = itemView.findViewById(R.id.tv_email);
            btnAddFriend = itemView.findViewById(R.id.btn_add_friend);
            btnSecondary = itemView.findViewById(R.id.btn_secondary_action);
        }
        
        @SuppressLint("SetTextI18n")
        public void bind(User user, OnUserClickListener listener) {
            // Set display name
            tvDisplayName.setText(user.getDisplayName());
            
            if (!user.getUsername().equals(user.getDisplayName())) {
                tvUsername.setText("@" + user.getUsername());
                tvUsername.setVisibility(View.VISIBLE);
            } else {
                tvUsername.setVisibility(View.GONE);
            }
            
            tvEmail.setText(user.getEmail());
            
            if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
                String avatarUrl = user.getAvatar();
                if (!avatarUrl.startsWith("http")) {
                    avatarUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + avatarUrl;
                }
                AvatarManager.getInstance(itemView.getContext()).loadAvatar(
                    avatarUrl,
                    ivProfilePicture,
                    R.drawable.ic_profile_placeholder
                );
            } else {
                ivProfilePicture.setImageResource(R.drawable.ic_profile_placeholder);
            }
            
            // Avatar click -> open ProfileViewActivity
            ivProfilePicture.setOnClickListener(v -> {
                try {
                    android.content.Intent intent = new android.content.Intent(itemView.getContext(), com.example.chatappjava.ui.theme.ProfileViewActivity.class);
                    intent.putExtra("user", user.toJson().toString());
                    itemView.getContext().startActivity(intent);
                } catch (org.json.JSONException e) {
                    if (listener != null) listener.onUserClick(user);
                }
            });
            
            String frStatus = user.getFriendRequestStatus();
            
            if ("forward".equals(mode)) {
                // Forward mode: show single Forward button
                btnAddFriend.setText("Forward");
                btnAddFriend.setVisibility(View.VISIBLE);
                btnAddFriend.setEnabled(true);
                btnAddFriend.setAlpha(1.0f);
                btnSecondary.setVisibility(View.GONE);
            } else if ("add_members".equals(mode)) {
                boolean isAlreadyMember = currentGroupMemberIds != null && currentGroupMemberIds.contains(user.getId());
                
                if (isAlreadyMember) {
                    btnAddFriend.setText("Already a Member");
                    btnAddFriend.setVisibility(View.VISIBLE);
                    btnAddFriend.setEnabled(false);
                    btnAddFriend.setAlpha(0.6f);
                    btnSecondary.setVisibility(View.GONE);
                } else {
                    btnAddFriend.setText("Add to Group");
                    btnAddFriend.setVisibility(View.VISIBLE);
                    btnAddFriend.setEnabled(true);
                    btnAddFriend.setAlpha(1.0f);
                    btnSecondary.setVisibility(View.GONE);
                }
            } else {
                if (user.isFriend()) {
                    btnAddFriend.setText("Chat");
                    btnAddFriend.setVisibility(View.VISIBLE);
                    btnAddFriend.setEnabled(true);
                    btnAddFriend.setAlpha(1.0f);
                    btnSecondary.setVisibility(View.GONE);
                } else if ("received".equals(frStatus)) {
                    btnAddFriend.setText("Accept");
                    btnAddFriend.setVisibility(View.VISIBLE);
                    btnAddFriend.setEnabled(true);
                    btnAddFriend.setAlpha(1.0f);
                    btnSecondary.setText("Reject");
                    btnSecondary.setVisibility(View.VISIBLE);
                } else if ("sent".equals(frStatus) || "pending".equals(frStatus)) {
                    btnAddFriend.setText("Pending");
                    btnAddFriend.setVisibility(View.VISIBLE);
                    btnAddFriend.setEnabled(false);
                    btnAddFriend.setAlpha(0.6f);
                    btnSecondary.setText("Chat");
                    btnSecondary.setVisibility(View.VISIBLE);
                } else {
                    btnAddFriend.setText("Chat");
                    btnAddFriend.setVisibility(View.VISIBLE);
                    btnAddFriend.setEnabled(true);
                    btnAddFriend.setAlpha(1.0f);
                    btnSecondary.setText("Add Friend");
                    btnSecondary.setVisibility(View.VISIBLE);
                }
            }
            
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onUserClick(user);
                }
            });
            
            itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onUserLongClick(user);
                    return true;
                }
                return false;
            });

            btnAddFriend.setOnClickListener(v -> {
                if (listener != null) {
                    if ("forward".equals(mode)) {
                        listener.onUserClick(user);
                    } else if ("add_members".equals(mode)) {
                        boolean isAlreadyMember = currentGroupMemberIds != null && currentGroupMemberIds.contains(user.getId());
                        if (!isAlreadyMember) {
                            listener.onUserClick(user);
                        }
                    } else {
                        String s = user.getFriendRequestStatus();
                        if (user.isFriend()) {
                            listener.onStartChatClick(user);
                        } else if ("received".equals(s)) {
                            listener.onRespondFriendRequest(user, true);
                        } else if ("sent".equals(s) || "pending".equals(s)) {
                            // Pending: disabled
                        } else {
                            listener.onStartChatClick(user);
                        }
                    }
                }
            });

            btnSecondary.setOnClickListener(v -> {
                if (listener != null) {
                    String s = user.getFriendRequestStatus();
                    if (user.isFriend()) {
                        // no-op
                    } else if ("received".equals(s)) {
                        listener.onRespondFriendRequest(user, false);
                    } else if ("sent".equals(s) || "pending".equals(s)) {
                        listener.onStartChatClick(user);
                    } else {
                        listener.onAddFriendClick(user);
                    }
                }
            });
        }
    }
}
