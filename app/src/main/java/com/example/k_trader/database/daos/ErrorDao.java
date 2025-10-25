package com.example.k_trader.database.daos;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import com.example.k_trader.database.entities.ErrorEntity;
import java.util.Date;
import java.util.List;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.Completable;

/**
 * 에러 데이터 접근 객체
 */
@Dao
public interface ErrorDao {
    
    // 단일 삽입
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertError(ErrorEntity error);
    
    // 배치 삽입 (성능 최적화)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertErrors(List<ErrorEntity> errors);
    
    // 해결되지 않은 에러들 조회
    @Query("SELECT * FROM errors WHERE resolved = 0 ORDER BY timestamp DESC")
    Flowable<List<ErrorEntity>> getUnresolvedErrors();
    
    // 최근 24시간 에러들 조회
    @Query("SELECT * FROM errors WHERE timestamp >= :fromTime ORDER BY timestamp DESC")
    Flowable<List<ErrorEntity>> getLast24HoursErrors(Long fromTime);
    
    // 특정 에러 타입의 에러들 조회
    @Query("SELECT * FROM errors WHERE error_type = :errorType ORDER BY timestamp DESC LIMIT :limit")
    Single<List<ErrorEntity>> getErrorsByType(String errorType, int limit);
    
    // 특정 기간의 에러들 조회
    @Query("SELECT * FROM errors WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    Single<List<ErrorEntity>> getErrorsByTimeRange(Long startTime, Long endTime);
    
    // 에러 해결 처리
    @Query("UPDATE errors SET resolved = 1, resolution_note = :resolutionNote, resolved_at = :resolvedAt WHERE id = :errorId")
    int resolveError(long errorId, String resolutionNote, Long resolvedAt);
    
    // 오래된 에러들 정리
    @Query("DELETE FROM errors WHERE timestamp < :cutoffTime")
    int deleteOldErrors(Long cutoffTime);
    
    // 해결된 에러들 정리
    @Query("DELETE FROM errors WHERE resolved = 1 AND resolved_at < :cutoffTime")
    int deleteResolvedErrors(Long cutoffTime);
    
    // 전체 에러 개수 조회
    @Query("SELECT COUNT(*) FROM errors")
    Single<Integer> getErrorCount();
    
    // 해결되지 않은 에러 개수 조회
    @Query("SELECT COUNT(*) FROM errors WHERE resolved = 0")
    Single<Integer> getUnresolvedErrorCount();
}