package com.example.k_trader.ui.activity;

import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import com.example.k_trader.R;
import com.example.k_trader.ui.fragment.MainPage;
import com.example.k_trader.base.GlobalSettings;
import com.example.k_trader.database.OrderRepository;
import com.example.k_trader.dialog.ProgressDialogManager;
import com.example.k_trader.di.DIContainer;
import com.example.k_trader.presentation.viewmodel.ViewModels.MainViewModel;
import android.arch.lifecycle.Observer;

import java.util.TimerTask;


public class MainActivity extends AppCompatActivity {

    int MAX_PAGE = 1; // MainPage만 사용, 내부 ViewPager에서 3개 페이지 관리
    ViewPager viewPager;
    
    // ViewModel
    private MainViewModel mainViewModel;
    private DIContainer diContainer;
    
    // Appbar의 TextView들
    private android.widget.TextView textAppTitle;
    private android.widget.TextView textLastSyncTime;

    public static final int STORAGE_PERMISSION_REQUEST = 0;
    public static final int NOTIFICATION_PERMISSION_REQUEST = 1;

    public static final String BROADCAST_PROGRESS_MESSAGE = "PROGRESS_MESSAGE";
    static int progress;

    public JobScheduler jobScheduler;
    public static org.apache.log4j.Logger logger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // DIContainer 초기화 (동기적으로 처리하여 Fragment 로드 전에 완료)
        try {
            diContainer = DIContainer.getInstance(this);
            android.util.Log.d("KTrader", "[MainActivity] DIContainer 인스턴스 생성 완료");
        } catch (Exception e) {
            android.util.Log.e("KTrader", "[MainActivity] DIContainer 초기화 실패", e);
            // DIContainer 초기화 실패 시에도 앱이 계속 실행되도록 처리
        }
        
        // Toolbar 설정
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        // Appbar TextView들 초기화
        textAppTitle = findViewById(R.id.textAppTitle);

        android.util.Log.d("KTrader", "[MainActivity] Appbar TextView initialization:");
        android.util.Log.d("KTrader", "[MainActivity] textAppTitle: " + (textAppTitle != null ? "not null" : "null"));
        android.util.Log.d("KTrader", "[MainActivity] textLastSyncTime: " + (textLastSyncTime != null ? "not null" : "null"));
        
        // 테마에 따라 Appbar 텍스트 색상 설정
        setAppBarTextColorsByTheme();
        
        // 테마에 따라 Status Bar 색상 동적 설정
        setStatusBarColorByTheme();
        
        // App bar 색상 설정
        setAppBarColorByTheme();
        
        // Notification 권한 요청 (Android 13 이상)
        requestNotificationPermission();

//        int id = 0;
        viewPager = findViewById(R.id.viewpager);
        viewPager.setAdapter(new adapter(getSupportFragmentManager()));
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int i, float v, int i1) {

            }

            @Override
            public void onPageSelected(int i) {
                // MainPage에 Page 변경 이벤트 전달
                try {
                    Fragment currentFragment = getSupportFragmentManager().findFragmentByTag("android:switcher:" + R.id.viewpager + ":" + i);
                    if (currentFragment instanceof MainPage) {
                        MainPage mainPage = (MainPage) currentFragment;
                        mainPage.onPageSelected(i);
                    }
                } catch (Exception e) {
                    android.util.Log.e("KTrader", "[MainActivity] Error handling page selection", e);
                }
            }

            @Override
            public void onPageScrollStateChanged(int i) {

            }
        });

        // 로그 저장 기능 활성화 (권한 불필요 - 앱 내부 저장소 사용)
//        enableFileLogging();

        IntentFilter theFilter = new IntentFilter();
        theFilter.addAction(BROADCAST_PROGRESS_MESSAGE);
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(new MyReceiver(), theFilter);

        // ViewModel 초기화 (DIContainer가 준비된 후에)
        try {
            if (diContainer != null && diContainer.isInitialized()) {
                mainViewModel = diContainer.createMainViewModel();
                // ViewModel 관찰 설정
                setupViewModelObservers();
                android.util.Log.d("KTrader", "[MainActivity] ViewModel 초기화 완료");
            } else {
                android.util.Log.w("KTrader", "[MainActivity] DIContainer가 초기화되지 않아 ViewModel 생성을 건너뜁니다");
            }
        } catch (Exception e) {
            android.util.Log.e("KTrader", "[MainActivity] ViewModel 초기화 실패", e);
        }

        // Load app settings (data/data/(package_name)/shared_prefs/SharedPreference)
        SharedPreferences sharedPreferences = getSharedPreferences("settings", MODE_PRIVATE);
        GlobalSettings.getInstance().setApiKey(sharedPreferences.getString(GlobalSettings.API_KEY_KEY_NAME, ""))
                                    .setApiSecret(sharedPreferences.getString(GlobalSettings.API_SECRET_KEY_NAME, ""))
                                    .setUnitPrice(sharedPreferences.getInt(GlobalSettings.UNIT_PRICE_KEY_NAME, GlobalSettings.UNIT_PRICE_DEFAULT_VALUE))
                                    .setTradeInterval(sharedPreferences.getInt(GlobalSettings.TRADE_INTERVAL_KEY_NAME, GlobalSettings.TRADE_INTERVAL_DEFAULT_VALUE))
                                    .setFileLogEnabled(sharedPreferences.getBoolean(GlobalSettings.FILE_LOG_ENABLED_KEY_NAME, false))
                                    .setEarningRate(sharedPreferences.getFloat(GlobalSettings.EARNING_RATE_KEY_NAME, GlobalSettings.EARNING_RATE_DEFAULT_VALUE))
                                    .setSlotIntervalRate(sharedPreferences.getFloat(GlobalSettings.SLOT_INTERVAL_RATE_KEY_NAME, GlobalSettings.SLOT_INTERVAL_RATE_DEFAULT_VALUE))
                                    .setCoinType(sharedPreferences.getString(GlobalSettings.COIN_TYPE_KEY_NAME, GlobalSettings.COIN_TYPE_DEFAULT_VALUE))
                                    .setAutoScroll(sharedPreferences.getBoolean(GlobalSettings.AUTO_SCROLL_KEY_NAME, GlobalSettings.AUTO_SCROLL_DEFAULT_VALUE));

        if (GlobalSettings.getInstance().getApiKey().isEmpty() || GlobalSettings.getInstance().getApiSecret().isEmpty()) {
            Toast.makeText(this, "거래를 위해서는 Key와 Secret값 설정이 필요합니다.", Toast.LENGTH_SHORT).show();
            // Launch setting activity
            startActivity(new Intent(this, SettingActivity.class));
        }
    }
    
    /**
     * Notification 권한 요청 (Android 13 이상)
     */
    private void requestNotificationPermission() {
        android.util.Log.d("KTrader", "[MainActivity] requestNotificationPermission() 시작");
        android.util.Log.d("KTrader", "[MainActivity] Android SDK 버전: " + Build.VERSION.SDK_INT);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.util.Log.d("KTrader", "[MainActivity] Android 13+ 감지 - Notification 권한 확인");
            
            int permissionStatus = ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS);
            android.util.Log.d("KTrader", "[MainActivity] 현재 권한 상태: " + permissionStatus);
            android.util.Log.d("KTrader", "[MainActivity] PERMISSION_GRANTED: " + android.content.pm.PackageManager.PERMISSION_GRANTED);
            
            if (permissionStatus != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                android.util.Log.d("KTrader", "[MainActivity] Notification 권한 요청 시작");
                ActivityCompat.requestPermissions(this, 
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 
                    NOTIFICATION_PERMISSION_REQUEST);
            } else {
                android.util.Log.d("KTrader", "[MainActivity] Notification 권한 이미 허용됨");
            }
        } else {
            android.util.Log.d("KTrader", "[MainActivity] Android 13 미만 - Notification 권한 불필요");
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        android.util.Log.d("KTrader", "[MainActivity] onRequestPermissionsResult() 호출");
        android.util.Log.d("KTrader", "[MainActivity] requestCode: " + requestCode);
        android.util.Log.d("KTrader", "[MainActivity] NOTIFICATION_PERMISSION_REQUEST: " + NOTIFICATION_PERMISSION_REQUEST);
        
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            android.util.Log.d("KTrader", "[MainActivity] Notification 권한 요청 결과 처리");
            
            if (grantResults.length > 0) {
                android.util.Log.d("KTrader", "[MainActivity] grantResults.length: " + grantResults.length);
                android.util.Log.d("KTrader", "[MainActivity] grantResults[0]: " + grantResults[0]);
                android.util.Log.d("KTrader", "[MainActivity] PERMISSION_GRANTED: " + android.content.pm.PackageManager.PERMISSION_GRANTED);
                
                if (grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    android.util.Log.d("KTrader", "[MainActivity] Notification 권한 허용됨");
                    Toast.makeText(this, "알림 권한이 허용되었습니다.", Toast.LENGTH_SHORT).show();
                } else {
                    android.util.Log.d("KTrader", "[MainActivity] Notification 권한 거부됨");
                    Toast.makeText(this, "알림 권한이 거부되었습니다. 거래 알림을 받을 수 없습니다.", Toast.LENGTH_LONG).show();
                }
            } else {
                android.util.Log.e("KTrader", "[MainActivity] grantResults가 비어있음");
            }
        } else {
            android.util.Log.d("KTrader", "[MainActivity] 다른 권한 요청 결과: " + requestCode);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        
        // 메뉴 아이콘 색상 설정
        setMenuIconColorsByTheme(menu);
        
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_Bithumb) {
            launchBithumbApp();
            return true;
        } else if (id == R.id.action_refresh) {
            refreshCurrentPage();
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingActivity.class));
            return true;
        } else if (id == R.id.action_clear) {
            clearAllDatabaseRecords();
            return true;
        } else if (id == R.id.action_scroll_to_bottom) {
            scrollToBottom();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    /**
     * 현재 표시 중인 페이지를 새로고침
     */
    private void refreshCurrentPage() {
        android.util.Log.d("KTrader", "[MainActivity] refreshCurrentPage() called");
        try {
            // ViewPager에서 현재 Fragment 가져오기
            if (viewPager != null) {
                int currentItem = viewPager.getCurrentItem();
                android.util.Log.d("KTrader", "[MainActivity] Current tab: " + currentItem);
                
                // FragmentManager를 통해 현재 Fragment 가져오기
                android.support.v4.app.FragmentManager fragmentManager = getSupportFragmentManager();
                android.support.v4.app.Fragment currentFragment = fragmentManager.findFragmentByTag("android:switcher:" + R.id.viewpager + ":" + currentItem);
                
                if (currentFragment != null && currentFragment instanceof MainPage) {
                    MainPage mainPage = (MainPage) currentFragment;
                    mainPage.refreshCurrentPage();
                    android.util.Log.d("KTrader", "[MainActivity] Refresh called on MainPage");
                }
            }
        } catch (Exception e) {
            android.util.Log.e("KTrader", "[MainActivity] Error in refreshCurrentPage()", e);
            Toast.makeText(this, "새로고침 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 빗썸 앱을 실행하는 메서드
     */
    private void launchBithumbApp() {
        try {
            android.util.Log.d("KTrader", "[MainActivity] Attempting to launch Bithumb app");
            
            // 설치된 모든 앱 중에서 빗썸 관련 앱 찾기
            android.util.Log.d("KTrader", "[MainActivity] Searching for installed Bithumb-related apps...");
            java.util.List<android.content.pm.ApplicationInfo> installedApps = getPackageManager().getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA);
            
            for (android.content.pm.ApplicationInfo appInfo : installedApps) {
                String packageName = appInfo.packageName;
                android.util.Log.d("KTrader", "[MainActivity] Checking package: " + packageName);
                
                if (packageName.toLowerCase().contains("bithumb") || packageName.toLowerCase().contains("btc")) {
                    android.util.Log.d("KTrader", "[MainActivity] Found potential Bithumb app: " + packageName);
                    
                    // 일반적인 앱 실행 시도
                    Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
                    if (launchIntent != null) {
                        android.util.Log.d("KTrader", "[MainActivity] Launching Bithumb app with package: " + packageName);
                        startActivity(launchIntent);
                        Toast.makeText(this, "빗썸 앱을 실행합니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    // 특정 액티비티로 직접 실행 시도
                    Intent specificIntent = new Intent();
                    specificIntent.setComponent(new android.content.ComponentName(packageName, ".MainActivity"));
                    specificIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    
                    try {
                        startActivity(specificIntent);
                        android.util.Log.d("KTrader", "[MainActivity] Launched Bithumb app with specific activity: " + packageName);
                        Toast.makeText(this, "빗썸 앱을 실행합니다.", Toast.LENGTH_SHORT).show();
                        return;
                    } catch (Exception e) {
                        android.util.Log.w("KTrader", "[MainActivity] Failed to launch specific activity for " + packageName + ": " + e.getMessage());
                    }
                }
            }
            
            // 가능한 빗썸 앱 패키지명들
            String[] possiblePackages = {
                "com.btckorea.bithumb"
            };
            
            // 각 패키지명을 시도해보기
            for (String packageName : possiblePackages) {
                android.util.Log.d("KTrader", "[MainActivity] Trying package: " + packageName);
                
                // 패키지 설치 여부 확인
                try {
                    getPackageManager().getPackageInfo(packageName, 0);
                    android.util.Log.d("KTrader", "[MainActivity] Package installed: " + packageName);
                    
                    // MAIN/LAUNCHER 인텐트로 직접 실행 시도
                    Intent launchIntent = new Intent();
                    launchIntent.setPackage(packageName);
                    launchIntent.setAction(Intent.ACTION_MAIN);
                    launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                    launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    
                    try {
                        startActivity(launchIntent);
                        android.util.Log.d("KTrader", "[MainActivity] Launched Bithumb app with package: " + packageName);
                        Toast.makeText(this, "빗썸 앱을 실행합니다.", Toast.LENGTH_SHORT).show();
                        return;
                    } catch (Exception e) {
                        android.util.Log.w("KTrader", "[MainActivity] Failed to launch app with package: " + packageName + ", error: " + e.getMessage());
                        
                        // 특정 액티비티로 직접 실행 시도
                        Intent specificIntent = new Intent();
                        specificIntent.setComponent(new android.content.ComponentName(packageName, ".MainActivity"));
                        specificIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        
                        try {
                            startActivity(specificIntent);
                            android.util.Log.d("KTrader", "[MainActivity] Launched Bithumb app with specific activity: " + packageName);
                            Toast.makeText(this, "빗썸 앱을 실행합니다.", Toast.LENGTH_SHORT).show();
                            return;
                        } catch (Exception e2) {
                            android.util.Log.w("KTrader", "[MainActivity] Failed to launch specific activity for " + packageName + ": " + e2.getMessage());
                        }
                    }
                } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                    android.util.Log.d("KTrader", "[MainActivity] Package not found: " + packageName);
                }
            }
            
            // 모든 방법이 실패한 경우 Play Store로 이동
            android.util.Log.d("KTrader", "[MainActivity] All methods failed, redirecting to Play Store");
            Intent playStoreIntent = new Intent(Intent.ACTION_VIEW);
            playStoreIntent.setData(android.net.Uri.parse("market://details?id=com.btckorea.bithumb"));
            
            if (playStoreIntent.resolveActivity(getPackageManager()) != null) {
                android.util.Log.d("KTrader", "[MainActivity] Opening Play Store app");
                startActivity(playStoreIntent);
                Toast.makeText(this, "빗썸 앱을 설치해주세요.", Toast.LENGTH_LONG).show();
            } else {
                // Play Store 앱이 없는 경우 웹 브라우저로 이동
                android.util.Log.d("KTrader", "[MainActivity] Play Store app not found, opening web browser");
                Intent webIntent = new Intent(Intent.ACTION_VIEW);
                webIntent.setData(android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.btckorea.bithumb"));
                startActivity(webIntent);
                Toast.makeText(this, "빗썸 앱을 설치해주세요.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            android.util.Log.e("KTrader", "[MainActivity] Error launching Bithumb app: " + e.getMessage());
            Toast.makeText(this, "빗썸 앱 실행 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 모든 DB 기록을 삭제하는 메서드
     */
    private void clearAllDatabaseRecords() {
        OrderRepository orderRepository = OrderRepository.getInstance(this);
        
        orderRepository.deleteAllOrders()
                .subscribeOn(io.reactivex.schedulers.Schedulers.io())
                .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(
                        () -> Toast.makeText(this, "모든 DB 기록이 삭제되었습니다.", Toast.LENGTH_SHORT).show(),
                        throwable -> Toast.makeText(this, "DB 삭제 중 오류가 발생했습니다: " + throwable.getMessage(), Toast.LENGTH_LONG).show()
                );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        android.util.Log.d("KTrader", "[MainActivity] onDestroy() 시작");
        
        // 간단한 로그만 남기고 복잡한 Notification 생성은 제거
        android.util.Log.d("KTrader", "[MainActivity] MainActivity destroyed");
    }

    private class adapter extends FragmentPagerAdapter {
        public adapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            if (position < 0 || MAX_PAGE <= position)
                return null;

            // MainPage만 반환 (내부에서 ViewPager로 관리)
            return new MainPage();
        }

        @Override
        public int getCount() {
            return MAX_PAGE;
        }
    }

    private class MyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            progress = intent.getIntExtra("progress", 0);
            android.util.Log.d("KTrader", "[MainActivity] received progress : " + String.valueOf(progress));

            // ProgressDialogManager를 사용하여 다이얼로그 표시
            ProgressDialogManager.show(MainActivity.this, progress);
        }
    }

    static class MyTask extends TimerTask {
        public void run() {
            // ProgressDialogManager를 사용하여 다이얼로그 닫기
            ProgressDialogManager.dismiss();
        }
    }

    @Override
    public void onBackPressed() {
        // 앱을 백그라운드로 보내기 (완전 종료하지 않음)
        // 이렇게 하면 사용자가 홈 버튼을 누른 것과 같은 효과
        moveTaskToBack(true);
    }

    /**
     * 현재 테마에 따라 Status Bar 색상을 설정하는 메서드
     */
    private void setStatusBarColorByTheme() {
        // 현재 테마가 Light 테마인지 확인
        boolean isLightTheme = isLightTheme();
        
        int statusBarColor;
        if (isLightTheme) {
            statusBarColor = ContextCompat.getColor(this, R.color.status_bar_light);
            // Light 테마에서는 Status bar 아이콘을 어둡게 설정
            getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        } else {
            statusBarColor = ContextCompat.getColor(this, R.color.status_bar_dark);
            // Dark 테마에서는 Status bar 아이콘을 밝게 설정
            getWindow().getDecorView().setSystemUiVisibility(0);
        }
            
        getWindow().setStatusBarColor(statusBarColor);
    }
    
    /**
     * 현재 테마가 Light 테마인지 확인하는 메서드
     */
    private boolean isLightTheme() {
        // Android 시스템의 다크 모드 설정 확인
        int nightModeFlags = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags != android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }
    
    /**
     * 현재 테마에 따라 App Bar 색상을 설정하는 메서드
     */
    private void setAppBarColorByTheme() {
        // 현재 테마가 Light 테마인지 확인
        boolean isLightTheme = isLightTheme();
        
        int appBarColor;
        if (isLightTheme) {
            appBarColor = ContextCompat.getColor(this, R.color.app_bar_light);
        } else {
            appBarColor = ContextCompat.getColor(this, R.color.app_bar_dark);
        }
        
        // ActionBar 색상 설정
        if (getSupportActionBar() != null) {
            getSupportActionBar().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(appBarColor));
            
            // ActionBar 그림자 효과 제거
            getSupportActionBar().setElevation(0);
            
            // 텍스트 색상 설정 (흰색 배경에서는 어두운 색 사용)
            if (isLightTheme) {
                // Light 테마에서는 어두운 텍스트 색상 사용
                setActionBarTitleColor(ContextCompat.getColor(this, android.R.color.black));
            } else {
                // Dark 테마에서는 밝은 텍스트 색상 사용
                setActionBarTitleColor(ContextCompat.getColor(this, android.R.color.white));
            }
        }
    }
    
    /**
     * ActionBar 타이틀 색상을 설정하는 메서드
     */
    private void setActionBarTitleColor(int color) {
        if (getSupportActionBar() != null) {
            // ActionBar의 타이틀을 커스텀 TextView로 설정
            android.widget.TextView titleView = new android.widget.TextView(this);
            titleView.setText(getSupportActionBar().getTitle());
            titleView.setTextColor(color);
            titleView.setTextSize(18);
            titleView.setTypeface(null, android.graphics.Typeface.BOLD);
            
            // ActionBar에 커스텀 타이틀 설정
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setCustomView(titleView);
            getSupportActionBar().setDisplayShowCustomEnabled(true);
        }
    }
    
    /**
     * 현재 테마에 따라 메뉴 아이콘 색상을 설정하는 메서드
     */
    private void setMenuIconColorsByTheme(android.view.Menu menu) {
        // 현재 테마가 Light 테마인지 확인
        boolean isLightTheme = isLightTheme();
        
        int iconColor;
        if (isLightTheme) {
            // Light 테마에서는 어두운 색상 사용
            iconColor = ContextCompat.getColor(this, android.R.color.black);
        } else {
            // Dark 테마에서는 밝은 색상 사용
            iconColor = ContextCompat.getColor(this, android.R.color.white);
        }
        
        // 각 메뉴 아이템의 아이콘 색상 설정
        MenuItem refreshItem = menu.findItem(R.id.action_refresh);
        MenuItem BithumbItem = menu.findItem(R.id.action_Bithumb);
        
        if (refreshItem != null) {
            android.graphics.drawable.Drawable refreshIcon = refreshItem.getIcon();
            if (refreshIcon != null) {
                refreshIcon.setColorFilter(iconColor, android.graphics.PorterDuff.Mode.SRC_IN);
                refreshItem.setIcon(refreshIcon);
            }
        }
        
        if (BithumbItem != null) {
            android.graphics.drawable.Drawable BithumbIcon = BithumbItem.getIcon();
            if (BithumbIcon != null) {
                BithumbIcon.setColorFilter(iconColor, android.graphics.PorterDuff.Mode.SRC_IN);
                BithumbItem.setIcon(BithumbIcon);
            }
        }
    }
    
    /**
     * Appbar의 마지막 동기화 시간 업데이트
     */
    public void updateLastSyncTime() {
        android.util.Log.d("KTrader", "[MainActivity] updateLastSyncTime() called");
        android.util.Log.d("KTrader", "[MainActivity] textLastSyncTime: " + (textLastSyncTime != null ? "not null" : "null"));
        
        if (textLastSyncTime != null) {
            java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
            String currentTime = timeFormat.format(new java.util.Date());
            android.util.Log.d("KTrader", "[MainActivity] Generated time: " + currentTime);
            
            textLastSyncTime.setText(currentTime);
            android.util.Log.d("KTrader", "[MainActivity] Updated last sync time in Appbar: " + currentTime);
        } else {
            android.util.Log.w("KTrader", "[MainActivity] Cannot update last sync time - textLastSyncTime is null");
        }
    }
    
    /**
     * 테마에 따라 Appbar 텍스트 색상 설정
     */
    private void setAppBarTextColorsByTheme() {
        boolean isLightTheme = isLightTheme();
        
        int textColor;
        if (isLightTheme) {
            textColor = ContextCompat.getColor(this, android.R.color.black);
        } else {
            textColor = ContextCompat.getColor(this, android.R.color.white);
        }
        
        if (textAppTitle != null) {
            textAppTitle.setTextColor(textColor);
        }
        
        if (textLastSyncTime != null) {
            textLastSyncTime.setTextColor(textColor);
        }
        
        android.util.Log.d("KTrader", "[MainActivity] Set Appbar text colors - Theme: " + (isLightTheme ? "Light" : "Dark") + ", Color: " + (isLightTheme ? "Black" : "White"));
    }
    
    /**
     * 현재 선택된 Fragment에 따라 스크롤 기능 실행
     */
    private void scrollToBottom() {
        android.util.Log.d("KTrader", "[MainActivity] scrollToBottom() called");
        
        try {
            if (viewPager != null) {
                int currentItem = viewPager.getCurrentItem();
                android.util.Log.d("KTrader", "[MainActivity] Current tab: " + currentItem);
                
                // FragmentManager를 통해 현재 Fragment 가져오기
                android.support.v4.app.FragmentManager fragmentManager = getSupportFragmentManager();
                android.support.v4.app.Fragment currentFragment = fragmentManager.findFragmentByTag("android:switcher:" + R.id.viewpager + ":" + currentItem);
                
                if (currentFragment != null) {
                    android.util.Log.d("KTrader", "[MainActivity] Current fragment: " + currentFragment.getClass().getSimpleName());
                    
                    if (currentItem == 0) {
                        // Main Page - 내부 ViewPager에 이벤트 전달
                        if (currentFragment instanceof MainPage) {
                            MainPage mainPage = (MainPage) currentFragment;
                            mainPage.scrollToBottomInPage();
                            android.util.Log.d("KTrader", "[MainActivity] Triggered scroll in MainPage");
                        }
                    } else {
                        android.util.Log.w("KTrader", "[MainActivity] Unknown tab: " + currentItem);
                    }
                } else {
                    android.util.Log.w("KTrader", "[MainActivity] Current fragment is null");
                }
            } else {
                android.util.Log.w("KTrader", "[MainActivity] ViewPager is null");
            }
        } catch (Exception e) {
            android.util.Log.e("KTrader", "[MainActivity] Error in scrollToBottom()", e);
            Toast.makeText(this, "스크롤 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * ViewModel 관찰자 설정
     */
    private void setupViewModelObservers() {
        if (mainViewModel == null) {
            android.util.Log.w("KTrader", "[MainActivity] mainViewModel이 null입니다. 관찰자 설정을 건너뜁니다.");
            return;
        }
        
        // 현재 가격 관찰
        mainViewModel.getCurrentPrice().observe(this, new Observer<com.example.k_trader.domain.model.DomainModels.CoinPriceInfo>() {
            @Override
            public void onChanged(com.example.k_trader.domain.model.DomainModels.CoinPriceInfo coinPriceInfo) {
                if (coinPriceInfo != null) {
                    android.util.Log.d("KTrader", "[MainActivity] Current price updated: " + coinPriceInfo.getCurrentPrice());
                    // TODO: UI 업데이트 로직 추가
                }
            }
        });
        
        // 활성 주문 관찰
        mainViewModel.getActiveOrders().observe(this, new Observer<java.util.List<com.example.k_trader.domain.model.DomainModels.Trade>>() {
            @Override
            public void onChanged(java.util.List<com.example.k_trader.domain.model.DomainModels.Trade> trades) {
                if (trades != null) {
                    android.util.Log.d("KTrader", "[MainActivity] Active orders updated: " + trades.size() + " orders");
                    // TODO: UI 업데이트 로직 추가
                }
            }
        });
        
        // 자동 거래 상태 관찰
        mainViewModel.getIsAutoTradingEnabled().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean isEnabled) {
                if (isEnabled != null) {
                    android.util.Log.d("KTrader", "[MainActivity] Auto trading status updated: " + isEnabled);
                    // TODO: UI 업데이트 로직 추가
                }
            }
        });
        
        // 에러 메시지 관찰
        mainViewModel.getErrorMessage().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String errorMessage) {
                if (errorMessage != null && !errorMessage.isEmpty()) {
                    android.util.Log.e("KTrader", "[MainActivity] Error from ViewModel: " + errorMessage);
                    Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                }
            }
        });
        
        // 로딩 상태 관찰
        mainViewModel.getIsLoading().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean isLoading) {
                if (isLoading != null) {
                    android.util.Log.d("KTrader", "[MainActivity] Loading state updated: " + isLoading);
                    // TODO: 로딩 UI 업데이트 로직 추가
                }
            }
        });
    }
    
    /**
     * 자동 거래 토글 (ViewModel 사용)
     */
    public void toggleAutoTrading() {
        android.util.Log.d("KTrader", "[MainActivity] Toggling auto trading via ViewModel");
        mainViewModel.toggleAutoTrading();
    }
    
    /**
     * 데이터 새로고침 (ViewModel 사용)
     */
    public void refreshData() {
        android.util.Log.d("KTrader", "[MainActivity] Refreshing data via ViewModel");
        if (mainViewModel != null) {
            mainViewModel.refreshData();
        }
    }
    
    /**
     * 코인 가격 관찰 시작 (ViewModel 사용)
     */
    public void startObservingCoinPrice(String coinType) {
        android.util.Log.d("KTrader", "[MainActivity] Starting to observe coin price for: " + coinType);
        if (mainViewModel != null) {
            mainViewModel.observeCoinPrice(coinType);
        }
    }
    
    /**
     * 활성 주문 관찰 시작 (ViewModel 사용)
     */
    public void startObservingActiveOrders() {
        android.util.Log.d("KTrader", "[MainActivity] Starting to observe active orders");
        if (mainViewModel != null) {
            mainViewModel.observeActiveOrders();
        }
    }

    public void refreshActiveOrdersSummary() {
        if (mainViewModel != null) {
            mainViewModel.refreshActiveOrdersSummary();
        }
    }

    public MainViewModel getMainViewModel() {
        return mainViewModel;
    }
}
