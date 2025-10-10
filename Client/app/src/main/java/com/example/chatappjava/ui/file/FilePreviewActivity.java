package com.example.chatappjava.ui.file;

import android.Manifest;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.chatappjava.R;
import com.example.chatappjava.network.ApiClient;
import com.example.chatappjava.config.ServerConfig;
import com.example.chatappjava.utils.SharedPreferencesManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class FilePreviewActivity extends AppCompatActivity {
    private static final String TAG = "FilePreviewActivity";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    
    private TextView fileNameText;
    private TextView fileSizeText;
    private TextView fileTypeText;
    private TextView previewText;
    private ImageView fileIconImage;
    private Button downloadButton;
    private Button openButton;
    private ProgressBar progressBar;
    
    private String chatId;
    private String fileName;
    private String originalName;
    private String fileUrl;
    private String mimeType;
    private long fileSize;
    private String previewContent;
    private String fileType;
    
    private SharedPreferencesManager sharedPrefsManager;
    private OkHttpClient httpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_preview);
        
        initViews();
        initData();
        loadFileInfo();
    }
    
    private void initViews() {
        fileNameText = findViewById(R.id.fileNameText);
        fileSizeText = findViewById(R.id.fileSizeText);
        fileTypeText = findViewById(R.id.fileTypeText);
        previewText = findViewById(R.id.previewText);
        fileIconImage = findViewById(R.id.fileIconImage);
        downloadButton = findViewById(R.id.downloadButton);
        openButton = findViewById(R.id.openButton);
        progressBar = findViewById(R.id.progressBar);
        
        downloadButton.setOnClickListener(v -> downloadFile());
        openButton.setOnClickListener(v -> openFile());
        
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
    }
    
    private void initData() {
        sharedPrefsManager = new SharedPreferencesManager(this);
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        
        // Get file info from intent
        Intent intent = getIntent();
        chatId = intent.getStringExtra("chatId");
        fileName = intent.getStringExtra("fileName");
        originalName = intent.getStringExtra("originalName");
        fileUrl = intent.getStringExtra("fileUrl");
        mimeType = intent.getStringExtra("mimeType");
        fileSize = intent.getLongExtra("fileSize", 0);
        
        if (originalName != null && !originalName.isEmpty()) {
            fileNameText.setText(originalName);
        } else {
            fileNameText.setText(fileName);
        }
        
        fileSizeText.setText(formatFileSize(fileSize));
        fileTypeText.setText(getFileTypeFromMime(mimeType));
        
        // Set file icon based on type
        setFileIcon(mimeType);
    }
    
    private void loadFileInfo() {
        if (chatId == null || fileName == null) {
            Toast.makeText(this, "File information not available", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        showProgress(true);
        
        String token = sharedPrefsManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        String url = ServerConfig.getBaseUrl() + "/api/upload/preview/" + chatId + "/" + fileName;
        
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .build();
        
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    showProgress(false);
                    Toast.makeText(FilePreviewActivity.this, "Failed to load file preview: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                runOnUiThread(() -> showProgress(false));
                
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        
                        if (jsonResponse.getBoolean("success")) {
                            previewContent = jsonResponse.optString("preview", "");
                            fileType = jsonResponse.optString("type", "other");
                            
                            runOnUiThread(() -> {
                                if (!TextUtils.isEmpty(previewContent)) {
                                    previewText.setText(previewContent);
                                    previewText.setVisibility(View.VISIBLE);
                                } else {
                                    previewText.setText("Preview not available for this file type");
                                    previewText.setVisibility(View.VISIBLE);
                                }
                            });
                        } else {
                            runOnUiThread(() -> {
                                Toast.makeText(FilePreviewActivity.this, "Failed to load preview", Toast.LENGTH_SHORT).show();
                            });
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing preview response", e);
                        runOnUiThread(() -> {
                            Toast.makeText(FilePreviewActivity.this, "Error parsing response", Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(FilePreviewActivity.this, "Failed to load file preview", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }
    
    private void downloadFile() {
        if (!checkStoragePermission()) {
            requestStoragePermission();
            return;
        }
        
        if (fileUrl == null || fileUrl.isEmpty()) {
            Toast.makeText(this, "File URL not available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            Uri uri = Uri.parse(ServerConfig.getBaseUrl() + fileUrl);
            
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
            request.setTitle(originalName != null ? originalName : fileName);
            request.setDescription("Downloading file...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, originalName != null ? originalName : fileName);
            
            downloadManager.enqueue(request);
            Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting download", e);
            Toast.makeText(this, "Failed to start download", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void openFile() {
        if (fileUrl == null || fileUrl.isEmpty()) {
            Toast.makeText(this, "File URL not available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            String fullUrl = ServerConfig.getBaseUrl() + fileUrl;
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(fullUrl), mimeType);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(this, "No app available to open this file", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening file", e);
            Toast.makeText(this, "Failed to open file", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void setFileIcon(String mimeType) {
        if (mimeType == null) {
            fileIconImage.setImageResource(R.drawable.ic_file);
            return;
        }
        
        if (mimeType.startsWith("image/")) {
            fileIconImage.setImageResource(R.drawable.ic_image);
        } else if (mimeType.equals("application/pdf")) {
            fileIconImage.setImageResource(R.drawable.ic_pdf);
        } else if (mimeType.equals("text/plain")) {
            fileIconImage.setImageResource(R.drawable.ic_text);
        } else if (mimeType.contains("word") || mimeType.contains("document")) {
            fileIconImage.setImageResource(R.drawable.ic_document);
        } else if (mimeType.contains("excel") || mimeType.contains("spreadsheet")) {
            fileIconImage.setImageResource(R.drawable.ic_spreadsheet);
        } else if (mimeType.contains("powerpoint") || mimeType.contains("presentation")) {
            fileIconImage.setImageResource(R.drawable.ic_presentation);
        } else {
            fileIconImage.setImageResource(R.drawable.ic_file);
        }
    }
    
    private String getFileTypeFromMime(String mimeType) {
        if (mimeType == null) return "Unknown";
        
        if (mimeType.equals("application/pdf")) return "PDF Document";
        if (mimeType.equals("text/plain")) return "Text File";
        if (mimeType.contains("word")) return "Word Document";
        if (mimeType.contains("excel")) return "Excel Spreadsheet";
        if (mimeType.contains("powerpoint")) return "PowerPoint Presentation";
        
        return mimeType;
    }
    
    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024.0));
        return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
    }
    
    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        downloadButton.setEnabled(!show);
        openButton.setEnabled(!show);
    }
    
    private boolean checkStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                   ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
                   ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    private void requestStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            }, PERMISSION_REQUEST_CODE);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, PERMISSION_REQUEST_CODE);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                downloadFile();
            } else {
                Toast.makeText(this, "Storage permission required for download", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
