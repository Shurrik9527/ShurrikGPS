package com.shurrik.gps;

import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.shurrik.service.HistoryDBHelper;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HistoryActivity extends AppCompatActivity {
    private ListView listView;
    private SimpleAdapter simAdapt;
    private TextView noRecordText;
//    private List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();

    private HistoryDBHelper historyDBHelper;
    private SQLiteDatabase sqLiteDatabase;

    private String bd09Longitude = "104.07018449827267";
    private String bd09Latitude = "30.547743718042415";
    private String wgs84Longitude = "104.06121778639009";
    private String wgs84Latitude = "30.544111926165282";

    List<Map<String, String>> allHistoryRecord;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.history_list);

        Log.d("HistoryActivity", "SQLiteDatabase init");

        //sqlite
        try {
            historyDBHelper = new HistoryDBHelper(getApplicationContext());
            sqLiteDatabase = historyDBHelper.getWritableDatabase();
        } catch (Exception e) {
            Log.e("HistoryActivity", "SQLiteDatabase init error");
            e.printStackTrace();
        }

        listView = (ListView) findViewById(R.id.list_view);
        noRecordText = (TextView) findViewById(R.id.no_record_textview);

//        if (recordArchive(sqLiteDatabase, HistoryDBHelper.TABLE_NAME)) {
//            Log.d("HistoryActivity", "archive success");
//        }

        initListView();


//        listView = (ListView) findViewById(R.id.list_view);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Map<String, String> recordMap = allHistoryRecord.get(i);
                finish();
            }
        });
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, final View view, int position, long id) {
                new AlertDialog.Builder(HistoryActivity.this)
                        .setTitle("Warning")//这里是表头的内容
                        .setMessage("确定要删除该项历史记录吗?")//这里是中间显示的具体信息
                        .setPositiveButton("确定",
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
//                                        String locID = (String) ((TextView) view.findViewById(R.id.LocationID)).getText();
//
//                                        boolean deleteRet = deleteRecord(sqLiteDatabase, HistoryDBHelper.TABLE_NAME, Integer.valueOf(locID));
//                                        if (deleteRet) {
//                                            DisplayToast("删除成功!");
//                                            initListView();
//                                        }
                                    }
                                })
                        .setNegativeButton("取消",
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                    }
                                })
                        .show();
                return true;
            }
        });


//        if (recordArchive(sqLiteDatabase,HistoryDBHelper.TABLE_NAME)){
//            Log.d("HistoryActivity","archive success");
//        }

//        sqLiteDatabase.close();

    }

    private void initListView() {
        allHistoryRecord = fetchAllRecord(sqLiteDatabase, HistoryDBHelper.TABLE_NAME);
        if (allHistoryRecord.size() == 0) {
            listView.setVisibility(View.GONE);
            noRecordText.setVisibility(View.VISIBLE);
        } else {
            try {
                simAdapt = new SimpleAdapter(
                        this,
                        allHistoryRecord,
                        R.layout.history_item,
                        new String[]{"key_location", "key_time", "kdy_bdlatlng"},// 与下面数组元素要一一对应
                        new int[]{R.id.LoctionText, R.id.TimeText, R.id.BDLatLngText});
                listView.setAdapter(simAdapt);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    //sqlite 操作 查询所有记录
    private List<Map<String, String>> fetchAllRecord(SQLiteDatabase sqLiteDatabase, String tableName) {
        List<Map<String, String>> data = new ArrayList<>();
        try {
            Cursor cursor = sqLiteDatabase.query(tableName, null, null, null, null, null, "TimeStamp DESC", null);
            DecimalFormat decimalFormat = new DecimalFormat("#.000000");
            while (cursor.moveToNext()) {
                Map<String, String> item = new HashMap<>();
                String address = cursor.getString(0);
                double longitude = cursor.getDouble(1);
                double latitude = cursor.getDouble(2);
                long timeStamp = cursor.getInt(3);

                item.put("key_location", address);
                item.put("key_time", timeStamp2Date(Long.toString(timeStamp), null));
                item.put("kdy_bdlatlng", decimalFormat.format(longitude) + "," + decimalFormat.format(latitude));
                data.add(item);
            }
            // 关闭光标
            cursor.close();
        } catch (Exception e) {
            Log.e("SQLITE", "query error");
            data.clear();
            e.printStackTrace();
        }
        return data;
    }

    public static String timeStamp2Date(String seconds, String format) {
        if (seconds == null || seconds.isEmpty() || seconds.equals("null")) {
            return "";
        }
        if (format == null || format.isEmpty()) format = "yyyy-MM-dd HH:mm:ss";
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        return sdf.format(new Date(Long.valueOf(seconds + "000")));
    }

    //sqlite 操作 保留七天的数据
    private boolean recordArchive(SQLiteDatabase sqLiteDatabase, String tableName) {
        boolean archiveRet = true;
        final long weekSecond = 7 * 24 * 60 * 60;
        try {
            sqLiteDatabase.delete(tableName,
                    "TimeStamp < ?", new String[]{Long.toString(System.currentTimeMillis() / 1000 - weekSecond)});
        } catch (Exception e) {
            Log.e("SQLITE", "archive error");
            archiveRet = false;
            e.printStackTrace();
        }
        Log.d("SQLITE", "archive success");
        return archiveRet;
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