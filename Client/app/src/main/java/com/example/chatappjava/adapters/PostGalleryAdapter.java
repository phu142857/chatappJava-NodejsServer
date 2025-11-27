package com.example.chatappjava.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatappjava.R;
import com.squareup.picasso.Picasso;

import java.util.List;

public class PostGalleryAdapter extends RecyclerView.Adapter<PostGalleryAdapter.GalleryViewHolder> {
    
    private final Context context;
    private final List<String> imageUrls;
    private OnImageClickListener onImageClickListener;
    
    public interface OnImageClickListener {
        void onImageClick(int position, String imageUrl);
    }
    
    public PostGalleryAdapter(Context context, List<String> imageUrls) {
        this.context = context;
        this.imageUrls = imageUrls;
    }
    
    public void setOnImageClickListener(OnImageClickListener listener) {
        this.onImageClickListener = listener;
    }
    
    @NonNull
    @Override
    public GalleryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_post_gallery_image, parent, false);
        return new GalleryViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull GalleryViewHolder holder, int position) {
        String imageUrl = imageUrls.get(position);
        holder.bind(imageUrl, position);
    }
    
    @Override
    public int getItemCount() {
        return imageUrls != null ? imageUrls.size() : 0;
    }
    
    class GalleryViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivGalleryImage;
        private final View overlay;
        
        public GalleryViewHolder(@NonNull View itemView) {
            super(itemView);
            ivGalleryImage = itemView.findViewById(R.id.iv_gallery_image);
            overlay = itemView.findViewById(R.id.view_overlay);
            
            itemView.setOnClickListener(v -> {
                if (onImageClickListener != null) {
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        onImageClickListener.onImageClick(position, imageUrls.get(position));
                    }
                }
            });
        }
        
        public void bind(String imageUrl, int position) {
            // Load image
            if (imageUrl != null && !imageUrl.isEmpty()) {
                // Construct full URL if needed
                if (!imageUrl.startsWith("http")) {
                    imageUrl = "http://" + com.example.chatappjava.config.ServerConfig.getServerIp() + 
                              ":" + com.example.chatappjava.config.ServerConfig.getServerPort() + imageUrl;
                }
                
                Picasso.get()
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .resize(400, 400)
                    .centerCrop()
                    .into(ivGalleryImage);
            } else {
                ivGalleryImage.setImageResource(R.drawable.ic_profile_placeholder);
            }
            
            // Show overlay with count if more than 4 images and this is the 4th
            if (imageUrls.size() > 4 && position == 3) {
                if (overlay != null) {
                    overlay.setVisibility(View.VISIBLE);
                    android.widget.TextView tvCount = overlay.findViewById(R.id.tv_more_count);
                    if (tvCount != null) {
                        tvCount.setText("+" + (imageUrls.size() - 4));
                    }
                }
            } else {
                if (overlay != null) {
                    overlay.setVisibility(View.GONE);
                }
            }
        }
    }
}

