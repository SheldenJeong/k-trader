package com.example.k_trader.domain.model;

/**
 * 코인별 특성을 정의하는 인터페이스
 * 각 코인의 거래 제한사항과 최소 거래 단위를 정의
 */
public interface CoinSpecific {
    
    /**
     * 매도 제한 수량 (이 값 이하로는 거래 불가)
     * @return 최소 매도 가능 수량
     */
    double getMinimumTradingAmount();
    
    /**
     * 최소 거래 단위 (원)
     * @return 최소 거래 금액
     */
    int getMinimumTradingUnit();
    
    /**
     * 코인 타입 반환
     * @return 코인 타입 (BTC, ETH 등)
     */
    String getCoinType();
    
    /**
     * 수량이 거래 가능한지 확인
     * @param quantity 확인할 수량
     * @return 거래 가능 여부
     */
    default boolean isTradableQuantity(double quantity) {
        return quantity >= getMinimumTradingAmount();
    }
    
    /**
     * 거래 금액이 최소 거래 단위를 만족하는지 확인
     * @param amount 확인할 거래 금액
     * @return 최소 거래 단위 만족 여부
     */
    default boolean isTradableAmount(int amount) {
        return amount >= getMinimumTradingUnit();
    }
}