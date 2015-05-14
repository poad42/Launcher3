/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.content.res.Resources;
import android.support.v7.widget.RecyclerView.Adapter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.LinearLayout;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DynamicGrid;
import com.android.launcher3.IconCache;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.R;
import com.android.launcher3.WidgetPreviewLoader;
import java.util.List;

/**
 * List view adapter for the widget tray.
 *
 * <p>Memory vs. Performance:
 * The less number of types of views are inserted into a {@link RecyclerView}, the more recycling
 * happens and less memory is consumed. {@link #getItemViewType} was not overridden as there is
 * only a single type of view.
 */
public class WidgetsListAdapter extends Adapter<WidgetsRowViewHolder> {

    private static final String TAG = "WidgetsListAdapter";
    private static final boolean DEBUG = true;

    private Context mContext;
    private Launcher mLauncher;
    private LayoutInflater mLayoutInflater;
    private IconCache mIconCache;

    private WidgetsModel mWidgetsModel;
    private WidgetPreviewLoader mWidgetPreviewLoader;

    private View.OnClickListener mIconClickListener;
    private View.OnLongClickListener mIconLongClickListener;

    private static final int PRESET_INDENT_SIZE_TABLET = 56;
    private int mIndent = 0;

    public WidgetsListAdapter(Context context,
            View.OnClickListener iconClickListener,
            View.OnLongClickListener iconLongClickListener,
            Launcher launcher) {
        mLayoutInflater = LayoutInflater.from(context);
        mContext = context;

        mIconClickListener = iconClickListener;
        mIconLongClickListener = iconLongClickListener;

        mLauncher = launcher;
        mIconCache = LauncherAppState.getInstance().getIconCache();

        setContainerHeight();
    }

    public void setWidgetsModel(WidgetsModel w) {
        mWidgetsModel = w;
    }

    @Override
    public int getItemCount() {
        return mWidgetsModel.getPackageSize();
    }

    @Override
    public void onBindViewHolder(WidgetsRowViewHolder holder, int pos) {
        List<Object> infoList = mWidgetsModel.getSortedWidgets(pos);

        ViewGroup row = ((ViewGroup) holder.getContent().findViewById(R.id.widgets_cell_list));
        if (DEBUG) {
            Log.d(TAG, String.format(
                    "onBindViewHolder [pos=%d, widget#=%d, row.getChildCount=%d]",
                    pos, infoList.size(), row.getChildCount()));
        }

        // Add more views.
        // if there are too many, hide them.
        int diff = infoList.size() - row.getChildCount();

        if (diff > 0) {
            for (int i = 0; i < diff; i++) {
                WidgetCell widget = new WidgetCell(mContext);
                widget = (WidgetCell) mLayoutInflater.inflate(
                        R.layout.widget_cell, row, false);

                // set up touch.
                widget.setOnClickListener(mIconClickListener);
                widget.setOnLongClickListener(mIconLongClickListener);
                LayoutParams lp = widget.getLayoutParams();
                lp.height = widget.cellSize;
                lp.width = widget.cellSize;
                widget.setLayoutParams(lp);

                row.addView(widget);
            }
        } else if (diff < 0) {
            for (int i=infoList.size() ; i < row.getChildCount(); i++) {
                row.getChildAt(i).setVisibility(View.GONE);
            }
        }

        // Bind the views in the application info section.
        PackageItemInfo infoOut = mWidgetsModel.getPackageItemInfo(pos);
        BubbleTextView tv = ((BubbleTextView) holder.getContent().findViewById(R.id.section));
        tv.applyFromPackageItemInfo(infoOut);

        // Bind the view in the widget horizontal tray region.
        for (int i=0; i < infoList.size(); i++) {
            WidgetCell widget = (WidgetCell) row.getChildAt(i);
            if (getWidgetPreviewLoader() == null) {
                return;
            }
            if (infoList.get(i) instanceof LauncherAppWidgetProviderInfo) {
                LauncherAppWidgetProviderInfo info = (LauncherAppWidgetProviderInfo) infoList.get(i);
                PendingAddWidgetInfo pawi = new PendingAddWidgetInfo(info, null);
                widget.setTag(pawi);
                widget.applyFromAppWidgetProviderInfo(info, -1, mWidgetPreviewLoader);
            } else if (infoList.get(i) instanceof ResolveInfo) {
                ResolveInfo info = (ResolveInfo) infoList.get(i);
                PendingAddShortcutInfo pasi = new PendingAddShortcutInfo(info.activityInfo);
                widget.setTag(pasi);
                widget.applyFromResolveInfo(mLauncher.getPackageManager(), info, mWidgetPreviewLoader);
            }
            widget.setVisibility(View.VISIBLE);
            widget.ensurePreview();
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    public WidgetsRowViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (DEBUG) {
            Log.v(TAG, "\nonCreateViewHolder");
        }

        ViewGroup container = (ViewGroup) mLayoutInflater.inflate(
                R.layout.widgets_list_row_view, parent, false);
        LinearLayout cellList = (LinearLayout) container.findViewById(R.id.widgets_cell_list);
        MarginLayoutParams lp = (MarginLayoutParams) cellList.getLayoutParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            lp.setMarginStart(mIndent);
        } else {
            lp.leftMargin = mIndent;
        }
        cellList.setLayoutParams(lp);
        return new WidgetsRowViewHolder(container);
    }

    @Override
    public void onViewRecycled(WidgetsRowViewHolder holder) {
        ViewGroup row = ((ViewGroup) holder.getContent().findViewById(R.id.widgets_cell_list));

        for (int i = 0; i < row.getChildCount(); i++) {
            WidgetCell widget = (WidgetCell) row.getChildAt(i);
            widget.clear();
        }
    }

    @Override
    public long getItemId(int pos) {
        return pos;
    }

    private WidgetPreviewLoader getWidgetPreviewLoader() {
        if (mWidgetPreviewLoader == null) {
            mWidgetPreviewLoader = LauncherAppState.getInstance().getWidgetCache();
        }
        return mWidgetPreviewLoader;
    }

    private void setContainerHeight() {
        Resources r = mContext.getResources();
        DeviceProfile profile = LauncherAppState.getInstance().getDynamicGrid().getDeviceProfile();
        if (profile.isLargeTablet || profile.isTablet) {
            mIndent = DynamicGrid.pxFromDp(PRESET_INDENT_SIZE_TABLET, r.getDisplayMetrics());
        }
    }
}
