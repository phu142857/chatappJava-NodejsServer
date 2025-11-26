package com.example.chatappjava.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatappjava.R;
import com.example.chatappjava.models.User;
import com.example.chatappjava.utils.AvatarManager;

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class MentionSuggestionAdapter extends RecyclerView.Adapter<MentionSuggestionAdapter.MentionViewHolder> {
    
    private final List<User> users;
    private final OnMentionClickListener listener;
    private static AvatarManager avatarManager;
    
    public interface OnMentionClickListener {
        void onMentionClick(User user);
    }
    
    public MentionSuggestionAdapter(Context context, List<User> users, OnMentionClickListener listener) {
        this.users = users;
        this.listener = listener;
        avatarManager = AvatarManager.getInstance(context);
    }
    
    @NonNull
    @Override
    public MentionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mention_suggestion, parent, false);
        return new MentionViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull MentionViewHolder holder, int position) {
        User user = users.get(position);
        holder.bind(user, listener);
    }
    
    @Override
    public int getItemCount() {
        return users.size();
    }
    
    public static class MentionViewHolder extends RecyclerView.ViewHolder {
        private final CircleImageView ivMentionAvatar;
        private final TextView tvMentionDisplayName;
        private final TextView tvMentionUsername;
        
        public MentionViewHolder(@NonNull View itemView) {
            super(itemView);
            ivMentionAvatar = itemView.findViewById(R.id.iv_mention_avatar);
            tvMentionDisplayName = itemView.findViewById(R.id.tv_mention_display_name);
            tvMentionUsername = itemView.findViewById(R.id.tv_mention_username);
        }
        
        @SuppressLint("SetTextI18n")
        public void bind(User user, OnMentionClickListener listener) {
            // Set display name
            String displayName = user.getDisplayName();
            tvMentionDisplayName.setText(displayName);
            
            // Set username with @ prefix
            tvMentionUsername.setText("@" + user.getUsername());
            
            // Load avatar
            if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
                String avatarUrl = user.getAvatar();
                if (!avatarUrl.startsWith("http")) {
                    avatarUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + 
                               ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + avatarUrl;
                }
                avatarManager.loadAvatar(avatarUrl, ivMentionAvatar, R.drawable.ic_profile_placeholder);
            } else {
                ivMentionAvatar.setImageResource(R.drawable.ic_profile_placeholder);
            }
            
            // Click listener
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onMentionClick(user);
                }
            });
        }
    }
}

