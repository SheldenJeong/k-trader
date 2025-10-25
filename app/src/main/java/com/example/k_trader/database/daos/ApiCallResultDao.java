package com.example.k_trader.database.daos;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Delete;

import com.example.k_trader.database.entities.ApiCallResultEntity;
import java.util.Date;
import java.util.List;
import io.reactivex.Flowable;
import io.reactivex.Single;

/**
 * API 호출 결과 데이터 접근 객체
 */
@Dao
public interface ApiCallResultDao {
    
    // 단일 삽입
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertApiCallResult(ApiCallResultEntity result);
    
    // 배치 삽입 (성능 최적화)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertApiCallResults(List<ApiCallResultEntity> results);
    
    // 모든 API 호출 결과 조회
    @Query("SELECT * FROM api_call_results ORDER BY timestamp DESC")
    List<ApiCallResultEntity> getAllApiCallResults();
    
    // 특정 시간 이후의 API 호출 결과 조회
    @Query("SELECT * FROM api_call_results WHERE timestamp >= :sinceTime ORDER BY timestamp DESC")
    List<ApiCallResultEntity> getApiCallResultsSince(long sinceTime);
    
    // 성공한 API 호출 결과 조회
    @Query("SELECT * FROM api_call_results WHERE success = 1 ORDER BY timestamp DESC")
    List<ApiCallResultEntity> getSuccessfulApiCallResults();
    
    // 실패한 API 호출 결과 조회
    @Query("SELECT * FROM api_call_results WHERE success = 0 ORDER BY timestamp DESC")
    List<ApiCallResultEntity> getFailedApiCallResults();
    
    // 특정 엔드포인트의 API 호출 결과 조회
    @Query("SELECT * FROM api_call_results WHERE endpoint = :endpoint ORDER BY timestamp DESC")
    List<ApiCallResultEntity> getApiCallResultsByEndpoint(String endpoint);
    
    // 오래된 데이터 삭제
    @Query("DELETE FROM api_call_results WHERE timestamp < :cutoffTime")
    int deleteOldApiCallResults(long cutoffTime);
    
    // 전체 API 호출 결과 개수 조회
    @Query("SELECT COUNT(*) FROM api_call_results")
    int getApiCallResultCount();
    
    // 성공한 API 호출 결과 개수 조회
    @Query("SELECT COUNT(*) FROM api_call_results WHERE success = 1")
    int getSuccessfulApiCallResultCount();
    
    // 실패한 API 호출 결과 개수 조회
    @Query("SELECT COUNT(*) FROM api_call_results WHERE success = 0")
    int getFailedApiCallResultCount();
}