package com.waydirect.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.view.GravityCompat;
import androidx.fragment.app.Fragment;

import com.waydirect.bridge.RouteLogger;
import com.waydirect.bridge.Stats;           // requestSnapshot → native / cache
import com.waydirect.bridge.StatsSnapshot;   // parse JSON into typed fields
import com.waydirect.core.GlassBlur;
import com.waydirect.core.ThemePrefs;
import com.waydirect.core.UiChrome;
import com.waydirect.databinding.FragmentStorageBinding; // fragment_storage.xml
import com.waydirect.utils.FormatSizes;

import org.json.JSONObject;

/**
 * Storage tab — usage summary, free space, category sizes, ring chart.
 *
 * <h3>Call route</h3>
 * <pre>
 * onCreateView / btnRefresh
 *   → loadStats(force)
 *   → Stats.requestSnapshot(ctx, force, this::handleStatsResult)
 *   → (bridge may hit cache or native)
 *   → handleStatsResult(rawJson)
 *        → StatsSnapshot.parse → binding text + storageRing
 * </pre>
 * The callback carries a JSON string. We convert it to {@link StatsSnapshot}
 * (plain fields: usedBytes, images, …) then write human text with {@link FormatSizes}.
 *
 * <p>Errors: show the real {@code reason}/{@code error} from JSON on the summary line
 * (not a silent generic failure). Logs go to {@code logs/storage/}.
 */
public class StorageFragment extends Fragment {

    private static final String ROUTE = "storage";
    private FragmentStorageBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentStorageBinding.inflate(inflater, container, false);
        setupButtons();
        setupSidebar();
        loadStats(false);
        return binding.getRoot();
    }

    private void setupSidebar() {
        binding.btnOpenStorageDrawer.setOnClickListener(v ->
                binding.storageDrawerLayout.openDrawer(GravityCompat.END));

        View panel = binding.storageOptionsSidebar.getRoot();
        binding.storageOptionsSidebar.sidebarTitle.setText("Storage options");
        refreshAppearanceLabels();
        GlassBlur.applyFromPrefs(panel, requireContext());

        binding.storageOptionsSidebar.btnTheme.setOnClickListener(v -> {
            String label = ThemePrefs.cycle(requireContext());
            refreshAppearanceLabels();
            if (getActivity() != null) UiChrome.apply(getActivity());
            Toast.makeText(getContext(), "Theme: " + label, Toast.LENGTH_SHORT).show();
        });

        binding.storageOptionsSidebar.btnBlur.setOnClickListener(v -> {
            if (!GlassBlur.isSupported()) {
                Toast.makeText(getContext(), "Blur needs Android 12+", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean on = ThemePrefs.toggleBlur(requireContext());
            GlassBlur.applyFromPrefs(panel, requireContext());
            refreshAppearanceLabels();
            Toast.makeText(getContext(), on ? "Glass blur on" : "Glass blur off", Toast.LENGTH_SHORT).show();
        });
    }

    private void refreshAppearanceLabels() {
        if (binding == null) return;
        String theme = ThemePrefs.currentLabel(requireContext());
        binding.storageOptionsSidebar.themeLabel.setText("Theme: " + theme);
        binding.storageOptionsSidebar.btnTheme.setText("Cycle theme (" + theme + ")");

        boolean blur = ThemePrefs.isBlurEnabled(requireContext());
        String blurTxt = !GlassBlur.isSupported()
                ? "Glass blur: N/A (API < 31)"
                : (blur ? "Glass blur: On" : "Glass blur: Off");
        binding.storageOptionsSidebar.blurLabel.setText(blurTxt);
        binding.storageOptionsSidebar.btnBlur.setText(
                GlassBlur.isSupported() ? "Toggle glass blur" : "Blur unavailable");
    }

    private void setupButtons() {
        binding.btnRefreshStorage.setOnClickListener(v -> loadStats(true));
    }

    private void loadStats(boolean force) {
        binding.storageSummary.setText("Calculating…");
        RouteLogger.step(requireContext(), ROUTE, "ui_loadStats", "force=" + force);
        // Callback = handleStatsResult(String rawJson) on whatever thread Stats uses
        Stats.requestSnapshot(getContext(), force, this::handleStatsResult);
    }

    /** Receives raw JSON from Stats; updates all summary TextViews + ring. */
    private void handleStatsResult(String rawJson) {
        if (binding == null) return;
        try {
            if (rawJson != null && (rawJson.contains("\"error\"") || looksLikeErrorStatus(rawJson))) {
                String reason = extractReason(rawJson);
                RouteLogger.error(requireContext(), ROUTE, "ui_error " + reason);
                binding.storageSummary.setText("Storage unavailable: " + reason);
                binding.storageFree.setText("Pull refresh or check Internals → logs/storage");
                return;
            }

            StatsSnapshot s = StatsSnapshot.parse(rawJson);

            binding.storageSummary.setText(FormatSizes.of(s.usedBytes) + " used of " + FormatSizes.of(s.totalBytes));
            binding.storageFree.setText(FormatSizes.of(s.freeBytes) + " free");

            if (binding.catImages != null) binding.catImages.setText(FormatSizes.of(s.images));
            if (binding.catVideos != null) binding.catVideos.setText(FormatSizes.of(s.videos));
            if (binding.catAudio != null) binding.catAudio.setText(FormatSizes.of(s.audio));
            if (binding.catDocuments != null) binding.catDocuments.setText(FormatSizes.of(s.documents));
            if (binding.catSystem != null) binding.catSystem.setText(FormatSizes.of(s.system));
            if (binding.catOthers != null) binding.catOthers.setText(FormatSizes.of(s.others));

            if (binding.catTotal != null) {
                binding.catTotal.setText(FormatSizes.of(s.categoriesTotal()));
            }

            if (binding.storageRing != null) {
                binding.storageRing.setCategoryBytes(
                        s.images, s.videos, s.audio, s.documents, s.system, s.others);
            }
            RouteLogger.step(requireContext(), ROUTE, "ui_ok", "used=" + s.usedBytes);
        } catch (Exception e) {
            RouteLogger.error(requireContext(), ROUTE, "ui_parse " + e.getMessage() + " raw=" + truncate(rawJson, 200));
            String reason = extractReason(rawJson);
            if (reason.isEmpty()) reason = e.getMessage() != null ? e.getMessage() : "parse_error";
            binding.storageSummary.setText("Storage error: " + reason);
            binding.storageFree.setText("See logs/storage for details");
        }
    }

    private static boolean looksLikeErrorStatus(String json) {
        try {
            JSONObject o = new JSONObject(json);
            String status = o.optString("status", "");
            return "error".equalsIgnoreCase(status);
        } catch (Exception e) {
            return false;
        }
    }

    /** Pull error / reason / status from native JSON when present. */
    private static String extractReason(String json) {
        if (json == null || json.isEmpty()) return "empty_response";
        try {
            JSONObject o = new JSONObject(json);
            if (o.has("reason")) return o.optString("reason", "error");
            if (o.has("error")) return o.optString("error", "error");
            if ("error".equalsIgnoreCase(o.optString("status", ""))) {
                return o.optString("message", "status_error");
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
