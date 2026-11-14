package com.waydirect.ui;

// Android view inflation + widgets (the row UI lives in XML; we fill it here).
import android.view.LayoutInflater;   // turns R.layout.item_file XML into a View tree
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

// AndroidX list infrastructure — Fragment owns the RecyclerView; this class owns each row.
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.waydirect.R; // generated ids: R.layout.item_file, R.id.file_name, drawables, …

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Bridges {@link FileItem} data → {@code res/layout/item_file.xml} rows.
 *
 * <h3>How Java and XML connect</h3>
 * <ol>
 *   <li>{@link #onCreateViewHolder} inflates {@code item_file.xml} once per recycled row.</li>
 *   <li>{@link ViewHolder} caches {@code findViewById} handles (icon, name, details, checkbox).</li>
 *   <li>{@link #onBindViewHolder} copies {@code item.name} / {@code item.details} into those views
 *       and wires click listeners for that position.</li>
 * </ol>
 *
 * <h3>Callbacks (Fragment is the caller)</h3>
 * Fragments set {@link OnItemClickListener}. On tap we either:
 * <ul>
 *   <li>selection mode → toggle path in {@link #selectedPaths} and notify {@link OnSelectionChangedListener}, or</li>
 *   <li>normal mode → {@code listener.onItemClick(item)} so Files/Transfer/Cleaner decide open vs browse.</li>
 * </ul>
 * Long-press always goes to {@code onItemLongClick} (rename/delete menus live in the Fragment).
 *
 * <p>Uses public fields on {@link FileItem} — no getters. See docs/APP_MAP.md.
 */
public class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {

    /** Fragment implements this — we only forward the {@link FileItem} that was tapped. */
    public interface OnItemClickListener {
        void onItemClick(FileItem item);
        void onItemLongClick(FileItem item);
    }

    /** Optional: Transfer multi-select uses this to enable "Send (N)". */
    public interface OnSelectionChangedListener {
        void onSelectionChanged(int count);
    }

    private final List<FileItem> items = new ArrayList<>();
    private final Set<String> selectedPaths = new LinkedHashSet<>();
    private boolean selectionMode = false;
    private OnItemClickListener listener;
    private OnSelectionChangedListener selectionListener;

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.selectionListener = listener;
    }

    /** Turn checkboxes on/off. Off clears the selection set. */
    public void setSelectionMode(boolean on) {
        selectionMode = on;
        if (!on) selectedPaths.clear();
        notifyDataSetChanged(); // rebinds every row so checkbox visibility updates
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public void toggleSelection(String path) {
        if (path == null) return;
        if (!selectedPaths.remove(path)) selectedPaths.add(path);
        notifyDataSetChanged();
        if (selectionListener != null) selectionListener.onSelectionChanged(selectedPaths.size());
    }

    public List<String> getSelectedPaths() {
        return new ArrayList<>(selectedPaths);
    }

    /**
 * Replace the list shown on screen.
 * Runtime: Fragment parses native JSON → List<FileItem> → here → rows redraw.
 */
    public void submitList(List<FileItem> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // XML → View: item_file.xml becomes a concrete row hierarchy
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FileItem item = items.get(position);
        // Data → widgets (public fields on FileItem)
        holder.name.setText(item.name);
        holder.details.setText(item.details);
        holder.icon.setImageResource(iconFor(item));
        holder.selectBox.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        holder.selectBox.setChecked(selectedPaths.contains(item.path));

        // Click route: selection mode stays in the adapter; else call Fragment
        holder.itemView.setOnClickListener(v -> {
            if (selectionMode) toggleSelection(item.path);
            else if (listener != null) listener.onItemClick(item);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onItemLongClick(item);
            return true; // event consumed
        });
    }

    /** Pick drawable from extension — display only; open routing is {@link FileOpenHelper}. */
    private static int iconFor(FileItem item) {
        if (item.directory) return R.drawable.ic_folder;
        String name = item.name.toLowerCase(Locale.US);
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1) : "";
        return switch (ext) {
            case "png", "jpg", "jpeg", "gif", "webp", "bmp", "heic" -> R.drawable.ic_file_image;
            case "mp4", "mkv", "avi", "mov", "webm", "3gp" -> R.drawable.ic_file_video;
            case "mp3", "wav", "flac", "aac", "ogg", "m4a" -> R.drawable.ic_file_audio;
            case "pdf", "doc", "docx", "txt", "md", "rtf", "odt", "json", "html", "htm", "xml", "csv" -> R.drawable.ic_file_doc;
            default -> R.drawable.ic_file;
        };
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
 * Holds the inflated views for one recycled row.
 * findViewById runs once per ViewHolder; onBindViewHolder only sets text/icons.
 */
    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView details;
        final CheckBox selectBox;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.file_icon);
            name = itemView.findViewById(R.id.file_name);
            details = itemView.findViewById(R.id.file_details);
            selectBox = itemView.findViewById(R.id.row_checkbox);
        }
    }
}
