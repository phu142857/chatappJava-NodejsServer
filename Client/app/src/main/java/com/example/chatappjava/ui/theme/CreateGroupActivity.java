package com.example.chatappjava.ui.theme;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.Switch;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.chatappjava.R;
import com.example.chatappjava.adapters.SelectableUserAdapter;
import com.example.chatappjava.models.User;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.utils.SharedPreferencesManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class CreateGroupActivity extends AppCompatActivity implements SelectableUserAdapter.OnSelectionChangedListener {

    private EditText etGroupName, etDescription;
    private RecyclerView rvFriends;
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch switchPublic;
    private ProgressBar progressBar;
    private SelectableUserAdapter adapter;
    private final List<User> friends = new ArrayList<>();
    private final List<String> selectedUserIds = new ArrayList<>();
    private ApiClient apiClient;
    private SharedPreferencesManager sharedPrefs;
    private EditText etSearch;
    private TextView tvCreate;
    private final List<User> allFriends = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_group);

        etGroupName = findViewById(R.id.et_group_name);
        etDescription = findViewById(R.id.et_group_description);
        rvFriends = findViewById(R.id.rv_friends);
        switchPublic = findViewById(R.id.switch_public);
        progressBar = findViewById(R.id.progress_bar);
        etSearch = findViewById(R.id.et_search);
        tvCreate = findViewById(R.id.tv_create);

        apiClient = new ApiClient();
        sharedPrefs = new SharedPreferencesManager(this);

        adapter = new SelectableUserAdapter(friends, this);
        rvFriends.setLayoutManager(new LinearLayoutManager(this));
        rvFriends.setAdapter(adapter);

        // Top bar "Create" action
        if (tvCreate != null) tvCreate.setOnClickListener(v -> createGroup());
        
        // Back button
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterFriends(s != null ? s.toString() : "");
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        loadFriends();
    }

    private void loadFriends() {
        String token = sharedPrefs.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        apiClient.authenticatedGet("/api/users/friends", token, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(CreateGroupActivity.this, "Failed to load friends", Toast.LENGTH_SHORT).show();
                });
            }

            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    try {
                        JSONObject json = new JSONObject(body);
                        if (response.code() == 200 && json.optBoolean("success", false)) {
                            JSONObject data = json.getJSONObject("data");
                            JSONArray arr = data.getJSONArray("friends");
                            friends.clear();
                            allFriends.clear();
                            for (int i = 0; i < arr.length(); i++) {
                                User u = User.fromJson(arr.getJSONObject(i));
                                friends.add(u);
                                allFriends.add(u);
                            }
                            adapter.notifyDataSetChanged();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(CreateGroupActivity.this, "Parse error", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void filterFriends(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        friends.clear();
        if (q.isEmpty()) {
            friends.addAll(allFriends);
        } else {
            for (User u : allFriends) {
                String displayName = u.getDisplayName() != null ? u.getDisplayName().toLowerCase(java.util.Locale.ROOT) : "";
                String username = u.getUsername() != null ? u.getUsername().toLowerCase(java.util.Locale.ROOT) : "";
                String email = u.getEmail() != null ? u.getEmail().toLowerCase(java.util.Locale.ROOT) : "";
                String phone = u.getPhoneNumber() != null ? u.getPhoneNumber().toLowerCase(java.util.Locale.ROOT) : "";
                if (displayName.contains(q) || username.contains(q) || email.contains(q) || phone.contains(q)) {
                    friends.add(u);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void createGroup() {
        String name = etGroupName.getText().toString().trim();
        String desc = etDescription.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Group name is required", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedUserIds.size() < 2) { // at least 2 others + creator = 3
            Toast.makeText(this, "Select at least 2 friends", Toast.LENGTH_SHORT).show();
            return;
        }

        String token = sharedPrefs.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject payload = getJsonObject(name, desc);

            progressBar.setVisibility(View.VISIBLE);
            apiClient.createGroupChat(token, payload, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(CreateGroupActivity.this, "Failed to create group", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String body = response.body().string();
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        try {
                            JSONObject json = new JSONObject(body);
                            if ((response.code() == 200 || response.code() == 201) && json.optBoolean("success", false)) {
                                Toast.makeText(CreateGroupActivity.this, "Group created", Toast.LENGTH_SHORT).show();
                                finish();
                            } else {
                                String msg = json.optString("message", "Create group failed");
                                Toast.makeText(CreateGroupActivity.this, msg, Toast.LENGTH_SHORT).show();
                            }
                        } catch (JSONException e) {
                            Toast.makeText(CreateGroupActivity.this, "Parse error", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (JSONException ignored) {}
    }

    @NonNull
    private JSONObject getJsonObject(String name, String desc) throws JSONException {
        JSONObject payload = new JSONObject();
        payload.put("name", name);
        if (!desc.isEmpty()) payload.put("description", desc);
        JSONArray ids = new JSONArray();
        for (String id : selectedUserIds) ids.put(id);
        payload.put("participantIds", ids);

        // settings.isPublic from toggle
        JSONObject settings = new JSONObject();
        settings.put("isPublic", switchPublic != null && switchPublic.isChecked());
        payload.put("settings", settings);
        return payload;
    }

    @Override
    public void onSelectionChanged(String userId, boolean selected) {
        if (selected) {
            if (!selectedUserIds.contains(userId)) selectedUserIds.add(userId);
        } else {
            selectedUserIds.remove(userId);
        }
    }
}


