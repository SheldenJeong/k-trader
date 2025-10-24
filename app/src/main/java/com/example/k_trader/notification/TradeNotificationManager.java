package com.example.k_trader.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.example.k_trader.R;
import com.example.k_trader.ui.activity.MainActivity;

/**
 * K-Trader 거래 알림을 관리하는 클래스
 * SRP 원칙에 따라 notification 관련 책임만 담당
 */
public class TradeNotificationManager {
    
    private static final String TAG = "KTrader";
    
    // Foreground Service 관련 상수
    private static final int FOREGROUND_SERVICE_ID = 1001;
    private static final String FOREGROUND_CHANNEL_ID = "k_trader_foreground_channel";
    
    // Trade Notification 관련 상수
    private static final String TRADE_CHANNEL_ID = "my_channel_id_03";
    
    private final Context context;
    private final NotificationManager notificationManager;
    
    public TradeNotificationManager(Context context) {
        this.context = context;
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }
    
    /**
     * Foreground Service용 Notification Channel 생성
     */
    public void createForegroundNotificationChannel() {
        Log.d(TAG, "[TradeNotificationManager] createForegroundNotificationChannel() 시작");
        
        try {
            NotificationChannel channel = new NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                "K-Trader Trading Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("K-Trader 자동 거래 서비스");
            channel.setShowBadge(false);
            channel.setLightColor(Color.parseColor("#FF8C42"));
            
            Log.d(TAG, "[TradeNotificationManager] NotificationChannel 생성 완료 - ID: " + FOREGROUND_CHANNEL_ID);
            
            if (notificationManager != null) {
                Log.d(TAG, "[TradeNotificationManager] NotificationManager 획득 성공");
                
                // 기존 채널이 있는지 확인
                NotificationChannel existingChannel = notificationManager.getNotificationChannel(FOREGROUND_CHANNEL_ID);
                if (existingChannel != null) {
                    Log.d(TAG, "[TradeNotificationManager] 기존 채널 발견 - 삭제 후 재생성");
                    notificationManager.deleteNotificationChannel(FOREGROUND_CHANNEL_ID);
                }
                
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "[TradeNotificationManager] NotificationChannel 생성 완료");
                
                // 채널 생성 확인
                NotificationChannel createdChannel = notificationManager.getNotificationChannel(FOREGROUND_CHANNEL_ID);
                if (createdChannel != null) {
                    Log.d(TAG, "[TradeNotificationManager] 채널 생성 확인 성공 - 중요도: " + createdChannel.getImportance());
                } else {
                    Log.e(TAG, "[TradeNotificationManager] 채널 생성 확인 실패");
                }
            } else {
                Log.e(TAG, "[TradeNotificationManager] NotificationManager 획득 실패");
            }
        } catch (Exception e) {
            Log.e(TAG, "[TradeNotificationManager] createForegroundNotificationChannel() 오류", e);
        }
    }
    
    /**
     * Trade Notification용 Notification Channel 생성
     */
    public void createTradeNotificationChannel() {
        Log.d(TAG, "[TradeNotificationManager] createTradeNotificationChannel() 시작");
        
        try {
            if (notificationManager != null) {
                Log.d(TAG, "[TradeNotificationManager] NotificationManager 획득 성공");
                
                NotificationChannel notificationChannel = new NotificationChannel(
                    TRADE_CHANNEL_ID, 
                    "K-Trader Trade Notifications", 
                    NotificationManager.IMPORTANCE_DEFAULT
                );

                // Configure the notification channel.
                notificationChannel.setDescription("K-Trader 거래 알림 채널");
                notificationChannel.enableLights(true);
                notificationChannel.setLightColor(Color.parseColor("#FF8C42"));
                notificationChannel.setVibrationPattern(new long[]{0, 100, 100, 100, 100, 100});
                notificationChannel.enableVibration(true);
                notificationChannel.setShowBadge(true);
                
                Log.d(TAG, "[TradeNotificationManager] NotificationChannel 생성 완료 - ID: " + TRADE_CHANNEL_ID);
                
                notificationManager.createNotificationChannel(notificationChannel);
                Log.d(TAG, "[TradeNotificationManager] NotificationChannel 등록 완료");
                
                // 채널 생성 확인
                NotificationChannel createdChannel = notificationManager.getNotificationChannel(TRADE_CHANNEL_ID);
                if (createdChannel != null) {
                    Log.d(TAG, "[TradeNotificationManager] 채널 생성 확인 성공 - 중요도: " + createdChannel.getImportance());
                } else {
                    Log.e(TAG, "[TradeNotificationManager] 채널 생성 확인 실패");
                }
            } else {
                Log.e(TAG, "[TradeNotificationManager] NotificationManager 획득 실패");
            }
        } catch (Exception e) {
            Log.e(TAG, "[TradeNotificationManager] createTradeNotificationChannel() 오류", e);
        }
    }
    
    /**
     * Foreground Service 시작을 위한 Notification 생성
     */
    public Notification createForegroundNotification(Service service) {
        Log.d(TAG, "[TradeNotificationManager] createForegroundNotification() 시작");
        
        try {
            Intent notificationIntent = new Intent(service, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                service, 0, notificationIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            Log.d(TAG, "[TradeNotificationManager] PendingIntent 생성 완료");

            NotificationCompat.Builder builder = new NotificationCompat.Builder(service, FOREGROUND_CHANNEL_ID)
                .setContentTitle("K-Trader 자동 거래")
                .setContentText("백그라운드에서 자동 거래가 실행 중입니다")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setColor(getNotificationColorByTheme());

            Log.d(TAG, "[TradeNotificationManager] NotificationCompat.Builder 생성 완료");

            // 채널 존재 확인
            if (notificationManager != null) {
                NotificationChannel channel = notificationManager.getNotificationChannel(FOREGROUND_CHANNEL_ID);
                if (channel != null) {
                    Log.d(TAG, "[TradeNotificationManager] 채널 확인 성공 - 중요도: " + channel.getImportance());
                } else {
                    Log.e(TAG, "[TradeNotificationManager] 채널 확인 실패 - 채널이 존재하지 않음");
                }
            }

            return builder.build();
        } catch (Exception e) {
            Log.e(TAG, "[TradeNotificationManager] createForegroundNotification() 오류", e);
            return null;
        }
    }
    
    /**
     * 거래 알림 발송
     */
    public void sendTradeNotification(String title, String text) {
        Log.d(TAG, "[TradeNotificationManager] sendTradeNotification() 시작 - title: " + title + ", text: " + text);
        
        try {
            Resources res = context.getResources();

            Intent notificationIntent = new Intent(context, MainActivity.class);
            notificationIntent.setAction(Intent.ACTION_MAIN);
            notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            PendingIntent contentIntent = PendingIntent.getActivity(
                context, 0, notificationIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            Log.d(TAG, "[TradeNotificationManager] PendingIntent 생성 완료");

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, TRADE_CHANNEL_ID);

            builder.setContentTitle(title)
                    .setContentText(text)
                    .setTicker(text)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setLargeIcon(BitmapFactory.decodeResource(res, R.mipmap.ic_launcher))
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true)
                    .setWhen(System.currentTimeMillis())
                    .setDefaults(Notification.DEFAULT_ALL);

            builder.setCategory(Notification.CATEGORY_MESSAGE)
                    .setVisibility(Notification.VISIBILITY_PUBLIC);

            Log.d(TAG, "[TradeNotificationManager] NotificationCompat.Builder 생성 완료");

            if (notificationManager != null) {
                Log.d(TAG, "[TradeNotificationManager] NotificationManager 획득 성공");
                
                int notificationId = (int)System.currentTimeMillis();
                notificationManager.notify(notificationId, builder.build());
                Log.d(TAG, "[TradeNotificationManager] 거래 알림 발송 완료 - ID: " + notificationId);
            } else {
                Log.e(TAG, "[TradeNotificationManager] NotificationManager 획득 실패");
            }
        } catch (Exception e) {
            Log.e(TAG, "[TradeNotificationManager] sendTradeNotification() 오류", e);
        }
    }
    
    /**
     * 현재 테마에 따라 Notification 색상을 반환하는 메서드
     */
    private int getNotificationColorByTheme() {
        // 현재 테마가 Light 테마인지 확인
        boolean isLightTheme = isLightTheme();
        
        if (isLightTheme) {
            return ContextCompat.getColor(context, R.color.notification_light);
        } else {
            return ContextCompat.getColor(context, R.color.notification_dark);
        }
    }
    
    /**
     * 현재 테마가 Light 테마인지 확인하는 메서드
     */
    private boolean isLightTheme() {
        // 현재 앱이 Light 테마를 사용하고 있는지 확인
        // AppTheme의 parent가 Theme.AppCompat.Light.DarkActionBar이므로 Light 테마
        return true; // 현재 앱은 Light 테마 사용
    }
    
    /**
     * Foreground Service 시작
     */
    public void startForegroundService(Service service) {
        Log.d(TAG, "[TradeNotificationManager] startForegroundService() 시작");
        
        try {
            Notification notification = createForegroundNotification(service);
            if (notification != null) {
                if (Build.VERSION.SDK_INT >= 34) {
                    // Android 14 (API 34) 이상에서는 서비스 타입을 지정해야 함
                    Log.d(TAG, "[TradeNotificationManager] Android 14+ - FOREGROUND_SERVICE_TYPE_DATA_SYNC 사용");
                    service.startForeground(FOREGROUND_SERVICE_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                } else {
                    Log.d(TAG, "[TradeNotificationManager] Android 13 이하 - 기본 startForeground 사용");
                    service.startForeground(FOREGROUND_SERVICE_ID, notification);
                }
                
                Log.d(TAG, "[TradeNotificationManager] startForeground() 호출 완료");
            } else {
                Log.e(TAG, "[TradeNotificationManager] Foreground notification 생성 실패");
            }
        } catch (Exception e) {
            Log.e(TAG, "[TradeNotificationManager] startForegroundService() 오류", e);
        }
    }
}
