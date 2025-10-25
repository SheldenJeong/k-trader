package com.example.k_trader.domain.model;

/**
 * 코인별 특성을 관리하는 팩토리 클래스
 */
public class CoinSpecificFactory {
    
    /**
     * 코인 타입에 따라 해당하는 CoinSpecific 구현체를 반환
     * @param coinType 코인 타입 (BTC, ETH 등)
     * @return 해당 코인의 특성 구현체
     */
    public static CoinSpecific getCoinSpecific(String coinType) {
        if (coinType == null) {
            throw new IllegalArgumentException("코인 타입이 null입니다");
        }
        
        switch (coinType.toUpperCase()) {
            case "BTC":
                return new BtcCoinSpecific();
            case "ETH":
                return new EthCoinSpecific();
            default:
                throw new IllegalArgumentException("지원하지 않는 코인 타입입니다: " + coinType);
        }
    }
    
    /**
     * 현재 설정된 코인 타입에 따라 CoinSpecific 구현체를 반환
     * @return 현재 설정된 코인의 특성 구현체
     */
    public static CoinSpecific getCurrentCoinSpecific() {
        String currentCoinType = com.example.k_trader.base.GlobalSettings.getInstance().getCoinType();
        return getCoinSpecific(currentCoinType);
    }
}
