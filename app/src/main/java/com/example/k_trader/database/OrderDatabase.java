package com.example.k_trader.database;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.content.Context;

import com.example.k_trader.database.daos.ApiCallResultDao;
import com.example.k_trader.database.daos.TransactionInfoDao;
import com.example.k_trader.database.entities.ApiCallResultEntity;
import com.example.k_trader.database.daos.CoinPriceInfoDao;
import com.example.k_trader.database.entities.CoinPriceInfoEntity;
import com.example.k_trader.database.daos.ErrorDao;
import com.example.k_trader.database.entities.ErrorEntity;
import com.example.k_trader.database.daos.OrderDao;
import com.example.k_trader.database.entities.OrderEntity;
import com.example.k_trader.database.daos.BithumbApiDao;
import com.example.k_trader.database.entities.BithumbApiEntities;
import com.example.k_trader.database.entities.TransactionInfoEntity;

/**
 * Room 데이터베이스 설정
 */
@Database(
    entities = {
        OrderEntity.class, 
        ErrorEntity.class,
        ApiCallResultEntity.class,
        CoinPriceInfoEntity.class, 
        TransactionInfoEntity.class,
        BithumbApiEntities.BithumbTickerEntity.class,
        BithumbApiEntities.BithumbBalanceEntity.class,
        BithumbApiEntities.BithumbOrderEntity.class,
        BithumbApiEntities.BithumbCandlestickEntity.class,
        BithumbApiEntities.ApiCallStatsEntity.class
    },
    version = 9, // 버전 증가
    exportSchema = false
)
public abstract class OrderDatabase extends RoomDatabase {

    private static volatile OrderDatabase INSTANCE;

    public abstract OrderDao orderDao();
    public abstract ErrorDao errorDao();
    public abstract ApiCallResultDao apiCallResultDao();
    public abstract CoinPriceInfoDao coinPriceInfoDao();
    public abstract TransactionInfoDao transactionInfoDao();
    
    // Bithumb API DAOs
    public abstract BithumbApiDao.BithumbTickerDao BithumbTickerDao();
    public abstract BithumbApiDao.BithumbBalanceDao BithumbBalanceDao();
    public abstract BithumbApiDao.BithumbOrderDao BithumbOrderDao();
    public abstract BithumbApiDao.BithumbCandlestickDao BithumbCandlestickDao();
    public abstract BithumbApiDao.ApiCallStatsDao apiCallStatsDao();

    /**
     * 싱글톤 패턴으로 데이터베이스 인스턴스 반환
     */
    public static OrderDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (OrderDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            OrderDatabase.class,
                            "order_database"
                    )
                    .fallbackToDestructiveMigration() // 스키마 변경 시 데이터 삭제
                    .allowMainThreadQueries() // 개발 중에만 사용 (ANR 방지)
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
