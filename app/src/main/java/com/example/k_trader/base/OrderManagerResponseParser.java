package com.example.k_trader.base;

import org.json.simple.JSONObject;

class OrderManagerResponseParser {

    boolean isNoActiveOrderResponse(JSONObject result) {
        if (result == null || result.get("status") == null) {
            return false;
        }
        String status = String.valueOf(result.get("status"));
        if (!"5600".equals(status)) {
            return false;
        }
        String message = result.get("message") != null ? result.get("message").toString() : "";
        return "거래 진행중인 내역이 존재하지 않습니다.".equals(message);
    }

    double parseDouble(String value) {
        try {
            if (value == null || value.trim().isEmpty()) {
                return 0.0;
            }
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
