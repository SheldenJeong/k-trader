package com.example.k_trader.ui.fragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ScrollView;

import com.example.k_trader.base.GlobalSettings;
import com.example.k_trader.R;
import com.example.k_trader.base.TradeData;
import com.example.k_trader.util.LogInfoFormatter;
import com.example.k_trader.database.DatabaseMonitor;

import java.util.List;

/**
 * Transaction Log 탭을 담당하는 Fragment
 * DB 구독 시스템을 통해 실시간으로 주문 데이터 로그를 표시
 * Created by K-Trader on 2024-12-25.
 */
public class TransactionLogPage extends Fragment {

    public static final String BROADCAST_LOG_MESSAGE = "TRADE_LOG";

    private static final int MAX_BUFFER = 10000;
    private static final long ORDER_UPDATE_THROTTLE_MS = 1000; // 1초마다만 업데이트

    private EditText editText;
    private ScrollView scrollView;
    private LogReceiver logReceiver;
    private DatabaseMonitor databaseMonitor;
    private String subscriberId;
    private long lastOrderUpdateTime = 0; // 마지막 주문 업데이트 시간

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_transaction_log, container, false);
        
        // UI 컴포넌트 초기화
        editText = view.findViewById(R.id.editText);
        scrollView = view.findViewById(R.id.scrollView1);
        
        // 기존 로그 데이터 유지 (새로고침하지 않음)
        Log.d("KTrader", "[TransactionLogPage] onCreateView - 기존 로그 데이터 유지");
        
        // Database Monitor 초기화
        databaseMonitor = DatabaseMonitor.getInstance(getContext());
        subscriberId = "TransactionLogFragment_" + System.currentTimeMillis();
        
        // BroadcastReceiver 등록
        if (getContext() != null) {
            registerBroadcastReceiver();
        }

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // DB 구독 해제
        if (databaseMonitor != null) {
            databaseMonitor.unsubscribe(subscriberId);
        }
        
        // BroadcastReceiver 해제
        if (logReceiver != null && getContext() != null) {
            LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(logReceiver);
        }
    }

    /**
     * BroadcastReceiver를 등록하는 메서드
     */
    private void registerBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BROADCAST_LOG_MESSAGE);
        
        logReceiver = new LogReceiver();
        if (getContext() != null) {
            LocalBroadcastManager.getInstance(getContext()).registerReceiver(logReceiver, filter);
        }
    }


    /**
     * 로그 메시지를 받는 BroadcastReceiver
     */
    private class LogReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null && intent.getAction().equals(BROADCAST_LOG_MESSAGE)) {
                String log = intent.getStringExtra("log");
                if (log != null && !log.isEmpty()) {
                    // 무한 루프 방지: UI에만 표시하고 다시 브로드캐스트하지 않음
                    appendLogToUI(log);
                }
            }
        }
    }

    // 마지막으로 추가된 로그 시간을 추적하는 변수
    private long lastLogTimestamp = 0;
    
    /**
     * 로그를 UI에 추가하는 메서드 (브로드캐스트 없이)
     */
    private void appendLogToUI(String log) {
        if (editText != null && getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                try {
                    editText.append(log + "\n");

                    // 최대 버퍼 크기 제한
                    String currentText = editText.getText().toString();
                    if (currentText.length() > MAX_BUFFER) {
                        String truncatedText = currentText.substring(currentText.length() - MAX_BUFFER);
                        editText.setText(truncatedText);
                    }
                    
                    // 자동 스크롤
                    if (scrollView != null) {
                        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
                    }
                    
                } catch (Exception e) {
                    android.util.Log.e("KTrader", "[TransactionLogPage] Error appending log to UI", e);
                }
            });
        }
    }

    /**
     * Scroll to bottom 기능을 제공하는 메서드
     */
    public void scrollToBottom() {
        if (scrollView != null) {
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        }
    }
}
