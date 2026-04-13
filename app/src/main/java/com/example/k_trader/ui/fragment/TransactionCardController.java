package com.example.k_trader.ui.fragment;

import android.util.Log;
import android.widget.TextView;

import java.util.Locale;

class TransactionCardController {
    private final TextView textCoinKwValueCard;
    private final TextView textEstimatedBalanceCard;
    private final TextView textTotalBalanceCard;
    private final TextView textTransactionTimeCard;
    private final TextView textLastBuyPriceCard;
    private final TextView textLastSellPriceCard;
    private final TextView textNextBuyPriceCard;

    TransactionCardController(TextView textCoinKwValueCard,
                              TextView textEstimatedBalanceCard,
                              TextView textTotalBalanceCard,
                              TextView textTransactionTimeCard,
                              TextView textLastBuyPriceCard,
                              TextView textLastSellPriceCard,
                              TextView textNextBuyPriceCard) {
        this.textCoinKwValueCard = textCoinKwValueCard;
        this.textEstimatedBalanceCard = textEstimatedBalanceCard;
        this.textTotalBalanceCard = textTotalBalanceCard;
        this.textTransactionTimeCard = textTransactionTimeCard;
        this.textLastBuyPriceCard = textLastBuyPriceCard;
        this.textLastSellPriceCard = textLastSellPriceCard;
        this.textNextBuyPriceCard = textNextBuyPriceCard;
    }

    void updateAmounts(String coinKwValue, String estimatedBalance) {
        if (textCoinKwValueCard == null || textEstimatedBalanceCard == null || textTotalBalanceCard == null) {
            Log.w("KTrader", "[TransactionCardController] transaction card views are null");
            return;
        }

        if (coinKwValue != null && !coinKwValue.isEmpty()) {
            textCoinKwValueCard.setText(coinKwValue);
        }
        if (estimatedBalance != null && !estimatedBalance.isEmpty()) {
            textEstimatedBalanceCard.setText(estimatedBalance);
        }

        String total = calculateTotalBalanceSafe(
                textCoinKwValueCard.getText().toString(),
                textEstimatedBalanceCard.getText().toString()
        );
        textTotalBalanceCard.setText(total);
    }

    void updateMeta(String transactionTime, String lastBuyPrice, String lastSellPrice, String nextBuyPrice) {
        if (textTransactionTimeCard != null && transactionTime != null && !transactionTime.isEmpty()) {
            textTransactionTimeCard.setText(transactionTime);
        }
        if (textLastBuyPriceCard != null && lastBuyPrice != null && !lastBuyPrice.isEmpty()) {
            textLastBuyPriceCard.setText(lastBuyPrice);
        }
        if (textLastSellPriceCard != null && lastSellPrice != null && !lastSellPrice.isEmpty()) {
            textLastSellPriceCard.setText(lastSellPrice);
        }
        if (textNextBuyPriceCard != null && nextBuyPrice != null && !nextBuyPrice.isEmpty()) {
            textNextBuyPriceCard.setText(nextBuyPrice);
        }
    }

    private String calculateTotalBalanceSafe(String coinKwValue, String estimatedBalance) {
        try {
            String coinKwNum = coinKwValue.replaceAll("[^0-9,]", "").replace(",", "");
            String estimatedNum = estimatedBalance.replaceAll("[^0-9,]", "").replace(",", "");

            long coinValue = coinKwNum.isEmpty() ? 0 : Long.parseLong(coinKwNum);
            long estimated = estimatedNum.isEmpty() ? 0 : Long.parseLong(estimatedNum);
            long total = coinValue + estimated;
            return String.format(Locale.getDefault(), "₩%,d", total);
        } catch (Exception e) {
            Log.e("KTrader", "[TransactionCardController] error calculating total balance", e);
            return "₩0";
        }
    }
}
