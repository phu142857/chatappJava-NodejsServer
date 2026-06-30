package com.example.chatappjava.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatappjava.R;
import com.example.chatappjava.models.Call;
import com.example.chatappjava.utils.AvatarManager;
import com.example.chatappjava.utils.MotionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import de.hdodenhof.circleimageview.CircleImageView;

public class CallListAdapter extends RecyclerView.Adapter<CallListAdapter.CallViewHolder> {
    
    private final Context context;
    private final List<Call> callList;
    private OnCallClickListener onCallClickListener;
    private final AvatarManager avatarManager;
    private String currentUserId;
    
    public interface OnCallClickListener {
        void onCallItemClick(Call call);
        void onCallActionClick(Call call);
    }
    
    public CallListAdapter(Context context) {
        this.context = context;
        this.callList = new ArrayList<>();
        this.avatarManager = AvatarManager.getInstance(context);
    }
    
    public void setOnCallClickListener(OnCallClickListener listener) {
        this.onCallClickListener = listener;
    }
    
    public void setCurrentUserId(String currentUserId) {
        this.currentUserId = currentUserId;
    }
    
    public void updateCalls(List<Call> newCallList) {
        List<Call> incoming = newCallList != null ? new ArrayList<>(newCallList) : new ArrayList<>();
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new CallDiffCallback(callList, incoming));
        callList.clear();
        callList.addAll(incoming);
        diffResult.dispatchUpdatesTo(this);
    }

    public void applyUserAvatarChange(String userId, String avatarPath) {
        if (userId == null || userId.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (int i = 0; i < callList.size(); i++) {
            Call call = callList.get(i);
            if (call == null) {
                continue;
            }
            call.applyUserAvatarChange(userId, avatarPath);
            changed = true;
            notifyItemChanged(i);
        }
        if (changed) {
            android.util.Log.d("CallListAdapter", "Applied avatar change for user: " + userId);
        }
    }

    @NonNull
    @Override
    public CallViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_call, parent, false);
        return new CallViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull CallViewHolder holder, int position) {
        Call call = callList.get(position);
        holder.bind(call);
    }
    
    @Override
    public int getItemCount() {
        return callList.size();
    }
    
    class CallViewHolder extends RecyclerView.ViewHolder {
        private final CircleImageView ivCallAvatar;
        private final TextView tvCallName;
        private final TextView tvCallStatus;
        private final TextView tvCallDuration;
        private final TextView tvCallTime;
        private final ImageButton btnCallAction;
        
        public CallViewHolder(@NonNull View itemView) {
            super(itemView);
            
            ivCallAvatar = itemView.findViewById(R.id.iv_call_avatar);
            tvCallName = itemView.findViewById(R.id.tv_call_name);
            tvCallStatus = itemView.findViewById(R.id.tv_call_status);
            tvCallDuration = itemView.findViewById(R.id.tv_call_duration);
            tvCallTime = itemView.findViewById(R.id.tv_call_time);
            btnCallAction = itemView.findViewById(R.id.btn_call_action);
            MotionUtils.attachPressFeedback(context, itemView);
            MotionUtils.attachPressFeedback(context, btnCallAction);

            itemView.setOnClickListener(v -> {
                if (onCallClickListener != null) {
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        onCallClickListener.onCallItemClick(callList.get(position));
                    }
                }
            });
            
            btnCallAction.setOnClickListener(v -> {
                if (onCallClickListener != null) {
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        onCallClickListener.onCallActionClick(callList.get(position));
                    }
                }
            });
        }
        
        public void bind(Call call) {
            // Clean up avatar ImageView first to prevent showing wrong avatar from recycled ViewHolder
            if (ivCallAvatar != null) {
                com.squareup.picasso.Picasso.get().cancelRequest(ivCallAvatar);
                ivCallAvatar.setTag(null);
                // Show placeholder immediately while loading
                ivCallAvatar.setImageResource(R.drawable.ic_profile_placeholder);
            }
            
            String displayName = call.getDisplayName(currentUserId);
            tvCallName.setText(displayName);
            itemView.setContentDescription(context.getString(R.string.call_row_cd, displayName));
            tvCallStatus.setText(call.getStatusText());

            if (call.isEnded() && call.getDuration() > 0) {
                tvCallDuration.setText(call.getFormattedDuration());
                tvCallDuration.setVisibility(View.VISIBLE);
            } else {
                tvCallDuration.setVisibility(View.GONE);
            }

            tvCallTime.setText(call.getFormattedTimeWithDate());

            String avatarUrl = call.getListAvatarUrl(currentUserId);

            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                android.util.Log.d("CallListAdapter", "Loading call avatar: " + avatarUrl);
                if (avatarManager != null) {
                    avatarManager.loadAvatar(
                        avatarUrl,
                        ivCallAvatar,
                        R.drawable.ic_profile_placeholder
                    );
                } else {
                    android.util.Log.w("CallListAdapter", "AvatarManager is null, using Picasso directly");
                    com.squareup.picasso.Picasso.get()
                        .load(avatarUrl)
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .error(R.drawable.ic_profile_placeholder)
                        .fit()
                        .centerCrop()
                        .into(ivCallAvatar);
                }
            } else {
                android.util.Log.d("CallListAdapter", "No avatar URL, using default");
                if (ivCallAvatar != null) {
                    ivCallAvatar.setImageResource(R.drawable.ic_profile_placeholder);
                }
            }
            
            // Set status color
            int statusColor = getStatusColor(call.getStatus());
            tvCallStatus.setTextColor(statusColor);
            
            // Set call action button
            setCallActionButton(call);
        }
        
        private int getStatusColor(String status) {
            return ContextCompat.getColor(context, R.color.text_white);
        }
        
        private void setCallActionButton(Call call) {
            // Set appropriate icon based on call type
            if (call.isVideoCall()) {
                btnCallAction.setImageResource(R.drawable.ic_videocam);
            } else {
                btnCallAction.setImageResource(R.drawable.ic_call);
            }
            
            // Set content description
            btnCallAction.setContentDescription(context.getString(
                    call.isVideoCall() ? R.string.video_call_description : R.string.audio_call_description));
        }
    }

    private static class CallDiffCallback extends DiffUtil.Callback {
        private final List<Call> oldCalls;
        private final List<Call> newCalls;

        CallDiffCallback(List<Call> oldCalls, List<Call> newCalls) {
            this.oldCalls = oldCalls;
            this.newCalls = newCalls;
        }

        @Override
        public int getOldListSize() {
            return oldCalls.size();
        }

        @Override
        public int getNewListSize() {
            return newCalls.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return Objects.equals(oldCalls.get(oldItemPosition).getCallId(), newCalls.get(newItemPosition).getCallId());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Call oldCall = oldCalls.get(oldItemPosition);
            Call newCall = newCalls.get(newItemPosition);
            return Objects.equals(oldCall.getStatus(), newCall.getStatus())
                    && oldCall.getDuration() == newCall.getDuration()
                    && oldCall.isVideoCall() == newCall.isVideoCall()
                    && Objects.equals(oldCall.getCallerAvatar(), newCall.getCallerAvatar());
        }
    }
}
