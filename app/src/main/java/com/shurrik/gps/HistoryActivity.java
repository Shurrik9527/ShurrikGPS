package com.shurrik.gps;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

import com.shurrik.database.HistoryDBHelper;
import com.shurrik.database.HistoryPoint;
import com.shurrik.database.ItemTouchHelperCallBack;
import com.shurrik.database.RecycleViewAdapter;

import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {
    private SQLiteDatabase sqLiteDatabase;
    private List<HistoryPoint> historyPointList;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.history_list);

        initSqlite();
        //读取所有历史数据
        readAllHistoryPoints();

        RecyclerView recyclerView = findViewById(R.id.rv_history_point);


        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        recyclerView.setLayoutManager(linearLayoutManager);
        RecycleViewAdapter recycleViewAdapter = new RecycleViewAdapter(this, historyPointList);
        recyclerView.setAdapter(recycleViewAdapter);

        ItemTouchHelper.Callback callback = new ItemTouchHelperCallBack(recycleViewAdapter);
        ItemTouchHelper touchHelper = new ItemTouchHelper(callback);
        touchHelper.attachToRecyclerView(recyclerView);
    }

    /**
     * 位置历史数据库
     */
    private void initSqlite() {
        try {
            //数据库工具类
            HistoryDBHelper historyDBHelper = new HistoryDBHelper(getApplicationContext());
            sqLiteDatabase = historyDBHelper.getWritableDatabase();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 读取数据库中所有的历史位置信息
     */
    private void readAllHistoryPoints() {
        historyPointList = new ArrayList<>();
        try {
            Cursor cursor = sqLiteDatabase.query(HistoryDBHelper.TABLE_NAME, null, null, null, null, null, "TimeStamp DESC", null);
            while (cursor.moveToNext()) {
                HistoryPoint historyPoint = new HistoryPoint();
                historyPoint.setAddress(cursor.getString(0));
                historyPoint.setLongitude(cursor.getDouble(1));
                historyPoint.setLatitude(cursor.getDouble(2));
                historyPoint.setTimeStamp(cursor.getLong(3));
                historyPointList.add(historyPoint);
            }
            // 关闭光标
            cursor.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //sqlite 操作 保留七天的数据
    public long removePoint(double longitude, double latitude) {
        long result;
        try {
            result = sqLiteDatabase.delete(HistoryDBHelper.TABLE_NAME,
                    "BD09Longitude = ? and BD09Latitude = ?", new String[]{String.valueOf(longitude), String.valueOf(latitude)});
        } catch (Exception e) {
            result = -99;
            e.printStackTrace();
        }
        return result;
    }

    //sqlite 操作 删除记录
    private boolean deleteRecord(SQLiteDatabase sqLiteDatabase, String tableName, int ID) {
        boolean deleteRet = true;
        try {
            sqLiteDatabase.delete(tableName,
                    "ID = ?", new String[]{Integer.toString(ID)});
            Log.d("DDDDDD", "delete success");
        } catch (Exception e) {
            Log.e("SQLITE", "delete error");
            deleteRet = false;
            e.printStackTrace();
        }
        return deleteRet;
    }

    @Override
    protected void onDestroy() {
        //close db
        sqLiteDatabase.close();
        super.onDestroy();
    }


    public void DisplayToast(String str) {
        Toast toast = Toast.makeText(this, str, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.TOP, 0, 220);
        toast.show();
    }
}