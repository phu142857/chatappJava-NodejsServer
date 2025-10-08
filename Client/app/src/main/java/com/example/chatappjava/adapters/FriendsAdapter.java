package com.example.chatappjava.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatappjava.R;
import com.example.chatappjava.models.User;
import com.example.chatappjava.utils.AvatarManager;

import java.util.ArrayList;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.FriendViewHolder> {

    public interface OnFriendClickListener {
        void onFriendClick(User user);
    }

    private final List<User> friends = new ArrayList<>();
    private final OnFriendClickListener listener;

    public FriendsAdapter(OnFriendClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<User> items) {
        friends.clear();
        if (items != null) friends.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FriendViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend_compact, parent, false);
        return new FriendViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FriendViewHolder holder, int position) {
        holder.bind(friends.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return friends.size();
    }

    static class FriendViewHolder extends RecyclerView.ViewHolder {
        private final CircleImageView ivAvatar;
        private final TextView tvName;
        private final TextView tvSubtitle;

        FriendViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
            tvName = itemView.findViewById(R.id.tv_name);
            tvSubtitle = itemView.findViewById(R.id.tv_subtitle);
        }

        void bind(User user, OnFriendClickListener listener) {
            tvName.setText(user.getDisplayName());
            tvSubtitle.setText("@" + user.getUsername());

            String avatarUrl = user.getAvatar();
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                if (!avatarUrl.startsWith("http")) {
                    avatarUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + avatarUrl;
                }
                AvatarManager.getInstance(itemView.getContext()).loadAvatar(avatarUrl, ivAvatar, R.drawable.ic_profile_placeholder);
            } else {
                ivAvatar.setImageResource(R.drawable.ic_profile_placeholder);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onFriendClick(user);
            });
        }
    }
}


