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

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class GroupMembersAdapter extends RecyclerView.Adapter<GroupMembersAdapter.MemberViewHolder> {
    
    public interface OnMemberClickListener {
        void onMemberClick(User member);
    }
    
    private final List<User> members;
    private final OnMemberClickListener listener;
    
    public GroupMembersAdapter(List<User> members, OnMemberClickListener listener) {
        this.members = members;
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group_member, parent, false);
        return new MemberViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
        User member = members.get(position);
        holder.bind(member);
    }
    
    @Override
    public int getItemCount() {
        return members.size();
    }
    
    class MemberViewHolder extends RecyclerView.ViewHolder {
        private final CircleImageView ivAvatar;
        private final TextView tvName;
        
        public MemberViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_member_avatar);
            tvName = itemView.findViewById(R.id.tv_member_name);
            
            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onMemberClick(members.get(position));
                }
            });
        }
        
        public void bind(User member) {
            tvName.setText(member.getDisplayName());
            
            // Load avatar with full URL construction
            if (member.getAvatar() != null && !member.getAvatar().isEmpty()) {
                String avatarUrl = member.getAvatar();
                android.util.Log.d("GroupMembersAdapter", "Loading member avatar: " + avatarUrl);
                
                // Construct full URL if needed
                if (!avatarUrl.startsWith("http")) {
                    avatarUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + 
                               ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + avatarUrl;
                    android.util.Log.d("GroupMembersAdapter", "Constructed full URL: " + avatarUrl);
                }
                
                AvatarManager.getInstance(itemView.getContext())
                        .loadAvatar(avatarUrl, ivAvatar, R.drawable.ic_profile_placeholder);
            } else {
                android.util.Log.d("GroupMembersAdapter", "No avatar URL, using placeholder");
                ivAvatar.setImageResource(R.drawable.ic_profile_placeholder);
            }
        }
    }
}
