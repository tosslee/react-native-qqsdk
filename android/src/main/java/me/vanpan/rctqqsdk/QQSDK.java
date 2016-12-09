package me.vanpan.rctqqsdk;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.UiThreadUtil;
import com.facebook.react.bridge.WritableMap;
import com.tencent.connect.common.Constants;
import com.tencent.connect.share.QQShare;
import com.tencent.connect.share.QzonePublish;
import com.tencent.connect.share.QzoneShare;
import com.tencent.open.GameAppOperation;
import com.tencent.tauth.IUiListener;
import com.tencent.tauth.Tencent;
import com.tencent.tauth.UiError;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

class ShareScene {
    public static final int QQ = 0;
    public static final int QQZone = 1;
    public static final int Favorite = 2;
}
class ImageType {
    public static final int Local = 0;
    public static final int Base64 = 1;
    public static final int Network = 2;
}
public class QQSDK extends ReactContextBaseJavaModule {

    private static Tencent mTencent;
    private String appId;
    private String appName;
    private Promise mPromise;
    private static final String ACTIVITY_DOES_NOT_EXIST = "ACTIVITY_DOES_NOT_EXIST";
    private static final String QQ_Client_NOT_INSYALLED_ERROR = "QQ client is not installed";
    private static final String QQ_RESPONSE_ERROR = "QQ response is error";
    private static final String QQ_CANCEL_BY_USER = "cancelled by user";
    private static final String QZONE_SHARE_CANCEL = "QZone share is cancelled";
    private static final String QQFAVORITES_CANCEL = "QQ Favorites is cancelled";


    private final ActivityEventListener mActivityEventListener = new BaseActivityEventListener() {

        @Override
        public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent intent) {
            mTencent.onActivityResultData(requestCode,resultCode,intent,loginListener);
            if (requestCode == Constants.REQUEST_API) {
                if (resultCode == Constants.REQUEST_LOGIN) {
                    Tencent.handleResultData(intent, loginListener);
                }
            }
            if (requestCode == Constants.REQUEST_QQ_SHARE) {
                if (resultCode == Constants.ACTIVITY_OK) {
                    Tencent.handleResultData(intent, qqShareListener);
                }
            }
            if (requestCode == Constants.REQUEST_QZONE_SHARE) {
                if (resultCode == Constants.ACTIVITY_OK) {
                    Tencent.handleResultData(intent, qZoneShareListener);
                }
            }
            super.onActivityResult(activity, requestCode, resultCode, intent);
        }
    };

    public QQSDK(ReactApplicationContext reactContext) {
        super(reactContext);
        reactContext.addActivityEventListener(mActivityEventListener);
        appId = this.getAppID(reactContext);
        appName = this.getAppName(reactContext);
        if (null == mTencent) {
            mTencent = Tencent.createInstance(appId, reactContext);
        }
    }

    @Override
    public void initialize() {
        super.initialize();
    }

    @Override
    public String getName() {
        return "QQSDK";
    }

    @Override
    public void onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy();
        if (mTencent != null) {
            mTencent.releaseResource();
            mTencent = null;
        }
        appId = null;
        appName = null;
        mPromise = null;
    }

    @ReactMethod
    public void checkClientInstalled(final Promise promise) {
        Activity currentActivity = getCurrentActivity();
        if (null == currentActivity) {
            promise.reject("405",ACTIVITY_DOES_NOT_EXIST);
            return;
        }
        Boolean installed = mTencent.isSupportSSOLogin(currentActivity);
        if (installed) {
            promise.resolve(true);
        } else {
            promise.reject("404", QQ_Client_NOT_INSYALLED_ERROR);
        }
    }

    @ReactMethod
    public void logout(Promise promise) {
        Activity currentActivity = getCurrentActivity();
        if (null == currentActivity) {
            promise.reject("405",ACTIVITY_DOES_NOT_EXIST);
            return;
        }
        mTencent.logout(currentActivity);
        promise.resolve(true);
    }

    @ReactMethod
    public void ssoLogin(final Promise promise) {
        if (mTencent.isSessionValid()) {
            WritableMap map = Arguments.createMap();
            map.putString("userid", mTencent.getOpenId());
            map.putString("access_token", mTencent.getAccessToken());
            map.putDouble("expires_time", mTencent.getExpiresIn());
            promise.resolve(map);
        } else {
            final Activity currentActivity = getCurrentActivity();
            if (null == currentActivity) {
                promise.reject("405",ACTIVITY_DOES_NOT_EXIST);
                return;
            }
            Runnable runnable = new Runnable() {

                @Override
                public void run() {
                    mPromise = promise;
                    mTencent.login(currentActivity, "all",
                            loginListener);
                }
            };
            UiThreadUtil.runOnUiThread(runnable);
        }
    }
    @ReactMethod
    public void shareText(String text,int shareScene, final Promise promise) {
        final Activity currentActivity = getCurrentActivity();
        if (null == currentActivity) {
            promise.reject("405",ACTIVITY_DOES_NOT_EXIST);
            return;
        }
        mPromise = promise;
        final Bundle params = new Bundle();
        switch (shareScene) {
            case ShareScene.QQ:
                promise.reject("500","Android不支持分享文字到QQ");
                break;
            case ShareScene.Favorite:
                params.putInt(GameAppOperation.QQFAV_DATALINE_REQTYPE, GameAppOperation.QQFAV_DATALINE_TYPE_TEXT);
                params.putString(GameAppOperation.QQFAV_DATALINE_TITLE, text);
                params.putString(GameAppOperation.QQFAV_DATALINE_DESCRIPTION, text);
                params.putString(GameAppOperation.QQFAV_DATALINE_APPNAME, appName);
                Runnable favitesRunnable = new Runnable() {
                    @Override
                    public void run() {
                        mTencent.addToQQFavorites(currentActivity, params, addToQQFavoritesListener);
                    }
                };
                UiThreadUtil.runOnUiThread(favitesRunnable);
                break;
            case ShareScene.QQZone:
                params.putInt(QzoneShare.SHARE_TO_QZONE_KEY_TYPE, QzonePublish.PUBLISH_TO_QZONE_TYPE_PUBLISHMOOD);
                params.putString(QzoneShare.SHARE_TO_QQ_TITLE, text);
                Runnable zoneRunnable = new Runnable() {

                    @Override
                    public void run() {
                        mTencent.publishToQzone(currentActivity,params,qZoneShareListener);
                    }
                };
                UiThreadUtil.runOnUiThread(zoneRunnable);
                break;
            default:
                break;

        }

    }

    @ReactMethod
    public void shareImage(String image,int imageType,String title, String description,int shareScene, final Promise promise) {
        final Activity currentActivity = getCurrentActivity();
        Log.d("图片地址",image);
        if (null == currentActivity) {
            promise.reject("405",ACTIVITY_DOES_NOT_EXIST);
            return;
        }
        mPromise = promise;
        final Bundle params = new Bundle();
        switch (shareScene) {
            case ShareScene.QQ:
                params.putInt(QQShare.SHARE_TO_QQ_KEY_TYPE, QQShare.SHARE_TO_QQ_TYPE_IMAGE);
                params.putString(QQShare.SHARE_TO_QQ_IMAGE_URL,image);
                params.putString(QQShare.SHARE_TO_QQ_TITLE, title);
                params.putString(QQShare.SHARE_TO_QQ_SUMMARY, description);
                Runnable qqRunnable = new Runnable() {

                    @Override
                    public void run() {
                        mTencent.shareToQQ(currentActivity,params,qqShareListener);
                    }
                };
                UiThreadUtil.runOnUiThread(qqRunnable);
                break;
            case ShareScene.Favorite:
                params.putInt(GameAppOperation.QQFAV_DATALINE_REQTYPE, GameAppOperation.QQFAV_DATALINE_TYPE_IMAGE_TEXT);
                params.putString(GameAppOperation.QQFAV_DATALINE_TITLE, title);
                params.putString(GameAppOperation.QQFAV_DATALINE_DESCRIPTION, description);
                params.putString(GameAppOperation.QQFAV_DATALINE_APPNAME, appName);
                Runnable favitesRunnable = new Runnable() {
                    @Override
                    public void run() {
                        mTencent.addToQQFavorites(currentActivity, params, addToQQFavoritesListener);
                    }
                };
                UiThreadUtil.runOnUiThread(favitesRunnable);
                break;
            case ShareScene.QQZone:
                params.putInt(QQShare.SHARE_TO_QQ_KEY_TYPE, QQShare.SHARE_TO_QQ_TYPE_DEFAULT);
                params.putString(QQShare.SHARE_TO_QQ_TITLE, title);
                params.putInt(QQShare.SHARE_TO_QQ_EXT_INT, QQShare.SHARE_TO_QQ_FLAG_QZONE_AUTO_OPEN);
                Runnable zoneRunnable = new Runnable() {

                    @Override
                    public void run() {
                        mPromise = promise;
                        mTencent.shareToQQ(currentActivity,params,qZoneShareListener);
                    }
                };
                UiThreadUtil.runOnUiThread(zoneRunnable);
                break;
            default:
                break;

        }
    }

    @ReactMethod
    public void shareNews(String url,String image,int imageType,String title, String description,int shareScene, final Promise promise) {
        final Activity currentActivity = getCurrentActivity();
        if (null == currentActivity) {
            promise.reject("405",ACTIVITY_DOES_NOT_EXIST);
            return;
        }
        mPromise = promise;
        final Bundle params = new Bundle();
        switch (shareScene) {
            case ShareScene.QQ:
                params.putInt(QQShare.SHARE_TO_QQ_KEY_TYPE, QQShare.SHARE_TO_QQ_TYPE_DEFAULT);
                if(imageType == ImageType.Network) {
                    params.putString(QQShare.SHARE_TO_QQ_IMAGE_URL,image);
                } else if (imageType == ImageType.Local) {
                    params.putString(QQShare.SHARE_TO_QQ_IMAGE_LOCAL_URL,image);
                }
                params.putString(QQShare.SHARE_TO_QQ_TITLE, title);
                params.putString(QQShare.SHARE_TO_QQ_TARGET_URL, url);
                params.putString(QQShare.SHARE_TO_QQ_SUMMARY, description);
                Runnable qqRunnable = new Runnable() {

                    @Override
                    public void run() {
                        mTencent.shareToQQ(currentActivity,params,qqShareListener);
                    }
                };
                UiThreadUtil.runOnUiThread(qqRunnable);
                break;
            case ShareScene.Favorite:
                params.putInt(GameAppOperation.QQFAV_DATALINE_REQTYPE, GameAppOperation.QQFAV_DATALINE_TYPE_DEFAULT);
                params.putString(GameAppOperation.QQFAV_DATALINE_TITLE, title);
                params.putString(GameAppOperation.QQFAV_DATALINE_DESCRIPTION,description);
                params.putString(GameAppOperation.QQFAV_DATALINE_IMAGEURL,image);
                params.putString(GameAppOperation.QQFAV_DATALINE_URL,url);
                params.putString(GameAppOperation.QQFAV_DATALINE_APPNAME, appName);
                Runnable favitesRunnable = new Runnable() {
                    @Override
                    public void run() {
                        mTencent.addToQQFavorites(currentActivity, params, addToQQFavoritesListener);
                    }
                };
                UiThreadUtil.runOnUiThread(favitesRunnable);
                break;
            case ShareScene.QQZone:
                ArrayList<String> imageUrls = new ArrayList<String>();
                imageUrls.add(image);
                params.putInt(QzoneShare.SHARE_TO_QZONE_KEY_TYPE, QzoneShare.SHARE_TO_QZONE_TYPE_IMAGE_TEXT);
                params.putString(QzoneShare.SHARE_TO_QQ_TITLE, title);
                params.putString(QzoneShare.SHARE_TO_QQ_SUMMARY,description);
                params.putString(QzoneShare.SHARE_TO_QQ_TARGET_URL,url);
                params.putStringArrayList(QzoneShare.SHARE_TO_QQ_IMAGE_URL,imageUrls);
                Runnable zoneRunnable = new Runnable() {

                    @Override
                    public void run() {
                        mTencent.shareToQzone(currentActivity,params,qZoneShareListener);
                    }
                };
                UiThreadUtil.runOnUiThread(zoneRunnable);
                break;
            default:
                break;

        }

    }

    @ReactMethod
    public void shareAudio(String url,String flashUrl,String image,int imageType,String title, String description,int shareScene, final Promise promise) {
        final Activity currentActivity = getCurrentActivity();
        if (null == currentActivity) {
            promise.reject("405",ACTIVITY_DOES_NOT_EXIST);
            return;
        }
        mPromise = promise;
        final Bundle params = new Bundle();
        switch (shareScene) {
            case ShareScene.QQ:
                params.putInt(QQShare.SHARE_TO_QQ_KEY_TYPE, QQShare.SHARE_TO_QQ_TYPE_DEFAULT);
                if(imageType == ImageType.Network) {
                    params.putString(QQShare.SHARE_TO_QQ_IMAGE_URL,image);
                } else if (imageType == ImageType.Local) {
                    params.putString(QQShare.SHARE_TO_QQ_IMAGE_LOCAL_URL,image);
                }
                params.putString(QQShare.SHARE_TO_QQ_AUDIO_URL, flashUrl);
                params.putString(QQShare.SHARE_TO_QQ_TITLE, title);
                params.putString(QQShare.SHARE_TO_QQ_TARGET_URL, url);
                params.putString(QQShare.SHARE_TO_QQ_SUMMARY, description);
                Runnable qqRunnable = new Runnable() {

                    @Override
                    public void run() {
                        mTencent.shareToQQ(currentActivity,params,qqShareListener);
                    }
                };
                UiThreadUtil.runOnUiThread(qqRunnable);
                break;
            case ShareScene.Favorite:
                params.putInt(GameAppOperation.QQFAV_DATALINE_REQTYPE, GameAppOperation.QQFAV_DATALINE_TYPE_DEFAULT);
                params.putString(GameAppOperation.QQFAV_DATALINE_TITLE, title);
                params.putString(GameAppOperation.QQFAV_DATALINE_DESCRIPTION,description);
                params.putString(GameAppOperation.QQFAV_DATALINE_IMAGEURL,image);
                params.putString(GameAppOperation.QQFAV_DATALINE_URL,url);
                params.putString(GameAppOperation.QQFAV_DATALINE_APPNAME, appName);
                params.putString(GameAppOperation.QQFAV_DATALINE_AUDIOURL,flashUrl);
                Runnable favitesRunnable = new Runnable() {
                    @Override
                    public void run() {
                        mTencent.addToQQFavorites(currentActivity, params, addToQQFavoritesListener);
                    }
                };
                UiThreadUtil.runOnUiThread(favitesRunnable);
                break;
            case ShareScene.QQZone:
                ArrayList<String> imageUrls = new ArrayList<String>();
                imageUrls.add(image);
                params.putInt(QzoneShare.SHARE_TO_QZONE_KEY_TYPE, QzoneShare.SHARE_TO_QZONE_TYPE_IMAGE_TEXT);
                params.putString(QzoneShare.SHARE_TO_QQ_TITLE, title);
                params.putString(QzoneShare.SHARE_TO_QQ_SUMMARY,description);
                params.putString(QzoneShare.SHARE_TO_QQ_TARGET_URL,url);
                params.putString(QzoneShare.SHARE_TO_QQ_AUDIO_URL,flashUrl);
                params.putStringArrayList(QzoneShare.SHARE_TO_QQ_IMAGE_URL,imageUrls);
                Runnable zoneRunnable = new Runnable() {

                    @Override
                    public void run() {
                        mTencent.shareToQzone(currentActivity,params,qZoneShareListener);
                    }
                };
                UiThreadUtil.runOnUiThread(zoneRunnable);
                break;
            default:
                break;

        }
    }
    @ReactMethod
    public void shareVideo(String url,String flashUrl,String image,int imageType,String title, String description,int shareScene, final Promise promise) {
        final Activity currentActivity = getCurrentActivity();
        if (null == currentActivity) {
            promise.reject("405",ACTIVITY_DOES_NOT_EXIST);
            return;
        }
        mPromise = promise;
        final Bundle params = new Bundle();
        switch (shareScene) {
            case ShareScene.QQ:
                promise.reject("500","Android不支持分享视频到QQ");
                break;
            case ShareScene.Favorite:
                promise.reject("500","Android不支持收藏视频到QQ");
                break;
            case ShareScene.QQZone:
                ArrayList<String> imageUrls = new ArrayList<String>();
                imageUrls.add(image);
                params.putInt(QzoneShare.SHARE_TO_QZONE_KEY_TYPE, QzonePublish.PUBLISH_TO_QZONE_TYPE_PUBLISHVIDEO);
                params.putString(QzonePublish.PUBLISH_TO_QZONE_IMAGE_URL, image);
                params.putString(QzonePublish.PUBLISH_TO_QZONE_SUMMARY,description);
                params.putString(QzonePublish.PUBLISH_TO_QZONE_VIDEO_PATH,flashUrl);
                Runnable zoneRunnable = new Runnable() {

                    @Override
                    public void run() {
                        mTencent.shareToQzone(currentActivity,params,qZoneShareListener);
                    }
                };
                UiThreadUtil.runOnUiThread(zoneRunnable);
                break;
            default:
                break;

        }
    }
    @Nullable
    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put("Local", ImageType.Local);
        constants.put("Base64", ImageType.Base64);
        constants.put("Network", ImageType.Network);
        constants.put("QQ", ShareScene.QQ);
        constants.put("QQZone", ShareScene.QQZone);
        constants.put("Favorite", ShareScene.Favorite);
        return constants;
    }

    /**
     * 获取Tencent SDK App ID
     * @param reactContext
     * @return
     */
    private String getAppID(ReactApplicationContext reactContext) {
        try {
            ApplicationInfo appInfo = reactContext.getPackageManager()
                    .getApplicationInfo(reactContext.getPackageName(),
                            PackageManager.GET_META_DATA);
            String key = appInfo.metaData.get("QQ_APP_ID").toString();
            return key;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取应用的名称
     * @param reactContext
     * @return
     */
    private String getAppName(ReactApplicationContext reactContext) {
        PackageManager packageManager = reactContext.getPackageManager();
        ApplicationInfo applicationInfo = null;
        try {
            applicationInfo = packageManager.getApplicationInfo(reactContext.getPackageName(), 0);
        } catch (final PackageManager.NameNotFoundException e) {}
        final String AppName = (String)((applicationInfo != null) ? packageManager.getApplicationLabel(applicationInfo) : "AppName");
        return AppName;
    }

    /**
     * 保存token 和 openid
     *
     * @param jsonObject
     */
    public static void initOpenidAndToken(JSONObject jsonObject) {
        try {
            String token = jsonObject.getString(Constants.PARAM_ACCESS_TOKEN);
            String expires = jsonObject.getString(Constants.PARAM_EXPIRES_IN);
            String openId = jsonObject.getString(Constants.PARAM_OPEN_ID);
            if (!TextUtils.isEmpty(token) && !TextUtils.isEmpty(expires)
                    && !TextUtils.isEmpty(openId)) {
                mTencent.setAccessToken(token, expires);
                mTencent.setOpenId(openId);
            }
        } catch (Exception e) {
        }
    }

    /**
     * 登录监听
     */
    IUiListener loginListener = new IUiListener() {
        @Override
        public void onComplete(Object response) {
            if (null == response) {
                mPromise.reject("403",QQ_RESPONSE_ERROR);
                return;
            }
            JSONObject jsonResponse = (JSONObject) response;
            if (null != jsonResponse && jsonResponse.length() == 0) {
                mPromise.reject("403",QQ_RESPONSE_ERROR);
                return;
            }
            initOpenidAndToken(jsonResponse);
            WritableMap map = Arguments.createMap();
            map.putString("userid", mTencent.getOpenId());
            map.putString("access_token", mTencent.getAccessToken());
            map.putDouble("expires_time", mTencent.getExpiresIn());
            mPromise.resolve(map);

        }

        @Override
        public void onError(UiError e) {
            String msg = String.format("[%1$d]%2$s: %3$s", e.errorCode, e.errorMessage, e.errorDetail);
            mPromise.reject("500",e.errorMessage);
        }

        @Override
        public void onCancel() {
            mPromise.reject("500",QQ_CANCEL_BY_USER);
        }
    };

    /**
     * QQ分享监听
     */
    IUiListener qqShareListener = new IUiListener() {
        @Override
        public void onCancel() {
            mPromise.reject("500",QQ_CANCEL_BY_USER);
        }

        @Override
        public void onComplete(Object response) {
            mPromise.resolve(true);
        }

        @Override
        public void onError(UiError e) {
            mPromise.reject("500",e.errorMessage);
        }

    };
    /**
     * QQZONE 分享监听
     */
    IUiListener qZoneShareListener = new IUiListener() {

        @Override
        public void onCancel() {
            mPromise.reject("500",QZONE_SHARE_CANCEL);
        }

        @Override
        public void onError(UiError e) {
            mPromise.reject("500",e.errorMessage);
        }

        @Override
        public void onComplete(Object response) {
            mPromise.resolve(true);
        }

    };
    /**
     * 添加到QQ收藏监听
     */
    IUiListener addToQQFavoritesListener = new IUiListener() {
        @Override
        public void onCancel() {
            mPromise.reject("500",QQFAVORITES_CANCEL);
        }

        @Override
        public void onComplete(Object response) {
            mPromise.resolve(true);
        }

        @Override
        public void onError(UiError e) {
            mPromise.reject("500",e.errorMessage);
        }
    };
}

