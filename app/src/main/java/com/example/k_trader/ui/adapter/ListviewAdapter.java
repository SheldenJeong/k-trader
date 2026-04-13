package com.example.k_trader.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.k_trader.R;
import java.util.ArrayList;

/**
 * Created by 김무창 on 2017-12-20.
 */

public class ListviewAdapter extends BaseAdapter {
    private LayoutInflater inflater;
    private final ArrayList<Listviewitem> data;
    private int layout;

    public ListviewAdapter(Context context, int layout, ArrayList<Listviewitem> data){
        this.inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        // 외부 리스트 변경으로 인한 동시성 문제를 피하기 위해 스냅샷을 사용한다.
        this.data = (data != null) ? new ArrayList<>(data) : new ArrayList<>();
        this.layout = layout;
    }

    @Override
    public int getCount(){return data.size();}

    @Override
    public String getItem(int position){
        if (position < 0 || position >= data.size()) {
            return "";
        }
        return data.get(position).getName();
    }

    @Override
    public long getItemId(int position){return position;}

    @Override
    public View getView(int position, View convertView, ViewGroup parent){
        if(convertView == null){
            convertView = inflater.inflate(layout,parent,false);
        }
        if (position < 0 || position >= data.size()) {
            TextView name = (TextView)convertView.findViewById(R.id.textview);
            name.setText("");
            convertView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            return convertView;
        }

        Listviewitem listviewitem = data.get(position);
//        ImageView icon = (ImageView)convertView.findViewById(R.id.imageview);
//        icon.setImageResource(listviewitem.getIcon());
        TextView name = (TextView)convertView.findViewById(R.id.textview);
        name.setText(listviewitem.getName());

        convertView.setBackgroundColor(listviewitem.getColor());

        return convertView;
    }
}
