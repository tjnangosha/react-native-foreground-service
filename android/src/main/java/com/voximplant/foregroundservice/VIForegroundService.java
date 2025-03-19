/*
 * Copyright (c) 2011-2019, Zingaya, Inc. All rights reserved.
 */

package com.voximplant.foregroundservice;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Build;
import android.content.pm.ServiceInfo;

import static com.voximplant.foregroundservice.Constants.NOTIFICATION_CONFIG;

public class VIForegroundService extends Service {

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if (action != null) {
            if (action.equals(Constants.ACTION_FOREGROUND_SERVICE_START)) {
                if (intent.getExtras() != null && intent.getExtras().containsKey(NOTIFICATION_CONFIG)) {
                    Bundle notificationConfig = intent.getExtras().getBundle(NOTIFICATION_CONFIG);
                    int foregroundServiceType = intent.getIntExtra("foregroundServiceType", 1);

                    // get the callback id
                    String callbackId = intent.getStringExtra("callbackId");

                    if (notificationConfig != null && notificationConfig.containsKey("id")) {
                        Notification notification = NotificationHelper.getInstance(getApplicationContext())
                                .buildNotification(getApplicationContext(), notificationConfig);

                       if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // android 14 and above
                            startForeground((int)notificationConfig.getDouble("id"), notification, foregroundServiceType);
                        } else {
                            startForeground((int)notificationConfig.getDouble("id"), notification);
                        }
                    }

                    // add some more code to handle some more event stuff
                    ReactInstanceManager reactInstanceManager = ((ReactApplication) getApplication()).getReactNativeHost().getReactInstanceManager();
                    ReactContext reactContext = ReactInstanceManager.getCurrentReactContext();
                    if (reactContext != null) {
                        VIForegroundServiceModule.emitEvent(reactContext, "SIGNAL_LOCATION_TRACK_START");
                    }

                    VIForegroundServiceModule.emitEvent("SIGNAL_LOCATION_TRACK_START");
                }
            } else if (action.equals(Constants.ACTION_FOREGROUND_SERVICE_STOP)) {
                stopSelf();
            }
        }
        return START_NOT_STICKY;

    }
}