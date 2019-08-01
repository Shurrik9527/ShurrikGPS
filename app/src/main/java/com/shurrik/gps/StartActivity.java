package com.shurrik.gps;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.widget.Toast;

import java.util.Objects;

public class StartActivity extends AppCompatActivity {
    private static final int LOCATION_CODE = 9527;
    private static final int GPS_CODE = 4399;
    private static final int DEVELOPER_CODE = 5288;
    private static final int FLOAT_WINDOW_CODE = 9999;
    private static final int GPS_WHAT = 0;
    private static final int DEVELOPER_WHAT = 1;
    private static final int FLOAT_WINDOW_WHAT = 2;


    private Handler mHander = new Handler(new Handler.Callback() {

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case GPS_WHAT:
                    //检查GPS是否开启
                    if (isGpsOpened()) {
                        //GPS已开启，下一步
                        mHander.sendEmptyMessage(DEVELOPER_WHAT);
                    } else {
                        DisplayToast("GPS定位未开启，请先打开GPS定位服务");
                        showGpsDialog();
                    }
                    break;
                case DEVELOPER_WHAT:
                    //
                    //提醒用户开启位置模拟
                    if (isAllowMockLocation()) {
                        //位置模拟已开启，下一步
                        mHander.sendEmptyMessage(FLOAT_WINDOW_WHAT);
                    } else {
                        DisplayToast("位置模拟未开启，请先将位置模拟设置为此程序");
                        showDeveloperDialog();
                    }
                    break;
                case FLOAT_WINDOW_WHAT:
                    //悬浮窗权限判断
                    if (Settings.canDrawOverlays(getApplicationContext())) {
                        //所有条件已具备
                        startMainActivity();
                    } else {
                        DisplayToast("悬浮窗未开启，请开启悬浮窗");
                        //启动Activity让用户授权
                        showFloatWindowDialog();
                    }
                    break;
                default:
                    showQuitDialog();
            }
            return false;
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        //请求授权
        requestPermissions();
    }

    /**
     * 请求定位相关权限
     */
    private void requestPermissions() {
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        // TODO Auto-generated method stub
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case LOCATION_CODE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 权限被用户同意
                    if (isNetworkConnected(StartActivity.this)) {
                        mHander.sendEmptyMessage(0);
                    } else {
                        mHander.sendEmptyMessage(-1);
                    }
                } else {
                    // 权限被用户拒绝了再次请求
                    requestPermissions();
                }
            }
        }
    }

    /**
     * 判断网络链接是否可用
     */
    private boolean isNetworkConnected(Context context) {
        if (context != null) {
            ConnectivityManager mConnectivityManager = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo mNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
            if (mNetworkInfo != null) {
                return mNetworkInfo.isConnected();
            }
        }
        return false;
    }

    /**
     * 判断GPS是否打开
     */
    private boolean isGpsOpened() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        // 通过GPS卫星定位，定位级别可以精确到街（通过24颗卫星定位，在室外和空旷的地方定位准确、速度快）
        boolean gps = Objects.requireNonNull(locationManager).isProviderEnabled(LocationManager.GPS_PROVIDER);
        // 通过WLAN或移动网络(3G/2G)确定的位置（也称作AGPS，辅助GPS定位。主要用于在室内或遮盖物（建筑群或茂密的深林等）密集的地方定位）
        boolean network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        return gps || network;
    }

    /**
     * 显示开启GPS的提示
     */
    private void showGpsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("请开启GPS定位服务")
                .setMessage("为了能正常使用位置模拟，请开启GPS定位服务")
                .setPositiveButton("开启",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                startActivityForResult(intent, GPS_CODE);
                            }
                        })
                .setNegativeButton("退出",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        })
                .show();
    }

    //提醒开启位置模拟的弹框
    private void showDeveloperDialog() {
        new AlertDialog.Builder(this)
                .setTitle("请添加位置模拟应用")
                .setMessage("请在\"开发者选项\"→\"选择模拟位置信息应用\"中进行设置")
                .setPositiveButton("设置",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                                    startActivityForResult(intent, DEVELOPER_CODE);
                                } catch (Exception e) {
                                    DisplayToast("无法跳转到开发者选项,请先确保您的设备已处于开发者模式");
                                    e.printStackTrace();
                                }
                            }
                        })
                .setNegativeButton("退出",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        })
                .show();
    }


    /**
     * 提醒开启悬浮窗的弹框
     */
    private void showFloatWindowDialog() {
        new AlertDialog.Builder(this)
                .setTitle("请启用悬浮窗")
                .setMessage("为了模拟定位的稳定性，请开启\"显示悬浮窗\"选项")
                .setPositiveButton("设置",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                                    startActivityForResult(intent, FLOAT_WINDOW_CODE);
                                } catch (Exception e) {
                                    DisplayToast("无法跳转到设置界面，请在权限管理中开启该应用的悬浮窗");
                                    e.printStackTrace();
                                }
                            }
                        })
                .setNegativeButton("退出",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        })
                .show();
    }


    /**
     * 网络异常弹框
     */
    private void showQuitDialog() {
        new AlertDialog.Builder(this)
                .setTitle("网络异常")
                .setMessage("无网络连接，请联网后重试")
                .setPositiveButton("确定",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        })
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case GPS_CODE:
                //从系统定位服务页面回来了
                mHander.sendEmptyMessage(GPS_WHAT);
                break;
            case DEVELOPER_CODE:
                //从开发者选项页面回来了
                mHander.sendEmptyMessage(DEVELOPER_WHAT);
                break;
            case FLOAT_WINDOW_CODE:
                //从开启悬浮窗页面回来了
                mHander.sendEmptyMessage(FLOAT_WINDOW_WHAT);
                break;
        }
    }

    /**
     * 模拟位置权限是否开启
     */
    public boolean isAllowMockLocation() {
        try {
            //获得LocationManager引用
            LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
            String providerStr = LocationManager.GPS_PROVIDER;
            assert locationManager != null;
            LocationProvider provider = locationManager.getProvider(providerStr);
            if (provider != null) {
                locationManager.addTestProvider(
                        provider.getName()
                        , provider.requiresNetwork()
                        , provider.requiresSatellite()
                        , provider.requiresCell()
                        , provider.hasMonetaryCost()
                        , provider.supportsAltitude()
                        , provider.supportsSpeed()
                        , provider.supportsBearing()
                        , provider.getPowerRequirement()
                        , provider.getAccuracy());
            } else {
                locationManager.addTestProvider(
                        providerStr
                        , true, true, false, false, true, true, true
                        , Criteria.POWER_HIGH, Criteria.ACCURACY_FINE);
            }
            locationManager.setTestProviderEnabled(providerStr, true);
            locationManager.setTestProviderStatus(providerStr, LocationProvider.AVAILABLE, null, System.currentTimeMillis());
            locationManager.setTestProviderEnabled(providerStr, false);
            locationManager.removeTestProvider(providerStr);
        } catch (SecurityException e) {
            return false;
        }
        return true;
    }

    /**
     * 启动程序主界面
     */
    private void startMainActivity() {
        new Handler().postDelayed(new Runnable() {
            public void run() {
                Intent intent = new Intent(StartActivity.this, MainActivity.class);
                startActivity(intent);
                StartActivity.this.finish();
            }
        }, 1000);
    }

    public void DisplayToast(String msg) {
        Toast toast = Toast.makeText(this, msg, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.TOP, 0, 360);
        toast.show();
    }

}
