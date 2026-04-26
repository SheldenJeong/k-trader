package com.example.k_trader.ui.fragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.example.k_trader.R;
import com.example.k_trader.base.TradeData;
import com.example.k_trader.database.CoinPriceInfoRepository;
import com.example.k_trader.database.TransactionInfoRepository;
import com.example.k_trader.database.entities.TransactionInfoEntity;
import com.example.k_trader.ui.activity.MainActivity;
import com.example.k_trader.presentation.viewmodel.ViewModels.MainViewModel;
import com.example.k_trader.service.TradeJobService;
import com.example.k_trader.base.GlobalSettings;
import com.example.k_trader.base.OrderManager;
import com.example.k_trader.base.DatabaseOrderManager;
import com.example.k_trader.util.PriceQueueManager;

import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;


/**
 * Created by 김무창 on 2017-12-20.
 */

public class MainPage extends Fragment {

    private static final String KEY_TRADING_STATE = "KEY_TRADING_STATE";
    public static final String BROADCAST_CARD_DATA = "TRADE_CARD_DATA";
    public static final String BROADCAST_TRANSACTION_DATA = "com.example.k_trader.TRANSACTION_DATA_UPDATED";

    private android.support.design.widget.FloatingActionButton fabTradingToggle;
    
    // 코인 정보 표시용 TextView들
    private TextView textCoinType;
    private TextView textCurrentPrice;
    private TextView textPriceChange;
    private TextView textActiveOrders;
    // private Button btnPreference; // App bar 메뉴로 이동
    private TabLayout tabLayout;
    private ViewPager viewPager;
    
    // TransactionCard TextView들
    private TextView textTransactionTimeCard;
    private TextView textCoinKwValueCard;
    private TextView textEstimatedBalanceCard;
    private TextView textTotalBalanceCard;
    private TextView textLastBuyPriceCard;
    private TextView textLastSellPriceCard;
    private TextView textNextBuyPriceCard;

    private MainActivity mainActivity;
    private MainViewModel mainViewModel;
    private boolean isTradingStarted = false;
    private DatabaseOrderManager databaseOrderManager;
    private CompositeDisposable disposables;
    private PlacedOrderPage placedOrderPage;
    private ProcessedOrderPage processedOrderPage;
    private TransactionLogPage transactionLogPage;
    
    // UI 상태 캐시 (static으로 변경하여 Fragment 재생성 시에도 유지)
    private static String cachedCoinType;
    private static boolean isDataLoaded = false;
    
    // BroadcastReceiver for card data updates
    private BroadcastReceiver cardDataReceiver;
    
    // 실시간 관찰을 위한 필드들
    private CoinPriceInfoRepository coinPriceInfoRepository;
    private TransactionInfoRepository transactionInfoRepository;
    private TransactionCardController transactionCardController;
    private CoinInfoController coinInfoController;
    private MainPageCoordinator mainPageCoordinator;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshCurrentPage();
            int AUTO_REFRESH_PERIOD = 5000;
            handler.postDelayed(this, AUTO_REFRESH_PERIOD); // 5초마다 반복
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {super.onCreate(savedInstanceState);}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ConstraintLayout layout = (ConstraintLayout)inflater.inflate(R.layout.main_page, container,false);
        mainActivity = (MainActivity) getActivity();
        if (mainActivity != null) {
            mainViewModel = mainActivity.getMainViewModel();
        }
        
        // UI 컴포넌트 초기화
        fabTradingToggle = layout.findViewById(R.id.fabTradingToggle);
        
        // 코인 정보 TextView들 초기화
        textCoinType = layout.findViewById(R.id.textCoinType);
        textCurrentPrice = layout.findViewById(R.id.textCurrentPrice);
        textPriceChange = layout.findViewById(R.id.textPriceChange);
        textActiveOrders = layout.findViewById(R.id.textActiveOrders);
        
        // TransactionCard TextView들 초기화
        textTransactionTimeCard = layout.findViewById(R.id.textTransactionTimeCard);
        textCoinKwValueCard = layout.findViewById(R.id.textCoinKwValueCard);
        textEstimatedBalanceCard = layout.findViewById(R.id.textEstimatedBalanceCard);
        textTotalBalanceCard = layout.findViewById(R.id.textTotalBalanceCard);
        textLastBuyPriceCard = layout.findViewById(R.id.textLastBuyPriceCard);
        textLastSellPriceCard = layout.findViewById(R.id.textLastSellPriceCard);
        textNextBuyPriceCard = layout.findViewById(R.id.textNextBuyPriceCard);
        transactionCardController = new TransactionCardController(
                textCoinKwValueCard,
                textEstimatedBalanceCard,
                textTotalBalanceCard,
                textTransactionTimeCard,
                textLastBuyPriceCard,
                textLastSellPriceCard,
                textNextBuyPriceCard
        );
        coinInfoController = new CoinInfoController();
        mainPageCoordinator = new MainPageCoordinator();
        
        // btnPreference = layout.findViewById(R.id.imageButtonPreference); // App bar 메뉴로 이동
        tabLayout = layout.findViewById(R.id.tabLayout);
        viewPager = layout.findViewById(R.id.viewPager);

        // ViewPager와 TabLayout 설정
        setupViewPagerAndTabs();

        // 상태 복원
        if (savedInstanceState != null) {
            isTradingStarted = savedInstanceState.getBoolean(KEY_TRADING_STATE);
        }

        // 실제 서비스 상태 확인하여 UI 동기화
        boolean actualServiceRunning = TradeJobService.isServiceRunning();
        if (actualServiceRunning && !isTradingStarted) {
            Log.d("KTrader", "[MainPage] 서비스가 실행 중이지만 UI가 중지 상태 - UI 동기화");
            isTradingStarted = true;
        } else if (!actualServiceRunning && isTradingStarted) {
            Log.d("KTrader", "[MainPage] 서비스가 중지되었지만 UI가 실행 상태 - UI 동기화");
            isTradingStarted = false;
        }

        // Floating Action Button 상태 초기화
        updateTradingToggleButton(isTradingStarted);

        // 버튼 이벤트 설정
        setupButtonListeners();

        // DatabaseOrderManager 초기화
        initializeDatabaseManager();

        // 캐시된 데이터가 있으면 먼저 복원
        restoreCachedData();
        
        // 코인 정보 초기화 (캐시된 데이터가 없을 때만)
        if (cachedCoinType == null) {
            updateCoinInfo();
        }
        
        // 데이터가 로드되지 않았거나 서비스가 실행 중이면 새로 로드
        if (!isDataLoaded || TradeJobService.isServiceRunning()) {
            // 즉시 초기 데이터 로드 시작 (Fragment 생성 후 약간의 지연)
            viewPager.postDelayed(this::loadInitialDataImmediately, 1000); // 1초 지연
        }
        
        // BroadcastReceiver 초기화 및 등록
        setupCardDataReceiver();
        bindMainViewModel();
        
        // 실시간 관찰 시작
        startReactiveObservations();

        return layout;
    }

    /**
     * DatabaseOrderManager 초기화
     */
    private void initializeDatabaseManager() {
        // DatabaseOrderManager 초기화
        if (getContext() != null) {
            databaseOrderManager = new DatabaseOrderManager(getContext());
            coinPriceInfoRepository = new com.example.k_trader.database.CoinPriceInfoRepository(getContext());
            transactionInfoRepository = new com.example.k_trader.database.TransactionInfoRepository(getContext());
            disposables = new CompositeDisposable();
        }
    }

    /**
     * 캐시된 데이터 복원 (Room DB에서)
     */
    private void restoreCachedData() {
        Log.d("KTrader", "[MainPage] Room DB에서 캐시된 데이터 복원 시작");
        
        if (coinPriceInfoRepository == null) {
            Log.w("KTrader", "[MainPage] coinPriceInfoRepository가 null입니다");
            return;
        }
        
        // Room DB에서 최신 코인 가격 정보 조회
        coinPriceInfoRepository.getCurrentPriceInfo()
            .subscribeOn(io.reactivex.schedulers.Schedulers.io())
            .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
            .subscribe(
                coinPriceInfo -> {
                    if (coinPriceInfo != null) {
                        Log.d("KTrader", "[MainPage] Room DB에서 코인 가격 정보 조회 성공: " + coinPriceInfo.toString());
                        
                        // 코인 타입 복원
                        if (coinPriceInfo.getCoinType() != null && textCoinType != null) {
                            textCoinType.setText(coinPriceInfo.getCoinType());
                            cachedCoinType = coinPriceInfo.getCoinType();
                            Log.d("KTrader", "[MainPage] 코인 타입 복원: " + coinPriceInfo.getCoinType());
                        }
                        
                        // 현재 가격 복원
                        if (coinPriceInfo.getCurrentPrice() != null && textCurrentPrice != null) {
                            textCurrentPrice.setText(coinPriceInfo.getCurrentPrice());
                            Log.d("KTrader", "[MainPage] 현재 가격 복원: " + coinPriceInfo.getCurrentPrice());
                        }
                        
                        // 가격 변동률 복원
                        if (coinPriceInfo.getPriceChange() != null && textPriceChange != null) {
                            textPriceChange.setText(coinPriceInfo.getPriceChange());
                            Log.d("KTrader", "[MainPage] 가격 변동률 복원: " + coinPriceInfo.getPriceChange());
                        }
                        
                        Log.d("KTrader", "[MainPage] Room DB에서 캐시된 데이터 복원 완료");
                    } else {
                        Log.d("KTrader", "[MainPage] Room DB에 캐시된 데이터가 없음 - 새로 로드 필요");
                    }
                },
                error -> {
                    Log.e("KTrader", "[MainPage] Room DB에서 캐시된 데이터 조회 실패", error);
                }
            );
        
        // 활성 주문 수는 별도로 복원 (TransactionInfoEntity에서)
        if (transactionInfoRepository != null) {
            transactionInfoRepository.getLatestTransactionInfoMaybe()
                .subscribeOn(io.reactivex.schedulers.Schedulers.io())
                .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(
                    transactionInfo -> {
                        if (transactionInfo != null && transactionInfo.getEstimatedBalance() != null) {
                            // 활성 주문 수는 별도 로직으로 복원 (현재는 생략)
                            Log.d("KTrader", "[MainPage] TransactionInfo 복원: " + transactionInfo.getEstimatedBalance());
                        }
                    },
                    error -> Log.e("KTrader", "[MainPage] TransactionInfo 조회 실패", error),
                    () -> Log.d("KTrader", "[MainPage] TransactionInfo 테이블에 데이터가 없음 - 정상적인 상황") // onComplete handler
                );
        }
    }

    /**
     * 코인 가격 정보를 Room DB에 저장
     */
    private void saveCoinPriceToDB(String currentPrice, String priceChange) {
        if (coinPriceInfoRepository == null) {
            Log.w("KTrader", "[MainPage] coinPriceInfoRepository가 null입니다");
            return;
        }
        
        // SharedPreferences에서 코인 타입 읽기
        android.content.SharedPreferences prefs = getContext().getSharedPreferences("settings", android.content.Context.MODE_PRIVATE);
        String coinType = prefs.getString(com.example.k_trader.base.GlobalSettings.COIN_TYPE_KEY_NAME, com.example.k_trader.base.GlobalSettings.COIN_TYPE_DEFAULT_VALUE);
        
        // Room DB에 저장
        coinPriceInfoRepository.savePriceInfo(coinType, currentPrice, priceChange)
            .subscribeOn(io.reactivex.schedulers.Schedulers.io())
            .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
            .subscribe(
                () -> Log.d("KTrader", "[MainPage] 코인 가격 정보를 Room DB에 저장 완료"),
                error -> Log.e("KTrader", "[MainPage] 코인 가격 정보 저장 실패", error)
            );
    }

    /**
     * 즉시 초기 데이터 로드 (Fragment 준비와 관계없이)
     */
    private void loadInitialDataImmediately() {
        if (databaseOrderManager == null) {
            Log.w("[K-TR]", "[MainPage] DatabaseOrderManager가 초기화되지 않음");
            return;
        }
        
        Log.d("[K-TR]", "[MainPage] 즉시 초기 데이터 로드 시작");
        
        Completable immediateLoad = databaseOrderManager.initializeAndSyncData("MainPage 즉시 초기화")
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnComplete(() -> {
                    Log.d("[K-TR]", "[MainPage] 즉시 초기 데이터 로드 완료");
                    isDataLoaded = true; // 데이터 로드 완료 플래그 설정
                })
                .doOnError(error -> Log.e("[K-TR]", "[MainPage] 즉시 초기 데이터 로드 실패", error));
        
        disposables.add(immediateLoad.subscribe());
    }

    /**
     * 버튼 눌린 효과 애니메이션
     */
    private void animateButtonPress(View button) {
        // 스케일 다운 애니메이션
        button.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction(() -> button.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .start())
                .start();
    }

    /**
     * ViewPager와 TabLayout을 설정하는 메서드
     */
    private void setupViewPagerAndTabs() {
        // ViewPager 어댑터 설정
        TransactionPagerAdapter pagerAdapter = new TransactionPagerAdapter(getChildFragmentManager());
        viewPager.setAdapter(pagerAdapter);
        
        // TabLayout과 ViewPager 연결
        tabLayout.setupWithViewPager(viewPager);
        
        // 기본 탭을 첫 번째 탭으로 설정
        viewPager.setCurrentItem(0);
    }
    
    /**
     * MainActivity에서 페이지 선택 이벤트를 받는 메서드
     */
    public void onPageSelected(int position) {
        // 내부 ViewPager의 페이지 변경 처리
        Log.d("KTrader", "[MainPage] onPageSelected: " + position);
    }
    
    /**
     * 스크롤을 하단으로 이동하는 메서드
     */
    /**
     * 현재 표시 중인 페이지를 새로고침
     */
    public void refreshCurrentPage() {
        Log.d("KTrader", "[MainPage] refreshCurrentPage called - refreshing all components");

        // 1. CoinInfo 새로고침
        refreshCoinData();
        Log.d("KTrader", "[MainPage] CoinInfo refresh called");

        // 2. Transaction Card 정보 직접 업데이트를 위한 API 호출
        fetchCurrentPriceFromApi();
        Log.d("KTrader", "[MainPage] Transaction card API call initiated");

        // 2-1. 브로드캐스트가 없더라도 카드의 코인 원화 잔고를 직접 계산해 갱신
        refreshTransactionCardCoinValue();

        // 3. 모든 하위 페이지 새로고침 (현재 선택된 탭과 관계없이)

        String tag0 = "android:switcher:" + viewPager.getId() + ":" + 0;
        Fragment placedFrag = getChildFragmentManager().findFragmentByTag(tag0);

        if (placedFrag instanceof PlacedOrderPage) {
            ((PlacedOrderPage) placedFrag).refresh();
            Log.d("KTrader", "[MainPage] PlacedOrderPage refresh called (via " + (placedOrderPage != null ? "field" : "tag") + ")");
        } else {
            Log.w("KTrader", "[MainPage] PlacedOrderPage instance not found for refresh");
        }

        String tag1 = "android:switcher:" + viewPager.getId() + ":" + 1;
        Fragment processedFrag = getChildFragmentManager().findFragmentByTag(tag1);

        if (processedFrag instanceof ProcessedOrderPage) {
            ((ProcessedOrderPage) processedFrag).refresh();
            Log.d("KTrader", "[MainPage] ProcessedOrderPage refresh called (via " + (processedOrderPage != null ? "field" : "tag") + ")");
        } else {
            Log.w("KTrader", "[MainPage] ProcessedOrderPage instance not found for refresh");
        }

        Log.d("KTrader", "[MainPage] All components refreshed");
    }

    /**
     * 현재 보유 코인 수량(available + in_use)과 현재가로 코인 원화 잔고를 계산하여 카드에 반영
     */
    private void refreshTransactionCardCoinValue() {
        new Thread(() -> {
            try {
                // 현재가 파싱 (텍스트에서 숫자만 추출)
                int currentPrice = 0;
                if (textCurrentPrice != null && textCurrentPrice.getText() != null) {
                    String priceNum = textCurrentPrice.getText().toString().replaceAll("[^0-9]", "");
                    if (!priceNum.isEmpty()) currentPrice = Integer.parseInt(priceNum);
                }

                if (currentPrice <= 0) {
                    Log.w("KTrader", "[MainPage] refreshTransactionCardCoinValue: currentPrice is not ready");
                    return;
                }

                OrderManager orderManager = new OrderManager();
                String coinType = GlobalSettings.getInstance().getCoinType();
                String fieldSuffix = coinType.toLowerCase();

                java.util.HashMap<String, String> params = new java.util.HashMap<>();
                params.put("currency", coinType);
                org.json.simple.JSONObject dataObj = orderManager.getBalanceWithParams("MainPage 카드 잔고 갱신", params);

                double available = 0.0;
                double inUse = 0.0;
                Object availObj = dataObj.get("available_" + fieldSuffix);
                Object inUseObj = dataObj.get("in_use_" + fieldSuffix);
                if (availObj != null) available = Double.parseDouble(availObj.toString());
                if (inUseObj != null) inUse = Double.parseDouble(inUseObj.toString());

                double totalCoin = available + inUse;
                long coinKw = (long) Math.floor(totalCoin * currentPrice);
                final String formattedCoinKw = String.format(java.util.Locale.getDefault(), "₩%,d", coinKw);

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> updateTransactionCardUi(formattedCoinKw, null));
                }
            } catch (Exception e) {
                Log.e("KTrader", "[MainPage] refreshTransactionCardCoinValue error", e);
            }
        }).start();
    }

    public void scrollToBottomInPage() {
        if (viewPager != null) {
            int currentItem = viewPager.getCurrentItem();
            Log.d("KTrader", "[MainPage] scrollToBottomInPage called, current item: " + currentItem);
            
            // 현재 페이지에 따라 처리
            if (currentItem == 0 && placedOrderPage != null) {
                // PlacedOrderPage는 별도 refresh 메서드가 없으므로 skip
                Log.d("KTrader", "[MainPage] PlacedOrderPage detected");
            } else if (currentItem == 1 && processedOrderPage != null) {
                processedOrderPage.refresh();
            } else if (currentItem == 2 && transactionLogPage != null) {
                transactionLogPage.scrollToBottom();
            }
        }
    }

    /**
     * 버튼 이벤트 리스너를 설정하는 메서드
     */
    @SuppressWarnings("ConstantConditions")
    private void setupButtonListeners() {
        fabTradingToggle.setOnClickListener(v -> {
            if (isTradingStarted) {
                stopTrading();
            } else {
                startTrading();
            }
        });
    }
    
    /**
     * 트레이딩 시작
     */
    private void startTrading() {
        Log.d("KTrader", "[MainPage] Start Trading button clicked");
        mainPageCoordinator.startTrading(mainActivity);

        isTradingStarted = true;
        updateTradingToggleButton(isTradingStarted);
        Log.d("KTrader", "[MainPage] Trading started successfully");
    }
    
    /**
     * 트레이딩 중지
     */
    private void stopTrading() {
        Log.d("KTrader", "[MainPage] Stop Trading button clicked");
        mainPageCoordinator.stopTrading(mainActivity);

        isTradingStarted = false;
        updateTradingToggleButton(isTradingStarted);
        Log.d("KTrader", "[MainPage] Trading stopped successfully");
    }
    
    /**
     * 트레이딩 토글 버튼 상태 업데이트
     */
    private void updateTradingToggleButton(boolean isTradingStarted) {
        if (fabTradingToggle != null) {
            if (isTradingStarted) {
                // 트레이딩 중 - Stop 아이콘과 빨간색
                fabTradingToggle.setImageResource(android.R.drawable.ic_media_pause);
                fabTradingToggle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    getResources().getColor(android.R.color.holo_red_dark)));
            } else {
                // 트레이딩 중지 - Play 아이콘과 초록색
                fabTradingToggle.setImageResource(android.R.drawable.ic_media_play);
                fabTradingToggle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#4CAF50")));
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        
        // 실제 서비스 상태 확인하여 UI 동기화
        boolean actualServiceRunning = mainPageCoordinator.syncTradingStateWithService(isTradingStarted);
        if (actualServiceRunning != isTradingStarted) {
            Log.d("KTrader", "[MainPage] onResume - 서비스 상태와 UI 상태 불일치 감지");
            isTradingStarted = actualServiceRunning;
            updateTradingToggleButton(isTradingStarted);
        }
        
        // SettingsActivity에서 돌아올 때 코인 정보 업데이트
        Log.d("KTrader", "[MainPage] onResume - updating coin info");
        updateCoinInfo();
        
        // Phase 3: ViewModel 경유 데이터 관찰/새로고침 시작
        if (mainActivity != null) {
            String coinTypeForObserve = cachedCoinType != null ? cachedCoinType : GlobalSettings.getInstance().getCoinType();
            mainActivity.startObservingCoinPrice(coinTypeForObserve);
            mainActivity.startObservingActiveOrders();
            mainActivity.refreshData();
        }
        handler.post(refreshRunnable); // 주기적 업데이트 시작
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(refreshRunnable);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_TRADING_STATE, isTradingStarted);
    }

    // 주어진 코인 가격에 대한 이익금(EARNINGS_RATIO)을 리턴한다.
    // 매도 리스트를 discrete하게 만들기 위해 주어진 가격에서 가장 앞자리만 남기고 절사한 금액의 이익금을 계산한다.
    // 예를 들어 주어진 가격이 4,325만원이라면 4,000으로 절사하고 그 EARNINGS_RATIO 금액(ex: earnings_ratio가 1%인 경우 40만원)을 리턴
    public static int getProfitPrice(int basePrice) {
        return (int)(getFloorPrice(basePrice) * (GlobalSettings.getInstance().getEarningRate() / 100.0));
    }

    // 주어진 코인 가격에 대한 매수구간(BUY_INTERVAL)을 리턴한다.
    // 매도 리스트를 discrete하게 만들기 위해 주어진 base price 가격에서 가장 앞자리만 남기고 절사한 금액을 사용한다.
    // 예를 들어 주어진 가격이 4,325만원이라면 4,000으로 절사하고 그 buy interval 금액(ex: 0.5%인 경우 20만원)을 리턴
    public static int getSlotIntervalPrice(int basePrice) {
        return (int)(getFloorPrice(basePrice) * (GlobalSettings.getInstance().getSlotIntervalRate() / 100.0));
    }

    public static int getProfitPrice() throws Exception {
        OrderManager orderManager = new OrderManager();
        int currentPrice;                  // 비트코인 현재 시장가

        JSONObject dataObj = orderManager.getCurrentPrice("");
        if (dataObj == null) {
            throw new Exception("Unknown network issue happens");
        }

        JSONArray dataArray = (JSONArray) dataObj.get("bids"); // 매수가
        if (dataArray != null && !dataArray.isEmpty()) {
            JSONObject item = (JSONObject) dataArray.get(0); // 기본 5개 아이템 중 첫번째 아이템 사용
            String priceStr = (String) item.get("price");
            if (priceStr != null) {
                currentPrice = (int) Double.parseDouble(priceStr);
                return getProfitPrice(currentPrice);
            }
        }

        throw new Exception("dataObj == null");
    }

    public static int getFloorPrice(int price) {
        int precision = 0;

        // 자리수 구하기
        while(price > 10) {
            price /= 10;
            precision++;

            // 천만 단위까지만 floor 시킴
            if (precision > 6)
                break;
        }

        // 절사된 floor value 구하기
        int floor = price;
        for (int i = 0; i<precision; i++) {
            floor *= 10;
        }

        return floor;
    }

    public static class TransactionCard {
        public String transactionTime;
        public String coinKwValue;        // 코인 원화 잔고
        public String estimatedBalance;   // 예상잔고
        public String totalBalance;        // 코인 원화 잔고 + 예상잔고 (총 잔고)
        public String krwBalance;
        public String lastBuyPrice;
        public String lastSellPrice;
        public String nextBuyPrice;

        public TransactionCard(String transactionTime, String coinKwValue, String estimatedBalance,
                               String lastBuyPrice, String lastSellPrice, String nextBuyPrice) {
            this.transactionTime = transactionTime;
            this.coinKwValue = coinKwValue;
            this.estimatedBalance = estimatedBalance;
            this.krwBalance = coinKwValue;

            // 코인 원화 잔고와 예상잔고 합산
            this.totalBalance = calculateTotalBalance(coinKwValue, estimatedBalance);

            this.lastBuyPrice = lastBuyPrice;
            this.lastSellPrice = lastSellPrice;
            this.nextBuyPrice = nextBuyPrice;
        }

        /**
         * 코인 원화 잔고와 예상잔고를 합산하여 총 잔고 계산
         */
        private String calculateTotalBalance(String coinKwValue, String estimatedBalance) {
            try {
                // 정규표현식으로 숫자만 추출
                String coinKwNum = coinKwValue.replaceAll("[^0-9,]", "").replace(",", "");
                String estimatedNum = estimatedBalance.replaceAll("[^0-9,]", "").replace(",", "");

                long coinValue = Long.parseLong(coinKwNum);
                long estimated = Long.parseLong(estimatedNum);

                long total = coinValue + estimated;

                return String.format(java.util.Locale.getDefault(), "₩%,d", total);
            } catch (Exception e) {
                Log.e("KTrader", "[TransactionCard] Error calculating total balance", e);
                return "₩0";
            }
        }

        /**
         * Transaction Time을 파싱하여 비교 가능한 시간값 반환
         * 형식: "MM/dd HH:mm" (예: "12/25 14:30")
         */
        public long getTimeInMillis() {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault());
                // 현재 연도를 기준으로 파싱
                java.util.Calendar cal = java.util.Calendar.getInstance();
                int currentYear = cal.get(java.util.Calendar.YEAR);

                String fullDateTime = currentYear + "/" + transactionTime;
                java.text.SimpleDateFormat fullSdf = new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault());
                return fullSdf.parse(fullDateTime).getTime();
            } catch (Exception e) {
                // 파싱 실패 시 현재 시간 반환
                return System.currentTimeMillis();
            }
        }
    }

    public static class ErrorCard {
        public String errorTime;
        public String errorType;
        public String errorMessage;
        public String apiEndpoint;
        public String errorCode;
        public String serverErrorMessage;
        public String apiErrorDetails;

        public ErrorCard(String errorTime, String errorType, String errorMessage) {
            this.errorTime = errorTime;
            this.errorType = errorType;
            this.errorMessage = errorMessage;
        }

        public ErrorCard(String errorTime, String errorType, String errorMessage,
                       String apiEndpoint, String errorCode, String serverErrorMessage, String apiErrorDetails) {
            this.errorTime = errorTime;
            this.errorType = errorType;
            this.errorMessage = errorMessage;
            this.apiEndpoint = apiEndpoint;
            this.errorCode = errorCode;
            this.serverErrorMessage = serverErrorMessage;
            this.apiErrorDetails = apiErrorDetails;
        }
    }

    public static class OrderCard {
        public TradeData tradeData;

        public OrderCard(TradeData tradeData) {
            this.tradeData = tradeData;
        }
    }

    /**
     * Transaction Fragment들을 관리하는 PagerAdapter
     */
    public class TransactionPagerAdapter extends FragmentPagerAdapter {
        
        private Fragment[] fragments = new Fragment[3];
        
        public TransactionPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // 이미 생성된 Fragment가 있으면 재사용
            if (fragments[position] != null) {
                return fragments[position];
            }
            
            // Fragment 생성 및 캐싱
            switch (position) {
                case 0:
                    if (placedOrderPage == null) {
                        placedOrderPage = new PlacedOrderPage();
                        fragments[position] = placedOrderPage;
                    }
                    return placedOrderPage;
                case 1:
                    if (processedOrderPage == null) {
                        processedOrderPage = new ProcessedOrderPage();
                        fragments[position] = processedOrderPage;
                    }
                    return processedOrderPage;
                case 2:
                    if (transactionLogPage == null) {
                        transactionLogPage = new TransactionLogPage();
                        fragments[position] = transactionLogPage;
                    }
                    return transactionLogPage;
                default:
                    if (placedOrderPage == null) {
                        placedOrderPage = new PlacedOrderPage();
                        fragments[0] = placedOrderPage;
                    }
                    return placedOrderPage;
            }
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "대기 주문";
                case 1:
                    return "처리 완료";
                case 2:
                    return getString(R.string.transaction_log);
                default:
                    return "대기 주문";
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // RxJava 리소스 정리
        stopReactiveObservations();
        
        // DatabaseOrderManager 정리
        if (databaseOrderManager != null) {
            databaseOrderManager.dispose();
        }
        
        // BroadcastReceiver 해제
        if (cardDataReceiver != null && getContext() != null) {
            android.support.v4.content.LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(cardDataReceiver);
        }
    }
    
    /**
     * BroadcastReceiver 설정
     */
    private void setupCardDataReceiver() {
        cardDataReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Fragment가 컨텍스트에 연결되어 있는지 확인
                if (!isAdded() || getContext() == null) {
                    Log.w("KTrader", "[MainPage] BroadcastReceiver received but Fragment is not attached to context");
                    return;
                }
                
                Log.d("KTrader", "[MainPage] BroadcastReceiver received: " + intent.getAction());
                
                // BROADCAST_CARD_DATA, BROADCAST_TRANSACTION_DATA만 처리
                if (intent.getAction() != null && 
                    (intent.getAction().equals(BROADCAST_CARD_DATA) ||
                     intent.getAction().equals(BROADCAST_TRANSACTION_DATA))) {
                    
                    // 카드 데이터에서 가격/잔고/메타 정보 추출하여 UI 업데이트
                    String btcCurrentPrice = intent.getStringExtra("btcCurrentPrice");
                    String hourlyChange = intent.getStringExtra("hourlyChange");
                    String dailyChange = intent.getStringExtra("dailyChange");
                    String coinKwValue = intent.getStringExtra("coinKwValue");
                    String estimatedBalance = intent.getStringExtra("estimatedBalance");
                    String transactionTime = intent.getStringExtra("transactionTime");
                    String lastBuyPrice = intent.getStringExtra("lastBuyPrice");
                    String lastSellPrice = intent.getStringExtra("lastSellPrice");
                    String nextBuyPrice = intent.getStringExtra("nextBuyPrice");
                    
                    Log.d("KTrader", "[MainPage] Received data - Price: " + btcCurrentPrice + ", HourlyChange: " + hourlyChange + ", DailyChange: " + dailyChange);
                    
                    // 가격 정보가 null이거나 0인 경우 API를 직접 호출
                    if (btcCurrentPrice == null || btcCurrentPrice.equals("0") || btcCurrentPrice.equals("null")) {
                        Log.w("KTrader", "[MainPage] Price data is null or 0, calling API directly");
                        fetchCurrentPriceFromApi();
                    } else if (textCurrentPrice != null) {
                        textCurrentPrice.setText(btcCurrentPrice);
                        Log.d("KTrader", "[MainPage] Updated current price: " + btcCurrentPrice);
                    }

                    // 트랜잭션 카드 UI 갱신 (잔고/예상잔고)
                    if (coinKwValue != null || estimatedBalance != null) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> updateTransactionCardUi(coinKwValue, estimatedBalance));
                        }
                    }

                    // 트랜잭션 카드 메타 갱신 (업데이트 시간/마지막 매수/마지막 매도/다음 매수)
                    if (transactionTime != null || lastBuyPrice != null || lastSellPrice != null || nextBuyPrice != null) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> updateTransactionCardMeta(transactionTime, lastBuyPrice, lastSellPrice, nextBuyPrice));
                        }
                    }
                    
                     // CoinInfo에는 1시간 등락폭 표시
                     if (hourlyChange != null && textPriceChange != null) {
                         textPriceChange.setText(hourlyChange);
                         // 등락폭에 따라 색상 변경 (+이면 빨간색, -이면 파란색)
                         if (hourlyChange.startsWith("+")) {
                             textPriceChange.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                         } else if (hourlyChange.startsWith("-")) {
                             textPriceChange.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
                         } else {
                             textPriceChange.setTextColor(getResources().getColor(android.R.color.black));
                         }
                         Log.d("KTrader", "[MainPage] Updated hourly price change (CoinInfo): " + hourlyChange);
                     }
                }
            }
        };
        
        // BroadcastReceiver 등록 - 두 액션 모두 등록
        if (getContext() != null) {
            android.content.IntentFilter filter = new android.content.IntentFilter();
            filter.addAction(BROADCAST_CARD_DATA);
            filter.addAction(BROADCAST_TRANSACTION_DATA);
            LocalBroadcastManager.getInstance(getContext()).registerReceiver(cardDataReceiver, filter);
            Log.d("KTrader", "[MainPage] BroadcastReceiver registered for both actions");
        }
    }
    
    /**
     * 코인 데이터 새로고침 (외부에서 호출 가능)
     */
    public void refreshCoinData() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                // 코인 타입만 업데이트 (가격과 활성 거래 수는 이전 값 유지)
                updateCoinTypeOnly();
                
                // API에서 최신 데이터 가져오기
                fetchLatestCoinData();
            });
        } else {
            Log.w("KTrader", "[MainPage] refreshCoinData() - getActivity() is null, cannot proceed");
        }
    }
    
    /**
     * 코인 타입만 업데이트 (새로고침 시 깜박임 방지)
     */
    private void updateCoinTypeOnly() {
        if (textCoinType == null) {
            Log.w("KTrader", "[MainPage] updateCoinTypeOnly() - textCoinType is null, returning");
            return;
        }
        
        // SharedPreferences에서 직접 코인 타입 읽어오기
        android.content.SharedPreferences sharedPreferences = getContext().getSharedPreferences("settings", android.content.Context.MODE_PRIVATE);
        String coinType = sharedPreferences.getString(com.example.k_trader.base.GlobalSettings.COIN_TYPE_KEY_NAME, com.example.k_trader.base.GlobalSettings.COIN_TYPE_DEFAULT_VALUE);

        // GlobalSettings도 업데이트
        com.example.k_trader.base.GlobalSettings.getInstance().setCoinType(coinType);
        
        // 코인 타입만 표시 (가격과 활성 거래 수는 이전 값 유지)
        textCoinType.setText(coinType);
    }
    
    /**
     * API에서 최신 코인 데이터 가져오기
     */
    private void fetchLatestCoinData() {
        if (databaseOrderManager == null) return;
        
        // 현재 설정된 코인 타입에 따라 API 호출
        String coinType = GlobalSettings.getInstance().getCoinType();
        
        // 직접 API 호출하여 가격 정보 가져오기
        fetchCurrentPriceFromApi();
        
        databaseOrderManager.periodicSyncData("refresh")
            .subscribe(
                () -> {
                    Log.d("KTrader", "[MainPage] Coin data refreshed successfully");
                    // 활성 거래 수 다시 업데이트
                    updateActiveOrdersCount();
                },
                throwable -> {
                    Log.e("KTrader", "[MainPage] Error refreshing coin data", throwable);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "데이터 새로고침 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            );
    }
    
    /**
     * API에서 현재 가격 및 등락률 정보 직접 가져오기
     */
    private void fetchCurrentPriceFromApi() {
        try {
            // OrderManager를 통해 가격과 등락률 정보 가져오기
            OrderManager orderManager = new com.example.k_trader.base.OrderManager();
            
            // 백그라운드에서 API 호출
            new Thread(() -> {
                try {
                    // 현재 가격 가져오기
                    JSONObject priceData = orderManager.getCurrentPrice("refresh");
                    int currentPrice = 0;
                    if (priceData != null && priceData.containsKey("bids")) {
                        JSONArray bids = (JSONArray) priceData.get("bids");
                        if (bids != null && !bids.isEmpty()) {
                            JSONObject firstBid = (JSONObject) bids.get(0);
                            String priceStr = (String) firstBid.get("price");
                            if (priceStr != null) {
                                currentPrice = (int) Double.parseDouble(priceStr);
                            }
                        }
                    }
                    
                    // Ticker 정보에서 등락률 가져오기
                    String dailyChange = "+0.00%";
                    String hourlyChange = "+0.00%";
                    
                    // 24시간 등락률은 API에서 가져오기
                    try {
                        JSONObject tickerData = orderManager.getTicker("refresh");
                        if (tickerData != null && tickerData.containsKey("data")) {
                            JSONObject data = (JSONObject) tickerData.get("data");
                            
                            // 24시간 등락률
                            if (data.containsKey("fluctate_rate_24H")) {
                                String rawDailyChange = data.get("fluctate_rate_24H").toString();
                                try {
                                    double changeValue = Double.parseDouble(rawDailyChange);
                                    if (changeValue >= 0) {
                                        dailyChange = String.format("+%.2f%%", changeValue);
                                    } else {
                                        dailyChange = String.format("%.2f%%", changeValue);
                                    }
                                } catch (NumberFormatException e) {
                                    Log.e("KTrader", "[MainPage] Error parsing daily change: " + rawDailyChange, e);
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e("KTrader", "[MainPage] Error getting ticker data", e);
                    }
                    
                    // 1시간 등락폭은 PriceQueueManager에서 계산
                    try {
                        PriceQueueManager priceManager = PriceQueueManager.getInstance();
                        
                        // 현재 가격을 PriceQueueManager에 추가
                        priceManager.addPrice(currentPrice);
                        
                        // 큐에 충분한 데이터가 있는지 확인 (최소 2개 이상)
                        if (priceManager.hasMinimumData(2)) {
                            float variationRate = priceManager.getPriceVariationRate();
                            
                            if (variationRate >= 0) {
                                hourlyChange = String.format("+%.2f%%", variationRate);
                            } else {
                                hourlyChange = String.format("%.2f%%", variationRate);
                            }
                        }
                    } catch (Exception e) {
                        Log.e("KTrader", "[MainPage] Error calculating hourly change from PriceQueueManager", e);
                    }
                    
                    final int finalCurrentPrice = currentPrice;
                    final String finalDailyChange = dailyChange;
                    final String finalHourlyChange = hourlyChange;
                    
                    // UI 스레드에서 업데이트
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            // 현재 가격 업데이트 (깜박임 방지)
                            if (textCurrentPrice != null && finalCurrentPrice > 0) {
                                String formattedPrice = String.format(java.util.Locale.getDefault(), "₩%,d", finalCurrentPrice);
                                textCurrentPrice.setText(formattedPrice);
                                // Room DB에 캐시 저장
                                saveCoinPriceToDB(formattedPrice, finalHourlyChange);
                            }
                            
                            // 1시간 등락폭 업데이트 (CoinInfo용)
                            if (textPriceChange != null) {
                                coinInfoController.applyPriceChangeColor(
                                        textPriceChange,
                                        finalHourlyChange,
                                        getResources().getColor(android.R.color.holo_red_dark),
                                        getResources().getColor(android.R.color.holo_blue_dark),
                                        getResources().getColor(android.R.color.black)
                                );
                            }
                            
                            // 마지막 동기화 시간 업데이트
                            updateLastSyncTime();
                        });
                    }
                    
                } catch (Exception e) {
                    Log.e("KTrader", "[MainPage] Error fetching price and change data", e);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "가격 정보를 가져올 수 없습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            }).start();
            
        } catch (Exception e) {
            Log.e("KTrader", "Error in fetchCurrentPriceFromApi", e);
        }
    }

    /**
     * 트랜잭션 카드 UI 업데이트 (잔고/예상잔고/총합)
     */
    private void updateTransactionCardUi(String coinKwValue, String estimatedBalance) {
        if (transactionCardController == null) {
            Log.w("KTrader", "[MainPage] transactionCardController is null");
            return;
        }
        transactionCardController.updateAmounts(coinKwValue, estimatedBalance);
    }

    /**
     * 트랜잭션 카드 UI 메타 업데이트 (업데이트 시간/마지막 매수/마지막 매도/다음 매수)
     */
    private void updateTransactionCardMeta(String transactionTime, String lastBuyPrice, String lastSellPrice, String nextBuyPrice) {
        if (transactionCardController == null) {
            Log.w("KTrader", "[MainPage] transactionCardController is null");
            return;
        }
        transactionCardController.updateMeta(transactionTime, lastBuyPrice, lastSellPrice, nextBuyPrice);
    }

    /**
     * 코인 정보 업데이트
     */
    private void updateCoinInfo() {
        String coinType = coinInfoController.updateCoinType(getContext(), textCoinType, coinPriceInfoRepository);
        if (coinType == null) {
            return;
        }
        cachedCoinType = coinType; // 캐시 저장
        
        // 활성 거래 수는 DB에서 가져오기
        updateActiveOrdersCount();
    }
    
    /**
     * 활성 거래 수 업데이트 (API 직접 호출)
     */
    private void updateActiveOrdersCount() {
        if (mainActivity != null) {
            mainActivity.refreshActiveOrdersSummary();
        }
    }

    private void bindMainViewModel() {
        if (mainViewModel == null) {
            return;
        }
        mainViewModel.getActiveOrdersSummary().observe(this, summary -> {
            if (summary != null && textActiveOrders != null) {
                textActiveOrders.setText(summary);
            }
        });
    }
    
    /**
     * 현재 가격과 등락률 업데이트 (API 호출 결과로부터)
     */
    public void updatePriceInfo(String currentPrice, String priceChange) {
        if (textCurrentPrice != null) {
            textCurrentPrice.setText(currentPrice);
        }
        if (textPriceChange != null) {
            coinInfoController.applyPriceChangeColor(
                    textPriceChange,
                    priceChange,
                    getResources().getColor(android.R.color.holo_green_dark),
                    getResources().getColor(android.R.color.holo_red_dark),
                    getResources().getColor(android.R.color.black)
            );
        }
    }
    
    /**
     * 실시간 관찰 시작
     */
    private void startReactiveObservations() {
        Log.d("KTrader", "[MainPage] startReactiveObservations() called");
        Log.d("KTrader", "[MainPage] disposables: " + (disposables != null ? "not null" : "null"));
        Log.d("KTrader", "[MainPage] coinPriceInfoRepository: " + (coinPriceInfoRepository != null ? "not null" : "null"));
        Log.d("KTrader", "[MainPage] databaseOrderManager: " + (databaseOrderManager != null ? "not null" : "null"));
        
        if (disposables == null || coinPriceInfoRepository == null || databaseOrderManager == null || transactionInfoRepository == null) {
            Log.w("KTrader", "[MainPage] Cannot start reactive observations - required components are null");
            return;
        }
        
        Log.d("KTrader", "[MainPage] Starting reactive observations");
        
        // CoinInfo는 TransactionInfoEntity의 dailyChange만 사용하므로 CoinPriceInfoRepository 관찰 제거
        
        // 2. 활성 주문 수 실시간 관찰
        disposables.add(
            databaseOrderManager.observeActiveSellOrdersCount()
                .subscribe(
                    sellCount -> {
                        Log.d("KTrader", "[MainPage] SELL orders count updated: " + sellCount);
                        // BUY 주문 수도 함께 조회하여 업데이트
                        updateActiveOrdersDisplay(sellCount, null);
                    },
                    throwable -> Log.e("KTrader", "[MainPage] Error observing SELL orders count", throwable)
                )
        );
        
        disposables.add(
            databaseOrderManager.observeActiveBuyOrdersCount()
                .subscribe(
                    buyCount -> {
                        Log.d("KTrader", "[MainPage] BUY orders count updated: " + buyCount);
                        // SELL 주문 수도 함께 조회하여 업데이트
                        updateActiveOrdersDisplay(null, buyCount);
                    },
                    throwable -> Log.e("KTrader", "[MainPage] Error observing BUY orders count", throwable)
                )
        );
        
        // 3. Transaction 정보 실시간 관찰
        disposables.add(
            transactionInfoRepository.observeLatestTransactionInfo()
                .subscribe(
                    transactionInfo -> {
                        Log.d("KTrader", "[MainPage] Transaction info updated: " + transactionInfo.toString());
                        updateUIWithTransactionInfo(transactionInfo);
                    },
                    throwable -> Log.e("KTrader", "[MainPage] Error observing transaction info", throwable)
                )
        );
    }
    
    /**
     * 활성 주문 수 표시 업데이트
     */
    private void updateActiveOrdersDisplay(Integer sellCount, Integer buyCount) {
        if (textActiveOrders == null) return;
        
        // 현재 표시된 값을 파싱하여 업데이트
        String currentText = textActiveOrders.getText().toString();
        int currentSell = 0;
        int currentBuy = 0;
        
        try {
            // 새로운 형식 파싱: "S0 : B0"
            if (currentText.contains("S") && currentText.contains("B")) {
                String[] parts = currentText.split(" : ");
                if (parts.length == 2) {
                    currentSell = Integer.parseInt(parts[0].substring(1)); // "S" 제거
                    currentBuy = Integer.parseInt(parts[1].substring(1));   // "B" 제거
                }
            }
        } catch (Exception e) {
            Log.w("KTrader", "[MainPage] Error parsing current active orders text: " + currentText);
        }
        
        // 새로운 값으로 업데이트
        int newSell = sellCount != null ? sellCount : currentSell;
        int newBuy = buyCount != null ? buyCount : currentBuy;
        
        String newText = "S" + newSell + " : B" + newBuy;
        textActiveOrders.setText(newText);
        Log.d("KTrader", "[MainPage] Updated active orders display: " + newText);
    }
    
    /**
     * Transaction 정보로 UI 업데이트
     */
    private void updateUIWithTransactionInfo(TransactionInfoEntity transactionInfo) {
        if (transactionInfo == null) return;
        
        // 현재 가격 업데이트
        if (textCurrentPrice != null && transactionInfo.getBtcCurrentPrice() != null) {
            textCurrentPrice.setText(transactionInfo.getBtcCurrentPrice());
            Log.d("KTrader", "[MainPage] Updated current price from DB: " + transactionInfo.getBtcCurrentPrice());
        }
        
        // 1시간 등락폭 업데이트 (CoinInfo용)
        if (textPriceChange != null && transactionInfo.getHourlyChange() != null) {
            textPriceChange.setText(transactionInfo.getHourlyChange());
            
            // 등락폭에 따라 색상 변경
            if (transactionInfo.getHourlyChange().startsWith("+")) {
                textPriceChange.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            } else if (transactionInfo.getHourlyChange().startsWith("-")) {
                textPriceChange.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
            } else {
                textPriceChange.setTextColor(getResources().getColor(android.R.color.black));
            }
            Log.d("KTrader", "[MainPage] Updated hourly price change from DB: " + transactionInfo.getHourlyChange());
        }
    }
    
    /**
     * 실시간 관찰 중지
     */
    private void stopReactiveObservations() {
        if (disposables != null && !disposables.isDisposed()) {
            Log.d("KTrader", "[MainPage] Stopping reactive observations");
            disposables.clear();
        }
    }
    
    /**
     * 마지막 동기화 시간 업데이트 (Appbar에 표시)
     */
    private void updateLastSyncTime() {
        Log.d("KTrader", "[MainPage] updateLastSyncTime() called");
        Log.d("KTrader", "[MainPage] mainActivity: " + (mainActivity != null ? "not null" : "null"));
        
        if (mainActivity != null) {
            Log.d("KTrader", "[MainPage] Calling mainActivity.updateLastSyncTime()");
            mainActivity.updateLastSyncTime();
            Log.d("KTrader", "[MainPage] Updated last sync time in Appbar");
        } else {
            Log.w("KTrader", "[MainPage] Cannot update last sync time - mainActivity is null");
        }
    }

}
