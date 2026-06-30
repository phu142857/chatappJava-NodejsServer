package com.example.chatappjava.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatappjava.R;
import com.github.chrisbanes.photoview.PhotoView;
import com.squareup.picasso.Picasso;

import java.util.List;

public class ImagePagerAdapter extends RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder> {
    
    private final List<String> imageUrls;
    
    public ImagePagerAdapter(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }
    
    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_image_viewpager, parent, false);
        return new ImageViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        String imageUrl = imageUrls.get(position);
        holder.bind(imageUrl);
    }
    
    @Override
    public int getItemCount() {
        return imageUrls != null ? imageUrls.size() : 0;
    }
    
    static class ImageViewHolder extends RecyclerView.ViewHolder {
        private final PhotoView ivZoomImage;
        
        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            ivZoomImage = itemView.findViewById(R.id.iv_zoom_image);
        }
        
        public void bind(String imageUrl) {
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
                    .into(ivZoomImage);
            } else {
                ivZoomImage.setImageResource(R.drawable.ic_profile_placeholder);
            }
        }
    }
}

