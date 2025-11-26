package com.example.chatappjava.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatappjava.R;
import com.example.chatappjava.config.ServerConfig;
import com.example.chatappjava.models.Comment;
import com.example.chatappjava.models.User;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.ui.theme.ProfileActivity;
import com.example.chatappjava.ui.theme.ProfileViewActivity;
import com.example.chatappjava.utils.AvatarManager;
import com.example.chatappjava.utils.DatabaseManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.hdodenhof.circleimageview.CircleImageView;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {

    public interface OnCommentClickListener {
        void onCommentClick(Comment comment);
        void onLikeClick(Comment comment, int position);
        void onReplyClick(Comment comment, int position);
        void onAuthorClick(Comment comment);
        void onReactionLongPress(Comment comment, int position, View view);
        void onCommentMenuClick(Comment comment, int position, View view);
        void onViewRepliesClick(Comment comment, int position);
        void onReactionCountClick(Comment comment, int position);
    }

    private List<Comment> comments;
    private final OnCommentClickListener listener;
    private final AvatarManager avatarManager;
    private final Context context;
    private final String currentUserId;

    public CommentAdapter(Context context, OnCommentClickListener listener, String currentUserId) {
        this.context = context;
        this.comments = new ArrayList<>();
        this.listener = listener;
        this.avatarManager = AvatarManager.getInstance(context);
        this.currentUserId = currentUserId;
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
        return new CommentViewHolder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        Comment comment = comments.get(position);
        holder.bind(comment, listener, avatarManager, position, currentUserId);
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    public void setComments(List<Comment> newComments) {
        this.comments.clear();
        if (newComments != null) {
            this.comments.addAll(newComments);
        }
        notifyDataSetChanged();
    }

    public void addComment(Comment comment) {
        comments.add(comment);
        notifyItemInserted(comments.size() - 1);
    }

    public void addComments(List<Comment> newComments) {
        if (newComments != null && !newComments.isEmpty()) {
            int startPosition = comments.size();
            comments.addAll(newComments);
            notifyItemRangeInserted(startPosition, newComments.size());
        }
    }

    public void updateComment(int position, Comment updatedComment) {
        if (position >= 0 && position < comments.size()) {
            comments.set(position, updatedComment);
            notifyItemChanged(position);
        }
    }

    public void removeComment(int position) {
        if (position >= 0 && position < comments.size()) {
            comments.remove(position);
            notifyItemRemoved(position);
        }
    }

    static class CommentViewHolder extends RecyclerView.ViewHolder {
        CircleImageView ivCommentAvatar;
        TextView tvCommentUsername, tvCommentContent, tvCommentTimestamp;
        TextView tvCommentLike, tvCommentReply, tvCommentReactionCount;
        ImageView ivCommentMedia;
        LinearLayout llReactionIconsInline;
        TextView tvReactionLike, tvReactionLove, tvReactionHaha, tvReactionWow, tvReactionSad, tvReactionAngry;
        ImageButton ivCommentMenu;
        LinearLayout llRepliesContainer;
        TextView tvViewReplies;
        LinearLayout llRepliesList;

        public CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCommentAvatar = itemView.findViewById(R.id.iv_comment_avatar);
            tvCommentUsername = itemView.findViewById(R.id.tv_comment_username);
            tvCommentContent = itemView.findViewById(R.id.tv_comment_content);
            tvCommentTimestamp = itemView.findViewById(R.id.tv_comment_timestamp);
            tvCommentLike = itemView.findViewById(R.id.tv_comment_like);
            tvCommentReply = itemView.findViewById(R.id.tv_comment_reply);
            tvCommentReactionCount = itemView.findViewById(R.id.tv_comment_reaction_count);
            ivCommentMedia = itemView.findViewById(R.id.iv_comment_media);
            llReactionIconsInline = itemView.findViewById(R.id.ll_reaction_icons_inline);
            tvReactionLike = itemView.findViewById(R.id.tv_reaction_like);
            tvReactionLove = itemView.findViewById(R.id.tv_reaction_love);
            tvReactionHaha = itemView.findViewById(R.id.tv_reaction_haha);
            tvReactionWow = itemView.findViewById(R.id.tv_reaction_wow);
            tvReactionSad = itemView.findViewById(R.id.tv_reaction_sad);
            tvReactionAngry = itemView.findViewById(R.id.tv_reaction_angry);
            ivCommentMenu = itemView.findViewById(R.id.iv_comment_menu);
            llRepliesContainer = itemView.findViewById(R.id.ll_replies_container);
            tvViewReplies = itemView.findViewById(R.id.tv_view_replies);
            llRepliesList = itemView.findViewById(R.id.ll_replies_list);
        }

        @SuppressLint("SetTextI18n")
        public void bind(Comment comment, OnCommentClickListener listener, AvatarManager avatarManager, int position, String currentUserId) {
            // Set username and content
            tvCommentUsername.setText(comment.getUsername());
            
            // Set content with @mention highlighting
            SpannableString spannableContent = createMentionSpannable(comment.getContent(), itemView.getContext());
            tvCommentContent.setText(spannableContent);
            tvCommentContent.setMovementMethod(LinkMovementMethod.getInstance());
            
            tvCommentTimestamp.setText(comment.getFormattedTimestamp());

            // Load avatar
            String avatarUrl = comment.getUserAvatar();
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                if (!avatarUrl.startsWith("http")) {
                    avatarUrl = ServerConfig.getBaseUrl() + avatarUrl;
                }
                avatarManager.loadAvatar(avatarUrl, ivCommentAvatar, R.drawable.ic_profile_placeholder);
            } else {
                ivCommentAvatar.setImageResource(R.drawable.ic_profile_placeholder);
            }

            // Update like button state
            if (comment.isLiked()) {
                tvCommentLike.setText("Liked");
                tvCommentLike.setTextColor(itemView.getContext().getColor(R.color.icon_like_active));
            } else {
                tvCommentLike.setText("Like");
                tvCommentLike.setTextColor(itemView.getContext().getColor(R.color.text_secondary));
            }

            // Hide media by default (will be shown if comment has media)
            if (ivCommentMedia != null) {
                ivCommentMedia.setVisibility(View.GONE);
            }

            // Hide reaction icons by default (will be shown if comment has reactions)
            if (llReactionIconsInline != null) {
                llReactionIconsInline.setVisibility(View.GONE);
            }

            // Calculate total reaction count from reactions array
            int totalReactions = 0;
            if (comment.getReactions() != null && !comment.getReactions().isEmpty()) {
                totalReactions = comment.getReactions().size();
            } else if (comment.getLikesCount() > 0) {
                // Fallback to likesCount if reactions array is not available
                totalReactions = comment.getLikesCount();
            }

            // Show/hide reaction count in action bar (Time | Like | Reply | Reaction Count)
            if (tvCommentReactionCount != null) {
                if (totalReactions > 0) {
                    tvCommentReactionCount.setText(totalReactions + "");
                    tvCommentReactionCount.setVisibility(View.VISIBLE);
                    tvCommentReactionCount.setOnClickListener(v -> {
                        if (listener != null) {
                            listener.onReactionCountClick(comment, position);
                        }
                    });
                } else {
                    tvCommentReactionCount.setVisibility(View.GONE);
                }
            }

            // Show/hide reaction icons in action bar based on reactions
            if (comment.getReactions() != null && !comment.getReactions().isEmpty()) {
                if (llReactionIconsInline != null) {
                    llReactionIconsInline.setVisibility(View.VISIBLE);
                    // Hide all reaction emojis first
                    if (tvReactionLike != null) tvReactionLike.setVisibility(View.GONE);
                    if (tvReactionLove != null) tvReactionLove.setVisibility(View.GONE);
                    if (tvReactionHaha != null) tvReactionHaha.setVisibility(View.GONE);
                    if (tvReactionWow != null) tvReactionWow.setVisibility(View.GONE);
                    if (tvReactionSad != null) tvReactionSad.setVisibility(View.GONE);
                    if (tvReactionAngry != null) tvReactionAngry.setVisibility(View.GONE);

                    // Count reactions by type
                    int likeCount = 0, loveCount = 0, hahaCount = 0, wowCount = 0, sadCount = 0, angryCount = 0;
                    for (Comment.Reaction reaction : comment.getReactions()) {
                        String type = reaction.type;
                        if ("like".equals(type)) likeCount++;
                        else if ("love".equals(type)) loveCount++;
                        else if ("haha".equals(type)) hahaCount++;
                        else if ("wow".equals(type)) wowCount++;
                        else if ("sad".equals(type)) sadCount++;
                        else if ("angry".equals(type)) angryCount++;
                    }

                    // Show emojis for reactions that exist
                    if (likeCount > 0 && tvReactionLike != null) tvReactionLike.setVisibility(View.VISIBLE);
                    if (loveCount > 0 && tvReactionLove != null) tvReactionLove.setVisibility(View.VISIBLE);
                    if (hahaCount > 0 && tvReactionHaha != null) tvReactionHaha.setVisibility(View.VISIBLE);
                    if (wowCount > 0 && tvReactionWow != null) tvReactionWow.setVisibility(View.VISIBLE);
                    if (sadCount > 0 && tvReactionSad != null) tvReactionSad.setVisibility(View.VISIBLE);
                    if (angryCount > 0 && tvReactionAngry != null) tvReactionAngry.setVisibility(View.VISIBLE);
                    
                    // Make reaction icons clickable to show users dialog
                    if (llReactionIconsInline != null && totalReactions > 0) {
                        llReactionIconsInline.setOnClickListener(v -> {
                            if (listener != null) {
                                listener.onReactionCountClick(comment, position);
                            }
                        });
                    }
                }
            } else if (comment.getLikesCount() > 0) {
                // Fallback: show like emoji if only likesCount is available
                if (llReactionIconsInline != null) {
                    llReactionIconsInline.setVisibility(View.VISIBLE);
                    if (tvReactionLike != null) {
                        tvReactionLike.setVisibility(View.VISIBLE);
                    }
                    
                    // Make reaction icons clickable
                    llReactionIconsInline.setOnClickListener(v -> {
                        if (listener != null) {
                            listener.onReactionCountClick(comment, position);
                        }
                    });
                }
            } else {
                if (llReactionIconsInline != null) {
                    llReactionIconsInline.setVisibility(View.GONE);
                }
            }

            // Handle replies
            int repliesCount = comment.getRepliesCount();
            boolean hasReplies = (comment.getReplies() != null && !comment.getReplies().isEmpty()) || repliesCount > 0;
            
            if (hasReplies) {
                llRepliesContainer.setVisibility(View.VISIBLE);
                int actualRepliesCount = comment.getReplies() != null ? comment.getReplies().size() : repliesCount;
                
                // Update replies count if needed
                if (comment.getReplies() != null && comment.getReplies().size() > repliesCount) {
                    comment.setRepliesCount(comment.getReplies().size());
                    repliesCount = comment.getReplies().size();
                }
                
                if (actualRepliesCount > 0 || repliesCount > 0) {
                    // Show replies if we have them loaded
                    if (comment.getReplies() != null && !comment.getReplies().isEmpty()) {
                        llRepliesList.setVisibility(View.VISIBLE);
                        // Add reply views dynamically
                        llRepliesList.removeAllViews();
                        for (Comment reply : comment.getReplies()) {
                            View replyView = LayoutInflater.from(itemView.getContext())
                                    .inflate(R.layout.item_comment_reply, llRepliesList, false);
                            bindReplyView(replyView, reply, listener, avatarManager, currentUserId);
                            llRepliesList.addView(replyView);
                        }
                        
                        // Show "View X replies" button only if there are more replies than displayed
                        int displayedCount = comment.getReplies().size();
                        if (repliesCount > displayedCount) {
                            tvViewReplies.setText("View " + (repliesCount - displayedCount) + " more " + ((repliesCount - displayedCount) == 1 ? "reply" : "replies"));
                            tvViewReplies.setVisibility(View.VISIBLE);
                        } else {
                            tvViewReplies.setVisibility(View.GONE);
                        }
                    } else {
                        // Show "View replies" button if replies exist but not loaded
                        if (repliesCount > 0) {
                            tvViewReplies.setText("View " + repliesCount + " " + (repliesCount == 1 ? "reply" : "replies"));
                            tvViewReplies.setVisibility(View.VISIBLE);
                        }
                        llRepliesList.setVisibility(View.GONE);
                    }
                } else {
                    tvViewReplies.setVisibility(View.GONE);
                    llRepliesList.setVisibility(View.GONE);
                }
            } else {
                llRepliesContainer.setVisibility(View.GONE);
            }

            // Click listeners
            tvCommentLike.setOnClickListener(v -> listener.onLikeClick(comment, position));
            tvCommentReply.setOnClickListener(v -> listener.onReplyClick(comment, position));
            ivCommentAvatar.setOnClickListener(v -> listener.onAuthorClick(comment));
            tvCommentUsername.setOnClickListener(v -> listener.onAuthorClick(comment));
            itemView.setOnClickListener(v -> listener.onCommentClick(comment));

            // Long-press on comment content or like button to show reaction picker
            tvCommentContent.setOnLongClickListener(v -> {
                listener.onReactionLongPress(comment, position, v);
                return true;
            });
            tvCommentLike.setOnLongClickListener(v -> {
                listener.onReactionLongPress(comment, position, v);
                return true;
            });

            // Three-dot menu click
            if (ivCommentMenu != null) {
                ivCommentMenu.setOnClickListener(v -> listener.onCommentMenuClick(comment, position, v));
            }

            // View replies click
            if (tvViewReplies != null) {
                tvViewReplies.setOnClickListener(v -> listener.onViewRepliesClick(comment, position));
            }
        }

        @SuppressLint("SetTextI18n")
        private void bindReplyView(View replyView, Comment reply, OnCommentClickListener listener, AvatarManager avatarManager, String currentUserId) {
            CircleImageView ivReplyAvatar = replyView.findViewById(R.id.iv_reply_avatar);
            TextView tvReplyUsername = replyView.findViewById(R.id.tv_reply_username);
            TextView tvReplyContent = replyView.findViewById(R.id.tv_reply_content);
            TextView tvReplyTimestamp = replyView.findViewById(R.id.tv_reply_timestamp);
            TextView tvReplyLikesCount = replyView.findViewById(R.id.tv_reply_likes_count);
            TextView tvReplyLike = replyView.findViewById(R.id.tv_reply_like);
            TextView tvReplyReply = replyView.findViewById(R.id.tv_reply_reply);

            tvReplyUsername.setText(reply.getUsername());
            
            // Set content with @mention highlighting
            SpannableString spannableReplyContent = createMentionSpannable(reply.getContent(), replyView.getContext());
            tvReplyContent.setText(spannableReplyContent);
            tvReplyContent.setMovementMethod(LinkMovementMethod.getInstance());
            
            tvReplyTimestamp.setText(reply.getFormattedTimestamp());

            // Load avatar
            String avatarUrl = reply.getUserAvatar();
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                if (!avatarUrl.startsWith("http")) {
                    avatarUrl = ServerConfig.getBaseUrl() + avatarUrl;
                }
                avatarManager.loadAvatar(avatarUrl, ivReplyAvatar, R.drawable.ic_profile_placeholder);
            } else {
                ivReplyAvatar.setImageResource(R.drawable.ic_profile_placeholder);
            }

            // Update like button
            if (reply.isLiked()) {
                tvReplyLike.setText("Liked");
                tvReplyLike.setTextColor(replyView.getContext().getColor(R.color.icon_like_active));
            } else {
                tvReplyLike.setText("Like");
                tvReplyLike.setTextColor(replyView.getContext().getColor(R.color.text_secondary));
            }

            if (reply.getLikesCount() > 0) {
                tvReplyLikesCount.setText(reply.getLikesCount() + "");
                tvReplyLikesCount.setVisibility(View.VISIBLE);
            } else {
                tvReplyLikesCount.setVisibility(View.GONE);
            }

            // Click listeners for reply
            tvReplyLike.setOnClickListener(v -> {
                // TODO: Handle reply like
            });
            tvReplyReply.setOnClickListener(v -> listener.onReplyClick(reply, -1));
            ivReplyAvatar.setOnClickListener(v -> listener.onAuthorClick(reply));
            tvReplyUsername.setOnClickListener(v -> listener.onAuthorClick(reply));
        }

        /**
         * Create SpannableString with @mention highlighting and clickable links
         */
        private SpannableString createMentionSpannable(String text, Context context) {
            if (text == null || text.isEmpty()) {
                return new SpannableString("");
            }

            SpannableString spannable = new SpannableString(text);
            // Pattern to match @username (alphanumeric and underscore, 1-30 chars)
            Pattern mentionPattern = Pattern.compile("@([a-zA-Z0-9_]{1,30})");
            Matcher matcher = mentionPattern.matcher(text);
            int mentionColor = 0xFF2d6bb3; // #2d6bb3

            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                String username = matcher.group(1);

                // Set color for @mention
                spannable.setSpan(new ForegroundColorSpan(mentionColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                // Make @mention clickable
                ClickableSpan clickableSpan = new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        openUserProfile(context, username);
                    }

                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        super.updateDrawState(ds);
                        ds.setColor(mentionColor);
                        ds.setUnderlineText(false);
                    }
                };
                spannable.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            return spannable;
        }

        /**
         * Open user profile by username
         */
        private void openUserProfile(Context context, String username) {
            DatabaseManager databaseManager = new DatabaseManager(context);
            String currentUsername = databaseManager.getUserName();
            
            // Check if the tagged username is the current user
            if (currentUsername != null && currentUsername.equalsIgnoreCase(username)) {
                // Open own profile (ProfileActivity)
                Intent intent = new Intent(context, ProfileActivity.class);
                context.startActivity(intent);
            } else {
                // Open other user's profile (ProfileViewActivity)
                Intent intent = new Intent(context, ProfileViewActivity.class);
                intent.putExtra("username", username);
                context.startActivity(intent);
            }
        }
    }
}

