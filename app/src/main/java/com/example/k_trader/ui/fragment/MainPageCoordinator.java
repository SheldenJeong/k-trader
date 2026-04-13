package com.example.k_trader.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.v4.app.FragmentActivity;
import android.util.Log;

import com.example.k_trader.base.OrderManager;
import com.example.k_trader.service.TradeJobService;

class MainPageCoordinator {

    void startTrading(FragmentActivity activity) {
        if (activity == null) {
            return;
        }

        requestBatteryOptimizationExemption(activity);
        cancelAllBuyOrdersInBackground();

        Intent serviceIntent = new Intent(activity, TradeJobService.class);
        activity.startService(serviceIntent);
    }

    void stopTrading(FragmentActivity activity) {
        if (activity == null) {
            return;
        }
        Intent serviceIntent = new Intent(activity, TradeJobService.class);
        activity.stopService(serviceIntent);
    }

    boolean syncTradingStateWithService(boolean currentUiState) {
        boolean serviceRunning = TradeJobService.isServiceRunning();
        return serviceRunning != currentUiState ? serviceRunning : currentUiState;
    }

    private void requestBatteryOptimizationExemption(FragmentActivity activity) {
        String packageName = activity.getPackageName();
        PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + packageName));
            activity.startActivity(intent);
        }
    }

    private void cancelAllBuyOrdersInBackground() {
        new Thread(() -> {
            try {
                boolean cancelled = new OrderManager().cancelAllBuyOrders();
                Log.d("KTrader", "[MainPageCoordinator] cancelAllBuyOrders: " + cancelled);
            } catch (Exception e) {
                Log.e("KTrader", "[MainPageCoordinator] failed to cancel buy orders", e);
            }
        }).start();
    }
}
