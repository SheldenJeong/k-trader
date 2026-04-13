package com.example.k_trader.base;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.example.k_trader.KTraderApplication;
import com.example.k_trader.util.LogInfoFormatter;

import org.json.simple.JSONObject;

import java.util.Calendar;
import java.util.Locale;

class OrderManagerErrorReporter {

    boolean hasValidApiStatus(JSONObject result, String tag, String endpoint, String errorType, String errorDescription) {
        if (result == null) {
            LogInfoFormatter.logInfo(tag + " : " + endpoint + " : null");
            sendErrorCard(errorType, errorDescription, endpoint, "NULL_RESPONSE", "API 응답이 null입니다");
            return false;
        }

        Log.d("KTrader", "[OrderManager] API 응답 (" + endpoint + "): " + result);

        if (result.get("status") instanceof Long) {
            LogInfoFormatter.logInfo(tag + " : " + endpoint + " : " + result);
            sendErrorCard(errorType, errorDescription, endpoint, "INVALID_STATUS_TYPE", result.toString());
            return false;
        }

        String status = String.valueOf(result.get("status"));
        if (!"0000".equals(status)) {
            String serverMessage = result.get("message") != null ? result.get("message").toString() : "unknown error";
            LogInfoFormatter.logInfo(tag + " : " + endpoint + " : " + result);
            LogInfoFormatter.logInfo(tag + " : API 오류 상세 - Status: " + status + ", Message: " + serverMessage);
            sendErrorCard(errorType, errorDescription, endpoint, status, serverMessage);
            return false;
        }
        return true;
    }

    void sendErrorCard(String errorType, String errorMessage, String apiEndpoint, String errorCode, String serverErrorMessage) {
        try {
            Calendar currentTime = Calendar.getInstance();
            String errorTime = String.format(Locale.getDefault(), "%d/%02d/%02d %02d:%02d:%02d",
                    currentTime.get(Calendar.YEAR), currentTime.get(Calendar.MONTH) + 1, currentTime.get(Calendar.DATE),
                    currentTime.get(Calendar.HOUR_OF_DAY), currentTime.get(Calendar.MINUTE), currentTime.get(Calendar.SECOND));

            Intent intent = new Intent("TRADE_ERROR_CARD");
            intent.putExtra("errorTime", errorTime);
            intent.putExtra("errorType", errorType);
            intent.putExtra("errorMessage", errorMessage);
            intent.putExtra("apiEndpoint", apiEndpoint != null ? apiEndpoint : "/unknown");
            intent.putExtra("errorCode", errorCode != null ? errorCode : "Unknown");
            intent.putExtra("serverErrorMessage", serverErrorMessage != null ? serverErrorMessage : errorMessage);

            if (KTraderApplication.getAppContext() != null) {
                LocalBroadcastManager.getInstance(KTraderApplication.getAppContext()).sendBroadcast(intent);
            }
        } catch (Exception e) {
            Log.e("OrderManager", "에러 카드 전송 중 오류 발생", e);
        }
    }

    void sendErrorCard(String errorType, String errorMessage) {
        sendErrorCard(errorType, errorMessage, "/unknown", "Unknown", errorMessage);
    }
}
