/*
 * Copyright (c) 2011-2019, Zingaya, Inc. All rights reserved.
 */

package com.voximplant.foregroundservice;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import androidx.annotation.Nullable;
import android.Manifest;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.facebook.react.modules.core.PermissionAwareActivity;
import com.facebook.react.modules.core.PermissionListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import static com.voximplant.foregroundservice.Constants.ERROR_INVALID_CONFIG;
import static com.voximplant.foregroundservice.Constants.ERROR_SERVICE_ERROR;
import static com.voximplant.foregroundservice.Constants.NOTIFICATION_CONFIG;
import static com.voximplant.foregroundservice.Constants.FOREGROUND_SERVICE_BUTTON_PRESSED;
import static com.voximplant.foregroundservice.Constants.ERROR_FGS_TYPE_MISSING;

public class VIForegroundServiceModule extends ReactContextBaseJavaModule {

    class ForegroundReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            buttonPressedEvent();
        }
    }

    private final ReactApplicationContext reactContext;
    private ForegroundReceiver foregroundReceiver = new ForegroundReceiver();

    public VIForegroundServiceModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    public void requestNotificationPermission(final Promise promise) {
        Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            promise.reject("E_ACTIVITY_DOES_NOT_EXIST", "Activity doesn't exist");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(currentActivity, Manifest.permission.POST_NOTIFICATIONS) 
                    == PackageManager.PERMISSION_GRANTED) {
                // Permission is already granted
                promise.resolve(true);
                return;
            }

            // We need to request the permission
            PermissionAwareActivity permissionAwareActivity;
            try {
                permissionAwareActivity = (PermissionAwareActivity) currentActivity;
            } catch (ClassCastException e) {
                promise.reject("E_CANNOT_CAST", "Unable to cast activity to PermissionAwareActivity");
                return;
            }

            permissionAwareActivity.requestPermissions(
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_NOTIFICATION_PERMISSION,
                new PermissionListener() {
                    @Override
                    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
                        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
                            boolean permissionGranted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
                            promise.resolve(permissionGranted);
                            return true;
                        }
                        return false;
                    }
                }
            );
        } else {
            // For Android versions less than 13, notification permissions are granted by default
            promise.resolve(true);
        }
    }

    @ReactMethod
    public void addListener(String eventName) {
        // Set up any upstream listeners or background tasks as necessary
    }

    @ReactMethod
    public void removeListeners(Integer count) {
        // Remove upstream listeners, stop unnecessary background tasks
    }

    @Override
    public String getName() {
        return "VIForegroundService";
    }

    @ReactMethod
    public void checkAndRequestNotificationPermission(final Promise promise) {
        requestNotificationPermission(promise);
    }

    @ReactMethod
    public void createNotificationChannel(ReadableMap channelConfig, Promise promise) {
        if (channelConfig == null) {
            promise.reject(ERROR_INVALID_CONFIG, "VIForegroundService: Channel config is invalid");
            return;
        }
        NotificationHelper.getInstance(getReactApplicationContext()).createNotificationChannel(channelConfig, promise);
    }

    @ReactMethod
    public void startService(ReadableMap notificationConfig,  @Nullable Integer foregroundServiceType, Promise promise) {
        // ask for notification permissions here for api level 33
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (foregroundServiceType == null) {
                promise.reject(ERROR_FGS_TYPE_MISSING, "VIForegroundService: Foreground service type is required");
                return;
            }
        }

        if (notificationConfig == null) {
            promise.reject(ERROR_INVALID_CONFIG, "VIForegroundService: Notification config is invalid");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // if device is running android 8 and above
            if (!notificationConfig.hasKey("channelId")) {
                promise.reject(ERROR_INVALID_CONFIG, "VIForegroundService: channelId is required");
                return;
            }
        }

        if (!notificationConfig.hasKey("id")) {
            promise.reject(ERROR_INVALID_CONFIG , "VIForegroundService: id is required");
            return;
        }

        if (!notificationConfig.hasKey("icon")) {
            promise.reject(ERROR_INVALID_CONFIG, "VIForegroundService: icon is required");
            return;
        }

        if (!notificationConfig.hasKey("title")) {
            promise.reject(ERROR_INVALID_CONFIG, "VIForegroundService: title is reqired");
            return;
        }

        if (!notificationConfig.hasKey("text")) {
            promise.reject(ERROR_INVALID_CONFIG, "VIForegroundService: text is required");
            return;
        }

        Intent intent = new Intent(getReactApplicationContext(), VIForegroundService.class);
        intent.setAction(Constants.ACTION_FOREGROUND_SERVICE_START);
        intent.putExtra(NOTIFICATION_CONFIG, Arguments.toBundle(notificationConfig));
        intent.putExtra("foregroundServiceType", foregroundServiceType);
        ComponentName componentName = getReactApplicationContext().startService(intent);

        IntentFilter filter = new IntentFilter();
        filter.addAction(FOREGROUND_SERVICE_BUTTON_PRESSED);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getReactApplicationContext().registerReceiver(
                foregroundReceiver, 
                filter, 
                null, 
                null, 
                Context.RECEIVER_NOT_EXPORTED
            );
        } else { 
            getReactApplicationContext().registerReceiver(foregroundReceiver, filter);
        }

        if (componentName != null) {
            promise.resolve(null);
        } else {
            promise.reject(ERROR_SERVICE_ERROR, "VIForegroundService: Foreground service is not started");
        }
    }

    @ReactMethod
    public void stopService(Promise promise) {
        Intent intent = new Intent(getReactApplicationContext(), VIForegroundService.class);
        intent.setAction(Constants.ACTION_FOREGROUND_SERVICE_STOP);
        boolean stopped = getReactApplicationContext().stopService(intent);
        if (stopped) {
            promise.resolve(null);
        } else {
            promise.reject(ERROR_SERVICE_ERROR, "VIForegroundService: Foreground service failed to stop");
        }
    }

    public void buttonPressedEvent() {
        WritableMap params = Arguments.createMap();
        params.putString("event", FOREGROUND_SERVICE_BUTTON_PRESSED);
        sendEvent("VIForegroundServiceButtonPressed", params);
    }

    private void sendEvent(String eventName, @Nullable WritableMap params) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }
}
