# K-Trader TDD 리팩토링 작업지시서

## 목표
- 중복 코드 제거
- SRP(단일 책임 원칙) 강화
- 네트워크/동시성 안정성 향상
- MVVM 전환 기반 마련

## 작업 원칙
1. Red-Green-Refactor 사이클로 작은 단위 변경
2. UI 제외 로직 우선 테스트 작성
3. 기존 기능 동작 유지(회귀 방지)
4. 단계별 완료 기준 충족 후 다음 단계 진행

## 단계별 실행 계획

### Phase 1 - 안정성/중복 제거 (우선)
- [x] `TradeDataManager` 단위 테스트 추가
- [x] API 네트워크 예외 전파 방어(`Api_Client`)
- [x] 리스트 스냅샷 기반 갱신(`PlacedOrderPage`, `ProcessedOrderPage`, `ListviewAdapter`)
- [x] `OrderManager` API 응답 검증 로직 공통화(`hasValidApiStatus`)
- [ ] `OrderManager` 전 메서드에 공통 응답 검증 적용

완료 기준:
- 기존 주요 크래시(UnknownHost/IndexOutOfBounds/ConcurrentModification) 재현되지 않을 것
- 관련 단위 테스트 통과

### Phase 2 - SRP 강화
- [ ] `MainPage`를 화면 조합/데이터 갱신 책임으로 분리
  - [ ] `CoinInfoController`
  - [ ] `TransactionCardController`
  - [ ] `MainPageCoordinator`
- [ ] `TradeJobService`의 비즈니스 판단 로직을 UseCase로 분리
- [ ] `OrderManager`의 에러 리포팅/파싱 책임 분리

완료 기준:
- `MainPage`, `TradeJobService`의 직접 도메인 로직 감소
- 클래스별 책임이 명확하게 구분

### Phase 3 - MVVM 실동작 전환
- [ ] `DIContainer`의 null 주입 제거
- [ ] UseCase 실제 구현 주입
- [ ] Fragment 내 직접 API/DB 호출 제거 후 ViewModel 경유
- [ ] ViewModel 상태 전이 테스트 추가

완료 기준:
- UI 계층은 상태 표시와 이벤트 전달만 수행
- 도메인 로직은 ViewModel/UseCase에서 처리

### Phase 4 - 성능 최적화
- [ ] `new Thread()` 반복 생성 제거(공용 Executor/Rx 스케줄러 통합)
- [ ] 폴링/갱신 주기 최적화
- [ ] 리스트 갱신 최소화 및 불필요한 rebind 제거

완료 기준:
- 스레드 생성 횟수 감소
- UI 갱신 빈도 최적화

## 이번 변경(진행)
- `OrderManager`의 중복된 API 응답 검증 분기(`null`, `status 타입`, `status != 0000`)를 공통 메서드로 통합
- `cancelOrder`, `addOrder`, `addOrderWithMarketPrice`에 공통 메서드 적용

