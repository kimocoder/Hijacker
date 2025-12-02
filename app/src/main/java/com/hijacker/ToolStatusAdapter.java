package com.hijacker;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ToolStatusAdapter extends RecyclerView.Adapter<ToolStatusAdapter.ViewHolder> {
    private final List<ToolStatus> items = new ArrayList<>();

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        AppCompatTextView label;
        AppCompatTextView chip;
        ViewHolder(View v){
            super(v);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
            chip = v.findViewById(R.id.chip);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tool_status, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ToolStatus ts = items.get(position);
        holder.icon.setImageResource(ts.iconResId);
        holder.label.setText(ts.label);
        holder.chip.setText(ts.statusText);
        // set simple background tint based on status text (testing/done/failed)
        if(ts.statusText!=null){
            String s = ts.statusText.toLowerCase();
            if(s.contains("done")) holder.chip.setBackgroundColor(holder.chip.getContext().getColor(android.R.color.holo_green_dark));
            else if(s.contains("fail")) holder.chip.setBackgroundColor(holder.chip.getContext().getColor(android.R.color.holo_red_dark));
            else holder.chip.setBackgroundColor(holder.chip.getContext().getColor(android.R.color.holo_orange_dark));
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    public void setItems(List<ToolStatus> list){
        items.clear();
        if(list!=null) items.addAll(list);
        notifyDataSetChanged();
    }

    public void updateStatusById(String id, int iconRes, String statusText){
        for(int i=0;i<items.size();i++){
            if(items.get(i).id.equals(id)){
                ToolStatus t = items.get(i);
                t.iconResId = iconRes;
                t.statusText = statusText;
                notifyItemChanged(i);
                return;
            }
        }
    }
}
