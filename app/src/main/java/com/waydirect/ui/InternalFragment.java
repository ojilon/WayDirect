package com.waydirect.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.waydirect.bridge.NativeEngine;
import com.waydirect.bridge.PathRegistry;
import com.waydirect.databinding.FragmentInternalBinding;
import com.waydirect.utils.FormatSizes;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Read-only browser for the private app home (index, cache, logs, manifest).
 *
 * <h3>Java ↔ XML</h3>
 * {@code fragment_internal.xml} → {@link FragmentInternalBinding}.
 * Rows: {@link FileAdapter} + {@code item_file.xml}.
 *
 * <h3>Data route</h3>
 * Listing uses plain {@link File#listFiles()} under {@link PathRegistry#root} —
 * no native list action. Opening a file goes to {@link DocumentActivity}.
 *
 * <p>{@link FileItem} uses public fields ({@code item.path}), not getters.
 */
public class InternalFragment extends Fragment {

    private FragmentInternalBinding binding;
    private FileAdapter adapter;
    private File root;
    private File current;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentInternalBinding.inflate(inflater, container, false);
        root = new File(PathRegistry.root(requireContext()));
        current = root;

        adapter = new FileAdapter();
        binding.fileList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.fileList.setAdapter(adapter);
        adapter.setOnItemClickListener(new FileAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(FileItem item) {
                File f = new File(item.path);
                if (f.isDirectory()) {
                    current = f;
                    loadDirectory();
                } else {
                    DocumentActivity.open(requireContext(), item.path);
                }
            }

            @Override
            public void onItemLongClick(FileItem item) {
                Toast.makeText(getContext(), "Read-only — no actions here", Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnUp.setOnClickListener(v -> {
            File parent = current.getParentFile();
            if (parent != null && isUnderRoot(parent)) {
                current = parent;
                loadDirectory();
            }
        });

        NativeEngine.initAppPathsAsync(requireContext(), result -> {
            if (binding == null || getContext() == null) return;
            root = new File(PathRegistry.root(requireContext()));
            if (!isUnderRoot(current)) current = root;
            loadDirectory();
        });
        return binding.getRoot();
    }

    private boolean isUnderRoot(File f) {
        try {
            String r = root.getCanonicalPath();
            String c = f.getCanonicalPath();
            return c.equals(r) || c.startsWith(r + File.separator);
        } catch (Exception e) {
            return false;
        }
    }

    private void loadDirectory() {
        if (binding == null) return;
        binding.currentPath.setText(current.getAbsolutePath());
        binding.btnUp.setEnabled(!current.equals(root));

        List<FileItem> rows = new ArrayList<>();
        File[] children = current.listFiles();
        if (children != null) {
            Arrays.sort(children, (a, b) -> {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
            for (File c : children) {
                boolean dir = c.isDirectory();
                rows.add(new FileItem(c.getName(), c.getAbsolutePath(),
                        dir ? "Folder" : FormatSizes.of(c.length()), dir, dir ? 0 : c.length()));
            }
        }
        adapter.submitList(rows);
        binding.emptyState.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        binding.fileList.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
