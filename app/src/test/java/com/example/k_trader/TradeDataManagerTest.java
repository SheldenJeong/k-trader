package com.example.k_trader;

import com.example.k_trader.base.TradeData;
import com.example.k_trader.base.TradeDataManager;

import org.junit.Test;

import static com.example.k_trader.base.TradeDataManager.Type.BUY;
import static com.example.k_trader.base.TradeDataManager.Type.SELL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class TradeDataManagerTest {

    @Test
    public void findById_returnsMatchingTradeData() {
        TradeDataManager manager = new TradeDataManager();
        manager.add(new TradeData().setType(BUY).setId("buy-1").setPrice(1000).setUnits(0.1f));
        manager.add(new TradeData().setType(SELL).setId("sell-1").setPrice(2000).setUnits(0.2f));

        TradeData result = manager.findById("sell-1");

        assertNotNull(result);
        assertEquals(SELL, result.getType());
        assertEquals(2000, result.getPrice());
    }

    @Test
    public void removeUnmarked_keepsOnlyMarkedTrades() {
        TradeDataManager manager = new TradeDataManager();
        manager.add(new TradeData().setId("1").setMarked(true));
        manager.add(new TradeData().setId("2").setMarked(false));
        manager.add(new TradeData().setId("3").setMarked(true));

        manager.removeUnmarked();

        assertEquals(2, manager.getList().size());
        assertNotNull(manager.findById("1"));
        assertNull(manager.findById("2"));
        assertNotNull(manager.findById("3"));
    }

    @Test
    public void getEstimation_sumsOnlySellTrades() {
        TradeDataManager manager = new TradeDataManager();
        manager.add(new TradeData().setType(SELL).setPrice(10000).setUnits(0.5f)); // 5000
        manager.add(new TradeData().setType(SELL).setPrice(20000).setUnits(0.2f)); // 4000
        manager.add(new TradeData().setType(BUY).setPrice(99999).setUnits(1.0f));  // ignored

        int estimation = manager.getEstimation();

        assertEquals(9000, estimation);
    }

    @Test
    public void findLatestProcessedTime_returnsLatestByType() {
        TradeDataManager manager = new TradeDataManager();
        manager.add(new TradeData().setType(SELL).setProcessedTime(1000L).setId("old"));
        manager.add(new TradeData().setType(SELL).setProcessedTime(5000L).setId("latest"));
        manager.add(new TradeData().setType(BUY).setProcessedTime(9999L).setId("buy"));

        TradeData latestSell = manager.findLatestProcessedTime(SELL);

        assertNotNull(latestSell);
        assertEquals("latest", latestSell.getId());
        assertEquals(5000L, latestSell.getProcessedTime());
    }

    @Test
    public void getByPrice_returnsAllMatchingPriceAndType() {
        TradeDataManager manager = new TradeDataManager();
        manager.add(new TradeData().setType(SELL).setPrice(12345).setId("a"));
        manager.add(new TradeData().setType(SELL).setPrice(12345).setId("b"));
        manager.add(new TradeData().setType(BUY).setPrice(12345).setId("c"));

        assertEquals(2, manager.getByPrice(SELL, 12345).size());
        assertEquals(1, manager.getByPrice(BUY, 12345).size());
    }
}
