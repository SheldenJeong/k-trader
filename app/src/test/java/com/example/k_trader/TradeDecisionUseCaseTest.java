package com.example.k_trader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.k_trader.domain.usecase.TradeDecisionUseCase;

import org.junit.Test;

public class TradeDecisionUseCaseTest {

    private final TradeDecisionUseCase useCase = new TradeDecisionUseCase();

    @Test
    public void getFloorPrice_returnsSame_whenAlreadyOnSlot() {
        int result = useCase.getFloorPrice(50000000, 100000);
        assertEquals(50000000, result);
    }

    @Test
    public void getFloorPrice_roundsDown_whenBetweenSlots() {
        int result = useCase.getFloorPrice(50001234, 100000);
        assertEquals(50000000, result);
    }

    @Test
    public void calculateUnitAmount_roundsTo4Digits() {
        double result = useCase.calculateUnitAmount(1200000, 49390000);
        assertEquals(0.0243, result, 0.000001);
    }

    @Test
    public void hasEnoughKrwBalance_appliesTolerancePolicy() {
        assertTrue(useCase.hasEnoughKrwBalance(1000.01, 1000.0));
        assertFalse(useCase.hasEnoughKrwBalance(1000.0, 1000.0));
    }
}
