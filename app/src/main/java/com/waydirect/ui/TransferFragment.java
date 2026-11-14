package com.waydirect.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.GravityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.waydirect.R;
import com.waydirect.bridge.NativeEngine;
import com.waydirect.bridge.PathCache;
import com.waydirect.bridge.PathRegistry;
import com.waydirect.bridge.RouteLogger;
import com.waydirect.core.Config;
import com.waydirect.core.GlassBlur;
import com.waydirect.core.ThemePrefs;
import com.waydirect.core.UiChrome;

//fragment_transfer.xml becomes FragmentTransferBinding
import com.waydirect.databinding.FragmentTransferBinding;

import com.waydirect.network.NetworkCallback;
import com.waydirect.network.NetworkManager;
import com.waydirect.storage.FileIndexer;
import com.waydirect.transfer.RecentTransfersStore;
import com.waydirect.transfer.TransferController;
import com.waydirect.transfer.TransferQueue;
import com.waydirect.utils.FormatSizes;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Transfer screen — connection, download/upload, local search, browse-for-save,
 * recent history, and in-session queue.
 *
 * <h3>Java ↔ XML</h3>
 * Inflates {@code fragment_transfer.xml} → {@link FragmentTransferBinding}.
 * Tabs are a {@code ViewFlipper} ({@code transfer_view_flipper}); each page is a child.
 * List rows reuse {@link FileAdapter} + {@code item_file.xml}.
 *
 * <h3>Important binding ids (must match XML)</h3>
 * <ul>
 *   <li>{@code save_path_hint} / {@code drawer_save_path} — where downloads land</li>
 *   <li>{@code search_input}, {@code btn_run_search} — search tab query + go</li>
 *   <li>{@code transfer_filename}, {@code btn_download}, {@code btn_upload}</li>
 *   <li>{@code btn_start_listener}, {@code connection_status}</li>
 * </ul>
 *
 * <h3>Network route</h3>
 * <pre>
 * download/upload → NetworkManager.processProtocolCommand("download|upload …", dir, callback)
 * listener        → TransferController.startServerListener(port, statusCallback)
 * </pre>
 * Callbacks carry console lines ({@link com.waydirect.network.NetworkCallback#onConsoleUpdate})
 * and a final status string ({@code onOperationComplete}).
 *
 * <h3>Search / multi-send</h3>
 * Index search → FileItem rows → selection mode on {@link FileAdapter}
 * → {@code getSelectedPaths()} → sequential upload.
 *
 * Logging: {@link RouteLogger} route {@code "transfer"}. See docs/APP_MAP.md.
 */
public class TransferFragment extends Fragment {

    private static final String ROUTE = "transfer";

    private FragmentTransferBinding binding;

    private long sessionSentBytes = 0;
    private long sessionReceivedBytes = 0;

    private String savePath = FileIndexer.getDefaultSavePath();
    private String browsePath = BrowseSession.transferBrowsePath;

    private FileAdapter browseAdapter;
    private FileAdapter recentAdapter;
    private FileAdapter searchAdapter;
    private FileAdapter sessionAdapter;

    private final List<FileItem> browseFiles = new ArrayList<>();
    private final List<FileItem> searchResultRows = new ArrayList<>();
    private final List<String> searchNavStack = new ArrayList<>();

    private volatile boolean queueCancelled = false;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentTransferBinding.inflate(inflater, container, false);
        if (browsePath == null) {
            browsePath = FileIndexer.getDefaultSavePath();
        }
        setupUI();
        ensureIndexWarm();
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // -------------------------------------------------------------------------
    // UI wiring
    // -------------------------------------------------------------------------

    private void setupUI() {
        binding.connectionDetails.setText("Target: " + Config.HOST + ":" + Config.PORT);
        binding.connectionStatus.setText("Not tested");
        updateSessionStats();
        updateSavePathLabel();
        updateThemeLabel();

        binding.btnTestConnection.setOnClickListener(v -> {
            RouteLogger.step(requireContext(), ROUTE, "ui", "testConnection");
            testConnection();
        });
        binding.btnStartListener.setOnClickListener(v -> {
            RouteLogger.step(requireContext(), ROUTE, "ui", "startListener");
            startListener();
        });
        binding.btnDownload.setOnClickListener(v -> {
            RouteLogger.step(requireContext(), ROUTE, "ui", "download");
            runDownload();
        });
        binding.btnUpload.setOnClickListener(v -> {
            RouteLogger.step(requireContext(), ROUTE, "ui", "uploadSearch");
            runUploadSearch();
        });

        binding.btnOpenTransferDrawer.setOnClickListener(v ->
                binding.transferDrawerLayout.openDrawer(GravityCompat.END));

        binding.btnChangeSave.setOnClickListener(v -> showBrowseTab());
        binding.btnRefreshIndexer.setOnClickListener(v -> {
            RouteLogger.step(requireContext(), ROUTE, "ui", "refreshIndexer");
            refreshIndexer();
        });
        binding.btnTheme.setOnClickListener(v -> {
            String label = ThemePrefs.cycle(requireContext());
            updateThemeLabel();
            if (getActivity() != null) UiChrome.apply(getActivity());
            Toast.makeText(getContext(), "Theme: " + label, Toast.LENGTH_SHORT).show();
        });
        binding.btnBlur.setOnClickListener(v -> {
            if (!GlassBlur.isSupported()) {
                Toast.makeText(getContext(), "Blur needs Android 12+", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean on = ThemePrefs.toggleBlur(requireContext());
            GlassBlur.applyFromPrefs(binding.transferSidebarRoot, requireContext());
            updateThemeLabel();
            Toast.makeText(getContext(), on ? "Glass blur on" : "Glass blur off",
                    Toast.LENGTH_SHORT).show();
        });
        binding.btnBackToTransfer.setOnClickListener(v -> showTransferTab());
        binding.btnUseThisFolder.setOnClickListener(v -> {
            savePath = browsePath;
            updateSavePathLabel();
            Toast.makeText(getContext(), "Save folder set", Toast.LENGTH_SHORT).show();
            showTransferTab();
            binding.transferDrawerLayout.closeDrawer(GravityCompat.END);
        });

        setupBrowseList();
        setupSearchTab();
        setupRecentList();
        setupSessionList();
        setupTabs();

        GlassBlur.applyFromPrefs(binding.transferSidebarRoot, requireContext());
        binding.transferTabs.check(R.id.tab_transfer);
    }

    private void setupTabs() {
        binding.transferTabs.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.tab_guide) {
                binding.transferViewFlipper.setDisplayedChild(0);
            } else if (checkedId == R.id.tab_transfer) {
                binding.transferViewFlipper.setDisplayedChild(1);
            } else if (checkedId == R.id.tab_search) {
                binding.transferViewFlipper.setDisplayedChild(2);
            } else if (checkedId == R.id.tab_browse) {
                binding.transferViewFlipper.setDisplayedChild(3);
            } else if (checkedId == R.id.tab_network) {
                binding.transferViewFlipper.setDisplayedChild(4);
            }
        });
    }

    private void showTransferTab() {
        binding.transferTabs.check(R.id.tab_transfer);
    }

    private void showBrowseTab() {
        binding.transferTabs.check(R.id.tab_browse);
        loadBrowseDirectory(browsePath);
        if (!binding.transferDrawerLayout.isDrawerOpen(GravityCompat.END)) {
            binding.transferDrawerLayout.openDrawer(GravityCompat.END);
        }
    }

    // -------------------------------------------------------------------------
    // Session queue
    // -------------------------------------------------------------------------

    private void setupSessionList() {
        sessionAdapter = new FileAdapter();
        binding.sessionQueueList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.sessionQueueList.setAdapter(sessionAdapter);
        sessionAdapter.setOnItemClickListener(new FileAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(FileItem item) {
                Toast.makeText(getContext(), item.details, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onItemLongClick(FileItem item) {
                // no-op
            }
        });
        binding.btnCancelQueue.setOnClickListener(v -> {
            queueCancelled = true;
            RouteLogger.step(requireContext(), ROUTE, "queue", "cancelRequested");
            Toast.makeText(getContext(), "Finishing current file…", Toast.LENGTH_SHORT).show();
        });
        renderSessionQueue();
    }

    /** Session rows straight from the queue file — the same file the uploader writes. */
    private void renderSessionQueue() {
        if (binding == null) return;
        List<FileItem> rows = new ArrayList<>();
        for (TransferQueue.Entry entry : TransferQueue.entries(requireContext())) {
            File f = new File(entry.path);
            rows.add(new FileItem(f.getName(), entry.path, entry.status, false, 0));
        }
        sessionAdapter.submitList(rows);
        binding.sessionQueueEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        binding.sessionQueueList.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void setQueueActive(boolean active) {
        if (binding == null) return;
        binding.btnCancelQueue.setVisibility(active ? View.VISIBLE : View.GONE);
    }

    private void setQueueStatus(String text) {
        if (binding == null) return;
        binding.sessionQueueStatus.setText(text);
    }

    // -------------------------------------------------------------------------
    // Recent transfers
    // -------------------------------------------------------------------------

    private void setupRecentList() {
        recentAdapter = new FileAdapter();
        binding.recentTransfersList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recentTransfersList.setAdapter(recentAdapter);
        recentAdapter.setOnItemClickListener(new FileAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(FileItem item) {
                binding.transferFilename.setText(item.name);
            }

            @Override
            public void onItemLongClick(FileItem item) {
                // no-op
            }
        });
        refreshRecentUi();
    }

    private void refreshRecentUi() {
        if (binding == null) return;
        List<RecentTransfersStore.Entry> entries = RecentTransfersStore.load(requireContext());
        if (entries.isEmpty()) {
            binding.recentTransfersEmpty.setVisibility(View.VISIBLE);
            binding.recentTransfersEmpty.setText("No transfers yet");
            binding.recentTransfersList.setVisibility(View.GONE);
            recentAdapter.submitList(null);
            return;
        }
        binding.recentTransfersEmpty.setVisibility(View.GONE);
        binding.recentTransfersList.setVisibility(View.VISIBLE);
        List<FileItem> items = new ArrayList<>();
        for (RecentTransfersStore.Entry e : entries) {
            String details = (e.download ? "Download" : "Upload") + " · " + e.detail;
            items.add(new FileItem(e.name, e.detail, details, false, 0));
        }
        recentAdapter.submitList(items);
    }

    private void recordSuccess(boolean download, String name, String detail) {
        RecentTransfersStore.add(requireContext(), download, name, detail);
        refreshRecentUi();
        RouteLogger.step(requireContext(), ROUTE, download ? "download_ok" : "upload_ok", name);
    }

    // -------------------------------------------------------------------------
    // Browse (save-folder picker)
    // -------------------------------------------------------------------------

    private void setupBrowseList() {
        browseAdapter = new FileAdapter();
        binding.browseFolderList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.browseFolderList.setAdapter(browseAdapter);

        browseAdapter.setOnItemClickListener(new FileAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(FileItem item) {
                if (item.directory) {
                    browsePath = item.path;
                    loadBrowseDirectory(browsePath);
                }
            }

            @Override
            public void onItemLongClick(FileItem item) {
                if (item.directory) {
                    savePath = item.path;
                    updateSavePathLabel();
                    Toast.makeText(getContext(), "Save folder: " + item.name, Toast.LENGTH_SHORT).show();
                }
            }
        });

        binding.btnBrowseUp.setOnClickListener(v -> {
            File parent = new File(browsePath).getParentFile();
            if (parent != null) {
                loadBrowseDirectory(parent.getAbsolutePath());
            } else {
                Toast.makeText(getContext(), "Already at the top", Toast.LENGTH_SHORT).show();
            }
        });

        binding.browseFilter.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                applyBrowseFilter(s != null ? s.toString() : "");
            }
        });
    }

    private void applyBrowseFilter(String query) {
        if (binding == null) return;
        if (query == null || query.trim().isEmpty()) {
            browseAdapter.submitList(new ArrayList<>(browseFiles));
            return;
        }
        String q = query.trim().toLowerCase();
        List<FileItem> filtered = new ArrayList<>();
        for (FileItem item : browseFiles) {
            if (item.name.toLowerCase().contains(q)) {
                filtered.add(item);
            }
        }
        browseAdapter.submitList(filtered);
    }

    private void loadBrowseDirectory(String path) {
        browsePath = path;
        BrowseSession.transferBrowsePath = path;
        binding.browseCurrentPath.setText(path);

        // Plain directory listing — no index involved. The native index only
        // serves global name search; browsing one folder never needs it.
        List<FileItem> items = new ArrayList<>();
        File[] children = new File(path).listFiles();
        if (children != null) {
            Arrays.sort(children, Comparator
                    .comparing((File f) -> !f.isDirectory())
                    .thenComparing(f -> f.getName().toLowerCase()));
            for (File c : children) {
                if (c.isDirectory()) {
                    items.add(new FileItem(c.getName(), c.getAbsolutePath(), "Folder", true, 0));
                }
            }
        }

        if (!path.equals(FileIndexer.DEFAULT_ROOT) && path.contains("/")) {
            int slash = path.lastIndexOf('/');
            if (slash > 0) {
                String parent = path.substring(0, slash);
                items.add(0, new FileItem("..", parent, "Up", true, 0));
            }
        }

        browseFiles.clear();
        browseFiles.addAll(items);
        browseAdapter.submitList(new ArrayList<>(items));
        applyBrowseFilter(binding.browseFilter.getText() != null
                ? binding.browseFilter.getText().toString() : "");
    }

    private void updateSavePathLabel() {
        if (binding == null) return;
        binding.savePathHint.setText("Save to: " + savePath);
        if (binding.drawerSavePath != null) binding.drawerSavePath.setText(savePath);
    }

    // -------------------------------------------------------------------------
    // Search tab (index-backed)
    // -------------------------------------------------------------------------

    private void setupSearchTab() {
        searchAdapter = new FileAdapter();
        binding.searchResultsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.searchResultsList.setAdapter(searchAdapter);

        searchAdapter.setOnItemClickListener(new FileAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(FileItem item) {
                if (item.directory) {
                    enterSearchFolder(item.path);
                }
            }

            @Override
            public void onItemLongClick(FileItem item) {
                // no-op
            }
        });
        searchAdapter.setOnSelectionChangedListener(count -> updateSearchSendButton());

        binding.btnRunSearch.setOnClickListener(v -> runSearchTab());
        binding.btnSearchUp.setOnClickListener(v -> searchUp());
        binding.btnSearchSelect.setOnClickListener(v -> {
            boolean next = !searchAdapter.isSelectionMode();
            searchAdapter.setSelectionMode(next);
            binding.btnSearchSelect.setText(next ? "Cancel" : "Select");
            updateSearchSendButton();
        });
        binding.btnSearchSend.setOnClickListener(v -> uploadSearchSelected());
        updateSearchSendButton();
    }

    private void updateSearchSendButton() {
        if (binding == null) return;
        int n = searchAdapter.getSelectedPaths().size();
        binding.btnSearchSend.setEnabled(n > 0);
        binding.btnSearchSend.setText(n > 0 ? "Send (" + n + ")" : "Send");
    }

    private void runSearchTab() {
        String q = binding.searchInput.getText() != null
                ? binding.searchInput.getText().toString().trim() : "";
        if (q.isEmpty()) {
            Toast.makeText(getContext(), "Enter a name to search", Toast.LENGTH_SHORT).show();
            return;
        }
        RouteLogger.step(requireContext(), ROUTE, "search_tab", q);
        binding.searchResultsHint.setText("Searching…");
        searchNavStack.clear();

        NativeEngine.indexMetaAsync(rawMeta -> {
            if (!isAdded()) return;
            if (isIndexMissing(rawMeta)) {
                binding.searchResultsHint.setText("Index missing — building…");
                NativeEngine.buildIndexAsync(requireContext(), FileIndexer.DEFAULT_ROOT,
                        rawBuild -> runOnUi(() -> doSearchTab(q)));
            } else {
                doSearchTab(q);
            }
        });
    }

    private void doSearchTab(String q) {
        String dir = PathRegistry.moduleDir(getContext(), "search");
        if (dir.isEmpty()) {
            binding.searchResultsHint.setText("Storage not ready");
            return;
        }
        String out = dir + "/transfer-tab-matches.json";
        PathCache.remember("transfer-tab-search", out);
        NativeEngine.searchIndexAsync(requireContext(), q, 80, out, rawJson -> {
            if (!isAdded()) return;
            if ("busy".equals(PathCache.reason(rawJson))) {
                binding.searchResultsHint.setText("Search busy — try again");
                return;
            }
            String content = PathCache.readFile(PathCache.envelopePath(rawJson));
            renderSearchResults(content);
        });
    }

    private void renderSearchResults(String content) {
        List<FileItem> rows = new ArrayList<>();
        List<String> folders = new ArrayList<>();
        if (content != null) {
            try {
                JSONArray array = new JSONObject(content).optJSONArray("matches");
                if (array != null) {
                    for (int i = 0; i < array.length(); i++) {
                        String path = array.getString(i);
                        File f = new File(path);
                        rows.add(new FileItem(f.getName(), path, "File", false, 0));
                        String parent = f.getParent();
                        if (parent != null && !folders.contains(parent)) {
                            folders.add(parent);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        for (String folder : folders) {
            File f = new File(folder);
            rows.add(new FileItem(f.getName(), folder, "Folder — tap to browse", true, 0));
        }
        searchResultRows.clear();
        searchResultRows.addAll(rows);
        searchNavStack.clear();
        searchAdapter.submitList(new ArrayList<>(rows));
        binding.searchResultsHint.setText(rows.isEmpty()
                ? "No matches — try Refresh index"
                : rows.size() + " result(s)");
    }

    /** Independent folder stack for the Search tab — results persist underneath. */
    private void enterSearchFolder(String path) {
        searchNavStack.add(path);
        listSearchFolder(path);
    }

    private void searchUp() {
        if (binding == null) return;
        if (searchNavStack.isEmpty()) {
            Toast.makeText(getContext(), "Already showing results", Toast.LENGTH_SHORT).show();
            return;
        }
        searchNavStack.remove(searchNavStack.size() - 1);
        if (searchNavStack.isEmpty()) {
            searchAdapter.submitList(new ArrayList<>(searchResultRows));
            binding.searchResultsHint.setText(searchResultRows.isEmpty()
                    ? "No matches — try Refresh index"
                    : searchResultRows.size() + " result(s)");
        } else {
            listSearchFolder(searchNavStack.get(searchNavStack.size() - 1));
        }
    }

    private void listSearchFolder(String path) {
        if (binding == null) return;
        List<FileItem> rows = new ArrayList<>();
        File[] children = new File(path).listFiles();
        if (children != null) {
            Arrays.sort(children, Comparator
                    .comparing((File f) -> !f.isDirectory())
                    .thenComparing(f -> f.getName().toLowerCase()));
            for (File c : children) {
                if (c.isDirectory()) {
                    rows.add(new FileItem(c.getName(), c.getAbsolutePath(), "Folder", true, 0));
                } else {
                    rows.add(new FileItem(c.getName(), c.getAbsolutePath(),
                            FormatSizes.of(c.length()), false, c.length()));
                }
            }
        }
        searchAdapter.submitList(rows);
        binding.searchResultsHint.setText(path);
    }

    // -------------------------------------------------------------------------
    // Index helpers
    // -------------------------------------------------------------------------

    private void ensureIndexWarm() {
        NativeEngine.indexMetaAsync(rawMeta -> {
            if (!isAdded()) return;
            if (isIndexMissing(rawMeta)) {
                RouteLogger.step(requireContext(), ROUTE, "index", "warm_build");
                NativeEngine.buildIndexAsync(requireContext(), FileIndexer.DEFAULT_ROOT, null);
            }
        });
    }

    private void refreshIndexer() {
        RouteLogger.step(requireContext(), ROUTE, "index", "refresh");
        appendLog("Refreshing index…");
        NativeEngine.buildIndexAsync(requireContext(), FileIndexer.DEFAULT_ROOT, raw -> {
            runOnUi(() -> appendLog("Index refresh finished"));
        });
    }

    private static boolean isIndexMissing(String rawMeta) {
        if (rawMeta == null || rawMeta.isEmpty()) return true;
        try {
            JSONObject o = new JSONObject(rawMeta);
            return !o.optBoolean("ready", false);
        } catch (Exception e) {
            return true;
        }
    }

    // -------------------------------------------------------------------------
    // Connection / network
    // -------------------------------------------------------------------------

    private void testConnection() {
        binding.connectionStatus.setText("Testing…");
        new Thread(() -> {
            boolean ok = false;
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(Config.HOST, Config.PORT), 2500);
                ok = true;
            } catch (Exception ignored) {
            }
            boolean finalOk = ok;
            runOnUi(() -> {
                binding.connectionStatus.setText(finalOk ? "Reachable" : "Unreachable");
                RouteLogger.step(requireContext(), ROUTE, "connection",
                        finalOk ? "ok" : "fail");
            });
        }).start();
    }

    private void startListener() {
        RouteLogger.step(requireContext(), ROUTE, "listener", "start");
        appendLog("Starting listener…");
        // Delegate to existing controller / network stack.
        // Real API: startServerListener(port, TransferCallback) → NetworkStatus
        TransferController.startServerListener(Config.PORT, status -> runOnUi(() -> {
            String msg = status != null
                    ? ("Listener: " + status.getStatus() + " port=" + status.getPort())
                    : "Listener: unknown";
            appendLog(msg);
            if (binding != null) binding.connectionStatus.setText(msg);
            RouteLogger.step(requireContext(), ROUTE, "listener_done", msg);
        }));
    }

    // -------------------------------------------------------------------------
    // Download
    // -------------------------------------------------------------------------

    private void runDownload() {
        String name = binding.transferFilename.getText() != null
                ? binding.transferFilename.getText().toString().trim() : "";
        if (name.isEmpty()) {
            Toast.makeText(getContext(), "Enter a remote file name", Toast.LENGTH_SHORT).show();
            return;
        }

        RouteLogger.step(requireContext(), ROUTE, "download_start", name);
        appendLog("Downloading \"" + name + "\" → " + savePath);
        setTransferButtonsEnabled(false);
        long started = System.currentTimeMillis();
        File workingDir = new File(savePath);
        if (!workingDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            workingDir.mkdirs();
        }

        String command = "download " + name;
        NetworkManager.processProtocolCommand(command, workingDir, new NetworkCallback() {
            @Override
            public void onConsoleUpdate(String outputText) {
                runOnUi(() -> appendLog(outputText));
            }

            @Override
            public void onOperationComplete(String finalResult) {
                long elapsed = Math.max(1, System.currentTimeMillis() - started);
                runOnUi(() -> {
                    appendLog(finalResult);
                    setTransferButtonsEnabled(true);

                    if (finalResult != null && finalResult.toLowerCase().contains("success")) {
                        File f = new File(workingDir, name);
                        long bytes = f.exists() ? f.length() : 0;
                        sessionReceivedBytes += bytes;
                        updateSessionStats();
                        if (bytes > 0) {
                            double kbps = (bytes / 1024.0) / (elapsed / 1000.0);
                            binding.bandwidthHint.setText(
                                    String.format("Last transfer ~ %.1f KB/s (%d ms)", kbps, elapsed));
                        }
                        recordSuccess(true, name, savePath);
                    } else {
                        RouteLogger.step(requireContext(), ROUTE, "download_fail", name);
                    }
                });
            }
        });
    }

    // -------------------------------------------------------------------------
    // Upload (search then send)
    // -------------------------------------------------------------------------

    private void runUploadSearch() {
        String name = binding.transferFilename.getText() != null
                ? binding.transferFilename.getText().toString().trim() : "";
        if (name.isEmpty()) {
            Toast.makeText(getContext(), "Enter a file name to search", Toast.LENGTH_SHORT).show();
            return;
        }

        appendLog("Searching index for \"" + name + "\"…");
        setTransferButtonsEnabled(false);
        RouteLogger.step(requireContext(), ROUTE, "upload_search", name);

        // Global search runs on the shared native index (same file every
        // screen reads). Builds it first when missing, then searches.
        NativeEngine.indexMetaAsync(rawMeta -> {
            if (!isAdded()) {
                runOnUi(() -> setTransferButtonsEnabled(true));
                return;
            }
            if (isIndexMissing(rawMeta)) {
                appendLog("Index missing — building first…");
                NativeEngine.buildIndexAsync(requireContext(), FileIndexer.DEFAULT_ROOT,
                        rawBuild -> runOnUi(() -> doUploadSearch(name)));
            } else {
                doUploadSearch(name);
            }
        });
    }

    private void doUploadSearch(String name) {
        String dir = PathRegistry.moduleDir(getContext(), "search");
        if (dir.isEmpty()) {
            setTransferButtonsEnabled(true);
            Toast.makeText(getContext(), "Storage not ready", Toast.LENGTH_SHORT).show();
            return;
        }
        String out = dir + "/transfer-matches.json";
        PathCache.remember("transfer-search", out);
        NativeEngine.searchIndexAsync(requireContext(), name, 50, out, rawJson -> {
            if (!isAdded()) return;
            setTransferButtonsEnabled(true);
            if ("busy".equals(PathCache.reason(rawJson))) {
                Toast.makeText(getContext(), "Search busy — try again", Toast.LENGTH_SHORT).show();
                return;
            }
            List<String> matches = parseIndexMatches(PathCache.readFile(PathCache.envelopePath(rawJson)));
            if (matches.isEmpty()) {
                appendLog("No local files matched \"" + name + "\"");
                Toast.makeText(getContext(), "No matches — try Refresh index", Toast.LENGTH_SHORT).show();
                return;
            }
            if (matches.size() == 1) {
                confirmSingleUpload(matches.get(0));
            } else {
                showMatchPicker(matches);
            }
        });
    }

    private static List<String> parseIndexMatches(String content) {
        List<String> matches = new ArrayList<>();
        if (content == null) return matches;
        try {
            JSONArray array = new JSONObject(content).optJSONArray("matches");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    matches.add(array.getString(i));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return matches;
    }

    private void confirmSingleUpload(String absolutePath) {
        File f = new File(absolutePath);
        new AlertDialog.Builder(requireContext())
                .setTitle("Upload this file?")
                .setMessage(f.getName() + "\n\n" + absolutePath)
                .setPositiveButton("Confirm upload", (d, w) -> performUpload(absolutePath, null))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showMatchPicker(List<String> matches) {
        String[] labels = new String[matches.size()];
        for (int i = 0; i < matches.size(); i++) {
            File f = new File(matches.get(i));
            labels[i] = f.getName() + "  ·  " + f.getParent();
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_list_item_1,
                labels
        );

        new AlertDialog.Builder(requireContext())
                .setTitle("Choose file to upload (" + matches.size() + ")")
                .setAdapter(adapter, (dialog, which) -> performUpload(matches.get(which), null))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void uploadSearchSelected() {
        List<String> files = filesOnly(searchAdapter.getSelectedPaths());
        if (files.isEmpty()) {
            Toast.makeText(getContext(), "Tick files to send first", Toast.LENGTH_SHORT).show();
            return;
        }
        searchAdapter.setSelectionMode(false);
        binding.btnSearchSelect.setText("Select");
        updateSearchSendButton();
        uploadPaths(files);
    }

    private static List<String> filesOnly(List<String> paths) {
        List<String> files = new ArrayList<>();
        if (paths == null) return files;
        for (String path : paths) {
            if (path != null && new File(path).isFile()) files.add(path);
        }
        return files;
    }

    private void uploadPaths(List<String> paths) {
        if (paths == null || paths.isEmpty()) return;
        if (!TransferQueue.save(requireContext(), paths)) {
            Toast.makeText(getContext(), "Cannot save queue", Toast.LENGTH_SHORT).show();
            return;
        }
        queueCancelled = false;
        setQueueActive(true);
        setQueueStatus("Queued " + paths.size());
        RouteLogger.step(requireContext(), ROUTE, "queue_start", paths.size() + " files");
        renderSessionQueue();
        uploadNext(0, paths, 0);
    }

    private void uploadNext(int index, List<String> paths, int sent) {
        if (queueCancelled) {
            queueCancelled = false;
            setQueueActive(false);
            setQueueStatus("Cancelled · " + sent + " sent");
            RouteLogger.step(requireContext(), ROUTE, "queue", "cancelled after " + sent);
            renderSessionQueue();
            return;
        }
        if (index >= paths.size()) {
            setQueueActive(false);
            setQueueStatus("Done · " + sent + " of " + paths.size());
            renderSessionQueue();
            RouteLogger.step(requireContext(), ROUTE, "queue", "done " + sent + "/" + paths.size());
            return;
        }
        String path = paths.get(index);
        setQueueStatus("Uploading " + new File(path).getName() + " (" + (index + 1) + "/" + paths.size() + ")");
        TransferQueue.setStatus(requireContext(), path, "sending");
        renderSessionQueue();
        performUpload(path, ok -> {
            TransferQueue.setStatus(requireContext(), path, ok ? "done" : "failed");
            renderSessionQueue();
            uploadNext(index + 1, paths, ok ? sent + 1 : sent);
        });
    }

    private interface UploadDone {
        void onDone(boolean ok);
    }

    private void performUpload(String absolutePath, UploadDone onDone) {
        File local = new File(absolutePath);
        if (!local.isFile()) {
            Toast.makeText(getContext(), "Not a file", Toast.LENGTH_SHORT).show();
            if (onDone != null) onDone.onDone(false);
            return;
        }

        RouteLogger.step(requireContext(), ROUTE, "upload_start", local.getName());
        appendLog("Uploading " + local.getName());
        setTransferButtonsEnabled(false);
        long started = System.currentTimeMillis();
        File parent = local.getParentFile();

        String command = "upload " + local.getName();
        NetworkManager.processProtocolCommand(command, parent, new NetworkCallback() {
            @Override
            public void onConsoleUpdate(String outputText) {
                runOnUi(() -> appendLog(outputText));
            }

            @Override
            public void onOperationComplete(String finalResult) {
                long elapsed = Math.max(1, System.currentTimeMillis() - started);
                runOnUi(() -> {
                    appendLog(finalResult);
                    setTransferButtonsEnabled(true);

                    boolean ok = finalResult != null
                            && finalResult.toLowerCase().contains("success");
                    if (ok) {
                        long bytes = local.exists() ? local.length() : 0;
                        sessionSentBytes += bytes;
                        updateSessionStats();
                        if (bytes > 0) {
                            double kbps = (bytes / 1024.0) / (elapsed / 1000.0);
                            binding.bandwidthHint.setText(
                                    String.format("Last transfer ~ %.1f KB/s (%d ms)", kbps, elapsed));
                        }
                        recordSuccess(false, local.getName(), absolutePath);
                    } else {
                        RouteLogger.step(requireContext(), ROUTE, "upload_fail", local.getName());
                    }
                    if (onDone != null) onDone.onDone(ok);
                });
            }
        });
    }

    // -------------------------------------------------------------------------
    // Theme / chrome
    // -------------------------------------------------------------------------

    private void updateThemeLabel() {
        if (binding == null) return;
        binding.themeLabel.setText("Theme: " + ThemePrefs.currentLabel(requireContext()));
        binding.btnTheme.setText("Cycle theme (" + ThemePrefs.currentLabel(requireContext()) + ")");
        boolean blur = ThemePrefs.isBlurEnabled(requireContext());
        String blurTxt = !GlassBlur.isSupported()
                ? "Glass blur: N/A (API < 31)"
                : (blur ? "Glass blur: On" : "Glass blur: Off");
        binding.blurLabel.setText(blurTxt);
        binding.btnBlur.setText(GlassBlur.isSupported() ? "Toggle glass blur" : "Blur unavailable");
    }

    // -------------------------------------------------------------------------
    // Small helpers
    // -------------------------------------------------------------------------

    private void setTransferButtonsEnabled(boolean enabled) {
        if (binding == null) return;
        binding.btnDownload.setEnabled(enabled);
        binding.btnUpload.setEnabled(enabled);
    }

    private void updateSessionStats() {
        if (binding == null) return;
        binding.sessionStats.setText(
                "Sent: " + FormatSizes.of(sessionSentBytes)
                        + " · Received: " + FormatSizes.of(sessionReceivedBytes));
    }

    private void appendLog(String line) {
        if (binding == null) return;
        binding.transferLog.setText(binding.transferLog.getText() + line + "\n");
    }

    private void runOnUi(Runnable r) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            if (binding != null) r.run();
        });
    }
}
