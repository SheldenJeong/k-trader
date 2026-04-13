package com.example.k_trader.ui.fragment;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.k_trader.R;
import com.example.k_trader.ui.activity.MainActivity;
import com.example.k_trader.ui.adapter.Listviewitem;
import com.example.k_trader.ui.adapter.ListviewAdapter;
import com.example.k_trader.base.OrderManager;
import com.example.k_trader.base.TradeData;
import com.example.k_trader.base.TradeDataManager;
import com.google.gson.Gson;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.Objects;

import static com.example.k_trader.base.TradeDataManager.Status.PROCESSED;
import static com.example.k_trader.base.TradeDataManager.Type.BUY;
import static com.example.k_trader.base.TradeDataManager.Type.NONE;
import static com.example.k_trader.base.TradeDataManager.Type.SELL;

/**
 * Created by 김무창 on 2017-12-20.
 * 현재까지 처리 완료 된 Sell / Buy 리스트를 보여주는 화면을 관리한다.
 */

public class ProcessedOrderPage extends Fragment {
    private static final String TRADE_DATA_LIST = "TRADE_DATA_LIST";

    MainActivity mainActivity;
    ArrayList<Listviewitem> list;
    ListView listView;
    TextView textView;
    Spinner spinnerRange;

    double feeTotal;
    int priceTotal;
    int buyTotal;
    int sellTotal;
    int rangeDays;

    private TradeDataManager tradedataManager;
    private OrderManager orderManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ConstraintLayout layout = (ConstraintLayout)inflater.inflate(R.layout.processed_order_page, container,false);
        listView = (ListView)layout.findViewById(R.id.listview);
        textView = (TextView)layout.findViewById(R.id.brief);

        list = new ArrayList<>();
        mainActivity = (MainActivity) getActivity();
        spinnerRange = layout.findViewById(R.id.spinner);

        ArrayAdapter<String> sAdapter = new ArrayAdapter<String>(mainActivity.getApplicationContext(), R.layout.spinner_item, new String[] {"1일", "2일", "5일", "7일", "10일", "15일", "30일"});
        spinnerRange.setAdapter(sAdapter);
        spinnerRange.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?>  parent, View view, int position, long id) {
                rangeDays = Integer.parseInt(sAdapter.getItem(position).replace("일", ""));
                refresh();
            }
            public void onNothingSelected(AdapterView<?>  parent) {
            }
        });

        if (tradedataManager == null)
            tradedataManager = new TradeDataManager();

        if (orderManager == null)
            orderManager = new OrderManager();

        // refresh 버튼 제거됨 - AppBar의 refresh로 대체

        if (savedInstanceState != null) {
            String json = savedInstanceState.getString(TRADE_DATA_LIST);
            Gson gson = new Gson();
            tradedataManager = gson.fromJson(json, TradeDataManager.class);

            ListviewAdapter adapter = new ListviewAdapter(mainActivity.getApplicationContext(), R.layout.list_item, list);
            listView.setAdapter(adapter);

            if (tradedataManager != null)
                addToUiList();
        }

        return layout;
    }

    private TradeDataManager.Type convertSearchType(int search) {
        switch(search) {
            case 1 : return BUY;
            case 2 : return SELL;
        }
        return NONE;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        Gson gson = new Gson();
        String json = gson.toJson(tradedataManager);
        outState.putString(TRADE_DATA_LIST, json);
    }

    private void addToUiList() {
        feeTotal = 0;
        priceTotal = 0;
        buyTotal = 0;
        sellTotal = 0;

        ArrayList<Listviewitem> newList = new ArrayList<>();

        // 리스트에 추가한다.
        for (TradeData data : tradedataManager.getList()) {
            String text;
            Calendar completeTime = Calendar.getInstance();
            completeTime.setTimeInMillis(data.getProcessedTime());
            String date = String.format(Locale.getDefault(), "%02d/%02d %02d:%02d:%02d"
                    , completeTime.get(Calendar.MONTH) + 1, completeTime.get(Calendar.DATE)
                    , completeTime.get(Calendar.HOUR_OF_DAY), completeTime.get(Calendar.MINUTE), completeTime.get(Calendar.SECOND));

            if (data.getType() == BUY) {
                text = "매수 완료 : ";
                buyTotal++;
            } else if (data.getType() == SELL) {
                text = "매도 완료 : ";
                sellTotal++;
            } else
                continue;

            text += String.format(Locale.getDefault(), "%.4f", data.getUnits())
                    + " : " + String.format(Locale.getDefault(), "%,d", data.getPrice())
                    + " : " + date
                    + " : " + (int)data.getFeeEvaluated();

            Listviewitem listItem = new Listviewitem(0, text);
            newList.add(listItem);
//                                Log.d("KTrader", text);

            feeTotal += data.getFeeEvaluated();
            priceTotal += (data.getPrice() * data.getUnits());
        }

        // run ui thread to prevent 'CalledFromWrongThreadException'
        Runnable runnable = new Runnable() {
            public void run() {
                mainActivity.runOnUiThread(new Runnable() {
                    public void run() {
                        list = new ArrayList<>(newList);
                        ListviewAdapter adapter = new ListviewAdapter(mainActivity.getApplicationContext(), R.layout.list_item, list);
                        listView.setAdapter(adapter);

                        textView.setText("거래회수 : " + (buyTotal + sellTotal) + "회 (매수 : " + buyTotal + ", 매도 : " + sellTotal + ")\r\n"
                                + "거래대금 : " + String.format(Locale.getDefault(), "%,d원", priceTotal) + "\r\n"
                                + "수수료    : " + String.format(Locale.getDefault(), "%,d원", (int)feeTotal));
                    }
                });
            }
        };
        runnable.run();
    }

    public void refresh() {
        Log.d("KTrader", "[ProcessedOrderPage] refresh() called");
        if (mainActivity == null || listView == null) {
            Log.w("KTrader", "[ProcessedOrderPage] Context is null, cannot refresh");
            return;
        }
        
        ListviewAdapter adapter = new ListviewAdapter(mainActivity.getApplicationContext(), R.layout.list_item, list);
        listView.setAdapter(adapter);

        // NetworkOnMainThreadException을 방지하기 위해 thread를 돌린다.
        new Thread() {
            public void run() {
                int offset = 0;
                boolean condition = true;

                if (tradedataManager == null)
                    return;

                tradedataManager.clear();
                Calendar currentTime = Calendar.getInstance();
                {
                    while(condition) {
                        // 매수/매도 완료 리스트를 가져온다.
                        JSONArray dataArray = null;
                        try {
                            dataArray = orderManager.getProcessedOrderList("", offset, "50");
                        } catch (Exception e) {
                            return;
                        }

                        if (dataArray.isEmpty()) {
                            condition = false;
                            break;
                        }

                        for (Object o : dataArray) {
                            JSONObject item = (JSONObject) o;

                            int search = Integer.parseInt((String) item.get("search"));
                            long processedTimeInMillis;
                            String date_string = (String) item.get("transfer_date");
                            if (date_string.length() == 13)
                                processedTimeInMillis = Long.parseLong((String) Objects.requireNonNull(item.get("transfer_date")));
                            else // micro second
                                processedTimeInMillis = Long.parseLong((String) item.get("transfer_date")) / 1000;

                            if ((currentTime.getTimeInMillis() - processedTimeInMillis) / 1000 / 60 / 60 / 24.0 > rangeDays) {
                                condition = false;
                                break;
                            }

                            if (tradedataManager.findByProcessedTime(processedTimeInMillis) == null && convertSearchType(search) != NONE) {
                                tradedataManager.add(tradedataManager.build()
                                        .setType(convertSearchType(search))
                                        .setStatus(PROCESSED)
                                        .setUnits(((float) Double.parseDouble(((String) item.get("units")).replace(" ", "").replace("-", ""))))
                                        .setPrice(Math.abs(Integer.parseInt(((String) item.get("price")))))
                                        .setFeeRaw((String) item.get("fee"))
                                        .setProcessedTime(processedTimeInMillis));
                            }
                        }

                        // 다음 50개 거래 리스트를 가져온다.
                        offset += 50;
                    }

                    addToUiList();
                }
            }
        }.start();
    }
}
