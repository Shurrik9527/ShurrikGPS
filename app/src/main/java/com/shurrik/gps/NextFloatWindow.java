package com.shurrik.gps;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.lang.reflect.Field;

public class NextFloatWindow implements View.OnTouchListener {

    private Context mContext;
    private WindowManager.LayoutParams mWindowParams;
    private WindowManager mWindowManager;

    private View mFloatLayout;
    private float mInViewX;
    private float mInViewY;
    private float mDownInScreenX;
    private float mDownInScreenY;
    private float mInScreenX;
    private float mInScreenY;
    private int statusBarHeight;

    private long firstClickTime = 0;

    private OnFloatWindowClickListener listener;

    public interface OnFloatWindowClickListener {
        void onFloatWindowClick();
    }

    NextFloatWindow(Context context, OnFloatWindowClickListener listener) {
        this.mContext = context;
        this.listener = listener;
        initFloatWindow();
        initStatusBarHeight(context);
    }

    @SuppressLint("InflateParams")
    private void initFloatWindow() {
        LayoutInflater inflater = LayoutInflater.from(mContext);
        if (inflater == null)
            return;
        mFloatLayout = inflater.inflate(R.layout.float_button, null);
        mFloatLayout.setOnTouchListener(this);

        mWindowParams = new WindowManager.LayoutParams();
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {//8.0新特性
            mWindowParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            mWindowParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        }
        mWindowParams.format = PixelFormat.RGBA_8888;
        mWindowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        mWindowParams.gravity = Gravity.START | Gravity.TOP;
        mWindowParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        mWindowParams.height = 108;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        return floatLayoutTouch(motionEvent);
    }

    private boolean floatLayoutTouch(MotionEvent motionEvent) {

        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                onFloatWindowDoubleClick();
                // 获取相对View的坐标，即以此View左上角为原点
                mInViewX = motionEvent.getX();
                mInViewY = motionEvent.getY();
                // 获取相对屏幕的坐标，即以屏幕左上角为原点
                mDownInScreenX = motionEvent.getRawX();
                mDownInScreenY = motionEvent.getRawY() - statusBarHeight;
                mInScreenX = motionEvent.getRawX();
                mInScreenY = motionEvent.getRawY() - statusBarHeight;
                break;
            case MotionEvent.ACTION_MOVE:
                // 更新浮动窗口位置参数
                mInScreenX = motionEvent.getRawX();
                mInScreenY = motionEvent.getRawY() - statusBarHeight;
                mWindowParams.x = (int) (mInScreenX - mInViewX);
                mWindowParams.y = (int) (mInScreenY - mInViewY);
                // 手指移动的时候更新小悬浮窗的位置
                mWindowManager.updateViewLayout(mFloatLayout, mWindowParams);
                break;
            case MotionEvent.ACTION_UP:
                // 如果手指离开屏幕时，xDownInScreen和xInScreen相等，且yDownInScreen和yInScreen相等，则视为触发了单击事件。
                if (mDownInScreenX == mInScreenX && mDownInScreenY == mInScreenY) {
                    listener.onFloatWindowClick();
                }
                break;
        }
        return true;
    }

    void showFloatWindow() {
        if (mFloatLayout.getParent() == null) {
            DisplayMetrics metrics = new DisplayMetrics();
            //默认固定位置，靠屏幕右边缘的中间
            mWindowManager.getDefaultDisplay().getMetrics(metrics);
//            mWindowParams.x = metrics.widthPixels;
            mWindowParams.x = 0;
//            mWindowParams.y = metrics.heightPixels/3*2 - statusBarHeight;
            mWindowParams.y = statusBarHeight * 2 + statusBarHeight / 2;
            mWindowManager.addView(mFloatLayout, mWindowParams);
        }
    }

    void hideFloatWindow() {
        if (mFloatLayout.getParent() != null)
            mWindowManager.removeView(mFloatLayout);
    }

    private void onFloatWindowDoubleClick() { // 最长间隔时间
        if (firstClickTime > 0) {
            if (System.currentTimeMillis() - firstClickTime < 500) {
                firstClickTime = 0; // 将第一次点击时间置为0

                //唤起MainActivity
                Intent intent = new Intent(mContext, MainActivity.class);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                intent.setAction(Intent.ACTION_MAIN);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                mContext.startActivity(intent);
            }
        }
        firstClickTime = System.currentTimeMillis();
    }

    // 获取系统状态栏高度
    @SuppressLint("PrivateApi")
    private void initStatusBarHeight(Context context) {
        Class<?> c;
        Object obj;
        Field field;
        int x;
        try {
            c = Class.forName("com.android.internal.R$dimen");
            obj = c.newInstance();
            field = c.getField("status_bar_height");
            x = Integer.parseInt(field.get(obj).toString());
            statusBarHeight = context.getResources().getDimensionPixelSize(x);
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }
}