package com.example.noten;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

public class AvatarAdapter extends BaseAdapter {

    private Context context;
    private int[] avatars;

    public AvatarAdapter(Context context, int[] avatars) {
        this.context = context;
        this.avatars = avatars;
    }

    @Override
    public int getCount() {
        return avatars.length;
    }

    @Override
    public Integer getItem(int position) {
        return avatars[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ImageView imageView = new ImageView(context);
        imageView.setImageResource(avatars[position]);
        imageView.setLayoutParams(new ViewGroup.LayoutParams(150, 150)); // Adjust the size as needed
        return imageView;
    }
}
