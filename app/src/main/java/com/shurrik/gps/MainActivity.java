package com.shurrik.gps;

import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.RequiresApi;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.Toast;
import android.widget.ZoomControls;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapPoi;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.model.LatLng;
import com.shurrik.service.HistoryDBHelper;
import com.shurrik.service.MockGpsService;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private final String TAG = MainActivity.class.getName();

    //位置欺骗相关
    public List<LatLng> historyPoints;

    private SQLiteDatabase locHistoryDB;

    // 定位相关
    private LocationClient locationClient = null;
    private int mCurrentDirection = 0;
    private MarkerOptions markerOptions = null;
    private String address = null;
    private LatLng point = null;

    private MapView mMapView;
    private BaiduMap mBaiduMap = null;

    //Button requestLocButton;
    boolean isFirstLoc = true; // 是否首次定位
    private MyLocationData locData;

    /**
     * 悬浮窗是否开启
     */
    private boolean isFloatWindowStart = false;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Toolbar
        initView();

        //初始化数据库
        initSqlite();

        //百度地图
        initBaiduMap();

        //地图加载成功后读取所有历史记录并标记
        readAllHistoryPoints();
    }

    private void initView() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //Navigation
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
    }

    /**
     * 位置历史数据库
     */
    private void initSqlite() {
        try {
            HistoryDBHelper historyDBHelper = new HistoryDBHelper(getApplicationContext());
            locHistoryDB = historyDBHelper.getWritableDatabase();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * sqlite 操作 插表HistoryLocation
     */
    private boolean insertHistoryLocTable(SQLiteDatabase sqLiteDatabase, String tableName, ContentValues contentValues) {
        boolean isSuccess = true;
        try {
            sqLiteDatabase.insert(tableName, null, contentValues);
        } catch (Exception e) {
            isSuccess = false;
            e.printStackTrace();
        }
        return isSuccess;
    }

    /**
     * 读取数据库中所有的历史位置信息
     */
    private void readAllHistoryPoints() {
        try {
            Cursor cursor = locHistoryDB.query(true, HistoryDBHelper.TABLE_NAME, null,
                    "ID > ?", new String[]{"0"},
                    null, null, "TimeStamp DESC", null);
            historyPoints = new ArrayList<>();
            while (cursor.moveToNext()) {
                String address = cursor.getString(1);
                double longitude = cursor.getDouble(2);
                double latitude = cursor.getDouble(3);
                long timeStamp = cursor.getInt(4);
                Log.d(TAG, address + " " + longitude + "," + latitude + " " + timeStamp);
                LatLng historyPoint = new LatLng(latitude, longitude);
                addPointToHistoryPoints(historyPoint);
            }
            // 关闭光标
            cursor.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 更新当前位置信息
     */
    private void setPoint(LatLng point) {
        if (point != null) {
            this.point = point;
        }
    }

    /**
     * 重置地图
     */
    private void resetMap() {
        //灵隐支路7鼓
        double dialog_lng_double = 120.114835;
        double dialog_lat_double = 30.250508;

        LatLng currentPt = new LatLng(dialog_lat_double, dialog_lng_double);
        MapStatusUpdate mapstatusupdate = MapStatusUpdateFactory.newLatLng(currentPt);
        mBaiduMap.setMapStatus(mapstatusupdate);
        //更新当前位置信息
        setPoint(currentPt);
    }


    /**
     * 加载百度地图
     */
    private void initBaiduMap() {
        // 地图初始化
        mMapView = findViewById(R.id.bmapView);
        // 不显示地图缩放控件（按钮控制栏）
        mMapView.showZoomControls(false);

        // 隐藏百度的LOGO
        View child = mMapView.getChildAt(1);
        if ((child instanceof ImageView || child instanceof ZoomControls)) {
            child.setVisibility(View.INVISIBLE);
        }

        mBaiduMap = mMapView.getMap();

        // 开启定位图层
        mBaiduMap.setMyLocationEnabled(true);

        mBaiduMap.setOnMapClickListener(new BaiduMap.OnMapClickListener() {
            /**
             * 单击地图
             */
            public void onMapClick(LatLng point) {
                startMockGpsService(point);
            }

            /**
             * 单击地图中的POI点
             */
            public boolean onMapPoiClick(MapPoi poi) {
                startMockGpsService(poi.getPosition());
                return false;
            }
        });

        //历史记录标记
        BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromResource(R.drawable.icon_gcoding);
        markerOptions = new MarkerOptions().icon(bitmapDescriptor);

        //地图类型切换
        setGroupListener();

        //保存按钮
        setFabListener();

        //如果GPS定位开启，则打开定位图层
        initLocationClient();
    }

    //set group button listener
    private void setGroupListener() {
        RadioGroup radioGroup = this.findViewById(R.id.rg_map_style);
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.normal) {
                    mBaiduMap.setMapType(BaiduMap.MAP_TYPE_NORMAL);
                }
                if (checkedId == R.id.statellite) {
                    mBaiduMap.setMapType(BaiduMap.MAP_TYPE_SATELLITE);
                }
            }
        });
    }

    //set float action button listener
    private void setFabListener() {
        //应用内悬浮按钮
        FloatingActionButton floatingActionButton = findViewById(R.id.fab_save);
        floatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //保存当前位置信息
                String result = savePoint(point, address);
                Snackbar.make(view, result, Snackbar.LENGTH_SHORT).setAction("Action", null).show();
            }
        });
    }

    /**
     * 开启百度地图的定位图层
     */
    private void initLocationClient() {
        // 定位初始化
        locationClient = new LocationClient(this);
        locationClient.registerLocationListener(new BDAbstractLocationListener() {
            @Override
            public void onReceiveLocation(BDLocation bdLocation) {
                // map view 销毁后不在处理新接收的位置
                if (bdLocation == null || bdLocation.getLocType() == BDLocation.TypeServerError) {
                    return;
                }
                //当前定位的地址信息
                address = bdLocation.getAddrStr();
                String msg = address + " " + bdLocation.getLongitude() + "," + bdLocation.getLatitude();
                Log.d(TAG, msg);


                locData = new MyLocationData.Builder()
                        .accuracy(bdLocation.getRadius())
                        // 此处设置开发者获取到的方向信息，顺时针0-360
                        .direction(mCurrentDirection).latitude(bdLocation.getLatitude())
                        .longitude(bdLocation.getLongitude()).build();
                mBaiduMap.setMyLocationData(locData);
                if (isFirstLoc) {
                    isFirstLoc = false;
                    LatLng ll = new LatLng(bdLocation.getLatitude(),
                            bdLocation.getLongitude());
                    MapStatus.Builder builder = new MapStatus.Builder();
                    builder.target(ll).zoom(18.0f);
                    mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));
                }
            }
        });
        //locationClient.registerLocationListener(myListener);
        LocationClientOption option = new LocationClientOption();
        option.setIsNeedAddress(true);
        option.setOpenGps(true); // 打开gps
        option.setCoorType("bd09ll"); // 设置坐标类型
        option.setScanSpan(1000);
        locationClient.setLocOption(option);
        locationClient.start();
    }


    /**
     * 储存位置信息到数据库
     *
     * @param point
     */
    private String savePoint(LatLng point, String address) {
        if (point == null || address == null) {
            return "获取位置信息失败，请重试！";
        }
        //插表参数
        ContentValues contentValues = new ContentValues();
        contentValues.put("Location", address);
        contentValues.put("BD09Longitude", point.longitude);
        contentValues.put("BD09Latitude", point.latitude);
        contentValues.put("TimeStamp", System.currentTimeMillis() / 1000);
        if (insertHistoryLocTable(locHistoryDB, HistoryDBHelper.TABLE_NAME, contentValues)) {
            addPointToHistoryPoints(point);
            return "保存[" + address + "]成功！";
        } else {
            return "保存位置失败！";
        }
    }

    /**
     * 添加坐标到历史坐标列表并更新地图标记
     *
     * @param point 待添加坐标
     */
    private void addPointToHistoryPoints(LatLng point) {
        historyPoints.add(point);
        markerOptions.position(point);
        mBaiduMap.addOverlay(markerOptions);
    }

    /**
     * 开启位置模拟
     *
     * @param point
     */
    private void startMockGpsService(LatLng point) {
        //更新当前位置信息
        setPoint(point);
        //start mock location service
        Intent mockLocServiceIntent = new Intent(MainActivity.this, MockGpsService.class);
        mockLocServiceIntent.putExtra("BD09Longitude", point.longitude);
        mockLocServiceIntent.putExtra("BD09Latitude", point.latitude);
        //insert end
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(mockLocServiceIntent);
        } else {
            startService(mockLocServiceIntent);
        }

        //这里开启悬浮窗
        if (!isFloatWindowStart) {

            //悬浮窗
            FloatWindow floatWindow = new FloatWindow(MainActivity.this, new FloatWindow.OnFloatWindowClickListener() {
                int index = 1;

                @Override
                public void onFloatWindowClick() {
                    if (historyPoints.size() == 0) {
                        DisplayToast("No History Location!!!");
                        return;
                    }


                    if (index >= historyPoints.size()) {
                        index = 0;
                    }
                    startMockGpsService(historyPoints.get(index));
                    index++;
                }
            });
            floatWindow.showFloatWindow();
            isFloatWindowStart = true;
        }
    }

    @Override
    protected void onPause() {
        mMapView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        mMapView.onResume();
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        // 退出时销毁定位
        locationClient.stop();
        // 关闭定位图层
        mBaiduMap.setMyLocationEnabled(false);
        mMapView.onDestroy();
        mMapView = null;
        //close db
        locHistoryDB.close();
        super.onDestroy();
    }

    public void DisplayToast(String str) {
        Toast toast = Toast.makeText(MainActivity.this, str, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.TOP, 0, 360);
        toast.show();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_setting) {
            //历史记录
            Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_input) {
            //经纬度定位
            showLatlngDialog();
        } else if (id == R.id.action_resetMap) {
            //重置地图
            resetMap();
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();
        if (id == R.id.nav_history) {
            //设置
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            startActivity(intent);
        } else if (id == R.id.nav_manage) {
            //开发人员选项
            try {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                startActivity(intent);
            } catch (Exception e) {
                DisplayToast("无法跳转到开发者选项,请先确保您的设备已处于开发者模式");
                e.printStackTrace();
            }
        } else if (id == R.id.nav_bug_report) {
            //选择日志文件并上传
            DisplayToast("代码没写完");
        }
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    //显示输入经纬度的对话框
    public void showLatlngDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("输入经度和纬度(BD09坐标系)");
        //    通过LayoutInflater来加载一个xml的布局文件作为一个View对象
        View view = LayoutInflater.from(MainActivity.this).inflate(R.layout.latlng_dialog, null);
        //    设置我们自己定义的布局文件作为弹出框的Content
        builder.setView(view);

        final EditText dialog_lng = view.findViewById(R.id.dialog_longitude);
        final EditText dialog_lat = view.findViewById(R.id.dialog_latitude);

        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String dialog_lng_str = "", dialog_lat_str = "";
                try {
                    dialog_lng_str = dialog_lng.getText().toString().trim();
                    dialog_lat_str = dialog_lat.getText().toString().trim();
                    double dialog_lng_double = Double.valueOf(dialog_lng_str);
                    double dialog_lat_double = Double.valueOf(dialog_lat_str);
                    if (dialog_lng_double > 180.0 || dialog_lng_double < -180.0 || dialog_lat_double > 90.0 || dialog_lat_double < -90.0) {
                        DisplayToast("经纬度超出限制!\n-180.0<经度<180.0\n-90.0<纬度<90.0");
                    } else {
                        LatLng currentPt = new LatLng(dialog_lat_double, dialog_lng_double);
                        MapStatusUpdate mapstatusupdate = MapStatusUpdateFactory.newLatLng(currentPt);
                        mBaiduMap.setMapStatus(mapstatusupdate);
                        //对地图的中心点进行更新
                        setPoint(currentPt);
                    }
                } catch (Exception e) {
                    DisplayToast("获取经纬度出错,请检查输入是否正确");
                    e.printStackTrace();
                }
            }
        });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        builder.show();
    }
}
