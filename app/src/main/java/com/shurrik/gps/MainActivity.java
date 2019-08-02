package com.shurrik.gps;

import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.Nullable;
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
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.Marker;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.map.TextOptions;
import com.baidu.mapapi.model.LatLng;
import com.shurrik.database.HistoryDBHelper;
import com.shurrik.service.MockGpsService;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final int HISTORY_CODE = 2048;
    private final String TAG = MainActivity.class.getName();

    //位置欺骗相关
    public List<LatLng> mHistoryPoints;

    private SQLiteDatabase sqLiteDatabase;

    // 定位相关
    private LocationClient locationClient = null;
    private int mCurrentDirection = 0;
    private MarkerOptions mMarkerOptions = null;
    private String address = null;
    private LatLng point = null;
    private LatLng originPoint = null;

    private MapView mMapView;
    private BaiduMap mBaiduMap = null;

    //Button requestLocButton;
    private MyLocationData locData;

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
            //数据库工具类
            HistoryDBHelper historyDBHelper = new HistoryDBHelper(getApplicationContext());
            sqLiteDatabase = historyDBHelper.getWritableDatabase();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * sqlite 操作 插表HistoryLocation
     */
    private long insertHistoryLocTable(ContentValues contentValues) {
        long result;
        try {
            result = sqLiteDatabase.insert(HistoryDBHelper.TABLE_NAME, null, contentValues);
        } catch (Exception e) {
            result = -99;
            e.printStackTrace();
        }
        return result;
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
             * 地图单击事件回调函数
             * @param point 点击的地理坐标
             */
            @Override
            public void onMapClick(LatLng point) {
                startMockGpsService(point, false);
            }

            /**
             * 地图内 Poi 单击事件回调函数
             * @param poi 点击的 poi 信息
             */
            @Override
            public boolean onMapPoiClick(MapPoi poi) {
                startMockGpsService(poi.getPosition(), false);
                return false;
            }
        });

        mBaiduMap.setOnMarkerClickListener(new BaiduMap.OnMarkerClickListener() {
            /**
             * 地图 Marker 覆盖物点击事件监听函数
             * @param marker 被点击的 marker
             */
            @Override
            public boolean onMarkerClick(Marker marker) {
                startMockGpsService(marker.getPosition(), false);
                return false;
            }
        });

        //历史记录标记
        BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromResource(R.drawable.icon_gcoding);
        mMarkerOptions = new MarkerOptions().icon(bitmapDescriptor);

        //地图类型切换
        setGroupListener();

        //保存按钮
        setFabListener();

        //悬浮窗
        initFloatWindow();

        //地图加载成功后读取所有历史记录并标记
        readAllHistoryPoints();

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

                if (point == null) {
                    //首次加载
                    originPoint = point = new LatLng(bdLocation.getLatitude(), bdLocation.getLongitude());
                    startMockGpsService(originPoint, true);
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
     * 读取数据库中所有的历史位置信息
     */
    private void readAllHistoryPoints() {
        try {
            Cursor cursor = sqLiteDatabase.query(HistoryDBHelper.TABLE_NAME, null,
                    null, null,
                    null, null, "TimeStamp DESC", null);
            if (mHistoryPoints == null) {
                mHistoryPoints = new ArrayList<>();
            } else {
                //清除已有记录
                mHistoryPoints.clear();
                mBaiduMap.clear();
            }
            while (cursor.moveToNext()) {
                String address = cursor.getString(0);
                double longitude = cursor.getDouble(1);
                double latitude = cursor.getDouble(2);
                long timeStamp = cursor.getLong(3);
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
     * 初始化悬浮窗
     */
    private void initFloatWindow() {
        //悬浮窗
        FloatWindow floatWindow = new FloatWindow(MainActivity.this, new FloatWindow.OnFloatWindowClickListener() {
            int index = 0;

            @Override
            public void onFloatWindowClick() {
                if (mHistoryPoints.size() == 0) {
                    DisplayToast("No History Location!!!");
                    return;
                }
                if (index >= mHistoryPoints.size()) {
                    index = 0;
                }
                startMockGpsService(mHistoryPoints.get(index), false);
                index++;
            }
        });
        floatWindow.showFloatWindow();
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
        contentValues.put("TimeStamp", System.currentTimeMillis());
        long result = insertHistoryLocTable(contentValues);
        if (result > 0) {
            addPointToHistoryPoints(point);
            return "保存[" + address + "]成功！！！";
        } else if (result == -1) {
            return "请勿重复添加位置！！！";
        } else {
            return "保存位置失败！！！";
        }
    }

    /**
     * 添加坐标到历史坐标列表并更新地图标记
     *
     * @param point 待添加坐标
     */
    private void addPointToHistoryPoints(LatLng point) {
        mHistoryPoints.add(0, point);
        mMarkerOptions.position(point);
        mBaiduMap.addOverlay(mMarkerOptions);
    }

    /**
     * 开启位置模拟
     *
     * @param point 要前往的坐标
     */
    private void startMockGpsService(LatLng point, boolean isZoom) {
        if (point == null) {
            return;
        }

        //移动中心点
        moveToThePoint(point, isZoom);

        //更新当前位置信息
        setPoint(point);

        //启动位置模拟服务
        Intent mockLocServiceIntent = new Intent(MainActivity.this, MockGpsService.class);
        mockLocServiceIntent.putExtra("BD09Longitude", point.longitude);
        mockLocServiceIntent.putExtra("BD09Latitude", point.latitude);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(mockLocServiceIntent);
        } else {
            startService(mockLocServiceIntent);
        }
    }

    /**
     * 将地图中心点移动到指定位置
     * @param point 待移动的点
     * @param isZoom 是否缩放
     */
    private void moveToThePoint(LatLng point, boolean isZoom) {
        if (isZoom) {
            //缩放移动地图位置
            MapStatus.Builder builder = new MapStatus.Builder();
            builder.target(point).zoom(18.0f);
            mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));
        } else {
            //移动地图位置
            mBaiduMap.setMapStatus(MapStatusUpdateFactory.newLatLng(point));
        }
    }

    /**
     * 更新当前位置信息
     */
    private void setPoint(LatLng point) {
        this.point = point;
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
        sqLiteDatabase.close();
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
            startActivityForResult(intent, HISTORY_CODE);
            return true;
        } else if (id == R.id.action_input) {
            //经纬度定位
            showLatlngDialog();
        } else if (id == R.id.action_resetMap) {
            //重置地图
            startMockGpsService(originPoint, true);
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
        // 通过LayoutInflater来加载一个xml的布局文件作为一个View对象
        View view = LayoutInflater.from(MainActivity.this).inflate(R.layout.latlng_dialog, null);
        // 设置我们自己定义的布局文件作为弹出框的Content
        builder.setView(view);

        final EditText dialog_lng = view.findViewById(R.id.dialog_longitude);
        final EditText dialog_lat = view.findViewById(R.id.dialog_latitude);

        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    double dialog_lng_double = Double.valueOf(dialog_lng.getText().toString().trim());
                    double dialog_lat_double = Double.valueOf(dialog_lat.getText().toString().trim());
                    if (dialog_lng_double > 180.0 || dialog_lng_double < -180.0 || dialog_lat_double > 90.0 || dialog_lat_double < -90.0) {
                        DisplayToast("经纬度超出限制!\n-180.0<经度<180.0\n-90.0<纬度<90.0");
                    } else {
                        LatLng targetPoint = new LatLng(dialog_lat_double, dialog_lng_double);
                        startMockGpsService(targetPoint, true);
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case HISTORY_CODE:
                //从历史记录页面回来了
                readAllHistoryPoints();
                break;
        }
    }
}
