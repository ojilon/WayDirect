package com.waydirect.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.waydirect.bridge.NativeEngine;
import com.waydirect.bridge.RouteLogger;
import com.waydirect.databinding.ActivityTextViewerBinding;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Lightweight viewer for text-like files: .txt .json .md .html .htm .xml .csv .log .odin .java .c .h
 *
 * Heavy read is done by native ACTION_READ_TEXT_FILE (capped, binary refused).
 * Java only formats and displays. Not a full editor yet — read-only foundation.
 *
 * Launch: TextViewerActivity.open(context, absolutePath);
 */
public class TextViewerActivity extends AppCompatActivity {

    public static final String EXTRA_FILE_PATH = "file_path";
    private static final String ROUTE = "preview";
    private static final int MAX_BYTES = 256 * 1024;

    private ActivityTextViewerBinding binding;
    private String filePath;

    public static void open(Context context, String filePath) {
        Intent intent = new Intent(context, TextViewerActivity.class);
        intent.putExtra(EXTRA_FILE_PATH, filePath);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTextViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        filePath = getIntent().getStringExtra(EXTRA_FILE_PATH);
        if (filePath == null || filePath.isEmpty()) {
            Toast.makeText(this, "No file path", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String name = filePath;
        int slash = filePath.lastIndexOf('/');
        if (slash >= 0 && slash < filePath.length() - 1) name = filePath.substring(slash + 1);

        binding.toolbar.setTitle(name);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.btnClose.setOnClickListener(v -> finish());
        binding.filePathLabel.setText(filePath);
        binding.content.setMovementMethod(new ScrollingMovementMethod());
        binding.content.setText("Reading…");

        RouteLogger.step(this, ROUTE, "open", filePath);
        loadViaNative();
    }

    private void loadViaNative() {
        NativeEngine.readTextFileAsync(filePath, MAX_BYTES, rawJson -> {
            if (isFinishing() || binding == null) return;
            try {
                JSONObject data = new JSONObject(rawJson);
                if (data.has("error")) {
                    String err = data.optString("error", "error");
                    RouteLogger.error(this, ROUTE, "native_error " + err);
                    binding.content.setText("Cannot open: " + err);
                    return;
                }
                if (data.optBoolean("binary", false)) {
                    binding.content.setText("Binary file — not shown.\nSize: " + data.optLong("size", 0) + " bytes");
                    return;
                }
                JSONArray lines = data.optJSONArray("lines");
                StringBuilder sb = new StringBuilder();
                if (lines != null) {
                    for (int i = 0; i < lines.length(); i++) {
                        if (i > 0) sb.append('\n');
                        sb.append(lines.optString(i));
                    }
                } else if (data.has("text")) {
                    sb.append(data.optString("text", ""));
                }
                if (data.optBoolean("truncated", false)) {
                    sb.append("\n\n… truncated (native cap)");
                }
                String body = sb.length() == 0 ? "(empty file)" : sb.toString();
                // Light pretty-print for JSON
                if (filePath.toLowerCase().endsWith(".json")) {
                    body = tryPrettyJson(body);
                }
                binding.content.setText(body);
                RouteLogger.step(this, ROUTE, "ok", "chars=" + body.length());
            } catch (Exception e) {
                RouteLogger.error(this, ROUTE, "parse " + e.getMessage());
                binding.content.setText("Failed to parse preview.\n" + e.getMessage());
            }
        });
    }

    private static String tryPrettyJson(String raw) {
        try {
            String t = raw.trim();
            if (t.startsWith("{")) {
                return new JSONObject(t).toString(2);
            }
            if (t.startsWith("[")) {
                return new org.json.JSONArray(t).toString(2);
            }
        } catch (Exception ignored) {
        }
        return raw;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
