package com.example.k_trader.base;

import android.content.Intent;
import android.content.Context;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.example.k_trader.database.entities.BithumbApiEntities;
import com.example.k_trader.database.daos.BithumbApiDao;
import com.example.k_trader.database.OrderDatabase;
import com.example.k_trader.domain.model.CoinSpecific;
import com.example.k_trader.domain.model.CoinSpecificFactory;
import com.google.gson.Gson;
import java.util.Date;
import com.example.k_trader.ui.activity.MainActivity;
import com.example.k_trader.KTraderApplication;
import com.example.k_trader.util.LogInfoFormatter;
import com.example.k_trader.bitthumb.lib.Api_Client;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

import static com.example.k_trader.base.TradeDataManager.Type.BUY;
import static com.example.k_trader.base.TradeDataManager.Type.NONE;
import static com.example.k_trader.base.TradeDataManager.Type.SELL;
import static com.example.k_trader.base.ErrorCode.*;

/**
 * Created by 김무창 on 2017-12-23.
 */

public class OrderManager {
    private static long lastRequestTimeInMillis = 0;
    private static final long safeIntervalInSec = 15;
    private static final org.apache.log4j.Logger logger = Log4jHelper.getLogger("OrderManager");
    private final TradeApiService tradeApiService;
    
    // API 응답 파싱 및 DB 저장을 위한 필드들
    private final Gson gson;
    private final OrderDatabase database;
    private final BithumbApiDao.BithumbTickerDao tickerDao;
    private final BithumbApiDao.BithumbBalanceDao balanceDao;
    private final BithumbApiDao.BithumbOrderDao orderDao;
    private final BithumbApiDao.ApiCallStatsDao apiStatsDao;
    private final OrderManagerErrorReporter errorReporter;
    private final OrderManagerResponseParser responseParser;

    public interface TradeApiService {
        Api_Client getApiService();
    }

    static class DefaultTradeApiService implements TradeApiService {
        @Override
        public Api_Client getApiService() {
            return new Api_Client();
        }
    }

    public OrderManager() {
        tradeApiService = new DefaultTradeApiService();
        gson = new Gson();
        errorReporter = new OrderManagerErrorReporter();
        responseParser = new OrderManagerResponseParser();
        Context appContext = KTraderApplication.getAppContext();
        if (appContext != null) {
            database = OrderDatabase.getInstance(appContext);
            tickerDao = database.BithumbTickerDao();
            balanceDao = database.BithumbBalanceDao();
            orderDao = database.BithumbOrderDao();
            apiStatsDao = database.apiCallStatsDao();
        } else {
            database = null;
            tickerDao = null;
            balanceDao = null;
            orderDao = null;
            apiStatsDao = null;
            Log.w("KTrader", "[OrderManager] App context is null. DB-related features are disabled.");
        }
    }

    public OrderManager(TradeApiService tradeApiService) {
        this.tradeApiService = tradeApiService;
        gson = new Gson();
        errorReporter = new OrderManagerErrorReporter();
        responseParser = new OrderManagerResponseParser();
        Context appContext = KTraderApplication.getAppContext();
        if (appContext != null) {
            database = OrderDatabase.getInstance(appContext);
            tickerDao = database.BithumbTickerDao();
            balanceDao = database.BithumbBalanceDao();
            orderDao = database.BithumbOrderDao();
            apiStatsDao = database.apiCallStatsDao();
        } else {
            database = null;
            tickerDao = null;
            balanceDao = null;
            orderDao = null;
            apiStatsDao = null;
            Log.w("KTrader", "[OrderManager] App context is null. DB-related features are disabled.");
        }
    }

    public boolean cancelOrder(String tag, TradeData data) {
        // 입력 데이터 유효성 검사 추가
        if (data == null) {
            Log.e("KTrader", "[OrderManager] cancelOrder - TradeData가 null입니다");
            return false;
        }

        if (data.getId() == null || data.getId().isEmpty()) {
            Log.e("KTrader", "[OrderManager] cancelOrder - Order ID가 null이거나 비어있습니다");
            LogInfoFormatter.logInfo(tag + " : Order ID가 null이거나 비어있어서 취소할 수 없습니다");
            return false;
        }

        Api_Client api = tradeApiService.getApiService();
        JSONObject result;

        HashMap<String, String> rgParams = new HashMap<>();
        if (data.getType() == BUY)
            rgParams.put("type", "bid");
        else
            rgParams.put("type", "ask");

        rgParams.put("order_currency", getCurrentCoinType());
        rgParams.put("order_id", data.getId());
        rgParams.put("payment_currency", "KRW");

        // 상세한 로깅 추가
        Log.d("KTrader", "[OrderManager] cancelOrder 요청 파라미터: " + rgParams.toString());
        LogInfoFormatter.logInfo(tag + " : " + data.getType().toString() + " 취소 : " + data.getId() + " : " + data.getUnits() + " : " + String.format(Locale.getDefault(), "%,d", data.getPrice()));

        try {
            result = api.callApi("POST", "/trade/cancel", rgParams);
            if (!hasValidApiStatus(result, tag, "/trade/cancel", "API 오류", ERR_API_001.getDescription())) {
                return false;
            }

            Log.d("KTrader", "[OrderManager] 주문 취소 성공 - Order ID: " + data.getId());
        } catch (Exception e) {
            e.printStackTrace();
            String logMessage = tag + " : " + "/trade/cancel : " + e.getMessage();
            Log.e("KTrader", "[OrderManager] " + logMessage);
            LogInfoFormatter.logInfo(logMessage);
            sendErrorCard("API 오류", ERR_API_001.getDescription(), "/trade/cancel", "EXCEPTION", e.getMessage());
            return false;
        }

        return true;
    }

    public boolean cancelAllBuyOrders() {
        Api_Client api = tradeApiService.getApiService();
        JSONObject result;
        try {
            result = api.callApi("POST", "/info/orders", null);
        } catch (Exception e) {
            Log.e("KTrader", "[OrderManager] /info/orders 호출 실패", e);
            LogInfoFormatter.logInfo("전체취소 : /info/orders 호출 실패 : " + e.getMessage());
            sendErrorCard("네트워크 오류", ERR_API_002.getDescription(), "/info/orders", "EXCEPTION", e.getMessage());
            return false;
        }
        int cancelCount = 0;

        if (result == null) {
            String logMessage = "/info/orders : null";
            LogInfoFormatter.logInfo("전체취소 : " + logMessage);
            sendErrorCard("API 오류", ERR_API_002.getDescription());
            return false;
        }

        JSONArray dataArray = (JSONArray) result.get("data");
        if (dataArray != null) {
            for (int i = 0; i < dataArray.size(); i++) {
                JSONObject item = (JSONObject) dataArray.get(i);
                String type = (String) item.get("type");
                if (type.equals("bid")) { // buy
                    TradeData data = new TradeData();
                    data.setType(BUY);
                    data.setId((String) item.get("order_id"));
                    data.setUnits((float) Double.parseDouble((String) item.get("units_remaining")));
                    data.setPrice(Integer.parseInt(((String) item.get("price")).replaceAll(",", "")));
                    cancelOrder("전체취소", data);
                    cancelCount++;
                }
            }
            LogInfoFormatter.logInfo(String.format("취소 결과 : %d개", cancelCount));
        }

        return true;
    }

    public JSONObject addOrder(String tag, TradeDataManager.Type type, double units, int price) {
        Api_Client api = tradeApiService.getApiService();
        JSONObject result;
        long requestTime = Calendar.getInstance().getTimeInMillis();

        // 코인별 특성 가져오기
        CoinSpecific coinSpecific = CoinSpecificFactory.getCurrentCoinSpecific();
        
        // 상세한 수량 처리 로깅 추가
        Log.d("KTrader", "[OrderManager] 수량 처리 상세:");
        Log.d("KTrader", "[OrderManager] - 원본 수량: " + units);
        Log.d("KTrader", "[OrderManager] - 수량 타입: double");
        Log.d("KTrader", "[OrderManager] - 코인 타입: " + coinSpecific.getCoinType());
        Log.d("KTrader", "[OrderManager] - 코인별 특성: " + coinSpecific.toString());
        
        // 코인별 최소 거래 수량 검증
        double minimumTradingAmount = coinSpecific.getMinimumTradingAmount();
        Log.d("KTrader", "[OrderManager] - 최소 거래 수량: " + minimumTradingAmount);
        Log.d("KTrader", "[OrderManager] - 거래 가능 여부: " + coinSpecific.isTradableQuantity(units));
        
        // 수량이 코인별 최소 거래 수량보다 작은 경우
        if (!coinSpecific.isTradableQuantity(units)) {
            String logMessage = tag + " : " + type.toString() + " 발행 취소 : 수량이 " + coinSpecific.getCoinType() + " 최소 거래 단위보다 작습니다. 수량: " + String.format("%.4f", units) + ", 최소: " + minimumTradingAmount;
            Log.e("KTrader", "[OrderManager] " + logMessage);
            LogInfoFormatter.logInfo(logMessage);
            sendErrorCard("유효성 검사 오류", coinSpecific.getCoinType() + " 최소 거래 단위 미달", "/trade/place", "MIN_UNITS_NOT_MET", "최소 거래 단위 미달");
            return null;
        }
        
        // 거래 금액 검증 (최소 거래 단위)
        int tradingAmount = (int)(units * price);
        Log.d("KTrader", "[OrderManager] - 필요 거래 금액: " + tradingAmount);
        Log.d("KTrader", "[OrderManager] - 최소 거래 단위: " + coinSpecific.getMinimumTradingUnit());
        Log.d("KTrader", "[OrderManager] - 거래 금액 충족 여부: " + coinSpecific.isTradableAmount(tradingAmount));
        
        if (!coinSpecific.isTradableAmount(tradingAmount)) {
            String logMessage = tag + " : " + type.toString() + " 발행 취소 : 거래 금액이 최소 거래 단위보다 작습니다. 금액: " + tradingAmount + ", 최소: " + coinSpecific.getMinimumTradingUnit();
            Log.e("KTrader", "[OrderManager] " + logMessage);
            LogInfoFormatter.logInfo(logMessage);
            sendErrorCard("유효성 검사 오류", "최소 거래 단위 미달", "/trade/place", "MIN_AMOUNT_NOT_MET", "최소 거래 단위 미달");
            return null;
        }

        // 마지막 요청으로부터 15초 이내에 신규 요청이 온 경우에는 delay 시킨다.
        // {"message":"Please try again","status":"5600"} 에러 방지 목적
        while ((requestTime - lastRequestTimeInMillis) < safeIntervalInSec * 1000) {
            Intent intent = new Intent(MainActivity.BROADCAST_PROGRESS_MESSAGE);

            Log.d("KTrader", "Order sending progress : " + String.valueOf((15 * 1000) - (requestTime - lastRequestTimeInMillis)));

            intent.putExtra("progress", (int)((safeIntervalInSec) - (requestTime - lastRequestTimeInMillis)/1000));
            if (KTraderApplication.getAppContext() != null)
                LocalBroadcastManager.getInstance(KTraderApplication.getAppContext()).sendBroadcast(intent);

            try {
                Thread.sleep((safeIntervalInSec * 1000) - (requestTime - lastRequestTimeInMillis));
            } catch (InterruptedException e) {
                // 에러처리
            }

            requestTime = Calendar.getInstance().getTimeInMillis();
        }

        // 수량 포맷팅 (모든 코인에 대해 소수점 4자리 사용)
        String unitsStr = String.format("%.4f", units);
        Log.d("KTrader", "[OrderManager] - 포맷팅 후 수량: " + unitsStr);
        
        HashMap<String, String> rgParams = new HashMap<>();
        rgParams.put("order_currency", getCurrentCoinType());
        rgParams.put("Payment_currency", "KRW");
        rgParams.put("units", unitsStr);
        rgParams.put("price", String.valueOf(price));
        rgParams.put("payment_currency", "KRW");

        if (type == BUY)
            rgParams.put("type", "bid");
        else
            rgParams.put("type", "ask");

        // 매수 주문인 경우 잔고 확인
        if (type == BUY) {
            try {
                JSONObject balanceData = getBalance("매수 전 잔고 확인");
                if (balanceData != null) {
                    String totalKrw = (String) balanceData.get("total_krw");
                    if (totalKrw != null) {
                        double krwBalance = Double.parseDouble(totalKrw);
                        double requiredAmount = units * price;
                        
                        if (krwBalance < requiredAmount) {
                            LogInfoFormatter.logInfo(tag + " : 잔고 부족으로 매수 주문을 건너뜁니다. 필요: " + 
                                String.format(Locale.getDefault(), "%,.0f", requiredAmount) + 
                                "원, 보유: " + String.format(Locale.getDefault(), "%,.0f", krwBalance) + "원");
                            return null;
                        }
                    }
                }
            } catch (Exception e) {
                LogInfoFormatter.logInfo(tag + " : 잔고 확인 중 오류 발생: " + e.getMessage());
                return null;
            }
        }

        LogInfoFormatter.logInfo(tag + " : " + type.toString() + " 발행 시도 : " + String.format("%.4f", units) + " : " + String.format(Locale.getDefault(), "%,d", price));

        try {
            result = api.callApi("POST", "/trade/place", rgParams);
            if (!hasValidApiStatus(result, tag, "/trade/place", "API Error", ERR_API_005.getDescription())) {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            String logMessage = tag + " : " + "/trade/place : " + e.getMessage();
            LogInfoFormatter.logInfo(logMessage);
            sendErrorCard("API Error", ERR_API_005.getDescription(), "/trade/place", "EXCEPTION", e.getMessage());
            Log.d("KTrader", "Order " + logMessage);
            return null;
        }

        lastRequestTimeInMillis = Calendar.getInstance().getTimeInMillis();
        Log.d("KTrader", "Order : " + result);
        return result;
    }


    public JSONObject addOrderWithMarketPrice(String tag, TradeDataManager.Type type, float units) {
        Log.d("KTrader", "[OrderManager] addOrderWithMarketPrice() 시작 - tag: " + tag + ", type: " + type + ", units: " + units);
        LogInfoFormatter.logInfo(tag + " : 시장가 주문 시작 - " + type.toString() + " " + String.format("%.4f", units));
        
        Api_Client api = tradeApiService.getApiService();
        JSONObject result;
        long requestTime = Calendar.getInstance().getTimeInMillis();

        // 마지막 요청으로부터 15초 이내에 신규 요청이 온 경우에는 delay 시킨다.
        // {"message":"Please try again","status":"5600"} 에러 방지 목적
        while ((requestTime - lastRequestTimeInMillis) < safeIntervalInSec * 1000) {
            Intent intent = new Intent(MainActivity.BROADCAST_PROGRESS_MESSAGE);

//            Log.d("KTrader", "sending progress : " + String.valueOf((15 * 1000) - (requestTime - lastRequestTimeInMillis)));

            intent.putExtra("progress", (int)((safeIntervalInSec * 1000) - (requestTime - lastRequestTimeInMillis)));
            LocalBroadcastManager.getInstance(KTraderApplication.getAppContext()).sendBroadcast(intent);

            try {
                Thread.sleep((safeIntervalInSec * 1000) - (requestTime - lastRequestTimeInMillis));
            } catch (InterruptedException e) {
                // 에러처리
            }

            requestTime = Calendar.getInstance().getTimeInMillis();
        }

        // 수량 포맷팅 (모든 코인에 대해 소수점 4자리 사용)
        String unitsStr = String.format("%.4f", units);
        Log.d("KTrader", "[OrderManager] - 포맷팅 후 수량: " + unitsStr);
        
        HashMap<String, String> rgParams = new HashMap<>();
        rgParams.put("order_currency", getCurrentCoinType());
        rgParams.put("units", unitsStr);
        rgParams.put("payment_currency", "KRW");

        // 매수 주문인 경우 잔고 확인 (PlacedOrderPage에서 이미 확인했지만 추가 안전장치)
        if (type == BUY) {
            Log.d("KTrader", "[OrderManager] 매수 주문 - 추가 잔고 확인");
            try {
                JSONObject balanceData = getBalance("시장가 매수 전 잔고 확인");
                Log.d("KTrader", "[OrderManager] 잔고 조회 결과: " + (balanceData != null ? "성공" : "실패"));
                
                if (balanceData != null) {
                    String totalKrw = (String) balanceData.get("total_krw");
                    Log.d("KTrader", "[OrderManager] KRW 잔고: " + totalKrw);
                    
                    if (totalKrw != null) {
                        double krwBalance = Double.parseDouble(totalKrw);
                        // 시장가 매수이므로 현재가를 가져와서 계산
                        JSONObject tickerData = getTicker("시장가 매수 현재가 확인");
                        Log.d("KTrader", "[OrderManager] ticker 조회 결과: " + (tickerData != null ? "성공" : "실패"));
                        
                        if (tickerData != null) {
                            JSONObject data = (JSONObject) tickerData.get("data");
                            if (data != null) {
                                String currentPriceStr = (String) data.get("closing_price");
                                Log.d("KTrader", "[OrderManager] 현재가: " + currentPriceStr);
                                
                                if (currentPriceStr != null) {
                                    double currentPrice = Double.parseDouble(currentPriceStr);
                                    double requiredAmount = units * currentPrice;
                                    Log.d("KTrader", "[OrderManager] 필요 금액: " + requiredAmount + ", 보유 금액: " + krwBalance);
                                    
                                    if (krwBalance < requiredAmount) {
                                        String message = tag + " : 잔고 부족으로 시장가 매수 주문을 건너뜁니다. 필요: " + 
                                            String.format(Locale.getDefault(), "%,.0f", requiredAmount) + 
                                            "원, 보유: " + String.format(Locale.getDefault(), "%,.0f", krwBalance) + "원";
                                        Log.w("KTrader", "[OrderManager] " + message);
                                        LogInfoFormatter.logInfo(message);
                                        return null;
                                    }
                                    Log.d("KTrader", "[OrderManager] 잔고 확인 완료 - 시장가 매수 가능");
                                } else {
                                    Log.e("KTrader", "[OrderManager] closing_price 정보가 null");
                                    LogInfoFormatter.logInfo(tag + " : 현재가 정보를 가져올 수 없습니다");
                                    return null;
                                }
                            } else {
                                Log.e("KTrader", "[OrderManager] ticker data가 null");
                                LogInfoFormatter.logInfo(tag + " : 현재가 데이터를 가져올 수 없습니다");
                                return null;
                            }
                        } else {
                            Log.e("KTrader", "[OrderManager] ticker 조회 실패");
                            LogInfoFormatter.logInfo(tag + " : 현재가 조회에 실패했습니다");
                            return null;
                        }
                    } else {
                        Log.e("KTrader", "[OrderManager] KRW 잔고 정보가 null");
                        LogInfoFormatter.logInfo(tag + " : KRW 잔고 정보를 가져올 수 없습니다");
                        return null;
                    }
                } else {
                    Log.e("KTrader", "[OrderManager] 잔고 조회 실패");
                    LogInfoFormatter.logInfo(tag + " : 잔고 조회에 실패했습니다");
                    return null;
                }
            } catch (Exception e) {
                Log.e("KTrader", "[OrderManager] 잔고 확인 중 오류 발생", e);
                LogInfoFormatter.logInfo(tag + " : 시장가 매수 잔고 확인 중 오류 발생: " + e.getMessage());
                return null;
            }
        }

        // 코인별 특성 가져오기 (시장가 주문)
        CoinSpecific coinSpecific = CoinSpecificFactory.getCurrentCoinSpecific();
        
        // 상세한 수량 처리 로깅 추가 (시장가 주문)
        Log.d("KTrader", "[OrderManager] 시장가 주문 수량 처리 상세:");
        Log.d("KTrader", "[OrderManager] - 원본 수량: " + units);
        Log.d("KTrader", "[OrderManager] - 수량 타입: float");
        Log.d("KTrader", "[OrderManager] - 코인 타입: " + coinSpecific.getCoinType());
        Log.d("KTrader", "[OrderManager] - 코인별 특성: " + coinSpecific.toString());
        
        // 코인별 최소 거래 수량 검증 (시장가 주문)
        double minimumTradingAmount = coinSpecific.getMinimumTradingAmount();
        Log.d("KTrader", "[OrderManager] - 최소 거래 수량: " + minimumTradingAmount);
        Log.d("KTrader", "[OrderManager] - 거래 가능 여부: " + coinSpecific.isTradableQuantity(units));
        
        // 수량이 코인별 최소 거래 수량보다 작은 경우
        if (!coinSpecific.isTradableQuantity(units)) {
            String logMessage = tag + " : " + type.toString() + " 시장가 발행 취소 : 수량이 " + coinSpecific.getCoinType() + " 최소 거래 단위보다 작습니다. 수량: " + String.format("%.4f", units) + ", 최소: " + minimumTradingAmount;
            Log.e("KTrader", "[OrderManager] " + logMessage);
            LogInfoFormatter.logInfo(logMessage);
            String endpoint = type == BUY ? "/trade/market_buy" : "/trade/market_sell";
            sendErrorCard("Validation Error", coinSpecific.getCoinType() + " 최소 거래 단위 미달", endpoint, "MIN_UNITS_NOT_MET", "최소 거래 단위 미달");
            return null;
        }

        String endpoint = type == BUY ? "/trade/market_buy" : "/trade/market_sell";
        
        try {
            Log.d("KTrader", "[OrderManager] API 호출 시작 - endpoint: " + endpoint);
            
            if (type == BUY)
                result = api.callApi("POST", "/trade/market_buy", rgParams);
            else
                result = api.callApi("POST", "/trade/market_sell", rgParams);
                
            Log.d("KTrader", "[OrderManager] API 호출 완료 - 결과: " + (result != null ? "성공" : "실패"));
            if (!hasValidApiStatus(result, tag, endpoint, "API Error", ERR_API_006.getDescription())) {
                return null;
            }
            
            Log.d("KTrader", "[OrderManager] 시장가 주문 성공");
        } catch (Exception e) {
            e.printStackTrace();
            String logMessage = tag + " : " + "/trade/market_(buy/sell)3 : " + e.getMessage();
            LogInfoFormatter.logInfo(logMessage);
            sendErrorCard("API Error", ERR_API_006.getDescription(), endpoint, "EXCEPTION", e.getMessage());
            return null;
        }

        lastRequestTimeInMillis = Calendar.getInstance().getTimeInMillis();
        Log.d("KTrader", "[OrderManager] addOrderWithMarketPrice() 완료 - 결과: " + result.toString());

        return result;
    }

    public JSONObject getBalance(String tag) throws Exception {
        // 기본값으로 모든 코인 정보 가져오기
        HashMap<String, String> params = new HashMap<>();
        params.put("currency", "ALL");
        return getBalanceWithParams(tag, params);
    }
    
    public JSONObject getBalanceWithParams(String tag, HashMap<String, String> params) throws Exception {
        Api_Client api = tradeApiService.getApiService();
        JSONObject result = null;
        long startTime = System.currentTimeMillis();

        try {
            // Bithumb API 문서에 따르면 currency 파라미터를 설정하여 특정 코인 또는 모든 코인 정보를 가져올 수 있습니다
            // https://apidocs.bithumb.com/v1.2.0/reference/%EB%B3%B4%EC%9C%A0%EC%9E%90%EC%82%B0-%EC%A1%B0%ED%9A%8C
            result = api.callApi("POST", "/info/balance", params);
            if (!hasValidApiStatus(result, tag, "/info/balance", "API Error", "Balance API 오류")) {
                saveApiStats("/info/balance", "POST", 0, System.currentTimeMillis() - startTime, false, "invalid response");
                throw new Exception("returns null");
            }
            
            // API 응답을 파싱하고 DB에 저장
            parseAndSaveBalanceResponse(result, tag);
            saveApiStats("/info/balance", "POST", 200, System.currentTimeMillis() - startTime, true, null);
            
        } catch (Exception e) {
            e.printStackTrace();
            LogInfoFormatter.logInfo(tag + " : " + "/info/balance : " + e.getMessage());
            saveApiStats("/info/balance", "POST", 500, System.currentTimeMillis() - startTime, false, e.getMessage());
            sendErrorCard("API Error", "Balance API 호출 중 예외 발생", "/info/balance", "EXCEPTION", e.getMessage());
            throw new Exception("returns null");
        }

        return (JSONObject)result.get("data");
    }

    public JSONObject getCurrentPrice(String tag) throws Exception {
        Api_Client api = tradeApiService.getApiService();
        JSONObject result = null;

        try {
            result = api.callApi("GET", "/public/orderbook/" + getCurrentCoinType(), null);
            String endpoint = "/public/orderbook/" + getCurrentCoinType();
            if (!hasValidApiStatus(result, tag, endpoint, "API Error", "Orderbook API 오류")) {
                throw new Exception("returns null");
            }
        } catch (Exception e) {
            e.printStackTrace();
            LogInfoFormatter.logInfo(tag + " : " + "/public/orderbook/" + getCurrentCoinType() + " : " + e.getMessage());
            sendErrorCard("API Error", "Orderbook API 호출 중 예외 발생", "/public/orderbook/" + getCurrentCoinType(), "EXCEPTION", e.getMessage());
            throw new Exception("returns null");
        }

        return (JSONObject)result.get("data");
    }

    public JSONObject getTicker(String tag) throws Exception {
        Api_Client api = tradeApiService.getApiService();
        JSONObject result = null;
        long startTime = System.currentTimeMillis();

        try {
            result = api.callApi("GET", "/public/ticker/" + getCurrentCoinType(), null);
            if (!hasValidApiStatus(result, tag, "/public/ticker/" + getCurrentCoinType(), "API Error", "Ticker API 오류")) {
                saveApiStats("/public/ticker", "GET", 0, System.currentTimeMillis() - startTime, false, "invalid response");
                throw new Exception("returns null");
            }
            
            // API 응답을 파싱하고 DB에 저장
            parseAndSaveTickerResponse(result, tag);
            saveApiStats("/public/ticker", "GET", 200, System.currentTimeMillis() - startTime, true, null);
            
        } catch (Exception e) {
            e.printStackTrace();
            LogInfoFormatter.logInfo(tag + " : " + "/public/ticker : " + e.getMessage());
            saveApiStats("/public/ticker", "GET", 500, System.currentTimeMillis() - startTime, false, e.getMessage());
            sendErrorCard("API Error", "Ticker API 호출 중 예외 발생", "/public/ticker/" + getCurrentCoinType(), "EXCEPTION", e.getMessage());
            throw new Exception("returns null");
        }

        return result;
    }

    public JSONArray getPlacedOrderList(String tag) throws Exception {
        Api_Client api = tradeApiService.getApiService();
        JSONObject result = null;

        try {
            HashMap param = new HashMap();
            param.put("count", "300");
            param.put("order_currency", getCurrentCoinType());

            result = api.callApi("POST", "/info/orders", param);

            if (isNoActiveOrderResponse(result)) {
                throw new Exception("returns null");
            }
            if (!hasValidApiStatus(result, tag, "/info/orders", "API Error", ERR_API_007.getDescription())) {
                throw new Exception("returns null");
            }
        } catch (Exception e) {
            e.printStackTrace();
            LogInfoFormatter.logInfo(tag + " : " + "/info/orders : 4 : " + e.getMessage());
            JSONArray jarr = new JSONArray();
            return jarr;
        }

        return (JSONArray)result.get("data");
    }

    public JSONArray getProcessedOrderList(String tag, int offset, String count) throws Exception {
        Api_Client api = tradeApiService.getApiService();
        JSONObject result = null;

        try {
            HashMap<String, String> rgParams = new HashMap<>();
            rgParams.put("offset", String.valueOf(offset));
            rgParams.put("count", count); // 1~50, default = 20
            rgParams.put("searchGb", "0"); // 0 = all, 1 = buy
            rgParams.put("order_currency", getCurrentCoinType());
            rgParams.put("payment_currency", "KRW");

            result = api.callApi("POST", "/info/user_transactions", rgParams);
            if (!hasValidApiStatus(result, tag, "/info/user_transactions", "API Error", ERR_API_008.getDescription())) {
                throw new Exception("returns null");
            }
        } catch (Exception e) {
            e.printStackTrace();
            LogInfoFormatter.logInfo(tag + " : " + "/info/user_transactions : " + e.getMessage());
            throw new Exception("returns null");
        }

        return (JSONArray) result.get("data");
    }

    public TradeDataManager.Type convertOrderType(String type) {
        switch(type) {
            case "bid" : return BUY;
            case "ask" : return SELL;
        }
        return NONE;
    }
    
    /**
     * 현재 설정된 코인 타입을 반환
     */
    private String getCurrentCoinType() {
        String coinType = GlobalSettings.getInstance().getCoinType();
        if (GlobalSettings.COIN_TYPE_ETH.equals(coinType)) {
            return "ETH";
        } else {
            return "BTC"; // 기본값
        }
    }

    private boolean hasValidApiStatus(JSONObject result, String tag, String endpoint, String errorType, String errorDescription) {
        return errorReporter.hasValidApiStatus(result, tag, endpoint, errorType, errorDescription);
    }

    private boolean isNoActiveOrderResponse(JSONObject result) {
        return responseParser.isNoActiveOrderResponse(result);
    }
    
    private void sendErrorCard(String errorType, String errorMessage, String apiEndpoint, String errorCode, String serverErrorMessage) {
        errorReporter.sendErrorCard(errorType, errorMessage, apiEndpoint, errorCode, serverErrorMessage);
    }
    
    // 기존 메서드와의 호환성을 위한 오버로드
    private void sendErrorCard(String errorType, String errorMessage) {
        errorReporter.sendErrorCard(errorType, errorMessage);
    }
    
    /**
     * Ticker API 응답을 파싱하고 DB에 저장
     */
    private void parseAndSaveTickerResponse(JSONObject response, String tag) {
        try {
            // API 응답 구조 확인을 위한 로깅
            Log.d("KTrader", "[OrderManager] Ticker API 응답 구조 확인: " + response.toString());
            
            // data 필드가 JSONObject인지 확인
            Object dataObj = response.get("data");
            if (dataObj == null) {
                Log.w("KTrader", "[OrderManager] Ticker API 응답에 data 필드가 없음");
                return;
            }
            
            // data가 JSONObject인 경우에만 파싱 시도
            if (dataObj instanceof JSONObject) {
                JSONObject dataJson = (JSONObject) dataObj;
                String coin_pair = getCurrentCoinType() + "_KRW";
                Date timestamp = new Date();
                
                // 직접 JSONObject에서 데이터 추출
                BithumbApiEntities.BithumbTickerEntity entity = new BithumbApiEntities.BithumbTickerEntity(
                    coin_pair,
                    parseDouble((String) dataJson.get("opening_price")),
                    parseDouble((String) dataJson.get("closing_price")),
                    parseDouble((String) dataJson.get("min_price")),
                    parseDouble((String) dataJson.get("max_price")),
                    parseDouble((String) dataJson.get("average_price")),
                    parseDouble((String) dataJson.get("units_traded")),
                    parseDouble((String) dataJson.get("volume_1day")),
                    parseDouble((String) dataJson.get("volume_7day")),
                    parseDouble((String) dataJson.get("fluctate_24H")),
                    parseDouble((String) dataJson.get("fluctate_rate_24H")),
                    parseDouble((String) dataJson.get("fluctate_rate_1H")),
                    timestamp
                );
                
                // DB에 저장 (동기)
                try {
                    long id = tickerDao.insertTicker(entity);
                    Log.d("KTrader", "[OrderManager] Ticker saved to DB: " + coin_pair + ", ID: " + id);
                } catch (Exception e) {
                    Log.e("KTrader", "[OrderManager] Error saving ticker to DB: " + coin_pair, e);
                }
            } else {
                Log.w("KTrader", "[OrderManager] Ticker API 응답의 data 필드가 JSONObject가 아님: " + dataObj.getClass().getSimpleName());
                LogInfoFormatter.logInfo(tag + " : Ticker API 응답 구조가 예상과 다름: " + dataObj.getClass().getSimpleName());
            }
        } catch (Exception e) {
            Log.e("KTrader", "[OrderManager] Ticker 저장 오류", e);
            LogInfoFormatter.logInfo(tag + " : Ticker 저장 오류: " + e.getMessage());
        }
    }
    
    /**
     * Balance API 응답을 파싱하고 DB에 저장
     */
    private void parseAndSaveBalanceResponse(JSONObject response, String tag) {
        try {
            // API 응답 구조 확인을 위한 로깅
            Log.d("KTrader", "[OrderManager] Balance API 응답 구조 확인: " + response.toString());
            
            // data 필드가 JSONObject인지 확인
            Object dataObj = response.get("data");
            if (dataObj == null) {
                Log.w("KTrader", "[OrderManager] Balance API 응답에 data 필드가 없음");
                return;
            }
            
            // data가 JSONObject인 경우에만 파싱 시도
            if (dataObj instanceof JSONObject) {
                JSONObject dataJson = (JSONObject) dataObj;
                Date timestamp = new Date();
                
                // KRW 잔고 저장
                String totalKrw = (String) dataJson.get("total_krw");
                if (totalKrw != null && !totalKrw.isEmpty()) {
                    BithumbApiEntities.BithumbBalanceEntity krwEntity = new BithumbApiEntities.BithumbBalanceEntity(
                        "KRW",
                        parseDouble(totalKrw),
                        parseDouble((String) dataJson.get("in_use_krw")),
                        parseDouble((String) dataJson.get("available_krw")),
                        timestamp
                    );
                    
                    // DB에 저장 (동기)
                    try {
                        long id = balanceDao.insertBalance(krwEntity);
                        Log.d("KTrader", "[OrderManager] KRW Balance saved to DB, ID: " + id);
                    } catch (Exception e) {
                        Log.e("KTrader", "[OrderManager] Error saving KRW balance to DB", e);
                    }
                }
                
                // BTC 잔고 저장
                String totalBtc = (String) dataJson.get("total_btc");
                if (totalBtc != null && !totalBtc.isEmpty()) {
                    BithumbApiEntities.BithumbBalanceEntity btcEntity = new BithumbApiEntities.BithumbBalanceEntity(
                        "BTC",
                        parseDouble(totalBtc),
                        parseDouble((String) dataJson.get("in_use_btc")),
                        parseDouble((String) dataJson.get("available_btc")),
                        timestamp
                    );
                    
                    // DB에 저장 (동기)
                    try {
                        long id = balanceDao.insertBalance(btcEntity);
                        Log.d("KTrader", "[OrderManager] BTC Balance saved to DB, ID: " + id);
                    } catch (Exception e) {
                        Log.e("KTrader", "[OrderManager] Error saving BTC balance to DB", e);
                    }
                }
                
                // ETH 잔고 저장
                String totalEth = (String) dataJson.get("total_eth");
                if (totalEth != null && !totalEth.isEmpty()) {
                    BithumbApiEntities.BithumbBalanceEntity ethEntity = new BithumbApiEntities.BithumbBalanceEntity(
                        "ETH",
                        parseDouble(totalEth),
                        parseDouble((String) dataJson.get("in_use_eth")),
                        parseDouble((String) dataJson.get("available_eth")),
                        timestamp
                    );
                    
                    // DB에 저장 (동기)
                    try {
                        long id = balanceDao.insertBalance(ethEntity);
                        Log.d("KTrader", "[OrderManager] ETH Balance saved to DB, ID: " + id);
                    } catch (Exception e) {
                        Log.e("KTrader", "[OrderManager] Error saving ETH balance to DB", e);
                    }
                }
            } else {
                Log.w("KTrader", "[OrderManager] Balance API 응답의 data 필드가 JSONObject가 아님: " + dataObj.getClass().getSimpleName());
                LogInfoFormatter.logInfo(tag + " : Balance API 응답 구조가 예상과 다름: " + dataObj.getClass().getSimpleName());
            }
        } catch (Exception e) {
            Log.e("KTrader", "[OrderManager] Balance 저장 오류", e);
            LogInfoFormatter.logInfo(tag + " : Balance 저장 오류: " + e.getMessage());
        }
    }
    
    /**
     * API 호출 통계를 DB에 저장
     */
    private void saveApiStats(String endpoint, String method, int statusCode, long responseTimeMs, boolean success, String errorMessage) {
        try {
            BithumbApiEntities.ApiCallStatsEntity statsEntity = new BithumbApiEntities.ApiCallStatsEntity(
                endpoint,
                method,
                statusCode,
                responseTimeMs,
                success,
                errorMessage,
                new Date()
            );
            
            // DB에 저장 (동기)
            try {
                long id = apiStatsDao.insertStats(statsEntity);
                Log.d("KTrader", "[OrderManager] API Stats saved to DB, ID: " + id);
            } catch (Exception e) {
                Log.e("KTrader", "[OrderManager] Error saving API stats to DB", e);
            }
        } catch (Exception e) {
            Log.e("KTrader", "[OrderManager] API Stats 저장 오류", e);
        }
    }
    
    /**
     * 문자열을 Double로 안전하게 변환
     */
    private double parseDouble(String value) {
        return responseParser.parseDouble(value);
    }
}
