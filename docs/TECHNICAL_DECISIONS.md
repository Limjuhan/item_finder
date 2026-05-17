# 기술 선택 이유 및 주요 문제 해결

## 기술 선택 이유

### React + 별도 프론트 서버 (vs JSP + AJAX)

JSP + AJAX로도 비동기 처리와 로딩 상태 표시는 기술적으로 가능합니다. React를 선택한 진짜 이유는 다음과 같습니다:

**이유:**
- **API 기반 아키텍처로 백엔드와 프론트엔드 완전 분리** — 서버가 HTML을 렌더링하지 않고 JSON API만 제공하므로 모바일, 웹, 다른 클라이언트 추가 시 백엔드 코드 재사용 가능
- **React 컴포넌트 기반으로 유지보수와 확장성 향상** — 상태 관리가 명확하고 (useState, Context), 컴포넌트를 독립적으로 재사용 가능
- **향후 모바일 앱이나 새로운 클라이언트 추가 시 백엔드를 재개발할 필요 없음** — API는 그대로 두고 프론트엔드만 변경

### Java HttpClient + 무신사 내부 API (vs Jsoup)

무신사 검색 페이지(`musinsa.com/search/goods`)는 JavaScript로 렌더링되므로 Jsoup으로는 빈 HTML만 반환됩니다. 브라우저 DevTools Network 탭에서 무신사가 내부적으로 호출하는 JSON API 엔드포인트를 발견하여 직접 호출합니다.

```
발견한 API: https://api.musinsa.com/api2/dp/v1/plp/goods
파라미터: keyword, gf, pageNumber, pageSize, sortCode, caller
```

**최대 10개 상품만 크롤링:** pageNumber 파라미터가 작동하지 않아 API 응답의 처음 10개 상품만 저장합니다. (성능 최적화 목적)

HTML 파싱 없이 JSON을 직접 파싱하므로 안정적이고 빠릅니다.

---

## 주요 기술적 문제와 해결

### Phase 1 (DB 저장 기반 아키텍처) - 현재 적용 안 됨

다음 3가지 문제는 Phase 1에서 DB에 크롤링 결과를 저장하던 시기에 경험한 문제들입니다. Phase 2 아키텍처(메모리 캐싱 + SSE 스트리밍)로 전환되면서 이들 문제는 더 이상 발생하지 않습니다.

#### 1. 트랜잭션 격리로 인한 첫 검색 빈 결과 (해결됨)

**당시 문제:** `ProductService.search()` 전체에 `@Transactional`을 걸면, 크롤러가 `REQUIRES_NEW`로 커밋한 데이터를 MySQL `REPEATABLE READ` 격리 수준에서 바깥 트랜잭션이 볼 수 없습니다. 첫 검색 결과가 항상 비어있는 현상이 발생했습니다.

**당시 해결:** `search()` 메서드의 `@Transactional` 제거로 문제 해결했습니다.

**현재 상태:** ProductService 자체가 삭제되고 메모리 캐시 기반으로 전환되어 이 문제는 더 이상 발생하지 않습니다.

---

#### 2. 크롤링 중 부분 실패 시 데이터 불일치 (해결됨)

**당시 문제:** 상품 10개를 하나의 트랜잭션으로 묶으면 중간 실패 시 전체 롤백됩니다. 반대로 예외만 catch하면 일부만 저장되는 불완전한 상태가 될 수 있습니다.

**당시 해결:** `upsert()` 메서드에 `@Transactional(propagation = REQUIRES_NEW)`를 적용하여 상품 1건 단위 원자성을 보장했습니다.

**현재 상태:** DB 저장 자체가 폐기되었으므로 이 문제는 발생하지 않습니다.

---

#### 3. 동시 요청 시 search_history 중복 키 처리 (해결됨)

**당시 문제:** 두 사용자가 동시에 같은 키워드를 처음 검색하면, 둘 다 `search_history`에 해당 키워드가 없다고 판단하고 동시에 INSERT를 시도합니다. `keyword` 컬럼의 UNIQUE 제약으로 하나는 `DataIntegrityViolationException`이 발생합니다.

**당시 해결:** `DataIntegrityViolationException`을 catch하여 정상 처리했습니다.

**현재 상태:** `search_history` 구조를 로그 방식으로 변경 (검색마다 새 row insert, keyword unique 제약 제거)하면서 이 문제는 더 이상 발생하지 않습니다. upsert 로직 자체가 제거되었습니다.

---

### 현재 아키텍처의 문제와 해결

### 4. 외부 API 요청 실패 시 서비스 중단

#### 4-1. 문제 상황

ItemFinder는 실시간 가격 정보를 제공하기 위해 무신사, 29cm, 쿠팡 등 여러 플랫폼의 API를 호출합니다. 이들 API가 고장 나거나 응답이 지연되는 경우:

1. 응답을 무한정 기다리게 되어 사용자는 30초 이상 대기
2. 연쇄적인 재요청으로 장애 API 서버에 추가 부하
3. 일시적 오류도 반복 시도로 문제 악화
4. 이 기간 동안 다른 사용자들도 같은 문제 경험
5. 최악의 경우 전체 검색 기능 마비

#### 4-2. 대안 검토

대안1: 무한정 기다리기 (현상 유지)
- 장점: 구현 간단
- 단점: 사용자 경험 악화, 서버 부하 증가

대안2: 과거 캐시 데이터 반환
- 장점: 데이터를 어라도 반환 가능
- 단점: 사용자는 "항상 최신 정확한 데이터" 받는다고 기대하는데 1시간 전 데이터 제공, ItemFinder의 핵심 가치 훼손

대안3: Timeout + Circuit Breaker + Graceful Degradation
- 장점: 부분 서비스 가능, 불필요한 요청 차단, 자동 복구
- 단점: 구현 복잡도 증가

#### 4-3. 최종 결정: 대안3 채택 (Timeout + Circuit Breaker + Graceful Degradation)

세 가지 전략을 조합하여 외부 API 실패에 대응합니다.

##### 타임아웃 (Timeout)

각 플랫폼 크롤러의 `HttpRequest`에 `.timeout(Duration.ofSeconds(5))`를 설정합니다. 5초 내에 응답이 없으면 `HttpTimeoutException`이 발생하고, Circuit Breaker의 실패로 기록됩니다.

**5초로 결정한 이유:**
- 무신사/29cm 내부 API의 정상 응답 시간은 1~3초
- 5초는 정상 응답 시간의 약 2배로, 일시적인 지연(네트워크 불안정 등)을 수용하면서도 사실상 죽은 API를 빠르게 감지
- 10초는 UX를 과도하게 희생 — SSE 스트리밍 구조에서도 첫 플랫폼 응답까지 사용자가 기다리는 시간이 길어짐
- Circuit Breaker와 조합하면 3회 연속 타임아웃 이후 30초간 해당 플랫폼 즉시 스킵 → 반복 대기 없음

프로덕션 모니터링 후 조정:
- 타임아웃 발생 빈도 로그 수집 후 1-2주 운영 기준으로 재조정
- 자주 타임아웃되면 증가 (5초 → 8초), 거의 없으면 유지

##### Circuit Breaker 패턴

같은 API가 연속으로 실패하면 일정 기간 동안 요청을 차단하여 불필요한 재시도를 방지합니다.

**커스텀 구현을 선택한 이유 (Resilience4j 대신):**
- Resilience4j는 Spring Cloud 의존성을 포함하며 슬라이딩 윈도우, Bulkhead, Rate Limiter 등 우리가 사용하지 않는 기능이 다수 포함
- 우리 요구사항은 단순: 실패 횟수 카운트 + 시간 기반 복구 → `AtomicInteger` + `AtomicLong` 두 개로 60~80줄 구현 가능
- 커스텀 구현은 코드 자체가 문서이며, 동작을 완전히 이해하고 통제 가능
- 필요 시 Resilience4j로 이전은 언제든 가능

**구현 위치:** `CircuitBreaker.java` — 각 크롤러가 인스턴스를 독립적으로 보유 (Musinsa 장애가 29cm에 영향 없음)

상태 변화:
- CLOSED (정상): 실패 횟수 < threshold → 모든 요청 통과
- OPEN (차단): 실패 횟수 ≥ threshold, 마지막 실패로부터 delay 미경과 → 즉시 거부
- HALF_OPEN (복구 확인): delay 경과 후 → 1회 요청 허용, 성공 시 CLOSED 복귀

설정값 (모든 플랫폼 동일):
- failureThreshold: 3회
- delay: 30초
- 근거: 플랫폼별로 신뢰도를 다르게 설정할 근거가 현재 없음. 단순하고 일관된 설정이 관리에 유리하며, 운영 데이터 수집 후 필요하면 플랫폼별로 분리 가능

##### Graceful Degradation (우아한 성능 저하)

하나 이상의 API가 실패해도 다른 플랫폼의 데이터는 정상 반환합니다. 실패한 플랫폼 목록은 SSE `done` 이벤트에 포함되어 프론트엔드에서 사용자에게 표시합니다.

동작:
- 무신사 실패 → 29cm 결과는 정상 반환 + "무신사 검색에 실패했습니다" 경고 표시
- 29cm 실패 → 무신사 결과는 정상 반환 + "29cm 검색에 실패했습니다" 경고 표시
- 모든 API 실패 → 결과 없음 + 전체 플랫폼 실패 경고 표시

SSE 프로토콜:
```
event: done
data: {"failedPlatforms": ["musinsa"]}  // 실패 시
data: {"failedPlatforms": []}           // 전체 성공 시
```

#### 4-4. 기대 효과

- Timeout: 무한 대기 방지, 5초 내 빠른 실패 감지
- Circuit Breaker: 반복 실패 API에 대한 불필요한 요청 차단, 30초 후 자동 복구 시도
- Graceful Degradation: 부분 장애 시에도 나머지 플랫폼 결과 제공, 사용자에게 투명한 상태 표시

#### 4-5. 프로덕션 모니터링 기준

초기 설정값(timeout 5초, failureThreshold 3회, delay 30초)은 개발 환경 기반 추정값입니다.
프로덕션 배포 후 아래 로그를 기준으로 1-2주 내 재조정합니다:

- `[CircuitBreaker][플랫폼] Failure recorded` 빈도
- `[CircuitBreaker][플랫폼] Reset to CLOSED` 빈도 (복구 확인)
- 각 플랫폼 crawl 완료 로그의 응답 시간 분포
