package com.shurrik.database;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.shurrik.gps.HistoryActivity;
import com.shurrik.gps.R;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecycleViewAdapter extends RecyclerView.Adapter<RecycleViewAdapter.ViewHolder> implements ItemTouchHelperAdapter {

    private HistoryActivity historyActivity;
    private List<HistoryPoint> historyPointList;
    private SimpleDateFormat simpleDateFormat;
    private DecimalFormat decimalFormat;

    public RecycleViewAdapter(HistoryActivity historyActivity, List<HistoryPoint> historyPointList) {
        this.historyActivity = historyActivity;
        this.historyPointList = historyPointList;
        simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        decimalFormat = new DecimalFormat("#.000000");
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(historyActivity).inflate(R.layout.history_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, final int position) {
        HistoryPoint historyPoint = historyPointList.get(position);
        holder.addressTextView.setText(historyPoint.getAddress());
        holder.datetimeTextView.setText(simpleDateFormat.format(new Date(historyPoint.getTimeStamp())));
        holder.latLngTextView.setText(String.format("%s,%s", decimalFormat.format(historyPoint.getLongitude()), decimalFormat.format(historyPoint.getLatitude())));
    }

    @Override
    public int getItemCount() {
        return historyPointList.size();
    }

    @Override
    public void onItemMove(int fromPosition, int toPosition) {
        //交换位置
        Collections.swap(historyPointList, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
    }

    @Override
    public void onItemDelete(int position) {
        //移除数据
        HistoryPoint historyPoint = historyPointList.get(position);
        long result = historyActivity.removePoint(historyPoint.getLongitude(), historyPoint.getLatitude());
        if (result == 1) {
            historyPointList.remove(position);
            notifyItemRemoved(position);
        }
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        TextView addressTextView;
        TextView datetimeTextView;
        TextView latLngTextView;

        ViewHolder(View itemView) {
            super(itemView);
            addressTextView = itemView.findViewById(R.id.LoctionText);
            datetimeTextView = itemView.findViewById(R.id.TimeText);
            latLngTextView = itemView.findViewById(R.id.BDLatLngText);
        }
    }
}