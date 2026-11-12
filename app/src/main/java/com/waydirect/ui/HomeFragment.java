package com.waydirect.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.view.GravityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.waydirect.R;
import com.waydirect.bridge.AppDirs;
import com.waydirect.bridge.NativeCache;
import com.waydirect.bridge.NativeEngine;
import com.waydirect.bridge.PathCache;
import com.waydirect.bridge.RouteLogger;
import com.waydirect.core.GlassBlur;
import com.waydirect.core.MainActivity;
import com.waydirect.core.ThemePrefs;
import com.waydirect.core.UiChrome;
import com.waydirect.databinding.FragmentHomeBinding;
import com.waydirect.transfer.RecentTransfersStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Home — recent files by day + shortcuts.
 * Route logs: logs/home/YYYY-MM-DD.log
 */
public class HomeFragment extends Fragment implements DaySectionAdapter.Listener {

    private static final String ROUTE = "home";

    private FragmentHomeBinding binding;
    private DaySectionAdapter daysAdapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        setupUI();
        setupSidebar();
        return binding.getRoot();
    }

    private void setupSidebar() {
        binding.btnOpenHomeDrawer.setOnClickListener(v ->
                binding.homeDrawerLayout.openDrawer(GravityCompat.END));

        View panel = binding.homeOptionsSidebar.getRoot();
        binding.homeOptionsSidebar.sidebarTitle.setText("Home options");
        refreshAppearanceLabels();
        GlassBlur.applyFromPrefs(panel, requireContext());

        binding.homeOptionsSidebar.btnTheme.setOnClickListener(v -> {
            String label = ThemePrefs.cycle(requireContext());
            refreshAppearanceLabels();
            if (getActivity() != null) UiChrome.apply(getActivity());
            Toast.makeText(getContext(), "Theme: " + label, Toast.LENGTH_SHORT).show();
        });

        binding.homeOptionsSidebar.btnBlur.setOnClickListener(v -> {
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
        binding.homeOptionsSidebar.themeLabel.setText("Theme: " + theme);
        binding.homeOptionsSidebar.btnTheme.setText("Cycle theme (" + theme + ")");

        boolean blur = ThemePrefs.isBlurEnabled(requireContext());
        String blurTxt = !GlassBlur.isSupported()
                ? "Glass blur: N/A (API < 31)"
                : (blur ? "Glass blur: On" : "Glass blur: Off");
        binding.homeOptionsSidebar.blurLabel.setText(blurTxt);
        binding.homeOptionsSidebar.btnBlur.setText(
                GlassBlur.isSupported() ? "Toggle glass blur" : "Blur unavailable");
    }

    private void setupUI() {
        daysAdapter = new DaySectionAdapter(this);
        binding.homeDaysList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.homeDaysList.setAdapter(daysAdapter);
        binding.homeDaysStatus.setText("Indexing files…");
        loadFilesByDay();

        binding.actionFiles.setOnClickListener(v ->
                ((MainActivity) requireActivity()).navigateTo(R.id.nav_files));
        binding.actionTransfer.setOnClickListener(v ->
                ((MainActivity) requireActivity()).navigateTo(R.id.nav_transfer));
        binding.actionStorage.setOnClickListener(v ->
                ((MainActivity) requireActivity()).navigateTo(R.id.nav_storage));
        binding.actionTerminal.setOnClickListener(v ->
                Toast.makeText(getContext(), "Terminal – coming soon", Toast.LENGTH_SHORT).show());

        refreshRecentTransfersHint();
    }

    private void loadFilesByDay() {
        RouteLogger.step(requireContext(), ROUTE, "loadFilesByDay", "start");
        String root = AppDirs.externalRoot().getPath();

        NativeEngine.buildIndexAsync(requireContext(), root, buildRaw -> {
            if (binding == null || getActivity() == null) return;
            RouteLogger.step(requireContext(), ROUTE, "index_built", truncate(buildRaw, 120));

            NativeEngine.groupIndexByDayAsync(requireContext(), 14, 50, groupRaw -> {
                if (binding == null || getActivity() == null) return;
                String path = PathCache.envelopePath(groupRaw);
                String json = PathCache.readFile(path);
                if (json == null) {
                    json = PathCache.readFile(NativeCache.filesByDayPath(requireContext()));
                }
                final String content = json;
                getActivity().runOnUiThread(() -> applyDaysJson(content));
            });
        });
    }

    private void applyDaysJson(String json) {
        if (binding == null) return;
        if (json == null || json.isEmpty()) {
            binding.homeDaysStatus.setText("No day data yet");
            daysAdapter.submit(new ArrayList<>());
            RouteLogger.warn(requireContext(), ROUTE, "no_day_data");
            return;
        }
        try {
            JSONObject root = new JSONObject(json);
            if (root.has("error")) {
                String err = root.optString("error");
                binding.homeDaysStatus.setText("Error: " + err);
                RouteLogger.error(requireContext(), ROUTE, err);
                return;
            }
            JSONArray days = root.optJSONArray("days");
            List<DaySection> list = new ArrayList<>();
            if (days != null) {
                for (int i = 0; i < days.length(); i++) {
                    JSONObject d = days.getJSONObject(i);
                    DaySection section = new DaySection(d.optString("day", ""));
                    JSONArray files = d.optJSONArray("files");
                    if (files != null) {
                        for (int j = 0; j < files.length(); j++) {
                            JSONObject f = files.getJSONObject(j);
                            section.files.add(new DaySection.FileEntry(
                                    f.optString("path", ""),
                                    f.optString("name", ""),
                                    f.optString("kind", "other"),
                                    f.optLong("size", 0),
                                    f.optLong("mtime", 0)
                            ));
                        }
                    }
                    list.add(section);
                }
            }
            daysAdapter.submit(list);
            binding.homeDaysStatus.setText(
                    list.isEmpty() ? "No recent files" : (list.size() + " day(s)"));
            RouteLogger.step(requireContext(), ROUTE, "days_ok", "count=" + list.size());
        } catch (Exception e) {
            binding.homeDaysStatus.setText("Failed to parse day list");
            RouteLogger.error(requireContext(), ROUTE, "parse " + e.getMessage());
        }
    }

    @Override
    public void onSeeMore(DaySection section) {
        Toast.makeText(getContext(),
                "See more: " + section.day + " (" + section.files.size() + " files)",
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onFileClick(DaySection.FileEntry file) {
        FileOpenHelper.open(requireContext(), file.path);
    }

    private void refreshRecentTransfersHint() {
        if (binding == null) return;
        List<RecentTransfersStore.Entry> list = RecentTransfersStore.load(requireContext());
        if (list.isEmpty()) {
            binding.recentTransfersEmpty.setText("No recent transfers");
        } else {
            RecentTransfersStore.Entry e = list.get(0);
            binding.recentTransfersEmpty.setText(
                    (e.download ? "↓ " : "↑ ") + e.name + " · and "
                            + Math.max(0, list.size() - 1) + " more");
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) refreshRecentTransfersHint();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
