package com.example.chatappjava.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatappjava.R;
import com.example.chatappjava.utils.DatabaseManager;
import com.example.chatappjava.utils.SettingsActionsHelper;

public class SettingsAdapter extends RecyclerView.Adapter<SettingsAdapter.SettingsViewHolder> {

    private final Context context;
    private final DatabaseManager databaseManager;
    private final SettingsActionsHelper settingsActionsHelper = new SettingsActionsHelper();

    public SettingsAdapter(Context context) {
        this.context = context;
        this.databaseManager = new DatabaseManager(context);
    }

    @NonNull
    @Override
    public SettingsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_settings_content, parent, false);
        return new SettingsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SettingsViewHolder holder, int position) {
        settingsActionsHelper.bind(holder.itemView, context, databaseManager);
    }

    @Override
    public int getItemCount() {
        return 1;
    }

    public void onDestroy() {
        settingsActionsHelper.onDestroy();
    }

    static class SettingsViewHolder extends RecyclerView.ViewHolder {
        SettingsViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
