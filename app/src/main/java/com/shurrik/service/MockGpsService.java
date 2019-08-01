package com.shurrik.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import com.baidu.mapapi.model.LatLng;
import com.shurrik.gps.R;

import java.util.UUID;

public class MockGpsService extends Service {


    private String TAG = "MockGpsService";

    private LocationManager locationManager;
    private HandlerThread handlerThread;

    private Handler handler;

    private boolean isStop = true;

    //经纬度字符串
    private String latLngInfo = "120.10361002621116&30.246439673700642";


    public static final int RunCode = 0x01;
    public static final int StopCode = 0x02;


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @SuppressLint("WrongConstant")
    @Override
    public void onCreate() {
        super.onCreate();

        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        //add a new test network location provider
        setNetworkTestProvider();
//        add a GPS test Provider
        setGPSTestProvider();

        //thread
        handlerThread = new HandlerThread(getUUID(), -2);
        handlerThread.start();

        handler = new Handler(handlerThread.getLooper()) {
            public void handleMessage(Message msg) {
                try {
                    Thread.sleep(128);
                    if (!isStop) {
                        setTestProviderLocation();
                        setGPSLocation();
                        sendEmptyMessage(0);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt();
                }
            }
        };
        handler.sendEmptyMessage(0);

        //开启通知栏
        showNotification();
    }


    private void showNotification() {
        String channelId = "channel_01";
        String name = "channel_name";
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification notification = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel mChannel = new NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_LOW);
            Log.i(TAG, mChannel.toString());
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(mChannel);
            }
            notification = new Notification.Builder(this)
                    .setChannelId(channelId)
                    .setContentTitle("位置模拟服务已启动")
                    .setContentText("MockLocation service is running")
                    .setSmallIcon(R.mipmap.ic_launcher).build();
        } else {
            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
                    .setContentTitle("位置模拟服务已启动")
                    .setContentText("MockLocation service is running")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setOngoing(true)
                    .setChannelId(channelId);//无效
            notification = notificationBuilder.build();
        }
        startForeground(1, notification);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //get location info from mainActivity
        double BD09Longitude = intent.getDoubleExtra("BD09Longitude",0);
        double BD09Latitude = intent.getDoubleExtra("BD09Latitude",0);

        //离线转换坐标系
        double latLng[] = Utils.bd09towgs84(BD09Longitude, BD09Latitude);
        //wgs84
        latLngInfo = latLng[0] + "&" + latLng[1];
        isStop = false;
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        isStop = true;

        handler.removeMessages(0);
        handlerThread.quit();

        //remove test provider
        rmNetworkTestProvider();
        rmGPSTestProvider();

        //rmGPSProvider();
        stopForeground(true);

        //broadcast to MainActivity
//        Intent intent = new Intent();
//        intent.putExtra("statusCode", StopCode);
//        intent.setAction("com.shurrik.service.MockGpsService");
//        sendBroadcast(intent);

        super.onDestroy();
    }

    //generate a location
    public Location generateLocation(LatLng latLng) {
        Location loc = new Location("gps");


        loc.setAccuracy(2.0F);
        loc.setAltitude(55.0D);
        loc.setBearing(1.0F);
        Bundle bundle = new Bundle();
        bundle.putInt("satellites", 7);
        loc.setExtras(bundle);


        loc.setLatitude(latLng.latitude);
        loc.setLongitude(latLng.longitude);

        loc.setTime(System.currentTimeMillis());
        loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());

        return loc;
    }

    //给test provider添加网络定位
    private void setTestProviderLocation() {
        String latLngStr[] = latLngInfo.split("&");
        LatLng latLng = new LatLng(Double.valueOf(latLngStr[1]), Double.valueOf(latLngStr[0]));
        String providerStr = LocationManager.NETWORK_PROVIDER;
        try {
            locationManager.setTestProviderLocation(providerStr, generateLocation(latLng));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //set gps location
    private void setGPSLocation() {
        String latLngStr[] = latLngInfo.split("&");
        LatLng latLng = new LatLng(Double.valueOf(latLngStr[1]), Double.valueOf(latLngStr[0]));
        String providerStr = LocationManager.GPS_PROVIDER;
        try {
            locationManager.setTestProviderLocation(providerStr, generateLocation(latLng));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //remove network provider
    private void rmNetworkTestProvider() {
        try {
            for (int i = 0; i < 3; i++) {
                String providerStr = LocationManager.NETWORK_PROVIDER;
                if (locationManager.isProviderEnabled(providerStr)) {
                    locationManager.removeTestProvider(providerStr);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //set new network provider
    private void setNetworkTestProvider() {
        String providerStr = LocationManager.NETWORK_PROVIDER;
        try {
            locationManager.addTestProvider(providerStr, false, false,
                    false, false, false, false,
                    false, 1, Criteria.ACCURACY_FINE);
        } catch (SecurityException e) {
            e.printStackTrace();
        }
        if (!locationManager.isProviderEnabled(providerStr)) {
            try {
                locationManager.setTestProviderEnabled(providerStr, true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // for test: set GPS provider
    private void rmGPSTestProvider() {
        try {
            for (int i = 0; i < 3; i++) {
                String providerStr = LocationManager.GPS_PROVIDER;
                if (locationManager.isProviderEnabled(providerStr)) {

                    locationManager.removeTestProvider(providerStr);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setGPSTestProvider() {
        try {
            locationManager.addTestProvider(LocationManager.GPS_PROVIDER, false, true, true,
                    false, true, true, true, 0, 5);
        } catch (Exception e) {
            e.printStackTrace();
        }

//        }
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            try {
                locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        //新
        locationManager.setTestProviderStatus(LocationManager.GPS_PROVIDER, LocationProvider.AVAILABLE, null,
                System.currentTimeMillis());
    }


    //uuid random
    public static String getUUID() {
        return UUID.randomUUID().toString();
    }
}


