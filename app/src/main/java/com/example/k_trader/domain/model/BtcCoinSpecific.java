package com.example.k_trader.domain.model;

/**
 * BTC 코인 특성 구현체
 */
public class BtcCoinSpecific implements CoinSpecific {
    
    private static final double SELL_LIMIT_COUNT = 0.0001;
    private static final int MINIMUM_TRADING_UNIT = 1000;
    private static final String COIN_TYPE = "BTC";
    
    public double getMinimumTradingAmount() {
        return SELL_LIMIT_COUNT;
    }
    
    public int getMinimumTradingUnit() {
        return MINIMUM_TRADING_UNIT;
    }
    
    public String getCoinType() {
        return COIN_TYPE;
    }
    
    @Override
    public String toString() {
        return "BtcCoinSpecific{" +
                "sellLimitCount=" + SELL_LIMIT_COUNT +
                ", minimumTradingUnit=" + MINIMUM_TRADING_UNIT +
                ", coinType='" + COIN_TYPE + '\'' +
                '}';
    }
}
