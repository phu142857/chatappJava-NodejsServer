package com.example.chatappjava.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.chatappjava.R;
import com.example.chatappjava.models.Comment;
import com.example.chatappjava.utils.AvatarManager;
import com.example.chatappjava.config.ServerConfig;
import de.hdodenhof.circleimageview.CircleImageView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReactionUserAdapter extends RecyclerView.Adapter<ReactionUserAdapter.ReactionUserViewHolder> {

    private final List<ReactionUser> reactionUsers;
    private final AvatarManager avatarManager;
    private final OnUserClickListener listener;

    public interface OnUserClickListener {
        void onUserClick(String userId);
    }

    public static class ReactionUser {
        public String userId;
        public String username;
        public String avatar;
        public String reactionType; // like, love, haha, wow, sad, angry
    }

    public ReactionUserAdapter(List<Comment.Reaction> reactions, AvatarManager avatarManager, OnUserClickListener listener) {
        this.avatarManager = avatarManager;
        this.listener = listener;
        this.reactionUsers = new ArrayList<>();
        
        // Group reactions by user and keep the latest reaction type
        Map<String, ReactionUser> userReactions = new HashMap<>();
        for (Comment.Reaction reaction : reactions) {
            ReactionUser user = userReactions.get(reaction.userId);
            if (user == null) {
                user = new ReactionUser();
                user.userId = reaction.userId;
                user.username = reaction.username;
                user.avatar = reaction.avatar;
                user.reactionType = reaction.type;
                userReactions.put(reaction.userId, user);
            } else {
                // Update to latest reaction type (user reacted again)
                user.reactionType = reaction.type;
                // Also update username and avatar if available
                if (reaction.username != null && !reaction.username.isEmpty()) {
                    user.username = reaction.username;
                }
                if (reaction.avatar != null && !reaction.avatar.isEmpty()) {
                    user.avatar = reaction.avatar;
                }
            }
        }
        this.reactionUsers.addAll(userReactions.values());
    }

    @NonNull
    @Override
    public ReactionUserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_reaction_user, parent, false);
        return new ReactionUserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReactionUserViewHolder holder, int position) {
        ReactionUser user = reactionUsers.get(position);
        
        // Set username (if available, otherwise show userId)
        if (user.username != null && !user.username.isEmpty()) {
            holder.tvUserName.setText(user.username);
            holder.tvUserUsername.setText("@" + user.username);
        } else {
            holder.tvUserName.setText("User");
            holder.tvUserUsername.setText(user.userId != null ? user.userId.substring(0, Math.min(8, user.userId.length())) : "");
        }
        
        // Set reaction emoji
        String emoji = getReactionEmoji(user.reactionType);
        holder.tvReactionEmoji.setText(emoji);
        
        // Load avatar
        if (user.avatar != null && !user.avatar.isEmpty()) {
            String avatarUrl = user.avatar;
            if (!avatarUrl.startsWith("http")) {
                if (!avatarUrl.startsWith("/")) {
                    avatarUrl = "/" + avatarUrl;
                }
                avatarUrl = ServerConfig.getBaseUrl() + avatarUrl;
            }
            avatarManager.loadAvatar(avatarUrl, holder.ivUserAvatar, R.drawable.ic_profile_placeholder);
        } else {
            holder.ivUserAvatar.setImageResource(R.drawable.ic_profile_placeholder);
        }
        
        // Set click listener
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onUserClick(user.userId);
            }
        });
    }

    @Override
    public int getItemCount() {
        return reactionUsers.size();
    }

    private String getReactionEmoji(String type) {
        if (type == null) return "👍";
        switch (type) {
            case "like": return "👍";
            case "love": return "❤️";
            case "haha": return "😂";
            case "wow": return "😮";
            case "sad": return "😢";
            case "angry": return "😠";
            default: return "👍";
        }
    }

    static class ReactionUserViewHolder extends RecyclerView.ViewHolder {
        CircleImageView ivUserAvatar;
        TextView tvUserName;
        TextView tvUserUsername;
        TextView tvReactionEmoji;

        ReactionUserViewHolder(@NonNull View itemView) {
            super(itemView);
            ivUserAvatar = itemView.findViewById(R.id.iv_user_avatar);
            tvUserName = itemView.findViewById(R.id.tv_user_name);
            tvUserUsername = itemView.findViewById(R.id.tv_user_username);
            tvReactionEmoji = itemView.findViewById(R.id.tv_reaction_emoji);
        }
    }
}

