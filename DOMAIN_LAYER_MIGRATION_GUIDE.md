# K-Trader Domain Layer 마이그레이션 가이드

## 📋 개요

이 문서는 K-Trader 앱의 기존 구조를 Clean Architecture 패턴에 맞는 Domain Layer로 마이그레이션하는 상세한 가이드입니다.

## 🎯 마이그레이션 목표

1. **Clean Architecture 적용**: UI, Domain, Data 레이어 분리
2. **단일 책임 원칙**: 각 클래스의 역할 명확화
3. **의존성 주입**: 느슨한 결합 구현
4. **반응형 프로그래밍**: RxJava와 LiveData 활용
5. **테스트 용이성**: Mock 객체 사용 가능한 구조

## 📊 현재 구조 vs 새로운 구조

### 현재 구조
```
UI Layer (Activities/Fragments)
    ↓ 직접 참조
Business Logic Layer (Services/Managers)
    ↓ 직접 참조
Data Layer (Database/API)
```

### 새로운 구조
```
Presentation Layer (Activities/Fragments/ViewModels)
    ↓ 인터페이스 참조
Domain Layer (Use Cases/Domain Services/Domain Models)
    ↓ 인터페이스 참조
Data Layer (Repository Implementations/API Service/Database)
```

## 🔄 마이그레이션 매핑

### UI 클래스 매핑

| 현재 클래스                | 새로운 ViewModel | 주요 변경사항 |
|-----------------------|------------------|---------------|
| `MainActivity`        | `MainViewModel` | LiveData 기반 UI 업데이트 |
| `MainPage`            | `MainViewModel` | 거래 상태 토글, 코인 정보 표시 |
| `PlacedOrderPage`     | `OrderManagementViewModel` | 대기 중인 주문 관리 |
| `ProcessedOrderPage`  | `OrderManagementViewModel` | 완료된 주문 조회 및 분석 |
| `TransactionItemPage` | `MainViewModel` | 거래 아이템 표시 |
| `TransactionLogPage`  | `OrderManagementViewModel` | 거래 로그 관리 |
| `SettingsActivity`    | `SettingsViewModel` | 거래 설정 관리 |

### Business Logic 클래스 매핑

| 현재 클래스 | 새로운 Domain Layer | 역할 |
|------------|-------------------|------|
| `TradeJobService` | `AutoTradingUseCase` + `TradingExecutionService` | 자동 거래 로직 |
| `OrderManager` | `TradingExecutionService` + `TradeRepository` | 주문 관리 |
| `TradeDataManager` | `ManageTradingDataUseCase` + `TradeRepository` | 거래 데이터 관리 |
| `TradeData` | `Trade` (Domain Model) | 거래 데이터 모델 |

## 📁 생성된 파일 구조

```
app/src/main/java/com/example/k_trader/
├── domain/
│   ├── model/
│   │   └── DomainModels.java              # 도메인 모델들
│   ├── repository/
│   │   └── RepositoryInterfaces.java     # Repository 인터페이스들
│   ├── usecase/
│   │   └── UseCases.java                 # Use Case 클래스들
│   └── service/
│       └── DomainServices.java           # 도메인 서비스들
├── presentation/
│   └── viewmodel/
│       └── ViewModels.java               # ViewModel 클래스들
└── di/
    └── DIContainer.java                  # 의존성 주입 컨테이너
```

## 🚀 단계별 마이그레이션 계획

### Phase 1: 의존성 추가 및 기본 설정

#### 1.1 Android Architecture Components 추가
```gradle
// app/build.gradle
dependencies {
    // Android Architecture Components
    implementation 'android.arch.lifecycle:extensions:1.1.1'
    implementation 'android.arch.lifecycle:viewmodel:1.1.1'
    implementation 'android.arch.lifecycle:livedata:1.1.1'
    
    // RxJava
    implementation 'io.reactivex.rxjava2:rxjava:2.2.19'
    implementation 'io.reactivex.rxjava2:rxandroid:2.1.1'
    
    // Room Database (이미 추가됨)
    implementation 'android.arch.persistence.room:runtime:1.1.1'
    implementation 'android.arch.persistence.room:rxjava2:1.1.1'
}
```

#### 1.2 Application 클래스 수정
```java
// KTraderApplication.java
public class KTraderApplication extends Application {
    private DIContainer diContainer;
    
    @Override
    public void onCreate() {
        super.onCreate();
        diContainer = DIContainer.getInstance(this);
    }
    
    public DIContainer getDIContainer() {
        return diContainer;
    }
}
```

### Phase 2: Repository 구현체 개발

#### 2.1 기존 Repository 구현체 수정
기존 `RepositoryImplementations.java`를 새로운 인터페이스에 맞게 수정:

```java
// RepositoryImplementations.java 수정 예시
public class TradeRepositoryImpl implements TradeRepository {
    private final BithumbApiService apiService;
    private final BithumbOrderDao orderDao;
    
    public TradeRepositoryImpl(BithumbApiService apiService, BithumbOrderDao orderDao) {
        this.apiService = apiService;
        this.orderDao = orderDao;
    }
    
    @Override
    public Single<List<Trade>> getAllTrades() {
        return apiService.getOrders()
            .map(this::convertToDomainModels)
            .flatMap(this::saveToDatabase);
    }
    
    private List<Trade> convertToDomainModels(OrdersResponse response) {
        // API 응답을 도메인 모델로 변환
    }
}
```

### Phase 3: UI 마이그레이션

#### 3.1 MainActivity 마이그레이션
```java
// MainActivity.java 수정 예시
public class MainActivity extends AppCompatActivity {
    private MainViewModel mainViewModel;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // ViewModel 초기화
        DIContainer diContainer = DIContainer.getInstance();
        mainViewModel = diContainer.getMainViewModel();
        
        // LiveData 관찰
        observeViewModel();
    }
    
    private void observeViewModel() {
        mainViewModel.getCurrentPrice().observe(this, priceInfo -> {
            // UI 업데이트
        });
        
        mainViewModel.getIsAutoTradingEnabled().observe(this, enabled -> {
            // 자동 거래 상태 UI 업데이트
        });
    }
}
```

#### 3.2 MainPage 마이그레이션
```java
// MainPage.java 수정 예시
public class MainPage extends Fragment {
    private MainViewModel mainViewModel;
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.main_page, container, false);
        
        // ViewModel 초기화
        DIContainer diContainer = DIContainer.getInstance();
        mainViewModel = diContainer.getMainViewModel();
        
        // UI 초기화
        initializeUI(view);
        
        return view;
    }
    
    private void initializeUI(View view) {
        // FloatingActionButton 설정
        FloatingActionButton fab = view.findViewById(R.id.fabTradingToggle);
        fab.setOnClickListener(v -> {
            mainViewModel.toggleAutoTrading();
        });
        
        // LiveData 관찰
        mainViewModel.getCurrentPrice().observe(this, priceInfo -> {
            updatePriceDisplay(priceInfo);
        });
    }
}
```

### Phase 4: 비즈니스 로직 마이그레이션

#### 4.1 TradeJobService 마이그레이션
기존 `TradeJobService`의 핵심 로직을 새로운 구조로 분리:

```java
// 기존 TradeJobService의 tradeBusinessLogic() 메서드를
// AutoTradingUseCase.executeAutoTrading()으로 마이그레이션

// TradeJobService.java 수정
public class TradeJobService extends JobService {
    private AutoTradingUseCase autoTradingUseCase;
    
    @Override
    public boolean onStartJob(JobParameters params) {
        DIContainer diContainer = DIContainer.getInstance();
        autoTradingUseCase = diContainer.getAutoTradingUseCase();
        
        autoTradingUseCase.executeAutoTrading()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                () -> Log.d("KTrader", "[TradeJobService] Auto trading completed"),
                error -> Log.e("KTrader", "[TradeJobService] Auto trading failed", error)
            );
        
        return true;
    }
}
```

#### 4.2 OrderManager 마이그레이션
기존 `OrderManager`의 기능을 `TradingExecutionService`로 마이그레이션:

```java
// 기존 OrderManager의 메서드들을
// TradingExecutionService의 메서드들로 대체

// PlacedOrderPage.java 수정 예시
public class PlacedOrderPage extends Fragment {
    private OrderManagementViewModel orderManagementViewModel;
    
    private void setupBuyButton() {
        btnBuyWithMarketPrice.setOnClickListener(v -> {
            orderManagementViewModel.executeMarketBuyOrder("BTC", 0.001);
        });
    }
}
```

### Phase 5: 데이터 모델 마이그레이션

#### 5.1 TradeData 마이그레이션
기존 `TradeData`를 새로운 `Trade` 도메인 모델로 마이그레이션:

```java
// 기존 TradeData 사용을 새로운 Trade 도메인 모델로 변경
// TradeDataManager.java 수정 예시
public class TradeDataManager {
    private TradeRepository tradeRepository;
    
    public TradeDataManager() {
        DIContainer diContainer = DIContainer.getInstance();
        tradeRepository = diContainer.getTradeRepository();
    }
    
    public Single<List<Trade>> getAllTrades() {
        return tradeRepository.getAllTrades();
    }
    
    public Completable addTrade(Trade trade) {
        return tradeRepository.saveTrade(trade);
    }
}
```

## 🔧 마이그레이션 체크리스트

### Phase 1: 기본 설정
- [ ] Android Architecture Components 의존성 추가
- [ ] RxJava 의존성 추가
- [ ] Application 클래스에 DIContainer 초기화
- [ ] 기본 프로젝트 구조 생성

### Phase 2: Repository 구현
- [ ] Repository 인터페이스 구현체 개발
- [ ] API Service와 Database 연동
- [ ] 에러 처리 및 로깅 추가
- [ ] 단위 테스트 작성

### Phase 3: UI 마이그레이션
- [ ] MainActivity ViewModel 연동
- [ ] MainPage ViewModel 연동
- [ ] PlacedOrderPage ViewModel 연동
- [ ] ProcessedOrderPage ViewModel 연동
- [ ] SettingsActivity ViewModel 연동
- [ ] Fragment들 ViewModel 연동

### Phase 4: 비즈니스 로직 마이그레이션
- [ ] TradeJobService 로직 분리
- [ ] OrderManager 기능 마이그레이션
- [ ] TradeDataManager 기능 마이그레이션
- [ ] 기존 서비스들 Use Case로 변환

### Phase 5: 데이터 모델 마이그레이션
- [ ] TradeData → Trade 도메인 모델 변환
- [ ] 기존 데이터 클래스들 도메인 모델로 변환
- [ ] 데이터 변환 로직 구현
- [ ] 기존 데이터 마이그레이션

### Phase 6: 테스트 및 검증
- [ ] 단위 테스트 작성
- [ ] 통합 테스트 작성
- [ ] UI 테스트 작성
- [ ] 성능 테스트
- [ ] 메모리 누수 검사

## ⚠️ 주의사항

### 1. 점진적 마이그레이션
- 한 번에 모든 것을 변경하지 말고 단계적으로 진행
- 각 단계마다 테스트를 통해 정상 동작 확인
- 롤백 계획 준비

### 2. 데이터 호환성
- 기존 데이터베이스 스키마 호환성 유지
- 데이터 마이그레이션 스크립트 준비
- 백업 및 복구 계획

### 3. 성능 고려사항
- RxJava 스트림 최적화
- 메모리 사용량 모니터링
- 배터리 사용량 최적화

### 4. 에러 처리
- 기존 에러 처리 로직 유지
- 새로운 에러 처리 메커니즘 추가
- 사용자 친화적 에러 메시지

## 📈 마이그레이션 후 기대 효과

### 1. 코드 품질 향상
- **가독성**: 명확한 레이어 분리로 코드 이해 용이
- **유지보수성**: 단일 책임 원칙으로 수정 범위 최소화
- **확장성**: 새로운 기능 추가 시 기존 코드 영향 최소화

### 2. 테스트 용이성
- **단위 테스트**: 각 레이어별 독립적 테스트 가능
- **통합 테스트**: Mock 객체를 활용한 테스트 가능
- **UI 테스트**: ViewModel 기반 UI 테스트 가능

### 3. 성능 최적화
- **메모리 효율성**: 불필요한 객체 생성 최소화
- **반응형 프로그래밍**: 효율적인 비동기 처리
- **데이터 캐싱**: Repository 레벨에서 데이터 캐싱

### 4. 개발 생산성
- **병렬 개발**: 레이어별 독립적 개발 가능
- **코드 재사용**: 도메인 로직의 재사용성 향상
- **디버깅**: 명확한 책임 분리로 디버깅 용이

## 🎯 다음 단계

1. **의존성 추가**: 필요한 라이브러리들을 `build.gradle`에 추가
2. **Repository 구현체 개발**: 기존 API 서비스와 연동
3. **UI 마이그레이션**: Fragment/Activity들을 새로운 ViewModel과 연결
4. **비즈니스 로직 마이그레이션**: 기존 서비스들을 새로운 Use Case로 변환
5. **테스트 작성**: 단위 테스트 및 통합 테스트 구현
6. **성능 최적화**: 메모리 사용량 및 배터리 사용량 최적화

이 마이그레이션을 통해 K-Trader 앱은 더욱 안정적이고 확장 가능하며 유지보수가 용이한 구조로 발전할 수 있습니다.

