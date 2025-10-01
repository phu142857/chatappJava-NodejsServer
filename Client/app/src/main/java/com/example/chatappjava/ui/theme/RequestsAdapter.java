package com.example.chatappjava.ui.theme;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.chatappjava.R;
import com.example.chatappjava.models.User;
import com.squareup.picasso.Picasso;
import java.util.List;

public class RequestsAdapter extends RecyclerView.Adapter<RequestsAdapter.VH> {
    public interface ActionListener {
        void onApprove(User u);
        void onReject(User u);
    }
    private final List<User> users;
    private final ActionListener listener;
    public RequestsAdapter(List<User> users, ActionListener listener) {
        this.users = users;
        this.listener = listener;
    }
    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_join_request, parent, false);
        return new VH(v);
    }
    @Override public void onBindViewHolder(@NonNull VH h, int pos) { h.bind(users.get(pos)); }
    @Override public int getItemCount() { return users.size(); }

    class VH extends RecyclerView.ViewHolder {
        de.hdodenhof.circleimageview.CircleImageView avatar; TextView name; Button approve; Button reject;
        VH(View v) { super(v);
            avatar = v.findViewById(R.id.iv_avatar);
            name = v.findViewById(R.id.tv_name);
            approve = v.findViewById(R.id.btn_approve);
            reject = v.findViewById(R.id.btn_reject);
        }
        void bind(User u) {
            name.setText(u.getDisplayName());
            try { Picasso.get().load(u.getAvatar()).placeholder(R.drawable.ic_group_placeholder).into(avatar); } catch (Exception ignored) {}
            approve.setOnClickListener(v -> { if (listener != null) listener.onApprove(u); });
            reject.setOnClickListener(v -> { if (listener != null) listener.onReject(u); });
        }
    }
}


