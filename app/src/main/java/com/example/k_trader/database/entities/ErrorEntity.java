package com.example.k_trader.database.entities;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.PrimaryKey;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.TypeConverters;
import com.example.k_trader.database.converters.DateConverter;
import java.util.Date;

/**
 * 에러 정보 엔티티
 * 애플리케이션 에러 정보를 저장
 */
@Entity(
    tableName = "errors",
    indices = {
        @Index(value = {"timestamp"}),
        @Index(value = {"error_type", "timestamp"}),
        @Index(value = {"resolved"})
    }
)
public class ErrorEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    @ColumnInfo(name = "error_type")
    public String errorType; // 에러 타입
    
    @ColumnInfo(name = "error_message")
    public String errorMessage; // 에러 메시지
    
    @ColumnInfo(name = "transaction_context")
    public String transactionContext; // 트랜잭션 컨텍스트
    
    @ColumnInfo(name = "exception_stack_trace")
    public String exceptionStackTrace; // 예외 스택 트레이스
    
    @ColumnInfo(name = "api_error_details")
    public String apiErrorDetails; // API 에러 상세 정보
    
    @ColumnInfo(name = "resolved")
    public boolean resolved; // 해결 여부
    
    @ColumnInfo(name = "resolution_note")
    public String resolutionNote; // 해결 노트
    
    @ColumnInfo(name = "resolved_at")
    @TypeConverters(DateConverter.class)
    public Date resolvedAt; // 해결 시간
    
    @ColumnInfo(name = "timestamp")
    @TypeConverters(DateConverter.class)
    public Date timestamp;
    
    @ColumnInfo(name = "created_at")
    @TypeConverters(DateConverter.class)
    public Date createdAt;
    
    // 기본 생성자
    public ErrorEntity() {
        this.createdAt = new Date();
        this.resolved = false;
    }
    
    // 생성자
    @Ignore
    public ErrorEntity(String errorType, String errorMessage, String transactionContext,
                      String exceptionStackTrace, String apiErrorDetails, Date timestamp) {
        this.errorType = errorType;
        this.errorMessage = errorMessage;
        this.transactionContext = transactionContext;
        this.exceptionStackTrace = exceptionStackTrace;
        this.apiErrorDetails = apiErrorDetails;
        this.timestamp = timestamp;
        this.createdAt = new Date();
        this.resolved = false;
    }
}