/*
 * Copyright (C) 2015 TeamEos project
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
 *
 * Adapter for displaying custom actions in a list
 */

package com.android.internal.navigation;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.utils.du.DUActionUtils;

public class EditActionItemAdapter extends BaseAdapter {
    private LayoutInflater mInflater;
    private Context mContext;
    private List<EditItem> mEditActions = new ArrayList<EditItem>();

    public EditActionItemAdapter(Context context) {
        mContext = context;
        mInflater = LayoutInflater.from(context);
        loadItems();
    }

    private void loadItems() {
        mEditActions.clear();
        EditItem item = new EditItem();
        item.name = "Edit";
        item.tag = "edit";
        item.icon = DUActionUtils.getDrawable(mContext, "ic_menu_edit", DUActionUtils.PACKAGE_ANDROID);
        mEditActions.add(item);
        item = new EditItem();
        item.name = "Add";
        item.tag = "add";
        item.icon = DUActionUtils.getDrawable(mContext, "ic_menu_add", DUActionUtils.PACKAGE_ANDROID);
        mEditActions.add(item);
        item = new EditItem();
        item.name = "Remove";
        item.tag = "remove";
        item.icon = DUActionUtils.getDrawable(mContext, "ic_menu_delete", DUActionUtils.PACKAGE_ANDROID);
        mEditActions.add(item);                
    }

    @Override
    public int getCount() {
        return mEditActions.size();
    }

    @Override
    public EditItem getItem(int position) {
        return mEditActions.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView != null) {
            holder = (ViewHolder) convertView.getTag();
        } else {
            int layoutId = DUActionUtils.getIdentifier(mContext, "action_edit_item", "layout", DUActionUtils.PACKAGE_SYSTEMUI);
            convertView = mInflater.inflate(layoutId, null, false);
            holder = new ViewHolder();
            convertView.setTag(holder);
            
            holder.icon = (ImageView) convertView.findViewById(DUActionUtils.getId(mContext, "item_icon", DUActionUtils.PACKAGE_SYSTEMUI));
            holder.title = (TextView) convertView.findViewById(DUActionUtils.getId(mContext, "item_title", DUActionUtils.PACKAGE_SYSTEMUI));
        }

        EditItem item = getItem(position);
        holder.icon.setImageDrawable(item.icon);
        holder.title.setText(item.name);
        return convertView;

    }
    
    public static class EditItem {
        String name;
        String tag;
        Drawable icon;
    }

    private static class ViewHolder {
        TextView title;
        ImageView icon;
    }
}
