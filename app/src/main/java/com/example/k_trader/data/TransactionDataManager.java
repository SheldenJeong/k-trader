package com.example.k_trader.data;

import android.content.Context;

/**
 * Transaction 데이터 관리자
 * 캐시된 데이터와 서버 데이터를 통합 관리하는 중앙 관리자
 * 앱 시작 시 즉시 캐시된 데이터를 로드하고, 백그라운드에서 서버 데이터를 동기화
 */
public class TransactionDataManager {
    
    private final TransactionCacheService cacheService;
    private static volatile TransactionDataManager INSTANCE;

    private TransactionDataManager(Context context) {
        this.cacheService = TransactionCacheService.getInstance(context);
    }

    /**
     * 싱글톤 패턴으로 인스턴스 반환
     */
    public static TransactionDataManager getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (TransactionDataManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new TransactionDataManager(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 캐시된 데이터를 외부에서 조회할 수 있도록 제공
     */
    public TransactionData getCachedData() {
        return cacheService.getCachedData();
    }


}
