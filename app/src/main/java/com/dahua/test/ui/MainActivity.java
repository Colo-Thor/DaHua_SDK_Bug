package com.dahua.test.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.dahua.test.MyApplication;
import com.dahua.test.R;
import com.dahua.test.util.DaHuaNetUtil;

import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity implements View.OnClickListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int HANDLER_SHOW_ERROR_DIALOG = 1;
    private static final int HANDLER_CHECK_RUNNING_STATUS = 2;

    private EditText tvIp;
    private EditText tvPort;
    private EditText tvUsername;
    private EditText tvPwd;
    private Button btnStart;
    private static Handler handler;

    private static boolean queryAndDownloadThreadStart;
    private static Thread queryAndDownloadThread;
    private static boolean errorCheckThreadStart;
    private static Thread errorCheckThread;
    private static boolean needStop;

    private static class MainHandler extends Handler {
        private final WeakReference<MainActivity> mTarget;

        public MainHandler(MainActivity mainActivity) {
            mTarget = new WeakReference<>(mainActivity);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            MainActivity mainActivity = mTarget.get();
            if (mainActivity == null || mainActivity.isFinishing()) {
                return;
            }

            switch (msg.what) {
                case HANDLER_SHOW_ERROR_DIALOG:
                    mainActivity.showErrorDialog();
                    break;
                case HANDLER_CHECK_RUNNING_STATUS:
                    mainActivity.checkRunningStatus();
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!isTaskRoot()) {
            super.finish();
            return;
        }

        tvIp = findViewById(R.id.et_ip);
        tvPort = findViewById(R.id.et_port);
        tvUsername = findViewById(R.id.et_username);
        tvPwd = findViewById(R.id.et_pwd);
        btnStart = findViewById(R.id.btn_start);
        btnStart.setOnClickListener(MainActivity.this);

        handler = new MainHandler(MainActivity.this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_start:
                Log.i(TAG, "onClick queryAndDownloadThreadStart: " + queryAndDownloadThreadStart + ", errorCheckThreadStart: " + errorCheckThreadStart);

                if (queryAndDownloadThreadStart && errorCheckThreadStart) {
                    needStop = true;
                    btnStart.setText("停止中...");
                    queryAndDownloadThread.interrupt();
                    errorCheckThread.interrupt();
                } else if (!queryAndDownloadThreadStart && !errorCheckThreadStart) {
                    needStop = false;
                    btnStart.setText("启动中...");
                    String ip = tvIp.getText().toString().trim();
                    String portStr = tvPort.getText().toString().trim();
                    String username = tvUsername.getText().toString().trim();
                    String pwd = tvPwd.getText().toString().trim();
                    if (TextUtils.isEmpty(ip) || TextUtils.isEmpty(portStr) || TextUtils.isEmpty(username) || TextUtils.isEmpty(pwd)) {
                        Toast.makeText(MainActivity.this, "请输入摄像头ip/端口/用户名/密码", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int port;
                    try {
                        port = Integer.parseInt(portStr);
                    } catch (NumberFormatException e) {
                        Toast.makeText(MainActivity.this, "摄像头端口格式错误", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    queryAndDownloadThread = new QueryAndDownloadThread(ip, port, username, pwd);
                    queryAndDownloadThread.start();
                    errorCheckThread = new ErrorCheckThread();
                    errorCheckThread.start();
                }
                break;
            default:
                break;
        }
    }

    private static class QueryAndDownloadThread extends Thread {
        private String cameraIp;
        private int port;
        private String username;
        private String password;

        public QueryAndDownloadThread(String cameraIp, int port, String username, String password) {
            this.cameraIp = cameraIp;
            this.port = port;
            this.username = username;
            this.password = password;
        }

        @Override
        public void run() {
            if (queryAndDownloadThreadStart) {
                return;
            }
            queryAndDownloadThreadStart = true;
            handler.sendEmptyMessage(HANDLER_CHECK_RUNNING_STATUS);

            boolean loginSucceed = true;
            String loginResult = DaHuaNetUtil.netDEVLogin(cameraIp, port, username, password);
            if (!TextUtils.isEmpty(loginResult)) {
                Log.e(TAG, "queryError: " + loginResult);
                loginSucceed = false;
            }

            DaHuaNetUtil.LoginInfo loginInfo = DaHuaNetUtil.getLoginHandler(cameraIp);
            if (loginInfo == null) {
                String msg = "DaHua device login info error " + cameraIp;
                Log.e(TAG, "queryError: " + msg);
                loginSucceed = false;
            }

            while (loginSucceed && !needStop) {
                try {
                    Thread.sleep(2000);

                    DaHuaNetUtil.isLoginHandlerValid_bug(cameraIp, loginInfo);
                } catch (Exception e) {
                    e.printStackTrace();
                    break;
                }
            }

            if (!loginSucceed) {
                needStop = true;
            }

            queryAndDownloadThreadStart = false;
            handler.sendEmptyMessage(HANDLER_CHECK_RUNNING_STATUS);
        }
    }

    private static class ErrorCheckThread extends Thread {
        private int temp = 0;
        private Integer temp0 = 0;
        private long lastCheckMillis;

        @Override
        public void run() {
            if (errorCheckThreadStart) {
                return;
            }
            errorCheckThreadStart = true;
            handler.sendEmptyMessage(HANDLER_CHECK_RUNNING_STATUS);

            while (!needStop) {
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                    break;
                }

                long now = System.currentTimeMillis();
                if (now - lastCheckMillis > 10000) {
                    lastCheckMillis = now;
                    Log.i(TAG, "init: " + temp + ", " + temp0);
                }

                if (temp != temp0) {
                    Log.i(TAG, "init: " + temp + ", " + temp0);
                    needStop = true;
                    if (queryAndDownloadThreadStart) {
                        queryAndDownloadThread.interrupt();
                    }
                    handler.sendEmptyMessage(HANDLER_SHOW_ERROR_DIALOG);
                    break;
                }
            }

            errorCheckThreadStart = false;
            handler.sendEmptyMessage(HANDLER_CHECK_RUNNING_STATUS);
        }
    }

    private void checkRunningStatus() {
        Log.i(TAG, "checkRunningStatus queryAndDownloadThreadStart: " + queryAndDownloadThreadStart + ", errorCheckThreadStart: " + errorCheckThreadStart);
        if (needStop) {
            if (queryAndDownloadThreadStart) {
                try {
                    if (queryAndDownloadThread != null) {
                        queryAndDownloadThread.interrupt();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (errorCheckThreadStart) {
                try {
                    if (errorCheckThread != null) {
                        errorCheckThread.interrupt();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        if (queryAndDownloadThreadStart && errorCheckThreadStart) {
            btnStart.setText("停止");
        }

        if (!queryAndDownloadThreadStart && !errorCheckThreadStart) {
            btnStart.setText("启动");
        }
    }

    private void showErrorDialog() {
        new AlertDialog.Builder(this).setTitle("错误")
                .setMessage("程序出现错误").setPositiveButton("退出", (dialogInterface, i) -> {
            dialogInterface.dismiss();
            MyApplication.startAppExit();
        }).create().show();
    }

    @Override
    public void onBackPressed() {
        exitByDoubleClick();
    }

    private static Boolean isExit = false;

    private void exitByDoubleClick() {
        Timer tExit = null;
        if (!isExit) {
            isExit = true;
            Toast.makeText(MainActivity.this, "再按一次退出程序", Toast.LENGTH_SHORT).show();
            tExit = new Timer();
            tExit.schedule(new TimerTask() {
                @Override
                public void run() {
                    isExit = false;//取消退出
                }
            }, 2000);// 如果2秒钟内没有按下返回键，则启动定时器取消掉刚才执行的任务
        } else {
            try {
                if (queryAndDownloadThreadStart && queryAndDownloadThread != null) {
                    queryAndDownloadThread.interrupt();
                }
                if (errorCheckThreadStart && errorCheckThread != null) {
                    errorCheckThread.interrupt();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            //返回主界面
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            startActivity(home);
            //退出应用
            MyApplication.startAppExit();
        }
    }
}
