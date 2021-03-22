package com.dahua.test;

import android.app.Application;

public class MyApplication extends Application {
    private static final String TAG = MyApplication.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();
    }

    /**
     * 开始退出App
     */
    public static void startAppExit() {
        try {
            doAppExit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 真实退出App
     */
    public static void doAppExit() {
        try {
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
