package com.example.chatappjava.ui.theme;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.chatappjava.R;
import com.example.chatappjava.utils.DatabaseManager;
import com.example.chatappjava.utils.SettingsActionsHelper;

public class SettingsActivity extends AppCompatActivity {

    private TextView tvUserName;
    private DatabaseManager databaseManager;
    private final SettingsActionsHelper settingsActionsHelper = new SettingsActionsHelper();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        databaseManager = new DatabaseManager(this);
        tvUserName = findViewById(R.id.tv_user_name);
        View backWell = findViewById(R.id.toolbar_back_well);
        if (backWell != null) {
            backWell.setVisibility(View.VISIBLE);
        }
        View back = findViewById(R.id.iv_toolbar_back);
        if (back != null) {
            back.setOnClickListener(v -> finish());
        }
        TextView toolbarTitle = findViewById(R.id.tv_toolbar_title);
        if (toolbarTitle != null) {
            toolbarTitle.setText(R.string.settings_title);
        }

        loadUserName();
        settingsActionsHelper.bind(findViewById(android.R.id.content), this, databaseManager);
    }

    private void loadUserName() {
        String userName = databaseManager.getUserName();
        if (userName != null && !userName.isEmpty()) {
            tvUserName.setText(userName);
        } else {
            tvUserName.setText(R.string.settings_default_user);
        }
    }

    @Override
    protected void onDestroy() {
        settingsActionsHelper.onDestroy();
        super.onDestroy();
    }
}
