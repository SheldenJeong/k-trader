package com.example.k_trader.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.TextView;

import com.example.k_trader.base.GlobalSettings;
import com.example.k_trader.database.CoinPriceInfoRepository;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

class CoinInfoController {

    String updateCoinType(Context context, TextView textCoinType, CoinPriceInfoRepository coinPriceInfoRepository) {
        if (context == null || textCoinType == null) {
            Log.w("KTrader", "[CoinInfoController] context or textCoinType is null");
            return null;
        }

        SharedPreferences sharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        String coinType = sharedPreferences.getString(GlobalSettings.COIN_TYPE_KEY_NAME, GlobalSettings.COIN_TYPE_DEFAULT_VALUE);

        GlobalSettings.getInstance().setCoinType(coinType);
        textCoinType.setText(coinType);

        if (coinPriceInfoRepository != null) {
            coinPriceInfoRepository.savePriceInfo(coinType, "", "")
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            () -> Log.d("KTrader", "[CoinInfoController] coin type saved"),
                            error -> Log.e("KTrader", "[CoinInfoController] failed to save coin type", error)
                    );
        }
        return coinType;
    }

    void applyPriceChangeColor(TextView textPriceChange, String priceChange, int plusColor, int minusColor, int neutralColor) {
        if (textPriceChange == null || priceChange == null) {
            return;
        }
        textPriceChange.setText(priceChange);
        if (priceChange.startsWith("+")) {
            textPriceChange.setTextColor(plusColor);
        } else if (priceChange.startsWith("-")) {
            textPriceChange.setTextColor(minusColor);
        } else {
            textPriceChange.setTextColor(neutralColor);
        }
    }
}
