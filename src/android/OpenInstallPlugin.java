package io.openinstall.cordova;

import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import io.openinstall.api.OpData;
import io.openinstall.api.OpError;
import io.openinstall.api.OpenInstall;
import io.openinstall.api.ResultCallBack;

public class OpenInstallPlugin extends CordovaPlugin {

    private static final String TAG = "OpenInstallPlugin";

    private static final String METHOD_CONFIG = "config";
    private static final String METHOD_SERIAL_ENABLED = "serialEnabled";
    private static final String METHOD_CLIPBOARD_ENABLED = "clipBoardEnabled";
    private static final String METHOD_INIT = "init";
    private static final String METHOD_INSTALL = "getInstall";
    private static final String METHOD_INSTALL_RETRY = "getInstallCanRetry";
    private static final String METHOD_WAKEUP = "registerWakeUpHandler";
    private static final String METHOD_REGISTER = "reportRegister";
    private static final String METHOD_EFFECT = "reportEffectPoint";
    private static final String METHOD_SHARE = "reportShare";

    private CallbackContext wakeupCallbackContext = null;
    private JSONObject pendingConfig = null;

    @Override
    protected void pluginInitialize() {
        super.pluginInitialize();
        // Initialize can be called from JS later; keep pluginInit lightweight.
    }

    @Override
    public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) throws JSONException {
        Log.d(TAG, "execute # action=" + action);
        if (TextUtils.isEmpty(action)) {
            return false;
        }
        if (METHOD_CONFIG.equals(action)) {
            config(args, callbackContext);
            return true;
        } else if (METHOD_SERIAL_ENABLED.equals(action)) {
            serialEnabled(args, callbackContext);
            return true;
        } else if (METHOD_CLIPBOARD_ENABLED.equals(action)) {
            clipBoardEnabled(args, callbackContext);
            return true;
        } else if (METHOD_INIT.equals(action)) {
            init(callbackContext);
            return true;
        } else if (METHOD_INSTALL.equals(action)) {
            getInstall(args, callbackContext);
            return true;
        } else if (METHOD_INSTALL_RETRY.equals(action)) {
            getInstallCanRetry(args, callbackContext);
            return true;
        } else if (METHOD_WAKEUP.equals(action)) {
            registerWakeUpHandler(callbackContext);
            return true;
        } else if (METHOD_REGISTER.equals(action)) {
            reportRegister(args, callbackContext);
            return true;
        } else if (METHOD_EFFECT.equals(action)) {
            reportEffectPoint(args, callbackContext);
            return true;
        } else if (METHOD_SHARE.equals(action)) {
            reportShare(args, callbackContext);
            return true;
        }
        return false;
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (wakeupCallbackContext != null) {
            getWakeUp(intent, wakeupCallbackContext);
        }
    }

    protected void config(CordovaArgs args, final CallbackContext callbackContext) {
        if (args != null && !args.isNull(0)) {
            pendingConfig = args.optJSONObject(0);
        } else {
            pendingConfig = new JSONObject();
        }
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.NO_RESULT));
    }

    private void serialEnabled(CordovaArgs args, final CallbackContext callbackContext) {
        // v1.0.0 SDK does not expose a dedicated "serial" switch; keep API for
        // compatibility.
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.NO_RESULT));
    }

    private void clipBoardEnabled(CordovaArgs args, final CallbackContext callbackContext) {
        boolean enabled = true;
        if (args != null && !args.isNull(0)) {
            // CordovaArgs#optBoolean 只接受一个参数，不能传默认值
            try {
                enabled = args.getBoolean(0);
            } catch (JSONException e) {
                enabled = true;
            }
        }
        if (!enabled) {
            OpenInstall.getInstance().disableFetchClipData();
        }
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.NO_RESULT));
    }

    protected void init(CallbackContext callbackContext) {
        // Initialize SDK. AppKey is provided via AndroidManifest meta-data:
        // com.openinstall.APP_KEY
        OpenInstall.initialize(cordova.getActivity().getApplicationContext());

        // Optional compatibility config (best-effort)
        if (pendingConfig != null) {
            if (pendingConfig.has("androidId")) {
                String androidId = pendingConfig.optString("androidId", null);
                if (!TextUtils.isEmpty(androidId)) {
                    OpenInstall.getInstance().setAndroidId(androidId);
                }
            }
            if (pendingConfig.optBoolean("clipBoardDisabled", false)
                    || pendingConfig.optBoolean("clipboardDisabled", false)) {
                OpenInstall.getInstance().disableFetchClipData();
            }
            if (pendingConfig.optBoolean("autoCollectDisabled", false)) {
                OpenInstall.getInstance().setAutoCollect(false);
            }
            if (pendingConfig.optBoolean("simulatorDisabled", false)) {
                OpenInstall.getInstance().disableCheckSimulator();
            }
        }

        OpenInstall.getInstance().start(cordova.getActivity());

        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.NO_RESULT));
    }

    protected void getInstall(CordovaArgs args, final CallbackContext callbackContext) {
        int timeout = 10;
        if (args != null && !args.isNull(0)) {
            timeout = args.optInt(0);
        }
        OpenInstall.getInstance().getInstallParam(timeout, new ResultCallBack<OpData>() {
            @Override
            public void onResult(OpData opData) {
                Log.d(TAG, "getInstallParam # " + String.valueOf(opData));
                callbackContext.success(parseData(opData));
            }

            @Override
            public void onError(OpError opError) {
                callbackContext.error(String.valueOf(opError));
            }
        });
    }

    protected void getInstallCanRetry(CordovaArgs args, final CallbackContext callbackContext) {
        int timeout = 3;
        if (args != null && !args.isNull(0)) {
            timeout = args.optInt(0);
        }
        OpenInstall.getInstance().getInstallParam(timeout, new ResultCallBack<OpData>() {
            @Override
            public void onResult(OpData opData) {
                JSONObject jsonObject = parseData(opData);
                try {
                    jsonObject.put("retry", false);
                } catch (JSONException ignore) {
                }
                callbackContext.success(jsonObject);
            }

            @Override
            public void onError(OpError opError) {
                callbackContext.error(String.valueOf(opError));
            }
        });
    }

    protected void registerWakeUpHandler(CallbackContext callbackContext) {
        this.wakeupCallbackContext = callbackContext;
        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);
        // 首次调用，程序启动时
        Intent intent = cordova.getActivity().getIntent();
        getWakeUp(intent, wakeupCallbackContext);
    }

    private void getWakeUp(Intent intent, final CallbackContext callbackContext) {
        OpenInstall.getInstance().handleDeepLink(intent, new ResultCallBack<OpData>() {
            @Override
            public void onResult(OpData opData) {
                JSONObject jsonObject = parseData(opData);
                PluginResult result = new PluginResult(PluginResult.Status.OK, jsonObject);
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);
            }

            @Override
            public void onError(OpError opError) {
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, String.valueOf(opError));
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);
            }
        });
    }

    protected void reportRegister(CordovaArgs args, final CallbackContext callbackContext) {
        OpenInstall.getInstance().register();
    }

    protected void reportEffectPoint(CordovaArgs args, final CallbackContext callbackContext) {
        if (args != null && !args.isNull(0) && !args.isNull(1)) {
            String pointId = args.optString(0);
            long pointValue = args.optLong(1);
            Log.d(TAG, "reportEffectPoint # pointId:" + pointId + ", pointValue:" + pointValue);
            Map<String, String> extraMap = new HashMap<String, String>();
            if (!args.isNull(2)) {
                JSONObject jsonObject = args.optJSONObject(2);
                Iterator<String> keys = jsonObject.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    extraMap.put(key, jsonObject.optString(key));
                }
            }
            OpenInstall.getInstance().saveEvent(pointId, pointValue, extraMap);
        }
    }

    protected void reportShare(CordovaArgs args, final CallbackContext callbackContext) {
        if (args != null && !args.isNull(0) && !args.isNull(1)) {
            String shareCode = args.optString(0);
            String sharePlatform = args.optString(1);
            Log.d(TAG, "reportShare # shareCode:" + shareCode + ", sharePlatform:" + sharePlatform);
            OpenInstall.getInstance().reportShare(shareCode, sharePlatform, new ResultCallBack<Boolean>() {
                @Override
                public void onResult(Boolean ok) {
                    callbackContext.success(ok != null && ok ? 1 : 0);
                }

                @Override
                public void onError(OpError opError) {
                    callbackContext.error(String.valueOf(opError));
                }
            });
        } else {
            callbackContext.error("参数错误");
        }
    }

    private JSONObject parseData(OpData opData) {
        JSONObject jsonObject = new JSONObject();
        if (opData != null) {
            try {
                jsonObject.put("channel", opData.getChannelCode());
                // bindData 是一个 JSON 字符串，这里直接原样透传给 JS，由 JS 再做 JSON.parse
                jsonObject.put("data", opData.getBindData());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return jsonObject;
    }

}
