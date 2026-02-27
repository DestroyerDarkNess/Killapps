package com.killapps;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.AppViewHolder> {
    private static final String TAG = "AppListAdapter";

    private List<AppItem> appList = new ArrayList<>();
    private List<AppItem> appListFiltered = new ArrayList<>();
    private PackageManager packageManager;
    private SharedPreferences prefs;

    // Filter states
    public static final int FILTER_ALL = 0;
    public static final int FILTER_USER = 1;
    public static final int FILTER_SYSTEM = 2;
    private int currentFilter = FILTER_USER;
    private Context context;

    public AppListAdapter(Context context, List<ApplicationInfo> installedApps) {
        this.context = context;
        this.packageManager = context.getPackageManager();
        this.prefs = context.getSharedPreferences("KillAppsPrefs", Context.MODE_PRIVATE);

        for (ApplicationInfo info : installedApps) {
            if (info == null || info.packageName == null) continue;
            // Skip ourselves
            if (info.packageName.equals(context.getPackageName())) continue;

            try {
                AppItem item = new AppItem();
                item.packageName = info.packageName;
                CharSequence label = packageManager.getApplicationLabel(info);
                item.label = label != null ? label.toString() : info.packageName;
                item.isSystem = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;

                // Restore saved state (default to true if not found)
                item.selected = prefs.getBoolean(item.packageName, true);
                appList.add(item);
            } catch (Exception e) {
                Log.w(TAG, "Skipping app due to invalid package metadata: " + info.packageName, e);
            }
        }

        applyFilter();
    }

    public void setFilter(int filterType) {
        this.currentFilter = filterType;
        applyFilter();
    }

    private void applyFilter() {
        appListFiltered.clear();
        for (AppItem item : appList) {
            if (currentFilter == FILTER_ALL) {
                appListFiltered.add(item);
            } else if (currentFilter == FILTER_USER && !item.isSystem) {
                appListFiltered.add(item);
            } else if (currentFilter == FILTER_SYSTEM && item.isSystem) {
                appListFiltered.add(item);
            }
        }
        notifyDataSetChanged();
    }

    public List<String> getSelectedPackages() {
        List<String> selected = new ArrayList<>();
        // Only return selected items that are currently visible in the active filter
        for (AppItem item : appListFiltered) {
            if (item.selected) {
                selected.add(item.packageName);
            }
        }
        return selected;
    }

    public void toggleAllInView(boolean check) {
        for (AppItem item : appListFiltered) {
            item.selected = check;
            saveState(item);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        AppItem item = appListFiltered.get(position);
        holder.tvAppName.setText(item.label);
        holder.tvAppPackage.setText(item.packageName);
        holder.cbSelect.setChecked(item.selected);

        // Load Icon
        try {
            Drawable icon = packageManager.getApplicationIcon(item.packageName);
            holder.ivIcon.setImageDrawable(icon);
        } catch (Exception e) {
            Log.w(TAG, "Fallback icon for package: " + item.packageName, e);
            holder.ivIcon.setImageResource(android.R.drawable.sym_def_app_icon);
        }

        // Handle clicks on the whole row
        holder.itemView.setOnClickListener(v -> {
            item.selected = !item.selected;
            holder.cbSelect.setChecked(item.selected);
            saveState(item);
            if (context instanceof MainActivity) {
                ((MainActivity) context).updateAppCount();
            }
        });

        // Handle direct checkbox clicks
        holder.cbSelect.setOnClickListener(v -> {
            item.selected = holder.cbSelect.isChecked();
            saveState(item);
            if (context instanceof MainActivity) {
                ((MainActivity) context).updateAppCount();
            }
        });
    }

    private void saveState(AppItem item) {
        prefs.edit().putBoolean(item.packageName, item.selected).apply();
    }

    @Override
    public int getItemCount() {
        return appListFiltered.size();
    }

    public static class AppItem {
        public String packageName;
        public String label;
        public boolean isSystem;
        public boolean selected;
    }

    public static class AppViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvAppName;
        TextView tvAppPackage;
        CheckBox cbSelect;

        public AppViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivAppIcon);
            tvAppName = itemView.findViewById(R.id.tvAppName);
            tvAppPackage = itemView.findViewById(R.id.tvAppPackage);
            cbSelect = itemView.findViewById(R.id.cbAppSelect);
        }
    }
}
