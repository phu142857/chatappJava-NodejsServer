package com.example.chatappjava.adapters;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatappjava.R;
import com.example.chatappjava.models.FriendRequest;
import com.example.chatappjava.models.User;
import com.example.chatappjava.utils.AvatarManager;

import java.util.List;

import android.widget.ImageView;

public class FriendRequestAdapter extends RecyclerView.Adapter<FriendRequestAdapter.FriendRequestViewHolder> {

    private List<FriendRequest> friendRequests;
    private final OnFriendRequestActionListener listener;
    private final String currentUserId;

    public interface OnFriendRequestActionListener {
        void onAcceptRequest(FriendRequest request);
        void onRejectRequest(FriendRequest request);
        void onCancelRequest(FriendRequest request);
        void onUserClick(User user);
    }

    public FriendRequestAdapter(List<FriendRequest> friendRequests, String currentUserId, OnFriendRequestActionListener listener) {
        this.friendRequests = friendRequests;
        this.currentUserId = currentUserId;
        this.listener = listener;
    }

    @NonNull
    @Override
    public FriendRequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend_request, parent, false);
        return new FriendRequestViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FriendRequestViewHolder holder, int position) {
        FriendRequest request = friendRequests.get(position);
        holder.bind(request);
    }

    @Override
    public int getItemCount() {
        return friendRequests.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updateRequests(List<FriendRequest> newRequests) {
        System.out.println("FriendRequestAdapter: Updating with " + newRequests.size() + " requests");
        this.friendRequests = newRequests;
        notifyDataSetChanged();
    }

    public void removeRequest(FriendRequest request) {
        int position = friendRequests.indexOf(request);
        if (position != -1) {
            friendRequests.remove(position);
            notifyItemRemoved(position);
        }
    }

    class FriendRequestViewHolder extends RecyclerView.ViewHolder {
        private final ImageView civProfilePicture;
        private final TextView tvUsername;
        private final TextView tvEmail;
        private final LinearLayout llActionButtons;
        private final Button btnAccept;
        private final Button btnReject;
        private final LinearLayout llSentRequestActions;
        private final TextView tvStatus;
        private final Button btnCancel;

        public FriendRequestViewHolder(@NonNull View itemView) {
            super(itemView);
            civProfilePicture = itemView.findViewById(R.id.civ_profile_picture);
            tvUsername = itemView.findViewById(R.id.tv_username);
            tvEmail = itemView.findViewById(R.id.tv_email);
            llActionButtons = itemView.findViewById(R.id.ll_action_buttons);
            btnAccept = itemView.findViewById(R.id.btn_accept);
            btnReject = itemView.findViewById(R.id.btn_reject);
            llSentRequestActions = itemView.findViewById(R.id.ll_sent_request_actions);
            tvStatus = itemView.findViewById(R.id.tv_status);
            btnCancel = itemView.findViewById(R.id.btn_cancel);
        }

        @SuppressLint("SetTextI18n")
        public void bind(FriendRequest request) {
            System.out.println("FriendRequestAdapter: Binding request " + request.getId() + " status: " + request.getStatus());
            System.out.println("Current User ID: " + currentUserId);
            System.out.println("Request Sender ID: " + request.getSenderId());
            System.out.println("Request Receiver ID: " + request.getReceiverId());
            
            User userToShow;
            boolean isReceivedRequest;
            
            if (currentUserId.isEmpty()) {
                return;
            }
            
            if (currentUserId.equals(request.getReceiverId())) {
                isReceivedRequest = true;
            } else if (currentUserId.equals(request.getSenderId())) {
                isReceivedRequest = false;
            } else {
                return;
            }
            
            if (isReceivedRequest) {
                userToShow = request.getSender();
                llActionButtons.setVisibility(View.VISIBLE);
                llSentRequestActions.setVisibility(View.GONE);
            } else {
                userToShow = request.getReceiver();
                llActionButtons.setVisibility(View.GONE);
                llSentRequestActions.setVisibility(View.VISIBLE);
                tvStatus.setText(request.getStatus().toUpperCase());
                btnCancel.setVisibility("pending".equals(request.getStatus()) ? View.VISIBLE : View.GONE);
            }

            if (userToShow != null) {
                tvUsername.setText(userToShow.getDisplayName());
                tvEmail.setText(userToShow.getEmail());

                // Load avatar using AvatarManager
                String avatarUrl = userToShow.getAvatar();
                if (avatarUrl != null && !avatarUrl.isEmpty()) {
                    if (!avatarUrl.startsWith("http")) {
                        avatarUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + 
                                   ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + avatarUrl;
                    }
                    try {
                        AvatarManager.getInstance(itemView.getContext()).loadAvatar(
                            avatarUrl,
                            civProfilePicture,
                            R.drawable.ic_profile_placeholder
                        );
                    } catch (Exception e) {
                        civProfilePicture.setImageResource(R.drawable.ic_profile_placeholder);
                    }
                } else {
                    civProfilePicture.setImageResource(R.drawable.ic_profile_placeholder);
                }

                itemView.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onUserClick(userToShow);
                    }
                });
            }

            btnAccept.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAcceptRequest(request);
                }
            });

            btnReject.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRejectRequest(request);
                }
            });

            btnCancel.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCancelRequest(request);
                }
            });
        }
    }
}
