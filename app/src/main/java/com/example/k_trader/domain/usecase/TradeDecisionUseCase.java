package com.example.k_trader.domain.usecase;

/**
 * TradeJobService의 순수 계산/판단 로직을 분리한 UseCase.
 * Android 의존성이 없어 단위 테스트가 쉽다.
 */
public class TradeDecisionUseCase {

    public int getFloorPrice(int price, int slotIntervalPrice) {
        if (slotIntervalPrice <= 0) {
            return price;
        }
        return price - (price % slotIntervalPrice);
    }

    public double calculateUnitAmount(int unitPrice, int targetPrice) {
        if (targetPrice <= 0) {
            return 0.0;
        }
        double unitAmount = (double) unitPrice / targetPrice;
        // 소수점 4자리 반올림 (기존 동작 유지)
        return Math.round(unitAmount * 10000.0) / 10000.0;
    }

    public boolean hasEnoughKrwBalance(double currentKrwBalance, double requiredAmount) {
        // 기존 TradeJobService의 부동소수점 보정 정책 유지
        return currentKrwBalance >= (requiredAmount + 0.01);
    }
}
