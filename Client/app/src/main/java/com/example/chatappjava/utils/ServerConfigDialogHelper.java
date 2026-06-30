package com.example.chatappjava.utils;

import android.text.TextUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.chatappjava.R;
import com.example.chatappjava.config.ServerConfig;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Shared server configuration dialog for login and settings screens.
 */
public final class ServerConfigDialogHelper {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private ServerConfigDialogHelper() {
    }

    public static void show(AppCompatActivity activity, DatabaseManager databaseManager, Runnable onSaved) {
        if (activity == null || databaseManager == null) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        android.view.View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_server_config, null);
        EditText etServerIp = dialogView.findViewById(R.id.et_server_ip);
        EditText etServerPort = dialogView.findViewById(R.id.et_server_port);
        CheckBox cbUseHttps = dialogView.findViewById(R.id.cb_use_https);
        CheckBox cbUseWss = dialogView.findViewById(R.id.cb_use_wss);
        Button btnTest = dialogView.findViewById(R.id.btn_test_server_connection);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel_server_config);
        Button btnSave = dialogView.findViewById(R.id.btn_save_server_config);

        etServerIp.setText(ServerConfig.getServerIp());
        etServerPort.setText(String.valueOf(ServerConfig.getServerPort()));
        cbUseHttps.setChecked(ServerConfig.isUsingHttps());
        cbUseWss.setChecked(ServerConfig.isUsingWss());

        AlertDialog dialog = builder.setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnTest.setOnClickListener(v -> testConnection(activity, etServerIp, etServerPort, cbUseHttps, btnTest));

        btnSave.setOnClickListener(v -> {
            String ip = etServerIp.getText() != null ? etServerIp.getText().toString().trim() : "";
            String portText = etServerPort.getText() != null ? etServerPort.getText().toString().trim() : "";

            if (TextUtils.isEmpty(ip)) {
                etServerIp.setError(activity.getString(R.string.error_server_ip_required));
                etServerIp.requestFocus();
                return;
            }

            int port;
            try {
                port = Integer.parseInt(portText);
                if (port <= 0 || port > 65535) {
                    throw new NumberFormatException("invalid port");
                }
            } catch (NumberFormatException ex) {
                etServerPort.setError(activity.getString(R.string.error_server_port_invalid));
                etServerPort.requestFocus();
                return;
            }

            databaseManager.setOverrideServerIp(ip);
            databaseManager.setOverrideServerPort(port);
            databaseManager.setOverrideUseHttps(cbUseHttps.isChecked());
            databaseManager.setOverrideUseWss(cbUseWss.isChecked());

            Toast.makeText(
                    activity,
                    activity.getString(R.string.success_server_config_saved, ServerConfig.getBaseUrl()),
                    Toast.LENGTH_LONG
            ).show();

            dialog.dismiss();
            if (onSaved != null) {
                onSaved.run();
            }
        });

        dialog.show();
    }

    private static void testConnection(
            AppCompatActivity activity,
            EditText etServerIp,
            EditText etServerPort,
            CheckBox cbUseHttps,
            Button btnTest
    ) {
        String ip = etServerIp.getText() != null ? etServerIp.getText().toString().trim() : "";
        String portText = etServerPort.getText() != null ? etServerPort.getText().toString().trim() : "";

        if (TextUtils.isEmpty(ip)) {
            etServerIp.setError(activity.getString(R.string.error_server_ip_required));
            etServerIp.requestFocus();
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException ex) {
            etServerPort.setError(activity.getString(R.string.error_server_port_invalid));
            etServerPort.requestFocus();
            return;
        }

        String protocol = cbUseHttps.isChecked() ? "https" : "http";
        String testUrl = protocol + "://" + ip + ":" + port + "/api/server/health";

        btnTest.setEnabled(false);
        btnTest.setText(R.string.action_testing_connection);

        EXECUTOR.execute(() -> {
            String resultMessage;
            boolean success = false;
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
            Request request = new Request.Builder().url(testUrl).get().build();

            try (Response response = client.newCall(request).execute()) {
                success = response.isSuccessful() || response.code() == 401 || response.code() == 404;
                resultMessage = activity.getString(
                        R.string.success_server_connection_test,
                        testUrl,
                        response.code()
                );
            } catch (IOException e) {
                resultMessage = activity.getString(R.string.error_server_connection_test, testUrl, e.getMessage());
            }

            boolean finalSuccess = success;
            String finalResultMessage = resultMessage;
            activity.runOnUiThread(() -> {
                btnTest.setEnabled(true);
                btnTest.setText(R.string.action_test_connection);
                Toast.makeText(
                        activity,
                        finalResultMessage,
                        finalSuccess ? Toast.LENGTH_LONG : Toast.LENGTH_LONG
                ).show();
            });
        });
    }
}
