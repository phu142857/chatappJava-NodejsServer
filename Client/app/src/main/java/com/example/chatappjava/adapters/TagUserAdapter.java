package com.example.chatappjava.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.chatappjava.R;
import com.example.chatappjava.models.User;
import com.example.chatappjava.utils.AvatarManager;
import de.hdodenhof.circleimageview.CircleImageView;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TagUserAdapter extends RecyclerView.Adapter<TagUserAdapter.TagUserViewHolder> {
    
    private final List<User> users;
    private final Set<String> selectedUserIds;
    private final OnTagUserClickListener listener;
    private static AvatarManager avatarManager;
    
    public interface OnTagUserClickListener {
        void onUserClick(User user, boolean isSelected);
    }
    
    public TagUserAdapter(Context context, List<User> users, Set<String> selectedUserIds, OnTagUserClickListener listener) {
        this.users = users;
        this.selectedUserIds = selectedUserIds != null ? selectedUserIds : new HashSet<>();
        this.listener = listener;
        avatarManager = AvatarManager.getInstance(context);
    }
    
    @NonNull
    @Override
    public TagUserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tag_user, parent, false);
        return new TagUserViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull TagUserViewHolder holder, int position) {
        User user = users.get(position);
        holder.bind(user, selectedUserIds.contains(user.getId()));
    }
    
    @Override
    public int getItemCount() {
        return users.size();
    }
    
    public void updateSelection(Set<String> newSelection) {
        selectedUserIds.clear();
        selectedUserIds.addAll(newSelection);
        notifyDataSetChanged();
    }
    
    public class TagUserViewHolder extends RecyclerView.ViewHolder {
        private final CircleImageView ivProfilePicture;
        private final TextView tvDisplayName;
        private final TextView tvUsername;
        private final CheckBox cbSelect;
        
        public TagUserViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProfilePicture = itemView.findViewById(R.id.iv_profile_picture);
            tvDisplayName = itemView.findViewById(R.id.tv_display_name);
            tvUsername = itemView.findViewById(R.id.tv_username);
            cbSelect = itemView.findViewById(R.id.cb_select);
        }
        
        @SuppressLint("SetTextI18n")
        public void bind(User user, boolean isSelected) {
            // Set display name
            String displayName = user.getDisplayName();
            tvDisplayName.setText(displayName);
            
            // Set username
            tvUsername.setText("@" + user.getUsername());
            
            // Set checkbox state
            cbSelect.setChecked(isSelected);
            
            // Load avatar
            if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
                String avatarUrl = user.getAvatar();
                if (!avatarUrl.startsWith("http")) {
                    avatarUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + 
                               ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + avatarUrl;
                }
                avatarManager.loadAvatar(avatarUrl, ivProfilePicture, R.drawable.ic_profile_placeholder);
            } else {
                ivProfilePicture.setImageResource(R.drawable.ic_profile_placeholder);
            }
            
            // Item click - toggle selection
            itemView.setOnClickListener(v -> {
                boolean newState = !cbSelect.isChecked();
                cbSelect.setChecked(newState);
                
                if (newState) {
                    selectedUserIds.add(user.getId());
                } else {
                    selectedUserIds.remove(user.getId());
                }
                
                if (listener != null) {
                    listener.onUserClick(user, newState);
                }
            });
            
            // Checkbox click - toggle selection
            cbSelect.setOnClickListener(v -> {
                boolean newState = cbSelect.isChecked();
                
                if (newState) {
                    selectedUserIds.add(user.getId());
                } else {
                    selectedUserIds.remove(user.getId());
                }
                
                if (listener != null) {
                    listener.onUserClick(user, newState);
                }
            });
        }
    }
}

