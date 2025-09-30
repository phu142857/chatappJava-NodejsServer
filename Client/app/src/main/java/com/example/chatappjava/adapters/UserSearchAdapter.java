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
        }
        
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
            tvStatus.setText(user.getStatusText());
            
            if (user.isOnline()) {
                ivStatusIndicator.setImageResource(R.drawable.ic_status_online);
            } else {
                ivStatusIndicator.setImageResource(R.drawable.ic_status_offline);
            }
            
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
            ivProfilePicture.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        android.content.Intent intent = new android.content.Intent(itemView.getContext(), com.example.chatappjava.ui.theme.ProfileViewActivity.class);
                        intent.putExtra("user", user.toJson().toString());
                        itemView.getContext().startActivity(intent);
                    } catch (org.json.JSONException e) {
                        if (listener != null) listener.onUserClick(user);
                    }
                }
            });
            
            String frStatus = user.getFriendRequestStatus();
            
            if ("add_members".equals(mode)) {
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

            btnAddFriend.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        if ("add_members".equals(mode)) {
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
                }
            });

            btnSecondary.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
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
                }
            });
        }
    }
}
