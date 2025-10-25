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
 * API 호출 결과 엔티티
 * API 호출 성공/실패 결과를 저장
 */
@Entity(
    tableName = "api_call_results",
    indices = {
        @Index(value = {"timestamp"}),
        @Index(value = {"endpoint", "timestamp"}),
        @Index(value = {"success"})
    }
)
public class ApiCallResultEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    @ColumnInfo(name = "endpoint")
    public String endpoint; // API 엔드포인트
    
    @ColumnInfo(name = "method")
    public String method; // HTTP 메서드 (GET, POST)
    
    @ColumnInfo(name = "status_code")
    public int statusCode; // HTTP 상태 코드
    
    @ColumnInfo(name = "success")
    public boolean success; // 성공 여부
    
    @ColumnInfo(name = "call_time")
    public long callTime; // 호출 시간
    
    @ColumnInfo(name = "api_endpoint")
    public String apiEndpoint; // API 엔드포인트
    
    @ColumnInfo(name = "error_code")
    public String errorCode; // 에러 코드
    
    @ColumnInfo(name = "server_error_message")
    public String serverErrorMessage; // 서버 에러 메시지
    
    @ColumnInfo(name = "transaction_data")
    public String transactionData; // 트랜잭션 데이터
    
    @ColumnInfo(name = "timestamp")
    @TypeConverters(DateConverter.class)
    public Date timestamp;
    
    @ColumnInfo(name = "created_at")
    @TypeConverters(DateConverter.class)
    public Date createdAt;
    
    // 기본 생성자
    public ApiCallResultEntity() {
        this.createdAt = new Date();
    }
    
    // 생성자
    @Ignore
    public ApiCallResultEntity(String endpoint, String method, int statusCode, 
                              boolean success, long callTime, String apiEndpoint,
                              String errorCode, String serverErrorMessage, String transactionData, Date timestamp) {
        this.endpoint = endpoint;
        this.method = method;
        this.statusCode = statusCode;
        this.success = success;
        this.callTime = callTime;
        this.apiEndpoint = apiEndpoint;
        this.errorCode = errorCode;
        this.serverErrorMessage = serverErrorMessage;
        this.transactionData = transactionData;
        this.timestamp = timestamp;
        this.createdAt = new Date();
    }
    
    // Getter/Setter 메서드들 (기존 코드 호환성을 위해)
    public boolean hasError() {
        return !success;
    }
    
    public String getErrorMessage() {
        return serverErrorMessage;
    }
    
    public String getApiEndpoint() {
        return apiEndpoint;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public String getServerErrorMessage() {
        return serverErrorMessage;
    }
    
    public String getResponseData() {
        return transactionData;
    }
    
    public long getCallTime() {
        return callTime;
    }
    
    public boolean isSuccessfulCall() {
        return success;
    }
    
    public String getTransactionData() {
        return transactionData;
    }
    
    public void setCallTime(long callTime) {
        this.callTime = callTime;
    }
    
    public void setApiEndpoint(String apiEndpoint) {
        this.apiEndpoint = apiEndpoint;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public void setResponseData(String responseData) {
        this.transactionData = responseData;
    }
    
    public void setTransactionData(String transactionData) {
        this.transactionData = transactionData;
    }
    
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.serverErrorMessage = errorMessage;
    }
    
    public void setServerErrorMessage(String serverErrorMessage) {
        this.serverErrorMessage = serverErrorMessage;
    }
}