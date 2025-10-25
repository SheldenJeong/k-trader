package com.example.k_trader.service;

import com.example.k_trader.notification.TradeNotificationManager;
import com.example.k_trader.domain.model.CoinSpecific;
import com.example.k_trader.domain.model.CoinSpecificFactory;
import android.app.Service;
import android.os.Handler;
import android.os.Looper;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.example.k_trader.ui.fragment.MainPage;
import com.example.k_trader.util.LogInfoFormatter;
import com.example.k_trader.util.PriceQueueManager;
import com.example.k_trader.KTraderApplication;
import com.example.k_trader.base.GlobalSettings;
import com.example.k_trader.base.OrderManager;
import com.example.k_trader.base.TradeData;
import com.example.k_trader.base.TradeDataManager;
import static com.example.k_trader.base.TradeDataManager.Type.BUY;
import static com.example.k_trader.base.TradeDataManager.Type.SELL;
import static com.example.k_trader.base.ErrorCode.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static com.example.k_trader.base.TradeDataManager.Status.PLACED;
import static com.example.k_trader.base.TradeDataManager.Status.PROCESSED;
import static com.example.k_trader.base.TradeDataManager.Type.NONE;

/**
 * Created by 김무창 on 2017-12-17.
 */

public class TradeJobService extends Service {

    private static final int PRICE_SAVING_QUEUE_COUNT = 60;  // 1시간 분량의 시장가를 저장해 두고 분석에 사용한다.
    private static final int SELL_SLOT_LOOK_ASIDE_MAX = 3; // 3 단계 위까지 매도점을 찾아본다.
    private static final int BUY_SLOT_LOOK_ASIDE_MAX = 3;
    // 코인별 최소 거래 수량은 CoinSpecific 인터페이스에서 관리
    
    // 싱글톤 인스턴스
    private static TradeJobService INSTANCE;
    
    // 타이머 관련 변수
    private Handler handler;
    private Runnable tradingRunnable;
    private boolean isServiceRunning = false;
    
    // Notification 관리자
    private TradeNotificationManager notificationManager;
    
    // Transaction 정보 저장소

    public static int currentPrice;                  // 현재 코인 시장가
    public static long lastNotiTimeInMillis;        // 마지막 Notification 완료 시점
    public static double availableCoinBalance;      // 현재 판매 가능한 코인 총량 = 현재 보유중인 코인 총량 - 매도 중인 코인 총량

    private final TradeDataManager placedOrderManager = new TradeDataManager();
    private static final TradeDataManager processedOrderManager = new TradeDataManager();

    // PriceQueueManager 사용 (Singleton)
    private final PriceQueueManager priceQueueManager = PriceQueueManager.getInstance();
    private Context ctx;
    private OrderManager orderManager;

    @Override
    public void onCreate() {
        super.onCreate();
        INSTANCE = this; // 싱글톤 인스턴스 설정
        notificationManager = new TradeNotificationManager(this);
        notificationManager.createForegroundNotificationChannel();
        notificationManager.createTradeNotificationChannel();
        initializeHandler();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("KTrader", "[TradeJobService] onStartCommand() 시작");
        
        // Foreground Service로 시작
        notificationManager.startForegroundService(this);
        
        // 거래 로직 시작
        startTrading();
        
        // Service가 종료되어도 재시작하도록 설정
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d("KTrader", "[TradeJobService] onDestroy() 시작");
        stopTrading();
        INSTANCE = null; // 싱글톤 인스턴스 해제
        super.onDestroy();
    }

    @Override
    public android.os.IBinder onBind(Intent intent) {
        return null; // Bound Service가 아니므로 null 반환
    }

    /**
     * 서비스 실행 상태를 반환하는 메서드
     */
    public static boolean isServiceRunning() {
        return INSTANCE != null && INSTANCE.isServiceRunning;
    }

    /**
     * 서비스 인스턴스를 반환하는 메서드
     */
    public static TradeJobService getInstance() {
        return INSTANCE;
    }

    /**
     * Handler 초기화
     */
    private void initializeHandler() {
        handler = new Handler(Looper.getMainLooper());
        tradingRunnable = new Runnable() {
            @Override
            public void run() {
                if (isServiceRunning) {
                    // 백그라운드 스레드에서 거래 로직 실행
                    new Thread(() -> {
                        try {
                            tradeBusinessLogic();
                        } catch (Exception e) {
                            LogInfoFormatter.logInfo(LogInfoFormatter.formatBusinessLogicError(e.getMessage()));
                            sendErrorCard("Trade Business Logic Error", ERR_BUSINESS_001.getDescription());
                        }
                    }).start();
                    
                    // 다음 실행 스케줄링
                    handler.postDelayed(this, GlobalSettings.getInstance().getTradeInterval() * 1000);
                }
            }
        };
    }

    /**
     * 거래 시작
     */
    private void startTrading() {
        Log.d("KTrader", "[TradeJobService] startTrading() 시작");
        isServiceRunning = true;
        ctx = this;
        orderManager = new OrderManager();
        
        // 즉시 실행
        handler.post(tradingRunnable);
    }

    /**
     * 거래 중지
     */
    private void stopTrading() {
        Log.d("KTrader", "[TradeJobService] stopTrading() 시작");
        isServiceRunning = false;
        if (handler != null && tradingRunnable != null) {
            handler.removeCallbacks(tradingRunnable);
        }
    }






    private TradeDataManager.Type convertSearchType(int search) {
        switch(search) {
            case 1 : return BUY;
            case 2 : return SELL;
        }
        return NONE;
    }

    // 1시간 동안 시장가 변동폭을 구해 리턴한다.
    private float getPriceVariationRate() {
        return priceQueueManager.getPriceVariationRate();
    }

    private List<TradeData> mergeSamePrice(List<TradeData> list) {
        Iterator<TradeData> i = list.iterator();
        List<TradeData> newList = new ArrayList<>();

        while (i.hasNext()) {
            TradeData outer = i.next();
            boolean skip = false;

            for (TradeData inner : newList) {
                if (inner.getPrice() == outer.getPrice()) {
                    inner.setUnits(outer.getUnits() + inner.getUnits());
                    if (outer.getProcessedTime() > inner.getProcessedTime())
                        inner.setProcessedTime(outer.getProcessedTime());
                    skip = true;
                }
            }

            if (!skip)
                newList.add(outer);
        }

        return newList;
    }

    private boolean isSameSlotOrder(TradeData oData, TradeData pData, int price) {
        if (((oData.getUnits() + pData.getUnits()) * price) <= (GlobalSettings.getInstance().getUnitPrice() + GlobalSettings.getInstance().getUnitPrice() * (GlobalSettings.getInstance().getEarningRate() / 100.0))) {
            LogInfoFormatter.logInfo(LogInfoFormatter.formatSameSlotOrder(
                    (int)((oData.getUnits() + pData.getUnits()) * price),
                    (int)(oData.getUnits() * price),
                    (int)(pData.getUnits() * price)));

            return true;
        }

        return false;
    }

    private void tradeBusinessLogic() throws Exception {
        Log.d("KTrader", "[TradeJobService] tradeBusinessLogic() 시작");
        
        // 코인별 특성 가져오기
        CoinSpecific coinSpecific = CoinSpecificFactory.getCurrentCoinSpecific();
        
        // placedOrderManager 초기화 - 매번 새로운 계산을 위해 기존 주문들 제거
        placedOrderManager.clear();
        Log.d("KTrader", "[TradeJobService] placedOrderManager 초기화 완료");
        
        // Read settings again if MainActivity has been terminated by Android
        if (GlobalSettings.getInstance().getApiKey() == null) {
            SharedPreferences sharedPreferences = ctx.getSharedPreferences("settings", MODE_PRIVATE);
            GlobalSettings.getInstance().setApiKey(sharedPreferences.getString(GlobalSettings.API_KEY_KEY_NAME, ""))
                                        .setApiSecret(sharedPreferences.getString(GlobalSettings.API_SECRET_KEY_NAME, ""))
                                        .setUnitPrice(sharedPreferences.getInt(GlobalSettings.UNIT_PRICE_KEY_NAME, GlobalSettings.UNIT_PRICE_DEFAULT_VALUE))
                                        .setTradeInterval(sharedPreferences.getInt(GlobalSettings.TRADE_INTERVAL_KEY_NAME, GlobalSettings.TRADE_INTERVAL_DEFAULT_VALUE))
                                        .setFileLogEnabled(sharedPreferences.getBoolean(GlobalSettings.FILE_LOG_ENABLED_KEY_NAME, false))
                                        .setEarningRate(sharedPreferences.getFloat(GlobalSettings.EARNING_RATE_KEY_NAME, GlobalSettings.EARNING_RATE_DEFAULT_VALUE))
                                        .setSlotIntervalRate(sharedPreferences.getFloat(GlobalSettings.SLOT_INTERVAL_RATE_KEY_NAME, GlobalSettings.SLOT_INTERVAL_RATE_DEFAULT_VALUE));
            LogInfoFormatter.logInfo(LogInfoFormatter.formatAppTerminated());
        }

        // static 변수 초기화 - 매번 현재 시간으로 설정하여 중복 노티 방지
        long currentTimeMillis = Calendar.getInstance().getTimeInMillis();
        
        // 마지막으로 처리된 거래의 시간을 찾아서 설정 (매수/매도 구분 없이)
        TradeData lastBuyTrade = processedOrderManager.findLatestProcessedTime(BUY);
        TradeData lastSellTrade = processedOrderManager.findLatestProcessedTime(SELL);
        
        long lastBuyTimeMillis = lastBuyTrade != null ? lastBuyTrade.getProcessedTime() : 0;
        long lastSellTimeMillis = lastSellTrade != null ? lastSellTrade.getProcessedTime() : 0;
        
        // 매수와 매도 중 더 최근 시간을 선택하되, 현재 시간보다는 작게 설정
        long latestTradeTime = Math.max(lastBuyTimeMillis, lastSellTimeMillis);
        
        if (latestTradeTime > 0 && latestTradeTime < currentTimeMillis) {
            lastNotiTimeInMillis = latestTradeTime;
            Calendar lastTradeCal = Calendar.getInstance();
            lastTradeCal.setTimeInMillis(lastNotiTimeInMillis);
            Log.d("KTrader", "[TradeJobService] lastNotiTimeInMillis 초기화 - 마지막 처리된 거래 시간: " + 
                String.format(Locale.getDefault(), "%02d/%02d %02d:%02d:%02d", 
                    lastTradeCal.get(Calendar.MONTH) + 1, lastTradeCal.get(Calendar.DATE),
                    lastTradeCal.get(Calendar.HOUR_OF_DAY), lastTradeCal.get(Calendar.MINUTE), lastTradeCal.get(Calendar.SECOND)));
        } else {
            // 처리된 거래가 없거나 시간이 이상한 경우 현재 시간으로 설정
            lastNotiTimeInMillis = currentTimeMillis;
            Calendar currentCal = Calendar.getInstance();
            currentCal.setTimeInMillis(lastNotiTimeInMillis);
            Log.d("KTrader", "[TradeJobService] lastNotiTimeInMillis 초기화 - 현재 시간으로 설정: " + 
                String.format(Locale.getDefault(), "%02d/%02d %02d:%02d:%02d", 
                    currentCal.get(Calendar.MONTH) + 1, currentCal.get(Calendar.DATE),
                    currentCal.get(Calendar.HOUR_OF_DAY), currentCal.get(Calendar.MINUTE), currentCal.get(Calendar.SECOND)));
        }

        LogInfoFormatter.logInfo(LogInfoFormatter.formatSeparator());
        LogInfoFormatter.logInfo(LogInfoFormatter.formatCurrentTime());

        // 잔고를 가져와 업데이트 한다.
        double krwBalance;
        {
            JSONObject dataObj = orderManager.getBalance("");
            String totalKrw = (String) dataObj.get("total_krw");
            String availableBtc = (String) dataObj.get("available_btc");
            String availableEth = (String) dataObj.get("available_eth");

            if (totalKrw != null) {
                krwBalance = Double.parseDouble(totalKrw);
                
                // 현재 설정된 코인 타입에 따라 적절한 잔고 사용
                String coinType = getCurrentCoinType();
                if ("ETH".equals(coinType) && availableEth != null) {
                    availableCoinBalance = Double.parseDouble(availableEth);
                    Log.d("KTrader", "[TradeJobService] Using ETH balance: " + availableCoinBalance);
                } else if (availableBtc != null) {
                    availableCoinBalance = Double.parseDouble(availableBtc);
                    Log.d("KTrader", "[TradeJobService] Using BTC balance: " + availableCoinBalance);
                } else {
                    LogInfoFormatter.logInfo(LogInfoFormatter.formatBalanceError());
                    sendErrorCard("Balance Error", ERR_API_003.getDescription());
                    return;
                }
            } else {
                LogInfoFormatter.logInfo(LogInfoFormatter.formatBalanceError());
                sendErrorCard("Balance Error", ERR_API_003.getDescription());
                return;
            }
        }

        // 현재 코인 현재가를 가져온다.
        {
            JSONObject dataObj = orderManager.getCurrentPrice("");
            JSONArray dataArray = (JSONArray) dataObj.get("bids"); // 매수가
            if (dataArray != null && !dataArray.isEmpty()) {
                JSONObject item = (JSONObject) dataArray.get(0); // 기본 5개 아이템 중 첫번째 아이템 사용
                String priceStr = (String) item.get("price");
                if (priceStr != null) {
                    currentPrice = (int)Double.parseDouble(priceStr);
                } else {
                    LogInfoFormatter.logInfo(LogInfoFormatter.formatPriceError());
                    sendErrorCard("Price Error", ERR_API_004.getDescription());
                    return;
                }
            } else {
                LogInfoFormatter.logInfo(LogInfoFormatter.formatBuyOrderError());
                sendErrorCard("Buy Order Error", ERR_API_002.getDescription());
                return;
            }

            LogInfoFormatter.logInfo(LogInfoFormatter.formatCurrentPrice(getCurrentCoinType(), currentPrice));
            
            // 카드 데이터 전송
            sendCardData(currentPrice, krwBalance);

            // 빗썸은 0.0001 코인이 최소 거래 단위이므로 체크
            String coinType = getCurrentCoinType();
            if (currentPrice / 10000 > GlobalSettings.getInstance().getUnitPrice()) {
                LogInfoFormatter.logInfo(LogInfoFormatter.formatTradingAmountWarning(
                        GlobalSettings.getInstance().getUnitPrice(), 
                        coinType, 
                        currentPrice / 10000));
                return;
            }

            priceQueueManager.addPrice(currentPrice);

            LogInfoFormatter.logInfo(LogInfoFormatter.formatPriceVariationRate(getPriceVariationRate()));
        }

        // 현재 걸려 있는 매도 리스트를 가져온다.
        {
            Log.d("KTrader", "[TradeJobService] API에서 현재 주문 목록 조회 시작");
            JSONArray dataArray = orderManager.getPlacedOrderList("");
            Log.d("KTrader", "[TradeJobService] placed order item count : " +  dataArray.size());

            for (int i = 0; i < dataArray.size(); i++) {
                JSONObject item = (JSONObject) dataArray.get(i);
                
                String typeStr = (String) item.get("type");
                String unitsStr = (String) item.get("units_remaining");
                String priceStr = (String) item.get("price");
                String orderDateStr = (String) item.get("order_date");
                
                if (typeStr != null && unitsStr != null && priceStr != null && orderDateStr != null) {
                    placedOrderManager.add(placedOrderManager.build()
                            .setType(orderManager.convertOrderType(typeStr))
                            .setStatus(PLACED)
                            .setId((String) item.get("order_id"))
                            .setUnits((float) Double.parseDouble(unitsStr))
                            .setPrice(Integer.parseInt(priceStr.replaceAll(",", "")))
                            .setPlacedTime(Long.parseLong(orderDateStr) / 1000));
                }
            }
            Log.d("KTrader", "[TradeJobService] API 주문 목록을 placedOrderManager에 추가 완료");
        }

        // 현재 매도 걸려 있는 order들이 전부 매도 완료되었을 때 예상 잔고
        // 코인별 특성을 고려한 예상 잔고 계산
        long placedOrderEstimation = placedOrderManager.getEstimation();
        long estimatedBalance = (long)(krwBalance + placedOrderEstimation) + (int)(availableCoinBalance * currentPrice);
        
        Log.d("KTrader", "[TradeJobService] 예상잔고 계산 상세:");
        Log.d("KTrader", "[TradeJobService] - KRW 잔고: " + krwBalance);
        Log.d("KTrader", "[TradeJobService] - 매도 주문 예상 금액: " + placedOrderEstimation);
        Log.d("KTrader", "[TradeJobService] - 코인 잔고 * 현재가: " + (availableCoinBalance * currentPrice));
        Log.d("KTrader", "[TradeJobService] - 최종 예상잔고: " + estimatedBalance);
        
        LogInfoFormatter.logInfo(LogInfoFormatter.formatEstimatedBalance(estimatedBalance, (long)(krwBalance), coinSpecific.getCoinType()));
        
        // 매도 완료 시 잔고 계산 (코인별 특성 고려)
        long sellCompleteBalance = (long)(placedOrderEstimation);
        
        // 주문 잔고: 매수 대기 중인 주문의 금액 (미체결 주문 예상 금액)
        int orderBalance = (int)placedOrderEstimation;
        
        Log.d("KTrader", "[TradeJobService] 매도완료시 계산 상세:");
        Log.d("KTrader", "[TradeJobService] - 매도 주문 예상 금액: " + sellCompleteBalance);
        Log.d("KTrader", "[TradeJobService] - 주문잔고 (매수 대기 중인 주문 금액): " + orderBalance);
        
        LogInfoFormatter.logInfo(LogInfoFormatter.formatSellCompleteBalance(sellCompleteBalance, orderBalance, coinSpecific.getCoinType()));

        // 매수/매도 완료 이력을 가져온다.
        {
            JSONArray dataArray = orderManager.getProcessedOrderList("", 0, "15");
            for (Object o : dataArray) {
                JSONObject item = (JSONObject)o;
//              Log.d("KTrader", item.toString());

                String searchStr = (String)item.get("search");
                String transferDateStr = (String)item.get("transfer_date");
                
                if (searchStr != null && transferDateStr != null) {
                    int search = Integer.parseInt(searchStr);
                    long processedTimeInMillis;
                    
                    if (transferDateStr.length() == 13)
                        processedTimeInMillis = Long.parseLong(transferDateStr);
                    else // micro second
                        processedTimeInMillis = Long.parseLong(transferDateStr) / 1000;

                if (processedOrderManager.findByProcessedTime(processedTimeInMillis) == null && convertSearchType(search) != NONE) {
//                                Log.d("KTrader", item.toString());
                    String unitsStr = (String) item.get("units");
                    String priceStr = (String) item.get("price");
                    String feeStr = (String) item.get("fee");
                    
                    if (unitsStr != null && priceStr != null) {
                        processedOrderManager.add(processedOrderManager.build()
                                .setType(convertSearchType(search))
                                .setStatus(PROCESSED)
                                .setUnits(((float) Double.parseDouble(unitsStr.replace(" ", "").replace("-", ""))))
                                .setPrice(Math.abs(Integer.parseInt(priceStr)))
                                .setFeeRaw(feeStr)
                                .setProcessedTime(processedTimeInMillis));
                    }
                }
                }
            }
        }

        // 마지막 매수 관련 정보를 초기화 한다.
        {
            TradeData data = processedOrderManager.findLatestProcessedTime(BUY);
            if (data != null) {
                Calendar lastBuyTime;
                lastBuyTime = Calendar.getInstance();
                lastBuyTime.setTimeInMillis(data.getProcessedTime());
                LogInfoFormatter.logInfo(LogInfoFormatter.formatLastBuyInfo(data.getPrice(), data.getProcessedTime()));
            }
        }

        // 마지막 매도 관련 정보를 초기화 환다.
        {
            TradeData data = processedOrderManager.findLatestProcessedTime(SELL);
            if (data != null) {
                Calendar lastSellTime;
                lastSellTime = Calendar.getInstance();
                lastSellTime.setTimeInMillis(data.getProcessedTime());
                LogInfoFormatter.logInfo(LogInfoFormatter.formatLastSellInfo(data.getPrice(), data.getProcessedTime()));
            }
        }

        // 마지막 Noti 이후 발생한 매도/매수에 대해서 Noti를 발송하고, 매수건에 대해서는 이익금을 더해 매도 오더를 발행한다.
        {
            // 마지막 Noti 이후 발생한 매도/매수만 필터링 한 결과를 얻는다.
            List<TradeData> allTrades = processedOrderManager.getList();
            Log.d("KTrader", "[TradeJobService] 전체 처리된 거래 수: " + allTrades.size());
            
            // 각 거래의 시간을 로그로 출력
            for (TradeData trade : allTrades) {
                Calendar tradeCal = Calendar.getInstance();
                tradeCal.setTimeInMillis(trade.getProcessedTime());
                Log.d("KTrader", "[TradeJobService] 거래 시간: " + 
                    String.format(Locale.getDefault(), "%02d/%02d %02d:%02d:%02d", 
                        tradeCal.get(Calendar.MONTH) + 1, tradeCal.get(Calendar.DATE),
                        tradeCal.get(Calendar.HOUR_OF_DAY), tradeCal.get(Calendar.MINUTE), tradeCal.get(Calendar.SECOND)) + 
                    ", 타입: " + trade.getType() + ", 가격: " + trade.getPrice());
            }
            
            List<TradeData> list = allTrades.stream()
                    .filter(T -> T.getProcessedTime() > lastNotiTimeInMillis)
                    .collect(Collectors.toList());
            
            Log.d("KTrader", "[TradeJobService] 필터링된 새로운 거래 수: " + list.size());
            
            Calendar lastNotiCal = Calendar.getInstance();
            lastNotiCal.setTimeInMillis(lastNotiTimeInMillis);
            Log.d("KTrader", "[TradeJobService] lastNotiTimeInMillis: " + 
                String.format(Locale.getDefault(), "%02d/%02d %02d:%02d:%02d", 
                    lastNotiCal.get(Calendar.MONTH) + 1, lastNotiCal.get(Calendar.DATE),
                    lastNotiCal.get(Calendar.HOUR_OF_DAY), lastNotiCal.get(Calendar.MINUTE), lastNotiCal.get(Calendar.SECOND)));

            // 동일 가격이 여러개로 나눠져 있으면 합친다. (따로 매도 등록 되지 않도록 방지)
            List<TradeData> newList = mergeSamePrice(list);

            // 각 항목에 대해 Noti 처리한다.
            long maxProcessedTime = lastNotiTimeInMillis; // 현재까지의 최대 처리 시간
            
            for (TradeData pData : newList) {
                Calendar time = Calendar.getInstance();
                time.setTimeInMillis(pData.getProcessedTime());

                if (pData.getType() == BUY) {
                    LogInfoFormatter.logInfo(LogInfoFormatter.formatBuyOccurred(pData.getPrice(), pData.getProcessedTime()));
                    notificationManager.sendTradeNotification("매수 발생", "매수 : " + String.format(Locale.getDefault(), "%,d", pData.getPrice()) + ", " + String.format(Locale.getDefault(), "%02d/%02d %02d:%02d"
                            , time.get(Calendar.MONTH) + 1, time.get(Calendar.DATE)
                            , time.get(Calendar.HOUR_OF_DAY), time.get(Calendar.MINUTE)));

                    // 매도 오더 발행 : 마지막 매수 오더가 완료되었다면 +INTERVAL_PRICE 가격에 매도 오더를 발행한다.
                    // 매수되었던 unit이 소수점 4자리 이하 일수도 있으니 다시 4자리로 절사 한다.
                    float unit = (float)((int)(pData.getUnits() * 10000) / 10000.0);

                    // 코인별 최소 거래 수량을 고려한 반올림 처리
                    double minTradingAmount = coinSpecific.getMinimumTradingAmount();
                    if ((pData.getUnits() - unit) > (minTradingAmount / 2)) {
                        if ((availableCoinBalance - unit) > minTradingAmount) {
                            unit = (float)(Math.round(pData.getUnits() * 10000d) / 10000d);
                            LogInfoFormatter.logInfo(LogInfoFormatter.formatSellCorrection(pData.getUnits(), unit));
                        }
                    }

                    // 이전 매수된 BTC 가 소수점 5자리에서 반올림 되는 경우 대비
                    // 남은 잔고보다 계산 값이 큰 경우에는 서버 에러가 발생하므로 잔고만큼만 매도한다. (ex : 0.0047 vs 0.00469..)
                    // 런타임에 availableCoinBalance 값이 변경되므로 조건문은 정상적으로 동작함
                    Log.d("KTrader", "[TradeJobService] unit : " + unit + " availableCoinBalance: " + availableCoinBalance);

                    // 코인별 특성 가져오기
                    // coinSpecific은 이미 메서드 시작 부분에서 정의됨
                    
                    // 코인 잔고가 코인별 최소 거래 수량보다 작은 경우 매도 주문 건너뜀
                    if (availableCoinBalance <= coinSpecific.getMinimumTradingAmount()) {
                        LogInfoFormatter.logInfo("매도 주문 건너뜀 - 코인 잔고 부족: " + availableCoinBalance + " (최소: " + coinSpecific.getMinimumTradingAmount() + ")");
                        Log.d("KTrader", "[TradeJobService] 매도 주문 건너뜀 - 코인 잔고 부족: " + availableCoinBalance + " (최소: " + coinSpecific.getMinimumTradingAmount() + ")");
                        continue; // 다음 매수 건으로 이동
                    }

                    // 매수한 코인이 아직 체결되지 않은 경우와 보유 코인 부족을 구분하여 처리
                    if (availableCoinBalance == 0.0 && unit > 0) {
                        // 매수한 코인이 아직 체결되지 않은 경우: 매수한 수량으로 매도 주문 시도
                        Log.d("KTrader", "[TradeJobService] 매수한 코인이 아직 체결되지 않음. 매수한 수량(" + unit + ")으로 매도 주문 시도");
                        LogInfoFormatter.logInfo("매수한 코인이 아직 체결되지 않아 매수한 수량으로 매도 주문을 시도합니다: " + unit);
                    } else if (unit > availableCoinBalance && availableCoinBalance > 0) {
                        // 보유 코인이 부족한 경우: 보유 코인 수량으로 조정
                        LogInfoFormatter.logInfo(LogInfoFormatter.formatSellCorrection2(unit, availableCoinBalance));
                        unit = (float)((int)(availableCoinBalance * 10000) / 10000.0);
                        Log.d("KTrader", "[TradeJobService] 매도 수량을 보유 잔고로 조정: " + unit);
                        
                        // 수량이 코인별 최소 거래 수량보다 작아진 경우 매도 주문 건너뜀
                        if (!coinSpecific.isTradableQuantity(unit)) {
                            LogInfoFormatter.logInfo("매도 주문 건너뜀 - 조정된 수량이 " + coinSpecific.getCoinType() + " 최소 단위보다 작음: " + unit + " (최소: " + coinSpecific.getMinimumTradingAmount() + ")");
                            Log.d("KTrader", "[TradeJobService] 매도 주문 건너뜀 - 조정된 수량이 " + coinSpecific.getCoinType() + " 최소 단위보다 작음: " + unit + " (최소: " + coinSpecific.getMinimumTradingAmount() + ")");
                            continue; // 다음 매수 건으로 이동
                        }
                    }

                    // 매수된 내용이 있다면 가능한 상위 slot에 매도하도록 한다.
                    boolean isSold = false;
                    for (int i = 0; i< SELL_SLOT_LOOK_ASIDE_MAX; i++) {
                        // intervalPrice가 바뀌는 경계값일 때 문제를 해결하기 위해서 매도할 때의 interval은 현재가가 아니라 매수가를 기준으로 산정한다.
                        int sellIntervalPrice = MainPage.getSlotIntervalPrice(pData.getPrice());
                        int targetPrice = pData.getPrice() + MainPage.getProfitPrice(pData.getPrice()) + (sellIntervalPrice * (SELL_SLOT_LOOK_ASIDE_MAX - 1 - i));
                        if ((pData.getPrice() % sellIntervalPrice) != 0)
                            targetPrice = (pData.getPrice() - (pData.getPrice() % sellIntervalPrice) + sellIntervalPrice) + MainPage.getProfitPrice(pData.getPrice()) + (sellIntervalPrice * (SELL_SLOT_LOOK_ASIDE_MAX - 1 - i));

                        TradeData oData = placedOrderManager.findByPrice(SELL, targetPrice);
                        // 런타임에 oData 값이 변경되므로 조건문은 정상적으로 동작함
                        @SuppressWarnings("ConstantConditions")
                        boolean oDataCondition = oData == null || // Slot이 비어 있다면 해당 Slot에 매도 주문을 넣는다.
                                (oData != null && isSameSlotOrder(oData, pData, targetPrice)); // 해당 Slot에 이미 Order가 있는 경우라도 분할 매수된 경우라면 동일 가격으로 매도 주문하도록 한다.
                        if (oDataCondition) {
                            // 매도 주문 전 최종 수량 검증 (코인별 특성 적용)
                            if (!coinSpecific.isTradableQuantity(unit)) {
                                LogInfoFormatter.logInfo("매도 주문 건너뜀 - 최종 수량이 " + coinSpecific.getCoinType() + " 최소 단위보다 작음: " + unit + " (최소: " + coinSpecific.getMinimumTradingAmount() + ")");
                                Log.d("KTrader", "[TradeJobService] 매도 주문 건너뜀 - 최종 수량이 " + coinSpecific.getCoinType() + " 최소 단위보다 작음: " + unit + " (최소: " + coinSpecific.getMinimumTradingAmount() + ")");
                                continue; // 다음 슬롯으로 이동
                            }
                            
                            Log.d("KTrader", "[TradeJobService] 매도 주문 시도 - 가격: " + targetPrice + ", 수량: " + unit);
                            JSONObject sellResult = orderManager.addOrder("매수 발생 대응 매도", SELL, unit, targetPrice);
                            if (sellResult == null) {
                                Log.e("KTrader", "[TradeJobService] 매도 주문 실패 - API 응답이 null");
                                // isSold는 false로 유지되어 매도 실패 노티가 발생함
                            } else if (!"0000".equals(sellResult.get("status"))) {
                                Log.e("KTrader", "[TradeJobService] 매도 주문 실패 - 상태: " + sellResult.get("status") + ", 메시지: " + sellResult.get("message"));
                                // isSold는 false로 유지되어 매도 실패 노티가 발생함
                            } else {
                                Log.d("KTrader", "[TradeJobService] 매도 주문 성공: " + sellResult.toString());
                                isSold = true;
                                availableCoinBalance -= unit;

                                // 매도 대기 정보 업데이트 노티 발생
                                Calendar sellTime = Calendar.getInstance();
                                String notificationTitle = "매도 대기 등록";
                                String notificationText = "매도 대기 : " + String.format(Locale.getDefault(), "%,d", targetPrice) + 
                                    ", " + String.format(Locale.getDefault(), "%02d/%02d %02d:%02d",
                                    sellTime.get(Calendar.MONTH) + 1, sellTime.get(Calendar.DATE),
                                    sellTime.get(Calendar.HOUR_OF_DAY), sellTime.get(Calendar.MINUTE));
                                
                                Log.d("KTrader", "[TradeJobService] 매도 대기 등록 노티 발생: " + notificationText);
                                notificationManager.sendTradeNotification(notificationTitle, notificationText);

                                // 실제 Order ID를 설정하여 placedOrderManager에 추가
                                String orderId = (String) sellResult.get("order_id");
                                if (orderId != null && !orderId.isEmpty()) {
                                    placedOrderManager.add(placedOrderManager.build()
                                            .setType(SELL)
                                            .setStatus(PLACED)
                                            .setId(orderId)  // 실제 Order ID 설정
                                            .setUnits(unit)
                                            .setPrice(targetPrice));
                                    Log.d("KTrader", "[TradeJobService] 매도 주문을 placedOrderManager에 추가 - Order ID: " + orderId);
                                } else {
                                    Log.e("KTrader", "[TradeJobService] Order ID가 null이거나 비어있음: " + sellResult.toString());
                                }
                                break;
                            }
                        }
                    }
                    if (!isSold) {
                        notificationManager.sendTradeNotification("매도 실패", "매도시도 : "
                                + String.format(Locale.getDefault(), "%,d", pData.getPrice()));
                    }
                } else if (pData.getType() == SELL) {
                    LogInfoFormatter.logInfo(LogInfoFormatter.formatSellOccurred(pData.getPrice(), pData.getProcessedTime()));
                    notificationManager.sendTradeNotification("매도 발생", "매도 : " + String.format(Locale.getDefault(), "%,d", pData.getPrice()) + ", " + String.format(Locale.getDefault(), "%02d/%02d %02d:%02d"
                            , time.get(Calendar.MONTH) + 1, time.get(Calendar.DATE)
                            , time.get(Calendar.HOUR_OF_DAY), time.get(Calendar.MINUTE)));
                } else {
                    // BUY, SELL 이외 수수료 쿠폰 구입 등의 항목일 경우에 여기로 올 수 있다.
                    LogInfoFormatter.logInfo(LogInfoFormatter.formatOtherTradeItem(pData.getType().toString()));
                }

                // 최대 처리 시간 업데이트
                if (pData.getProcessedTime() > maxProcessedTime) {
                    maxProcessedTime = pData.getProcessedTime();
                }
            }
            
            // 모든 노티 처리 완료 후 lastNotiTimeInMillis 업데이트
            if (maxProcessedTime > lastNotiTimeInMillis) {
                lastNotiTimeInMillis = maxProcessedTime;
                Calendar updatedCal = Calendar.getInstance();
                updatedCal.setTimeInMillis(lastNotiTimeInMillis);
                Log.d("KTrader", "[TradeJobService] lastNotiTimeInMillis 업데이트 완료: " + 
                    String.format(Locale.getDefault(), "%02d/%02d %02d:%02d:%02d", 
                        updatedCal.get(Calendar.MONTH) + 1, updatedCal.get(Calendar.DATE),
                        updatedCal.get(Calendar.HOUR_OF_DAY), updatedCal.get(Calendar.MINUTE), updatedCal.get(Calendar.SECOND)));
            }
        }

        // 매수건에 대한 매도를 다 처리 했음에도 코인 잔고가 남아 있는 경우에 대한 예외처리, 가능한 slot을 찾아 매도 오더를 발행한다.
        // 예) 매수 발생 후 앱이 종료되었다가 앱이 재실행 된 경우
        if (availableCoinBalance > coinSpecific.getMinimumTradingAmount()) {
            LogInfoFormatter.logInfo(LogInfoFormatter.formatSellRequiredBalance(availableCoinBalance));
            // 현재가보다 상위에 비어 있는 slot 중 하나를 찾아보고 있다면 매도하도록 한다.
            int floorPrice = getFloorPrice(currentPrice);
            double unit = Math.min(getUnitAmount4Price(floorPrice), (availableCoinBalance * 10000) / 10000.0);
            
            // 코인별 특성 가져오기 (예외 처리)
            // coinSpecific은 이미 메서드 시작 부분에서 정의됨
            
            // 수량이 코인별 최소 거래 수량보다 작은 경우 매도 주문 건너뜀
            if (!coinSpecific.isTradableQuantity(unit)) {
                LogInfoFormatter.logInfo("예외 처리 매도 주문 건너뜀 - 수량이 " + coinSpecific.getCoinType() + " 최소 단위보다 작음: " + unit + " (최소: " + coinSpecific.getMinimumTradingAmount() + ")");
                Log.d("KTrader", "[TradeJobService] 예외 처리 매도 주문 건너뜀 - 수량이 " + coinSpecific.getCoinType() + " 최소 단위보다 작음: " + unit + " (최소: " + coinSpecific.getMinimumTradingAmount() + ")");
            } else {
                int sellIntervalPrice = MainPage.getSlotIntervalPrice(floorPrice) ;
            for (int i = 0; i< SELL_SLOT_LOOK_ASIDE_MAX; i++) {
                int targetPrice = floorPrice + MainPage.getProfitPrice(floorPrice) + (sellIntervalPrice * (SELL_SLOT_LOOK_ASIDE_MAX - 1 - i));

                TradeData oData = placedOrderManager.findByPrice(SELL, targetPrice);
                // 런타임에 oData 값이 변경되므로 조건문은 정상적으로 동작함
                @SuppressWarnings("ConstantConditions")
                boolean oDataCondition = oData == null || // Slot이 비어 있다면 해당 Slot에 매도 주문을 넣는다.
                        (oData != null && isSameSlotOrder(oData, new TradeData().build().setUnits((float)unit), targetPrice)); // 해당 Slot에 이미 Order가 있는 경우라도 분할 매수된 경우라면 동일 가격으로 매도 주문하도록 한다.
                if (oDataCondition) {
                    // 예외 처리 매도 주문 전 최종 수량 검증 (코인별 특성 적용)
                    if (!coinSpecific.isTradableQuantity(unit)) {
                        LogInfoFormatter.logInfo("예외 처리 매도 주문 건너뜀 - 최종 수량이 " + coinSpecific.getCoinType() + " 최소 단위보다 작음: " + unit + " (최소: " + coinSpecific.getMinimumTradingAmount() + ")");
                        Log.d("KTrader", "[TradeJobService] 예외 처리 매도 주문 건너뜀 - 최종 수량이 " + coinSpecific.getCoinType() + " 최소 단위보다 작음: " + unit + " (최소: " + coinSpecific.getMinimumTradingAmount() + ")");
                        continue; // 다음 슬롯으로 이동
                    }
                    
                    JSONObject sellResult = orderManager.addOrder("이전 실행 매수 발생 대응 매도", SELL, unit, targetPrice);
                    if (sellResult == null) {
                        Log.e("KTrader", "[TradeJobService] 예외 처리 매도 주문 실패 - API 응답이 null");
                        return;
                    } else if (!"0000".equals(sellResult.get("status"))) {
                        Log.e("KTrader", "[TradeJobService] 예외 처리 매도 주문 실패 - 상태: " + sellResult.get("status") + ", 메시지: " + sellResult.get("message"));
                        return;
                    } else {
                        Log.d("KTrader", "[TradeJobService] 예외 처리 매도 주문 성공: " + sellResult.toString());
                        availableCoinBalance -= unit;

                        // 매도 대기 정보 업데이트 노티 발생
                        Calendar exceptionTime = Calendar.getInstance();
                        String notificationTitle = "매도 대기 등록";
                        String notificationText = "매도 대기 : " + String.format(Locale.getDefault(), "%,d", targetPrice) + 
                            ", " + String.format(Locale.getDefault(), "%02d/%02d %02d:%02d",
                            exceptionTime.get(Calendar.MONTH) + 1, exceptionTime.get(Calendar.DATE),
                            exceptionTime.get(Calendar.HOUR_OF_DAY), exceptionTime.get(Calendar.MINUTE));
                        
                        Log.d("KTrader", "[TradeJobService] 예외 처리 매도 대기 등록 노티 발생: " + notificationText);
                        notificationManager.sendTradeNotification(notificationTitle, notificationText);

                        // 실제 Order ID를 설정하여 placedOrderManager에 추가
                        String orderId = (String) sellResult.get("order_id");
                        if (orderId != null && !orderId.isEmpty()) {
                            placedOrderManager.add(placedOrderManager.build()
                                    .setType(SELL)
                                    .setStatus(PLACED)
                                    .setId(orderId)  // 실제 Order ID 설정
                                    .setUnits((float)unit)
                                    .setPrice(targetPrice));
                            Log.d("KTrader", "[TradeJobService] 매도 주문을 placedOrderManager에 추가 - Order ID: " + orderId);
                        } else {
                            Log.e("KTrader", "[TradeJobService] Order ID가 null이거나 비어있음: " + sellResult.toString());
                        }
                        break;
                    }
                }
            }
            }
        }

        // 매수 요청 발행, 어느 시점에서나 active한 매수 오더는 1개만 유지하도록 한다.
        {
            Log.d("KTrader", "[TradeJobService] 매수 주문 로직 시작 - 현재가: " + currentPrice);
            Log.d("KTrader", "[TradeJobService] KRW 잔고: " + krwBalance);
            
            for (int i = 0; i< BUY_SLOT_LOOK_ASIDE_MAX; i++) {
                int targetPrice = getFloorPrice(currentPrice);
                targetPrice -= (i * (MainPage.getSlotIntervalPrice(targetPrice)));
                
                Log.d("KTrader", "[TradeJobService] 매수 슬롯 " + i + " - 목표가격: " + targetPrice);

                // 해당 가격에 이미 대기중인 매수가 있다면 skip
                TradeData existingBuy = placedOrderManager.findByPrice(BUY, targetPrice);
                if (existingBuy != null) {
                    Log.d("KTrader", "[TradeJobService] 이미 대기중인 매수 주문 존재 - 가격: " + targetPrice + ", 수량: " + existingBuy.getUnits());
                    return;
                }

                // 해당 가격에 이미 대기중인 매도가 있다면 skip
                int sellPrice = targetPrice + MainPage.getProfitPrice(targetPrice);
                TradeData existingSell = placedOrderManager.findByPrice(SELL, sellPrice);
                if (existingSell != null) {
                    Log.d("KTrader", "[TradeJobService] 이미 대기중인 매도 주문 존재 - 가격: " + sellPrice + ", 수량: " + existingSell.getUnits());
                    continue;
                }

                LogInfoFormatter.logInfo(LogInfoFormatter.formatNextLowBuyPrice(targetPrice));

                // 매수 주문 전 잔고 확인 (부동소수점 오차 고려)
                double requiredAmount = getUnitAmount4Price(targetPrice) * targetPrice;
                Log.d("KTrader", "[TradeJobService] 매수 주문 필요 금액: " + requiredAmount + ", 보유 금액: " + krwBalance);
                
                // 부동소수점 오차를 고려한 잔고 확인 (0.01원 여유분 추가)
                if (krwBalance < (requiredAmount + 0.01)) {
                    LogInfoFormatter.logInfo("잔고 부족으로 매수 주문을 건너뜁니다. 필요: " +
                        String.format(Locale.getDefault(), "%,.2f", requiredAmount) + 
                        "원, 보유: " + String.format(Locale.getDefault(), "%,.2f", krwBalance) + "원");
                    Log.d("KTrader", "[TradeJobService] 잔고 부족으로 매수 주문 건너뜀");
                    continue; // 다음 슬롯으로 이동
                }

                // 체결 되기 어려운 낮은 가격 order는 모두 취소한다.
                Log.d("KTrader", "[TradeJobService] 기존 매수 주문 취소 시작");
                for (TradeData tmp : placedOrderManager.getList()) {
                    if (tmp.getType() == BUY) {  // 1000만원 단위 경계에서 buy price가 미세하게 차이나서 data가 null이 되어 들어올 수 있으므로 전체 buy를 취소한다.
                        // Order ID 유효성 검사
                        if (tmp.getId() == null || tmp.getId().isEmpty()) {
                            Log.e("KTrader", "[TradeJobService] Order ID가 null이거나 비어있어서 취소 건너뜀: " + tmp.toString());
                            continue;
                        }
                        
                        Log.d("KTrader", "[TradeJobService] 기존 매수 주문 취소 - Order ID: " + tmp.getId() + ", 가격: " + tmp.getPrice() + ", 수량: " + tmp.getUnits());
                        if (!orderManager.cancelOrder("체결 안 될 오더", tmp)) {
                            Log.e("KTrader", "[TradeJobService] 기존 매수 주문 취소 실패 - Order ID: " + tmp.getId());
                            return;
                        } else {
                            Log.d("KTrader", "[TradeJobService] 기존 매수 주문 취소 성공 - Order ID: " + tmp.getId());
                        }
                    }
                }

                // add buy request for target price
                double unitAmount = getUnitAmount4Price(targetPrice);
                Log.d("KTrader", "[TradeJobService] 매수 주문 발행 시도 - 가격: " + targetPrice + ", 수량: " + unitAmount + ", 필요 금액: " + (unitAmount * targetPrice));
                
                JSONObject buyResult = orderManager.addOrder("저점", BUY, unitAmount, targetPrice);
                if (buyResult == null) {
                    Log.e("KTrader", "[TradeJobService] 매수 주문 발행 실패 - API 응답이 null");
                    return;
                } else if (!"0000".equals(buyResult.get("status"))) {
                    Log.e("KTrader", "[TradeJobService] 매수 주문 발행 실패 - 상태: " + buyResult.get("status") + ", 메시지: " + buyResult.get("message"));
                    return;
                } else {
                    Log.d("KTrader", "[TradeJobService] 매수 주문 발행 성공: " + buyResult.toString());
                }
                break;
            }
        }
    }

    // 주어진 가격 아래쪽의 첫번째 매수 slot 가격을 구한다.
    private int getFloorPrice(int price) {
        return price - (price % MainPage.getSlotIntervalPrice(price));
    }

    // 주어진 가격 slot에 매수 가능한 코인 개수를 구한다. 소수점 아래 4자리로 절사
    private double getUnitAmount4Price(int price) {
        double unitAmount = (double)GlobalSettings.getInstance().getUnitPrice() / price;
        // 소수점 4자리로 반올림하여 부동소수점 오차 방지
        return Math.round(unitAmount * 10000.0) / 10000.0;
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
    
    /**
     * PriceQueueManager를 이용한 1시간 등락폭 계산
     */
    private String getCurrentPriceChangeFromApi() {
        try {
            PriceQueueManager priceManager = PriceQueueManager.getInstance();
            
            // 큐에 충분한 데이터가 있는지 확인 (최소 2개 이상)
            if (!priceManager.hasMinimumData(2)) {
                Log.w("KTrader", "[TradeJobService] Not enough price data for variation calculation, using default");
                return "+0.00%";
            }
            
            // PriceQueueManager에서 변동률 계산
            float variationRate = priceManager.getPriceVariationRate();
            
            // 변동률을 퍼센트 문자열로 포맷팅
            String formattedChange;
            if (variationRate >= 0) {
                formattedChange = String.format("+%.2f%%", variationRate);
            } else {
                formattedChange = String.format("%.2f%%", variationRate);
            }
            
            Log.d("KTrader", "[TradeJobService] Calculated hourly change from PriceQueueManager: " + formattedChange);
            Log.d("KTrader", "[TradeJobService] PriceQueue status: " + priceManager.getQueueStatus());
            
            return formattedChange;
            
        } catch (Exception e) {
            Log.e("KTrader", "[TradeJobService] Error calculating hourly change from PriceQueueManager", e);
            return "+0.00%";
        }
    }
    
    /**
     * API에서 전일 대비 등락률 정보를 가져옴 (CoinInfo용)
     */
    private String getDailyChangeFromApi() {
        try {
            // TransactionDataManager를 통해 최신 등락률 정보 가져오기
            com.example.k_trader.data.TransactionDataManager dataManager = 
                com.example.k_trader.data.TransactionDataManager.getInstance(KTraderApplication.getAppContext());
            
            // 캐시된 데이터에서 전일 대비 등락률 정보 가져오기
            com.example.k_trader.data.TransactionData cachedData = dataManager.getCachedData();
            if (cachedData != null && cachedData.getDailyChange() != null) {
                String change = cachedData.getDailyChange();
                Log.d("KTrader", "[TradeJobService] Using cached daily change (24H): " + change);
                return change;
            }
            
            // 캐시된 데이터가 없으면 기본값 반환
            Log.w("KTrader", "[TradeJobService] No cached daily change data available");
            return "+0.00%";
            
        } catch (Exception e) {
            Log.e("KTrader", "[TradeJobService] Error getting daily change from API", e);
            return "+0.00%";
        }
    }

    public void setContext(Context ctx) {
        this.ctx = ctx;
    }

    public void setOrderManager(OrderManager orderManager) {
        this.orderManager = orderManager;
    }
    
    private void sendCardData(int currentPrice, double krwBalance) {
        try {
            Calendar currentTime = Calendar.getInstance();
            String transactionTime = String.format(Locale.getDefault(), "%d/%02d/%02d %02d:%02d:%02d",
                currentTime.get(Calendar.YEAR), currentTime.get(Calendar.MONTH) + 1, currentTime.get(Calendar.DATE),
                currentTime.get(Calendar.HOUR_OF_DAY), currentTime.get(Calendar.MINUTE), currentTime.get(Calendar.SECOND));
            
            String coinCurrentPrice = String.format(Locale.getDefault(), "₩%,d", currentPrice);
            
            // 시간당 변화율은 API에서 가져온 실제 데이터 사용
            String hourlyChange = getCurrentPriceChangeFromApi();
            
            String estimatedBalance = String.format(Locale.getDefault(), "₩%,.0f", krwBalance);
            
            // 마지막 매수 정보 가져오기
            String lastBuyPrice = "정보 없음";
            TradeData lastBuyData = processedOrderManager.findLatestProcessedTime(BUY);
            if (lastBuyData != null) {
                Calendar lastBuyTime = Calendar.getInstance();
                lastBuyTime.setTimeInMillis(lastBuyData.getProcessedTime());
                lastBuyPrice = String.format(Locale.getDefault(), "₩%,d (%02d/%02d %02d:%02d)",
                    lastBuyData.getPrice(),
                    lastBuyTime.get(Calendar.MONTH) + 1, lastBuyTime.get(Calendar.DATE),
                    lastBuyTime.get(Calendar.HOUR_OF_DAY), lastBuyTime.get(Calendar.MINUTE));
            }
            
            // 마지막 매도 정보 가져오기
            String lastSellPrice = "정보 없음";
            TradeData lastSellData = processedOrderManager.findLatestProcessedTime(SELL);
            if (lastSellData != null) {
                Calendar lastSellTime = Calendar.getInstance();
                lastSellTime.setTimeInMillis(lastSellData.getProcessedTime());
                lastSellPrice = String.format(Locale.getDefault(), "₩%,d (%02d/%02d %02d:%02d)",
                    lastSellData.getPrice(),
                    lastSellTime.get(Calendar.MONTH) + 1, lastSellTime.get(Calendar.DATE),
                    lastSellTime.get(Calendar.HOUR_OF_DAY), lastSellTime.get(Calendar.MINUTE));
            }
            
            // 다음 저점 매수가 계산 (간단한 예시)
            String nextBuyPrice = String.format(Locale.getDefault(), "₩%,d (%02d/%02d %02d:%02d)",
                currentPrice - 100000, // 현재가에서 10만원 낮춘 가격
                currentTime.get(Calendar.MONTH) + 1, currentTime.get(Calendar.DATE),
                currentTime.get(Calendar.HOUR_OF_DAY), currentTime.get(Calendar.MINUTE) + 5);
            
            // 가격 정보를 데이터베이스에 저장 (실시간 관찰을 위해) - CoinInfo용 전일 대비 등락률 사용
            String dailyChangeForCoinInfo = getDailyChangeFromApi();
            savePriceInfoToDatabase(coinCurrentPrice, dailyChangeForCoinInfo);
            
            Intent intent = new Intent("TRADE_CARD_DATA");
            intent.putExtra("transactionTime", transactionTime);
            intent.putExtra("btcCurrentPrice", coinCurrentPrice);  // MainPage에서 사용하는 키로 변경
            intent.putExtra("hourlyChange", hourlyChange);
            intent.putExtra("estimatedBalance", estimatedBalance);
            intent.putExtra("lastBuyPrice", lastBuyPrice);
            intent.putExtra("lastSellPrice", lastSellPrice);
            intent.putExtra("nextBuyPrice", nextBuyPrice);
            
            Log.d("KTrader", "[TradeJobService] Sending card data - Price: " + coinCurrentPrice + ", Change: " + hourlyChange);
            LocalBroadcastManager.getInstance(KTraderApplication.getAppContext()).sendBroadcast(intent);
        } catch (Exception e) {
            Log.e("[TradeJobService]", "카드 데이터 전송 중 오류 발생", e);
            
            // 에러 카드 전송
            sendErrorCard("Card Data Send Error", ERR_CARD_DATA_001.getDescription());
        }
    }
    
    private void sendErrorCard(String errorType, String errorMessage) {
        try {
            Calendar currentTime = Calendar.getInstance();
            String errorTime = String.format(Locale.getDefault(), "%d/%02d/%02d %02d:%02d:%02d",
                currentTime.get(Calendar.YEAR), currentTime.get(Calendar.MONTH) + 1, currentTime.get(Calendar.DATE),
                currentTime.get(Calendar.HOUR_OF_DAY), currentTime.get(Calendar.MINUTE), currentTime.get(Calendar.SECOND));
            
            Intent intent = new Intent("TRADE_ERROR_CARD");
            intent.putExtra("errorTime", errorTime);
            intent.putExtra("errorType", errorType);
            intent.putExtra("errorMessage", errorMessage);
            
            LocalBroadcastManager.getInstance(KTraderApplication.getAppContext()).sendBroadcast(intent);
        } catch (Exception e) {
            Log.e("[TradeJobService[", "에러 카드 전송 중 오류 발생", e);
        }
    }
    
    
    /**
     * 가격 정보를 데이터베이스에 저장
     */
    private void savePriceInfoToDatabase(String currentPrice, String priceChange) {
        try {
            // 현재 코인 타입 가져오기
            String coinType = com.example.k_trader.base.GlobalSettings.getInstance().getCoinType();
            
            // CoinPriceInfoRepository를 사용하여 데이터베이스에 저장
            com.example.k_trader.database.CoinPriceInfoRepository repository = 
                new com.example.k_trader.database.CoinPriceInfoRepository(KTraderApplication.getAppContext());
            
            repository.savePriceInfo(coinType, currentPrice, priceChange)
                .subscribe(
                    () -> Log.d("KTrader", "[TradeJobService] Price info saved to database successfully"),
                    throwable -> Log.e("KTrader", "[TradeJobService] Error saving price info to database", throwable)
                );
                
        } catch (Exception e) {
            Log.e("KTrader", "[TradeJobService] Error in savePriceInfoToDatabase", e);
        }
    }
}
