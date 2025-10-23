package com.example.chatappjava.ui.theme;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatappjava.R;
import com.example.chatappjava.models.User;
import com.example.chatappjava.utils.AvatarManager;

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class BlockedUsersAdapter extends RecyclerView.Adapter<BlockedUsersAdapter.VH> {

    public interface OnUnblockClick {
        void onUnblock(User user);
    }

    private final List<User> users;
    private final OnUnblockClick listener;

    public BlockedUsersAdapter(List<User> users, OnUnblockClick listener) {
        this.users = users;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_blocked_user, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(users.get(position));
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    class VH extends RecyclerView.ViewHolder {
        CircleImageView ivAvatar;
        TextView tvName;
        TextView tvUsername;
        Button btnUnblock;

        VH(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_profile_picture);
            tvName = itemView.findViewById(R.id.tv_display_name);
            tvUsername = itemView.findViewById(R.id.tv_username);
            btnUnblock = itemView.findViewById(R.id.btn_unblock);
        }

        void bind(User u) {
            tvName.setText(u.getDisplayName());
            tvUsername.setText("@" + (u.getUsername() != null ? u.getUsername() : ""));
            // tvStatus removed - not present in current layout
            String avatarUrl = u.getFullAvatarUrl();
            if (avatarUrl != null) {
                AvatarManager.getInstance(itemView.getContext())
                        .loadAvatar(avatarUrl, ivAvatar, R.drawable.ic_profile_placeholder);
            } else {
                ivAvatar.setImageResource(R.drawable.ic_profile_placeholder);
            }
            btnUnblock.setOnClickListener(v -> {
                if (listener != null) listener.onUnblock(u);
            });
        }
    }
}


