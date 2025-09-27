package com.example.chatappjava.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.chatappjava.R;
import com.example.chatappjava.models.User;
import java.util.List;

public class SelectableUserAdapter extends RecyclerView.Adapter<SelectableUserAdapter.VH> {

    public interface OnSelectionChangedListener {
        void onSelectionChanged(String userId, boolean selected);
    }

    private final List<User> users;
    private final OnSelectionChangedListener listener;

    public SelectableUserAdapter(List<User> users, OnSelectionChangedListener listener) {
        this.users = users;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_selectable, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        User u = users.get(position);
        holder.tvName.setText(u.getDisplayName());
        holder.tvUsername.setText("@" + u.getUsername());
        holder.cb.setOnCheckedChangeListener(null);
        holder.cb.setChecked(false);
        holder.cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) listener.onSelectionChanged(u.getId(), isChecked);
        });
        holder.itemView.setOnClickListener(v -> holder.cb.performClick());
    }

    @Override
    public int getItemCount() { return users.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView iv;
        TextView tvName;
        TextView tvUsername;
        CheckBox cb;
        VH(@NonNull View itemView) {
            super(itemView);
            iv = itemView.findViewById(R.id.iv_profile_picture);
            tvName = itemView.findViewById(R.id.tv_display_name);
            tvUsername = itemView.findViewById(R.id.tv_username);
            cb = itemView.findViewById(R.id.cb_select);
        }
    }
}


