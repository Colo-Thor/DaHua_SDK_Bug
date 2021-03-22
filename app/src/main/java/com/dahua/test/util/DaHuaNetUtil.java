package com.dahua.test.util;

import android.text.TextUtils;
import android.util.Log;

import com.company.NetSDK.CB_fDisConnect;
import com.company.NetSDK.CB_fHaveReConnect;
import com.company.NetSDK.EM_LOGIN_SPAC_CAP_TYPE;
import com.company.NetSDK.FinalVar;
import com.company.NetSDK.INetSDK;
import com.company.NetSDK.NET_DEVICEINFO_Ex;
import com.company.NetSDK.NET_IN_LOGIN_WITH_HIGHLEVEL_SECURITY;
import com.company.NetSDK.NET_OUT_LOGIN_WITH_HIGHLEVEL_SECURITY;
import com.company.NetSDK.NET_PARAM;

import java.util.HashMap;
import java.util.Map;

/**
 * 大华摄像头工具类
 */
public class DaHuaNetUtil {
    private static final String TAG = DaHuaNetUtil.class.getSimpleName();
    /// Timeout of NetSDK API
    /// INetSDK 接口超时时间
    private static final int TIMEOUT_5S = 5000;      // 5 second
    private static final int TIMEOUT_10S = 10000;    // 10 second
    private static final int TIMEOUT_30S = 30000;    // 30 second

    private static boolean init;
    private static DeviceDisConnect mDisconnect = new DeviceDisConnect();
    private static DeviceReConnect mReconnect = new DeviceReConnect();
    private static Map<String, LoginInfo> loginHandlerMap = new HashMap<>();

    public static String netDEVLogin(String cameraIp, int port, String username, String password) {
        return netDEVLogin(cameraIp, port, username, password, false);
    }

    /**
     * @param cameraIp     摄像头ip
     * @param forceReLogin 是否强制重新登录
     * @return
     */
    public static synchronized String netDEVLogin(String cameraIp, int port, String username, String password, boolean forceReLogin) {
        String initResult = init(forceReLogin);
        if (!TextUtils.isEmpty(initResult)) {
            return initResult;
        }
        if (!forceReLogin && loginHandlerMap.containsKey(cameraIp) && isLoginHandlerValid_bug(cameraIp, loginHandlerMap.get(cameraIp))) {
            return "";
        }
        return login(cameraIp, port, username, password);
    }

    public static LoginInfo getLoginHandler(String cameraIp) {
        return loginHandlerMap.get(cameraIp);
    }

    private static String init(boolean reInit) {
        if (!reInit && init) {
            return "";
        }

        if (init) {
            INetSDK.Cleanup();
        }

        INetSDK.LoadLibrarys();

        boolean initResult = INetSDK.Init(mDisconnect);
        if (!initResult) {
            String msg = " inti DaHua NetSDK error";
            Log.e(TAG, msg);
            return msg;
        }
        init = true;

        INetSDK.SetGDPREnable(true);

        // Set Reconnect callback.
        // 设置断线重连回调 : 当app重新连接上设备时，会触发该回调;
        INetSDK.SetAutoReconnect(mReconnect);

        NET_PARAM stNetParam = new NET_PARAM();
        stNetParam.nConnectTime = TIMEOUT_10S;
        // Time out of common Interface.
        stNetParam.nWaittime = TIMEOUT_10S;
        // Time out of Playback interface.
        stNetParam.nSearchRecordTime = TIMEOUT_30S;
        INetSDK.SetNetworkParam(stNetParam);

        return "";
    }

    private static String login(String address, int port, String username, String password) {
        NET_IN_LOGIN_WITH_HIGHLEVEL_SECURITY stuIn = new NET_IN_LOGIN_WITH_HIGHLEVEL_SECURITY();
        System.arraycopy(address.getBytes(), 0, stuIn.szIP, 0, address.getBytes().length);
        stuIn.nPort = port;
        System.arraycopy(username.getBytes(), 0, stuIn.szUserName, 0, username.getBytes().length);
        System.arraycopy(password.getBytes(), 0, stuIn.szPassword, 0, password.getBytes().length);
        stuIn.emSpecCap = EM_LOGIN_SPAC_CAP_TYPE.EM_LOGIN_SPEC_CAP_TCP;
        NET_OUT_LOGIN_WITH_HIGHLEVEL_SECURITY stuOut = new NET_OUT_LOGIN_WITH_HIGHLEVEL_SECURITY();
        long mLoginHandle = INetSDK.LoginWithHighLevelSecurity(stuIn, stuOut);

        if (0 == mLoginHandle) {
            int errorCode = INetSDK.GetLastError();
            String msg = "Failed to Login DaHua Device " + address + ", errorCode: " + (errorCode & 0x7fffffff) + ", errorMsg: " + getErrorMsg(errorCode);
            Log.e(TAG, msg);
            return msg;
        }
        Log.d(TAG, address + " login succeed, loginHandler 0x" + Long.toHexString(mLoginHandle));

        loginHandlerMap.put(address, new LoginInfo(mLoginHandle, stuOut.stuDeviceInfo));
        return "";
    }

    /**
     * 登录凭证是否过期
     * 当前通过能否成功查询录像状态判断
     */
    public static boolean isLoginHandlerValid(String cameraIp, LoginInfo loginInfo) {
        byte[] channelBytes = new byte[1];
        boolean succeed = INetSDK.QueryRecordState(loginInfo.getLoginHandler(), channelBytes, 1, 3000);
        if (!succeed) {
            int errorCode = INetSDK.GetLastError();
            Log.e(TAG, "DaHua device QueryDevState error " + cameraIp + "，errorCode: " + (errorCode & 0x7fffffff) + ", errorMsg: " + DaHuaNetUtil.getErrorMsg(errorCode));
            Log.d(TAG, cameraIp + " loginHandler 0x" + Long.toHexString(loginInfo.getLoginHandler()) + " invalid, need reLogin");
        }
        return succeed;
    }

    /**
     * 登录凭证是否过期
     * 当前通过能否成功查询录像状态判断
     */
    public static boolean isLoginHandlerValid_bug(String cameraIp, LoginInfo loginInfo) {
        byte[] channelBytes = new byte[1];
        boolean succeed = INetSDK.QueryRecordState(loginInfo.getLoginHandler(), channelBytes, 0, 3000);
        if (!succeed) {
            int errorCode = INetSDK.GetLastError();
            Log.e(TAG, "DaHua device QueryDevState error " + cameraIp + "，errorCode: " + (errorCode & 0x7fffffff) + ", errorMsg: " + DaHuaNetUtil.getErrorMsg(errorCode));
            Log.d(TAG, cameraIp + " loginHandler 0x" + Long.toHexString(loginInfo.getLoginHandler()) + " invalid, need reLogin");
        }
        return succeed;
    }

    public static String getErrorMsg(int errorCode) {
        String errorMsg;
        switch (errorCode) {
            case FinalVar.NET_NOERROR:
                errorMsg = "没有错误";
                break;
            case FinalVar.NET_ERROR:
                errorMsg = "未知错误 ";
                break;
            case FinalVar.NET_SYSTEM_ERROR:
                errorMsg = "Windows系统出错";
                break;
            case FinalVar.NET_NETWORK_ERROR:
                errorMsg = "网络错误";
                break;
            case FinalVar.NET_DEV_VER_NOMATCH:
                errorMsg = "设备协议不匹配";
                break;
            case FinalVar.NET_INVALID_HANDLE:
                errorMsg = "句柄无效";
                break;
            case FinalVar.NET_OPEN_CHANNEL_ERROR:
                errorMsg = "打开通道失败";
                break;
            case FinalVar.NET_CLOSE_CHANNEL_ERROR:
                errorMsg = "关闭通道失败";
                break;
            case FinalVar.NET_ILLEGAL_PARAM:
                errorMsg = "用户参数不合法";
                break;
            case FinalVar.NET_SDK_INIT_ERROR:
                errorMsg = "SDK初始化出错";
                break;
            case FinalVar.NET_SDK_UNINIT_ERROR:
                errorMsg = "SDK清理出错";
                break;
            case FinalVar.NET_RENDER_OPEN_ERROR:
                errorMsg = "申请render资源出错";
                break;
            case FinalVar.NET_DEC_OPEN_ERROR:
                errorMsg = "打开解码库出错";
                break;
            case FinalVar.NET_DEC_CLOSE_ERROR:
                errorMsg = "关闭解码库出错";
                break;
            case FinalVar.NET_MULTIPLAY_NOCHANNEL:
                errorMsg = "多画面预览中检测到通道数为0";
                break;
            case FinalVar.NET_TALK_INIT_ERROR:
                errorMsg = "录音库初始化失败";
                break;
            case FinalVar.NET_TALK_NOT_INIT:
                errorMsg = "录音库未经初始化";
                break;
            case FinalVar.NET_TALK_SENDDATA_ERROR:
                errorMsg = "发送音频数据出错";
                break;
            case FinalVar.NET_REAL_ALREADY_SAVING:
                errorMsg = "实时数据已经处于保存状态";
                break;
            case FinalVar.NET_NOT_SAVING:
                errorMsg = "未保存实时数据";
                break;
            case FinalVar.NET_OPEN_FILE_ERROR:
                errorMsg = "打开文件出错";
                break;
            case FinalVar.NET_PTZ_SET_TIMER_ERROR:
                errorMsg = "启动云台控制定时器失败";
                break;
            case FinalVar.NET_RETURN_DATA_ERROR:
                errorMsg = "对返回数据的校验出错";
                break;
            case FinalVar.NET_INSUFFICIENT_BUFFER:
                errorMsg = "没有足够的缓存";
                break;
            case FinalVar.NET_NOT_SUPPORTED:
                errorMsg = "当前SDK未支持该功能";
                break;
            case FinalVar.NET_NO_RECORD_FOUND:
                errorMsg = "查询不到录像";
                break;
            case FinalVar.NET_NOT_AUTHORIZED:
                errorMsg = "无操作权限";
                break;
            case FinalVar.NET_NOT_NOW:
                errorMsg = "暂时无法执行";
                break;
            case FinalVar.NET_NO_TALK_CHANNEL:
                errorMsg = "未发现对讲通道";
                break;
            case FinalVar.NET_NO_AUDIO:
                errorMsg = "未发现音频";
                break;
            case FinalVar.NET_NO_INIT:
                errorMsg = "网络SDK未经初始化";
                break;
            case FinalVar.NET_DOWNLOAD_END:
                errorMsg = "下载已结束";
                break;
            case FinalVar.NET_EMPTY_LIST:
                errorMsg = "查询结果为空";
                break;
            case FinalVar.NET_ERROR_GETCFG_SYSATTR:
                errorMsg = "获取系统属性配置失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_SERIAL:
                errorMsg = "获取序列号失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_GENERAL:
                errorMsg = "获取常规属性失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_DSPCAP:
                errorMsg = "获取DSP能力描述失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_NETCFG:
                errorMsg = "获取网络配置失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_CHANNAME:
                errorMsg = "获取通道名称失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_VIDEO:
                errorMsg = "获取视频属性失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_RECORD:
                errorMsg = "获取录像配置失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_PRONAME:
                errorMsg = "获取解码器协议名称失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_FUNCNAME:
                errorMsg = "获取232串口功能名称失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_485DECODER:
                errorMsg = "获取解码器属性失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_232COM:
                errorMsg = "获取232串口配置失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_ALARMIN:
                errorMsg = "获取外部报警输入配置失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_ALARMDET:
                errorMsg = "获取动态检测报警失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_SYSTIME:
                errorMsg = "获取设备时间失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_PREVIEW:
                errorMsg = "获取预览参数失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_AUTOMT:
                errorMsg = "获取自动维护配置失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_VIDEOMTRX:
                errorMsg = "获取视频矩阵配置失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_COVER:
                errorMsg = "获取区域遮挡配置失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_WATERMAKE:
                errorMsg = "获取图像水印配置失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_GENERAL:
                errorMsg = "修改常规属性失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_NETCFG:
                errorMsg = "修改网络配置失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_CHANNAME:
                errorMsg = "修改通道名称失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_VIDEO:
                errorMsg = "修改视频属性失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_RECORD:
                errorMsg = "修改录像配置失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_485DECODER:
                errorMsg = "修改解码器属性失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_232COM:
                errorMsg = "修改232串口配置失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_ALARMIN:
                errorMsg = "修改外部输入报警配置失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_SYSTIME:
                errorMsg = "修改设备时间失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_PREVIEW:
                errorMsg = "修改预览参数失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_AUTOMT:
                errorMsg = "修改自动维护配置失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_VIDEOMTRX:
                errorMsg = "修改视频矩阵配置失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_COVER:
                errorMsg = "修改区域遮挡配置失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_WATERMAKE:
                errorMsg = "修改图像水印配置失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_WLAN:
                errorMsg = "修改无线网络信息失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_WLANDEV:
                errorMsg = "修改无线网络信息失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_REGISTER:
                errorMsg = "修改主动注册参数配置失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_CAMERA:
                errorMsg = "修改摄像头属性配置失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_INFRARED:
                errorMsg = "修改红外报警配置失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_SOUNDALARM:
                errorMsg = "修改音频报警配置失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_STORAGE:
                errorMsg = "修改存储位置配置失败";
                break;
            case FinalVar.NET_AUDIOENCODE_NOTINIT:
                errorMsg = "音频编码接口没有成功初始化";
                break;
            case FinalVar.NET_DATA_TOOLONGH:
                errorMsg = "数据过长";
                break;
            case FinalVar.NET_UNSUPPORTED:
                errorMsg = "设备不支持该操作";
                break;
            case FinalVar.NET_DEVICE_BUSY:
                errorMsg = "设备资源不足";
                break;
            case FinalVar.NET_SERVER_STARTED:
                errorMsg = "服务器已经启动";
                break;
            case FinalVar.NET_SERVER_STOPPED:
                errorMsg = "服务器尚未成功启动";
                break;
            case FinalVar.NET_LISTER_INCORRECT_SERIAL:
                errorMsg = "输入序列号有误";
                break;
            case FinalVar.NET_QUERY_DISKINFO_FAILED:
                errorMsg = "获取硬盘信息失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_SESSION:
                errorMsg = "获取连接Session信息";
                break;
            case FinalVar.NET_USER_FLASEPWD_TRYTIME:
                errorMsg = "输入密码错误超过限制次数";
                break;
            case FinalVar.NET_LOGIN_ERROR_PASSWORD:
                errorMsg = "密码不正确";
                break;
            case FinalVar.NET_LOGIN_ERROR_USER:
                errorMsg = "帐户不存在";
                break;
            case FinalVar.NET_LOGIN_ERROR_TIMEOUT:
                errorMsg = "等待登录返回超时";
                break;
            case FinalVar.NET_LOGIN_ERROR_RELOGGIN:
                errorMsg = "帐号已登录";
                break;
            case FinalVar.NET_LOGIN_ERROR_LOCKED:
                errorMsg = "帐号已被锁定";
                break;
            case FinalVar.NET_LOGIN_ERROR_BLACKLIST:
                errorMsg = "帐号已被列为黑名单";
                break;
            case FinalVar.NET_LOGIN_ERROR_BUSY:
                errorMsg = "资源不足,系统忙";
                break;
            case FinalVar.NET_LOGIN_ERROR_CONNECT:
                errorMsg = "连接主机失败";
                break;
            case FinalVar.NET_LOGIN_ERROR_NETWORK:
                errorMsg = "网络连接失败";
                break;
            case FinalVar.NET_LOGIN_ERROR_SUBCONNECT:
                errorMsg = "登录设备成功,但无法创建视频通道,请检查网络状况";
                break;
            case FinalVar.NET_LOGIN_ERROR_MAXCONNECT:
                errorMsg = "超过最大连接数";
                break;
            case FinalVar.NET_RENDER_SOUND_ON_ERROR:
                errorMsg = "Render库打开音频出错";
                break;
            case FinalVar.NET_RENDER_SOUND_OFF_ERROR:
                errorMsg = "Render库关闭音频出错";
                break;
            case FinalVar.NET_RENDER_SET_VOLUME_ERROR:
                errorMsg = "Render库控制音量出错";
                break;
            case FinalVar.NET_RENDER_ADJUST_ERROR:
                errorMsg = "Render库设置画面参数出错";
                break;
            case FinalVar.NET_RENDER_PAUSE_ERROR:
                errorMsg = "Render库暂停播放出错";
                break;
            case FinalVar.NET_RENDER_SNAP_ERROR:
                errorMsg = "Render库抓图出错";
                break;
            case FinalVar.NET_RENDER_STEP_ERROR:
                errorMsg = "Render库步进出错";
                break;
            case FinalVar.NET_RENDER_FRAMERATE_ERROR:
                errorMsg = "Render库设置帧率出错";
                break;
            case FinalVar.NET_GROUP_EXIST:
                errorMsg = "组名已存在";
                break;
            case FinalVar.NET_GROUP_NOEXIST:
                errorMsg = "组名不存在";
                break;
            case FinalVar.NET_GROUP_RIGHTOVER:
                errorMsg = "组的权限超出权限列表范围";
                break;
            case FinalVar.NET_GROUP_HAVEUSER:
                errorMsg = "组下有用户,不能删除";
                break;
            case FinalVar.NET_GROUP_RIGHTUSE:
                errorMsg = "组的某个权限被用户使用,不能出除";
                break;
            case FinalVar.NET_GROUP_SAMENAME:
                errorMsg = "新组名同已有组名重复";
                break;
            case FinalVar.NET_USER_EXIST:
                errorMsg = "用户已存在";
                break;
            case FinalVar.NET_USER_NOEXIST:
                errorMsg = "用户不存在";
                break;
            case FinalVar.NET_USER_RIGHTOVER:
                errorMsg = "用户权限超出组权限";
                break;
            case FinalVar.NET_USER_PWD:
                errorMsg = "保留帐号,不容许修改密码";
                break;
            case FinalVar.NET_USER_FLASEPWD:
                errorMsg = "密码不正确";
                break;
            case FinalVar.NET_USER_NOMATCHING:
                errorMsg = "密码不匹配";
                break;
            case FinalVar.NET_ERROR_GETCFG_ETHERNET:
                errorMsg = "获取网卡配置失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_WLAN:
                errorMsg = "获取无线网络信息失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_WLANDEV:
                errorMsg = "获取无线网络设备失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_REGISTER:
                errorMsg = "获取主动注册参数失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_CAMERA:
                errorMsg = "获取摄像头属性失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_INFRARED:
                errorMsg = "获取红外报警配置失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_SOUNDALARM:
                errorMsg = "获取音频报警配置失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_STORAGE:
                errorMsg = "获取存储位置配置失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_MAIL:
                errorMsg = "获取邮件配置失败";
                break;
            case FinalVar.NET_CONFIG_DEVBUSY:
                errorMsg = "暂时无法设置";
                break;
            case FinalVar.NET_CONFIG_DATAILLEGAL:
                errorMsg = "配置数据不合法";
                break;
            case FinalVar.NET_ERROR_GETCFG_DST:
                errorMsg = "获取夏令时配置失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_DST:
                errorMsg = "获取夏令时配置失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_VIDEO_OSD:
                errorMsg = "获取视频OSD叠加配置失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_VIDEO_OSD:
                errorMsg = "设置视频OSD叠加配置失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_GPRSCDMA:
                errorMsg = "获取CDMA、GPRS网络配置失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_GPRSCDMA:
                errorMsg = "设置CDMA、GPRS网络配置失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_IPFILTER:
                errorMsg = "获取IP过滤配置失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_IPFILTER:
                errorMsg = "设置IP过滤配置失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_TALKENCODE:
                errorMsg = "获取语音对讲编码配置失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_TALKENCODE:
                errorMsg = "设置语音对讲编码配置失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_RECORDLEN:
                errorMsg = "获取录像打包长度配置失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_RECORDLEN:
                errorMsg = "设置录像打包长度配置失败";
                break;
            case FinalVar.NET_DONT_SUPPORT_SUBAREA:
                errorMsg = "不支持网络硬盘分区 ";
                break;
            case FinalVar.NET_ERROR_GET_AUTOREGSERVER:
                errorMsg = "获取设备上主动注册服务器信息失败";
                break;
            case FinalVar.NET_ERROR_CONTROL_AUTOREGISTER:
                errorMsg = "主动注册重定向注册错误";
                break;
            case FinalVar.NET_ERROR_DISCONNECT_AUTOREGISTER:
                errorMsg = "断开主动注册服务器错误";
                break;
            case FinalVar.NET_ERROR_GETCFG_MMS:
                errorMsg = "获取mms配置失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_MMS:
                errorMsg = "设置mms配置失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_SMSACTIVATION:
                errorMsg = "获取短信激活无线连接配置失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_SMSACTIVATION:
                errorMsg = "设置短信激活无线连接配置失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_DIALINACTIVATION:
                errorMsg = "获取拨号激活无线连接配置失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_DIALINACTIVATION:
                errorMsg = "设置拨号激活无线连接配置失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_VIDEOOUT:
                errorMsg = "查询视频输出参数配置失败 ";
                break;
            case FinalVar.NET_ERROR_SETCFG_VIDEOOUT:
                errorMsg = "设置视频输出参数配置失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_OSDENABLE:
                errorMsg = "获取osd叠加使能配置失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_OSDENABLE:
                errorMsg = "设置osd叠加使能配置失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_ENCODERINFO:
                errorMsg = "设置数字通道前端编码接入配置失败 ";
                break;
            case FinalVar.NET_ERROR_GETCFG_TVADJUST:
                errorMsg = "获取TV调节配置失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_TVADJUST:
                errorMsg = "设置TV调节配置失败 ";
                break;
            case FinalVar.NET_ERROR_CONNECT_FAILED:
                errorMsg = "请求建立连接失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_BURNFILE:
                errorMsg = "请求刻录文件上传失败";
                break;
            case FinalVar.NET_ERROR_SNIFFER_GETCFG:
                errorMsg = "获取抓包配置信息失败";
                break;
            case FinalVar.NET_ERROR_SNIFFER_SETCFG:
                errorMsg = "设置抓包配置信息失败";
                break;
            case FinalVar.NET_ERROR_DOWNLOADRATE_GETCFG:
                errorMsg = "查询下载限制信息失败";
                break;
            case FinalVar.NET_ERROR_DOWNLOADRATE_SETCFG:
                errorMsg = "设置下载限制信息失败";
                break;
            case FinalVar.NET_ERROR_SEARCH_TRANSCOM:
                errorMsg = "查询串口参数失败";
                break;
            case FinalVar.NET_ERROR_GETCFG_POINT:
                errorMsg = "获取预制点信息错误";
                break;
            case FinalVar.NET_ERROR_SETCFG_POINT:
                errorMsg = "设置预制点信息错误";
                break;
            case FinalVar.NET_SDK_LOGOUT_ERROR:
                errorMsg = "SDK没有正常登出设备";
                break;
            case FinalVar.NET_ERROR_GET_VEHICLE_CFG:
                errorMsg = "获取车载配置失败";
                break;
            case FinalVar.NET_ERROR_SET_VEHICLE_CFG:
                errorMsg = "设置车载配置失败";
                break;
            case FinalVar.NET_ERROR_GET_ATM_OVERLAY_CFG:
                errorMsg = "获取atm叠加配置失败";
                break;
            case FinalVar.NET_ERROR_SET_ATM_OVERLAY_CFG:
                errorMsg = "设置atm叠加配置失败";
                break;
            case FinalVar.NET_ERROR_GET_ATM_OVERLAY_ABILITY:
                errorMsg = "获取atm叠加能力失败";
                break;
            case FinalVar.NET_ERROR_GET_DECODER_TOUR_CFG:
                errorMsg = "获取解码器解码轮巡配置失败";
                break;
            case FinalVar.NET_ERROR_SET_DECODER_TOUR_CFG:
                errorMsg = "设置解码器解码轮巡配置失败";
                break;
            case FinalVar.NET_ERROR_CTRL_DECODER_TOUR:
                errorMsg = "控制解码器解码轮巡失败";
                break;
            case FinalVar.NET_GROUP_OVERSUPPORTNUM:
                errorMsg = "超出设备支持最大用户组数目";
                break;
            case FinalVar.NET_USER_OVERSUPPORTNUM:
                errorMsg = "超出设备支持最大用户数目";
                break;
            case FinalVar.NET_ERROR_GET_SIP_CFG:
                errorMsg = "获取SIP配置失败";
                break;
            case FinalVar.NET_ERROR_SET_SIP_CFG:
                errorMsg = "设置SIP配置失败";
                break;
            case FinalVar.NET_ERROR_GET_SIP_ABILITY:
                errorMsg = "获取SIP能力失败";
                break;
            case FinalVar.NET_ERROR_GET_WIFI_AP_CFG:
                errorMsg = "获取WIFI ap配置失败";
                break;
            case FinalVar.NET_ERROR_SET_WIFI_AP_CFG:
                errorMsg = "设置WIFI ap配置失败";
                break;
            case FinalVar.NET_ERROR_GET_DECODE_POLICY:
                errorMsg = "获取解码策略配置失败";
                break;
            case FinalVar.NET_ERROR_SET_DECODE_POLICY:
                errorMsg = "设置解码策略配置失败";
                break;
            case FinalVar.NET_ERROR_TALK_REJECT:
                errorMsg = "拒绝对讲 ";
                break;
            case FinalVar.NET_ERROR_TALK_OPENED:
                errorMsg = "对讲被其他客户端打开";
                break;
            case FinalVar.NET_ERROR_TALK_RESOURCE_CONFLICIT:
                errorMsg = "资源冲突";
                break;
            case FinalVar.NET_ERROR_TALK_UNSUPPORTED_ENCODE:
                errorMsg = "不支持的语音编码格式";
                break;
            case FinalVar.NET_ERROR_TALK_RIGHTLESS:
                errorMsg = "无权限";
                break;
            case FinalVar.NET_ERROR_TALK_FAILED:
                errorMsg = "请求对讲失败";
                break;
            case FinalVar.NET_ERROR_GET_MACHINE_CFG:
                errorMsg = "获取机器相关配置失败";
                break;
            case FinalVar.NET_ERROR_SET_MACHINE_CFG:
                errorMsg = "设置机器相关配置失败";
                break;
            case FinalVar.NET_ERROR_GET_DATA_FAILED:
                errorMsg = "设备无法获取当前请求数据";
                break;
            case FinalVar.NET_ERROR_MAC_VALIDATE_FAILED:
                errorMsg = "MAC地址验证失败";
                break;
            case FinalVar.NET_ERROR_GET_INSTANCE:
                errorMsg = "获取服务器实例失败";
                break;
            case FinalVar.NET_ERROR_JSON_REQUEST:
                errorMsg = "生成的jason字符串错误";
                break;
            case FinalVar.NET_ERROR_JSON_RESPONSE:
                errorMsg = "响应的jason字符串错误";
                break;
            case FinalVar.NET_ERROR_VERSION_HIGHER:
                errorMsg = "协议版本低于当前使用的版本";
                break;
            case FinalVar.NET_SPARE_NO_CAPACITY:
                errorMsg = "热备操作失败, 容量不足";
                break;
            case FinalVar.NET_ERROR_SOURCE_IN_USE:
                errorMsg = "显示源被其他输出占用";
                break;
            case FinalVar.NET_ERROR_REAVE:
                errorMsg = "高级用户抢占低级用户资源";
                break;
            case FinalVar.NET_ERROR_NETFORBID:
                errorMsg = "禁止入网";
                break;
            case FinalVar.NET_ERROR_GETCFG_MACFILTER:
                errorMsg = "获取MAC过滤配置失败";
                break;
            case FinalVar.NET_ERROR_SETCFG_MACFILTER:
                errorMsg = "设置MAC过滤配置失败 ";
                break;
            case FinalVar.NET_ERROR_GETCFG_IPMACFILTER:
                errorMsg = "获取IP/MAC过滤配置失败 ";
                break;
            case FinalVar.NET_ERROR_SETCFG_IPMACFILTER:
                errorMsg = "设置IP/MAC过滤配置失败";
                break;
            case FinalVar.NET_ERROR_OPERATION_OVERTIME:
                errorMsg = "当前操作超时";
                break;
            case FinalVar.NET_ERROR_SENIOR_VALIDATE_FAILED:
                errorMsg = "高级校验失败";
                break;
            case FinalVar.NET_ERROR_DEVICE_ID_NOT_EXIST:
                errorMsg = "设备ID不存在";
                break;
            case FinalVar.NET_ERROR_UNSUPPORTED:
                errorMsg = "不支持当前操作";
                break;
            case FinalVar.NET_ERROR_SPEAK_FAILED:
                errorMsg = "请求喊话失败";
                break;
            case FinalVar.NET_ERROR_NOT_SUPPORT_F6:
                errorMsg = "设备不支持此F6接口调用";
                break;
            default:
                errorMsg = "未知错误";
                break;
        }
        return errorMsg;
    }

    /// while app disconnect with device, the interface will be invoked.
    /// 断线回调
    public static class DeviceDisConnect implements CB_fDisConnect {
        @Override
        public void invoke(long loginHandle, String deviceIp, int devicePort) {
            Log.e(TAG, "DaHua Device " + deviceIp + " is disConnected !");
        }
    }

    /// After app reconnect the device, the interface will be invoked.
    /// 重连回调
    public static class DeviceReConnect implements CB_fHaveReConnect {
        @Override
        public void invoke(long loginHandle, String deviceIp, int devicePort) {
            Log.d(TAG, "DaHua Device " + deviceIp + " is reconnect !");
        }
    }

    public static class LoginInfo {
        public long loginHandler;
        public NET_DEVICEINFO_Ex deviceInfo;

        public LoginInfo() {
        }

        public LoginInfo(long loginHandler, NET_DEVICEINFO_Ex deviceInfo) {
            this.loginHandler = loginHandler;
            this.deviceInfo = deviceInfo;
        }

        public long getLoginHandler() {
            return loginHandler;
        }

        public void setLoginHandler(long loginHandler) {
            this.loginHandler = loginHandler;
        }

        public NET_DEVICEINFO_Ex getDeviceInfo() {
            return deviceInfo;
        }

        public void setDeviceInfo(NET_DEVICEINFO_Ex deviceInfo) {
            this.deviceInfo = deviceInfo;
        }
    }
}
