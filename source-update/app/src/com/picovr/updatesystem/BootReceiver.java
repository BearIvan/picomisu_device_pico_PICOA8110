// Picomisu Source Update: after boot, read the last updater result and check the server once the
// network is up (keeps the Settings red dot, pvr_update, current). With Auto Download on, a found
// package is downloaded while the headset charges on an unmetered network, as PICO's auto download.
package com.picovr.updatesystem;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.BatteryManager;
import android.provider.Settings;
import android.text.TextUtils;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        UpdateModel model = UpdateModel.get(context);
        model.refreshLastResult();
        if (TextUtils.isEmpty(model.serverUrl()) || !model.isAcceptUpdate()) return;
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED).build();
        cm.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                cm.unregisterNetworkCallback(this);
                model.addListener(new UpdateModel.Listener() {
                    @Override
                    public void onModelChanged() {
                        if (model.state() != UpdateModel.State.NEW_VERSION) return;
                        model.removeListener(this);
                        if (autoDownload(context) && charging(context) && !cm.isActiveNetworkMetered()) {
                            model.startDownload();
                        }
                    }
                });
                model.check();
            }
        });
    }

    private static boolean autoDownload(Context context) {
        int value = Settings.Global.getInt(context.getContentResolver(), UpdateModel.KEY_AUTO_DOWNLOAD, -1);
        return value == -1 ? UpdateModel.get(context).isAcceptUpdate() : value == 1;
    }

    private static boolean charging(Context context) {
        BatteryManager bm = context.getSystemService(BatteryManager.class);
        return bm != null && bm.isCharging();
    }
}
