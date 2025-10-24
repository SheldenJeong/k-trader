package com.example.k_trader.util;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 가격 큐 관리자 (Singleton)
 * 1시간 분량의 시장가를 저장하고 분석에 사용
 * Thread-safe하게 구현되어 어디서든 안전하게 사용 가능
 */
public class PriceQueueManager {
    
    private static final String TAG = "KTrader";
    private static final int DEFAULT_QUEUE_SIZE = 60; // 1시간 분량 (1분마다 업데이트 가정)
    
    private static volatile PriceQueueManager instance;
    private static final Object instanceLock = new Object();
    
    private final List<Integer> priceQueue;
    private final ReadWriteLock queueLock;
    private final int maxQueueSize;
    
    /**
     * Private constructor for Singleton pattern
     */
    private PriceQueueManager() {
        this.priceQueue = new ArrayList<>();
        this.queueLock = new ReentrantReadWriteLock();
        this.maxQueueSize = DEFAULT_QUEUE_SIZE;
        
        Log.d(TAG, "[PriceQueueManager] Singleton instance created with max size: " + maxQueueSize);
    }
    
    /**
     * Singleton instance getter
     * Thread-safe double-checked queueLocking pattern
     */
    public static PriceQueueManager getInstance() {
        if (instance == null) {
            synchronized (instanceLock) {
                if (instance == null) {
                    instance = new PriceQueueManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * 새로운 가격을 큐에 추가
     * 큐 크기가 최대값을 초과하면 가장 오래된 가격을 제거
     * 
     * @param price 추가할 가격
     */
    public void addPrice(int price) {
        if (price <= 0) {
            Log.w(TAG, "[PriceQueueManager] Invalid price: " + price + ", skipping");
            return;
        }
        
        queueLock.writeLock().lock();
        try {
            priceQueue.add(price);
            
            // 큐 크기 제한
            while (priceQueue.size() > maxQueueSize) {
                int removedPrice = priceQueue.remove(0);
                Log.d(TAG, "[PriceQueueManager] Removed oldest price: " + removedPrice);
            }
            
            Log.d(TAG, "[PriceQueueManager] Added price: " + price + ", Queue size: " + priceQueue.size());
            
        } finally {
            queueLock.writeLock().unlock();
        }
    }
    
    /**
     * 현재 큐에 저장된 가격 개수 반환
     * 
     * @return 큐 크기
     */
    public int getQueueSize() {
        queueLock.readLock().lock();
        try {
            return priceQueue.size();
        } finally {
            queueLock.readLock().unlock();
        }
    }
    
    /**
     * 큐가 비어있는지 확인
     * 
     * @return 큐가 비어있으면 true
     */
    public boolean isEmpty() {
        queueLock.readLock().lock();
        try {
            return priceQueue.isEmpty();
        } finally {
            queueLock.readLock().unlock();
        }
    }
    
    /**
     * 큐가 최소 크기에 도달했는지 확인
     * 
     * @param minSize 최소 크기
     * @return 큐 크기가 최소 크기 이상이면 true
     */
    public boolean hasMinimumData(int minSize) {
        queueLock.readLock().lock();
        try {
            return priceQueue.size() >= minSize;
        } finally {
            queueLock.readLock().unlock();
        }
    }
    
    /**
     * 큐의 최대값 반환
     * 
     * @return 최대 가격, 큐가 비어있으면 0
     */
    public int getMaxPrice() {
        queueLock.readLock().lock();
        try {
            if (priceQueue.isEmpty()) {
                return 0;
            }
            return Collections.max(priceQueue);
        } finally {
            queueLock.readLock().unlock();
        }
    }
    
    /**
     * 큐의 최소값 반환
     * 
     * @return 최소 가격, 큐가 비어있으면 0
     */
    public int getMinPrice() {
        queueLock.readLock().lock();
        try {
            if (priceQueue.isEmpty()) {
                return 0;
            }
            return Collections.min(priceQueue);
        } finally {
            queueLock.readLock().unlock();
        }
    }
    
    /**
     * 큐의 평균 가격 계산
     * 
     * @return 평균 가격, 큐가 비어있으면 0
     */
    public double getAveragePrice() {
        queueLock.readLock().lock();
        try {
            if (priceQueue.isEmpty()) {
                return 0.0;
            }
            
            long sum = 0;
            for (int price : priceQueue) {
                sum += price;
            }
            
            return (double) sum / priceQueue.size();
        } finally {
            queueLock.readLock().unlock();
        }
    }
    
    /**
     * 1시간 동안의 가격 변동률 계산
     * 기존 TradeJobService의 getPriceVariationRate() 로직과 동일
     * 
     * @return 변동률 (%), 큐에 충분한 데이터가 없으면 0
     */
    public float getPriceVariationRate() {
        queueLock.readLock().lock();
        try {
            if (priceQueue.size() < 2) {
                Log.w(TAG, "[PriceQueueManager] Not enough data for variation rate calculation");
                return 0.0f;
            }
            
            int maxPrice = Collections.max(priceQueue);
            int minPrice = Collections.min(priceQueue);
            
            int minIndex = priceQueue.indexOf(minPrice);
            int maxIndex = priceQueue.indexOf(maxPrice);
            
            float variationRate;
            if (minIndex < maxIndex) {
                // 상승: 최소값이 최대값보다 먼저 발생
                variationRate = ((maxPrice / (float) minPrice) - 1) * 100;
                Log.d(TAG, "[PriceQueueManager] Price variation: UP " + String.format("%.2f", variationRate) + "%");
            } else {
                // 하락: 최대값이 최소값보다 먼저 발생
                variationRate = ((minPrice / (float) maxPrice) - 1) * 100;
                Log.d(TAG, "[PriceQueueManager] Price variation: DOWN " + String.format("%.2f", variationRate) + "%");
            }
            
            return variationRate;
            
        } finally {
            queueLock.readLock().unlock();
        }
    }
    
    /**
     * 최근 N개의 가격 반환
     * 
     * @param count 반환할 가격 개수
     * @return 최근 가격들의 리스트 (새로운 리스트로 복사)
     */
    public List<Integer> getRecentPrices(int count) {
        queueLock.readLock().lock();
        try {
            if (priceQueue.isEmpty()) {
                return new ArrayList<>();
            }
            
            int startIndex = Math.max(0, priceQueue.size() - count);
            return new ArrayList<>(priceQueue.subList(startIndex, priceQueue.size()));
            
        } finally {
            queueLock.readLock().unlock();
        }
    }
    
    /**
     * 전체 가격 리스트 반환 (복사본)
     * 
     * @return 전체 가격 리스트의 복사본
     */
    public List<Integer> getAllPrices() {
        queueLock.readLock().lock();
        try {
            return new ArrayList<>(priceQueue);
        } finally {
            queueLock.readLock().unlock();
        }
    }
    
    /**
     * 큐 초기화
     */
    public void clear() {
        queueLock.writeLock().lock();
        try {
            int size = priceQueue.size();
            priceQueue.clear();
            Log.d(TAG, "[PriceQueueManager] Cleared " + size + " prices from queue");
        } finally {
            queueLock.writeLock().unlock();
        }
    }
    
    /**
     * 큐 상태 정보 반환
     * 
     * @return 큐 상태 문자열
     */
    public String getQueueStatus() {
        queueLock.readLock().lock();
        try {
            if (priceQueue.isEmpty()) {
                return "Queue is empty";
            }
            
            int min = getMinPrice();
            int max = getMaxPrice();
            double avg = getAveragePrice();
            float variation = getPriceVariationRate();
            
            return String.format("Size: %d, Min: %d, Max: %d, Avg: %.2f, Variation: %.2f%%",
                    priceQueue.size(), min, max, avg, variation);
        } finally {
            queueLock.readLock().unlock();
        }
    }
    
    /**
     * 큐 크기 설정 (런타임에 변경 가능)
     * 
     * @param newSize 새로운 최대 큐 크기
     */
    public void setMaxQueueSize(int newSize) {
        if (newSize <= 0) {
            Log.w(TAG, "[PriceQueueManager] Invalid queue size: " + newSize);
            return;
        }
        
        queueLock.writeLock().lock();
        try {
            // 현재 큐 크기가 새로운 크기보다 크면 오래된 데이터 제거
            while (priceQueue.size() > newSize) {
                int removedPrice = priceQueue.remove(0);
                Log.d(TAG, "[PriceQueueManager] Removed price due to size limit: " + removedPrice);
            }
            
            Log.d(TAG, "[PriceQueueManager] Max queue size changed to: " + newSize);
        } finally {
            queueLock.writeLock().unlock();
        }
    }
    
    /**
     * 디버깅용 상세 정보 출력
     */
    public void printDetailedInfo() {
        queueLock.readLock().lock();
        try {
            Log.d(TAG, "[PriceQueueManager] === Detailed Queue Info ===");
            Log.d(TAG, "[PriceQueueManager] Queue size: " + priceQueue.size() + "/" + maxQueueSize);
            Log.d(TAG, "[PriceQueueManager] Min price: " + getMinPrice());
            Log.d(TAG, "[PriceQueueManager] Max price: " + getMaxPrice());
            Log.d(TAG, "[PriceQueueManager] Average price: " + String.format("%.2f", getAveragePrice()));
            Log.d(TAG, "[PriceQueueManager] Variation rate: " + String.format("%.2f", getPriceVariationRate()) + "%");
            
            if (!priceQueue.isEmpty()) {
                Log.d(TAG, "[PriceQueueManager] Recent 5 prices: " + getRecentPrices(5));
            }
            Log.d(TAG, "[PriceQueueManager] ===========================");
        } finally {
            queueLock.readLock().unlock();
        }
    }
}
