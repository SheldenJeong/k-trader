package com.example.k_trader.database;

import android.content.Context;
import android.util.Log;

import com.example.k_trader.database.daos.ErrorDao;
import com.example.k_trader.database.entities.ErrorEntity;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

/**
 * 에러 데이터 Repository
 * 에러 데이터의 비즈니스 로직을 처리하고 데이터베이스 접근을 추상화
 */
public class ErrorRepository {
    
    private final ErrorDao errorDao;
    private static volatile ErrorRepository INSTANCE;

    private ErrorRepository(Context context) {
        OrderDatabase database = OrderDatabase.getInstance(context);
        this.errorDao = database.errorDao();
    }

    /**
     * 싱글톤 패턴으로 인스턴스 반환
     */
    public static ErrorRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (ErrorRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ErrorRepository(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 에러를 데이터베이스에 저장
     */
    public Single<Long> saveError(long errorTime, String errorType, String errorMessage, 
                                 String transactionContext, String apiErrorDetails) {
        ErrorEntity error = new ErrorEntity(errorType, errorMessage, transactionContext, 
                                          null, apiErrorDetails, new Date(errorTime));
        
        return Single.fromCallable(() -> errorDao.insertError(error))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    /**
     * 예외와 함께 에러를 데이터베이스에 저장
     */
    public Single<Long> saveErrorFromException(Exception exception, String errorType, String transactionContext) {
        ErrorEntity error = new ErrorEntity(errorType, exception.getMessage(), transactionContext,
                                          getStackTrace(exception), null, new Date());
        
        return Single.fromCallable(() -> errorDao.insertError(error))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    /**
     * API 상세 정보와 함께 에러를 데이터베이스에 저장
     */
    public Single<Long> saveErrorWithApiDetails(long errorTime, String errorType, String errorMessage,
                                               String transactionContext, Exception exception, String apiErrorDetails) {
        ErrorEntity error = new ErrorEntity(errorType, errorMessage, transactionContext,
                                          getStackTrace(exception), apiErrorDetails, new Date(errorTime));
        
        return Single.fromCallable(() -> errorDao.insertError(error))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    /**
     * 해결되지 않은 에러들 조회
     */
    public Flowable<List<ErrorEntity>> getUnresolvedErrors() {
        return errorDao.getUnresolvedErrors()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    /**
     * 최근 24시간 에러들 조회
     */
    public Flowable<List<ErrorEntity>> getLast24HoursErrors() {
        long fromTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24);
        return errorDao.getLast24HoursErrors(fromTime)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    /**
     * 에러 해결 처리
     */
    public Completable resolveError(long errorId, String resolutionNote) {
        return Completable.fromAction(() -> {
            errorDao.resolveError(errorId, resolutionNote, System.currentTimeMillis());
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread());
    }

    /**
     * 오래된 에러들 정리
     */
    public Completable cleanupOldErrors() {
        return Completable.fromAction(() -> {
            long cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
            errorDao.deleteOldErrors(cutoffTime);
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread());
    }

    /**
     * 예외의 스택 트레이스를 문자열로 변환
     */
    private String getStackTrace(Exception exception) {
        if (exception == null) return null;
        
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        exception.printStackTrace(pw);
        return sw.toString();
    }
}