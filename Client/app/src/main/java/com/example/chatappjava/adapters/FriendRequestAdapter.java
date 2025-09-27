package com.example.chatappjava.adapters;

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

import java.util.List;

import android.widget.ImageView;

public class FriendRequestAdapter extends RecyclerView.Adapter<FriendRequestAdapter.FriendRequestViewHolder> {

    private List<FriendRequest> friendRequests;
    private OnFriendRequestActionListener listener;
    private String currentUserId;

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
        private ImageView civProfilePicture;
        private TextView tvUsername;
        private TextView tvEmail;
        private TextView tvRequestType;
        private LinearLayout llActionButtons;
        private Button btnAccept;
        private Button btnReject;
        private LinearLayout llSentRequestActions;
        private TextView tvStatus;
        private Button btnCancel;

        public FriendRequestViewHolder(@NonNull View itemView) {
            super(itemView);
            civProfilePicture = itemView.findViewById(R.id.civ_profile_picture);
            tvUsername = itemView.findViewById(R.id.tv_username);
            tvEmail = itemView.findViewById(R.id.tv_email);
            tvRequestType = itemView.findViewById(R.id.tv_request_type);
            llActionButtons = itemView.findViewById(R.id.ll_action_buttons);
            btnAccept = itemView.findViewById(R.id.btn_accept);
            btnReject = itemView.findViewById(R.id.btn_reject);
            llSentRequestActions = itemView.findViewById(R.id.ll_sent_request_actions);
            tvStatus = itemView.findViewById(R.id.tv_status);
            btnCancel = itemView.findViewById(R.id.btn_cancel);
        }

        public void bind(FriendRequest request) {
            System.out.println("FriendRequestAdapter: Binding request " + request.getId() + " status: " + request.getStatus());
            System.out.println("Current User ID: " + currentUserId);
            System.out.println("Request Sender ID: " + request.getSenderId());
            System.out.println("Request Receiver ID: " + request.getReceiverId());
            
            User userToShow;
            boolean isReceivedRequest = currentUserId.equals(request.getReceiverId());
            System.out.println("Is Received Request: " + isReceivedRequest);
            System.out.println("String comparison: '" + currentUserId + "' == '" + request.getReceiverId() + "' = " + currentUserId.equals(request.getReceiverId()));
            System.out.println("String comparison: '" + currentUserId + "' == '" + request.getSenderId() + "' = " + currentUserId.equals(request.getSenderId()));
            System.out.println("Current User ID length: " + (currentUserId != null ? currentUserId.length() : "null"));
            System.out.println("Sender ID length: " + (request.getSenderId() != null ? request.getSenderId().length() : "null"));
            System.out.println("Receiver ID length: " + (request.getReceiverId() != null ? request.getReceiverId().length() : "null"));
            
            // Additional debug for string comparison
            if (currentUserId != null && request.getReceiverId() != null) {
                System.out.println("Current User ID bytes: " + java.util.Arrays.toString(currentUserId.getBytes()));
                System.out.println("Receiver ID bytes: " + java.util.Arrays.toString(request.getReceiverId().getBytes()));
                System.out.println("Are they equal? " + currentUserId.equals(request.getReceiverId()));
                System.out.println("Are they == ? " + (currentUserId == request.getReceiverId()));
            }
            
            // Debug for null/empty checks
            if (currentUserId == null || currentUserId.isEmpty()) {
                System.out.println("WARNING: Current User ID is null or empty!");
            }
            if (request.getReceiverId() == null || request.getReceiverId().isEmpty()) {
                System.out.println("WARNING: Receiver ID is null or empty!");
            }
            if (request.getSenderId() == null || request.getSenderId().isEmpty()) {
                System.out.println("WARNING: Sender ID is null or empty!");
            }
            
            // Force debug the logic
            System.out.println("=== FORCE DEBUG LOGIC ===");
            System.out.println("currentUserId: '" + currentUserId + "'");
            System.out.println("request.getReceiverId(): '" + request.getReceiverId() + "'");
            System.out.println("request.getSenderId(): '" + request.getSenderId() + "'");
            System.out.println("currentUserId.equals(request.getReceiverId()): " + currentUserId.equals(request.getReceiverId()));
            System.out.println("currentUserId.equals(request.getSenderId()): " + currentUserId.equals(request.getSenderId()));
            System.out.println("=== END FORCE DEBUG ===");
            
            // Additional safety check
            if (currentUserId == null || currentUserId.isEmpty()) {
                System.out.println("ERROR: Cannot determine request type - currentUserId is null or empty!");
                return; // Skip this item
            }
            
            // Force the logic to be correct
            if (currentUserId.equals(request.getReceiverId())) {
                System.out.println("FORCED: This is a RECEIVED request");
                isReceivedRequest = true;
            } else if (currentUserId.equals(request.getSenderId())) {
                System.out.println("FORCED: This is a SENT request");
                isReceivedRequest = false;
            } else {
                System.out.println("ERROR: Current user is neither sender nor receiver!");
                System.out.println("DEBUG: currentUserId = '" + currentUserId + "'");
                System.out.println("DEBUG: senderId = '" + request.getSenderId() + "'");
                System.out.println("DEBUG: receiverId = '" + request.getReceiverId() + "'");
                return; // Skip this item
            }
            
            // Final debug before UI setup
            System.out.println("FINAL: isReceivedRequest = " + isReceivedRequest);
            System.out.println("FINAL: Will show " + (isReceivedRequest ? "RECEIVED" : "SENT") + " request UI");
            
            if (isReceivedRequest) {
                // This is a received request - show sender info
                userToShow = request.getSender();
                tvRequestType.setText("Sent you a friend request");
                llActionButtons.setVisibility(View.VISIBLE);
                llSentRequestActions.setVisibility(View.GONE);
                System.out.println("Setting up RECEIVED request UI - showing sender: " + (userToShow != null ? userToShow.getDisplayName() : "null"));
                System.out.println("RECEIVED: llActionButtons visibility = VISIBLE, llSentRequestActions visibility = GONE");
                System.out.println("RECEIVED: btnAccept visibility = " + btnAccept.getVisibility());
                System.out.println("RECEIVED: btnReject visibility = " + btnReject.getVisibility());
            } else {
                // This is a sent request - show receiver info
                userToShow = request.getReceiver();
                tvRequestType.setText("Friend request sent");
                llActionButtons.setVisibility(View.GONE);
                llSentRequestActions.setVisibility(View.VISIBLE);
                tvStatus.setText(request.getStatus().toUpperCase());
                System.out.println("Setting up SENT request UI - showing receiver: " + (userToShow != null ? userToShow.getDisplayName() : "null"));
                System.out.println("SENT: llActionButtons visibility = GONE, llSentRequestActions visibility = VISIBLE");
                System.out.println("SENT: tvStatus text = " + tvStatus.getText());
                System.out.println("SENT: btnCancel visibility = " + btnCancel.getVisibility());
                
                // Show cancel button only for pending requests
                if ("pending".equals(request.getStatus())) {
                    btnCancel.setVisibility(View.VISIBLE);
                    System.out.println("Showing cancel button for pending request");
                } else {
                    btnCancel.setVisibility(View.GONE);
                    System.out.println("Hiding cancel button for " + request.getStatus() + " request");
                }
            }

            if (userToShow != null) {
                tvUsername.setText(userToShow.getDisplayName());
                tvEmail.setText(userToShow.getEmail());
                System.out.println("Displaying user: " + userToShow.getDisplayName() + " (" + userToShow.getEmail() + ")");

                // Set profile picture (you can add image loading logic here)
                // For now, using placeholder
                civProfilePicture.setImageResource(R.drawable.ic_profile_placeholder);

                // Set click listener for profile
                itemView.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onUserClick(userToShow);
                    }
                });
            } else {
                System.out.println("ERROR: userToShow is null!");
                System.out.println("ERROR: request.getSender() = " + request.getSender());
                System.out.println("ERROR: request.getReceiver() = " + request.getReceiver());
            }

            // Set button click listeners
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
            
            // Final debug after all UI setup
            System.out.println("=== FINAL UI STATE ===");
            System.out.println("llActionButtons visibility: " + llActionButtons.getVisibility());
            System.out.println("llSentRequestActions visibility: " + llSentRequestActions.getVisibility());
            System.out.println("btnAccept visibility: " + btnAccept.getVisibility());
            System.out.println("btnReject visibility: " + btnReject.getVisibility());
            System.out.println("btnCancel visibility: " + btnCancel.getVisibility());
            System.out.println("tvStatus text: " + tvStatus.getText());
            System.out.println("tvRequestType text: " + tvRequestType.getText());
            System.out.println("=== END FINAL UI STATE ===");
            
            // Force UI to be correct (temporary fix)
            if (isReceivedRequest) {
                System.out.println("FORCING: Setting RECEIVED request UI");
                llActionButtons.setVisibility(View.VISIBLE);
                llSentRequestActions.setVisibility(View.GONE);
                btnAccept.setVisibility(View.VISIBLE);
                btnReject.setVisibility(View.VISIBLE);
                btnCancel.setVisibility(View.GONE);
            } else {
                System.out.println("FORCING: Setting SENT request UI");
                llActionButtons.setVisibility(View.GONE);
                llSentRequestActions.setVisibility(View.VISIBLE);
                btnAccept.setVisibility(View.GONE);
                btnReject.setVisibility(View.GONE);
                if ("pending".equals(request.getStatus())) {
                    btnCancel.setVisibility(View.VISIBLE);
                } else {
                    btnCancel.setVisibility(View.GONE);
                }
            }
            
            // Final verification
            System.out.println("=== FINAL VERIFICATION ===");
            System.out.println("After forcing - llActionButtons visibility: " + llActionButtons.getVisibility());
            System.out.println("After forcing - llSentRequestActions visibility: " + llSentRequestActions.getVisibility());
            System.out.println("After forcing - btnAccept visibility: " + btnAccept.getVisibility());
            System.out.println("After forcing - btnReject visibility: " + btnReject.getVisibility());
            System.out.println("After forcing - btnCancel visibility: " + btnCancel.getVisibility());
            System.out.println("=== END FINAL VERIFICATION ===");
            
            // Additional debug for layout issues
            System.out.println("=== LAYOUT DEBUG ===");
            System.out.println("itemView width: " + itemView.getWidth());
            System.out.println("itemView height: " + itemView.getHeight());
            System.out.println("llActionButtons width: " + llActionButtons.getWidth());
            System.out.println("llActionButtons height: " + llActionButtons.getHeight());
            System.out.println("llSentRequestActions width: " + llSentRequestActions.getWidth());
            System.out.println("llSentRequestActions height: " + llSentRequestActions.getHeight());
            System.out.println("=== END LAYOUT DEBUG ===");
            
            // Force layout refresh
            itemView.requestLayout();
            llActionButtons.requestLayout();
            llSentRequestActions.requestLayout();
            
            // Post a runnable to ensure UI updates are applied
            itemView.post(() -> {
                System.out.println("=== POST LAYOUT VERIFICATION ===");
                System.out.println("Post layout - llActionButtons visibility: " + llActionButtons.getVisibility());
                System.out.println("Post layout - llSentRequestActions visibility: " + llSentRequestActions.getVisibility());
                System.out.println("Post layout - btnAccept visibility: " + btnAccept.getVisibility());
                System.out.println("Post layout - btnReject visibility: " + btnReject.getVisibility());
                System.out.println("Post layout - btnCancel visibility: " + btnCancel.getVisibility());
                System.out.println("=== END POST LAYOUT VERIFICATION ===");
            });
        }
    }
}
