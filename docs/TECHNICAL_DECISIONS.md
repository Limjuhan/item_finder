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

### 1. 트랜잭션 격리로 인한 첫 검색 빈 결과

**문제:** `ProductService.search()` 전체에 `@Transactional`을 걸면, 크롤러가 `REQUIRES_NEW`로 커밋한 데이터를 MySQL `REPEATABLE READ` 격리 수준에서 바깥 트랜잭션이 볼 수 없습니다. 첫 검색 결과가 항상 비어있는 현상이 발생했습니다.

**해결: `search()` 메서드의 `@Transactional` 제거**
`search()` 자체는 트랜잭션 범위에서 제외하고, 크롤러가 REQUIRES_NEW로 커밋한 후, 새로 시작된 DB 조회에서 최신 데이터를 읽습니다.

---

### 2. 크롤링 중 부분 실패 시 데이터 불일치

**문제:** 상품 10개를 하나의 트랜잭션으로 묶으면 중간 실패 시 전체 롤백됩니다. 반대로 예외만 catch하면 `product_listing`에는 저장됐는데 일부만 저장되는 불완전한 상태가 될 수 있습니다.

**해결: 상품 1건 단위 독립 트랜잭션 (`REQUIRES_NEW`)**
`upsert()` 메서드에 `@Transactional(propagation = REQUIRES_NEW)`를 적용하여 상품 1건을 원자적 단위로 처리합니다. 한 상품이 실패해도 다른 상품 저장에는 영향이 없습니다.

---

### 3. 동시 요청 시 search_history 중복 키 처리

**문제:** 두 사용자가 동시에 같은 키워드를 처음 검색하면, 둘 다 `search_history`에 해당 키워드가 없다고 판단하고 동시에 INSERT를 시도합니다. `keyword` 컬럼의 UNIQUE 제약으로 하나는 `DataIntegrityViolationException`이 발생합니다.

**해결: 예외 포착 후 무시**
두 번째 INSERT가 실패해도 크롤링 데이터는 이미 저장됐으므로 검색 결과에 영향이 없습니다. `DataIntegrityViolationException`을 catch하여 정상 처리합니다.

---

### 4. 외부 API 요청 실패 시 서비스 중단

**문제:** 플랫폼별 크롤링 API(무신사, 29cm, 쿠팡 등)가 고장 나거나 응답 지연이 발생하면:
- 응답을 무한정 기다려 사용자 경험 악화
- 연쇄적 요청으로 API 서버 부하 증가
- 복구될 가능성 있는 오류도 반복 시도로 악화

**해결: Timeout + Circuit Breaker + Graceful Degradation 전략**

#### 4-1. Timeout 설정 (요청당 최대 대기 시간)

각 크롤링 작업에 타임아웃을 설정하여 무한 대기를 방지합니다:

```java
// SearchController.java
ExecutorService executor = Executors.newFixedThreadPool(4);

for (PlatformCrawler crawler : crawlers) {
    try {
        Future<List<ProductSearchResponse>> future = 
            executor.submit(() -> crawler.crawl(keyword));
        
        // 각 플랫폼별로 설정된 타임아웃 시간 내에 결과 수신
        List<ProductSearchResponse> results = 
            future.get(timeout, TimeUnit.SECONDS);
        
        allResults.addAll(results);
    } catch (TimeoutException e) {
        log.warn("[{}] 타임아웃 초과", crawler.getName());
        failedPlatforms.add(crawler.getName());
    }
}
```

**타임아웃 값 결정 기준:**
- API 응답이 정상일 때 걸리는 평균 시간 측정
- 타임아웃 = (평균 시간 + 여유) 로 설정
- 예시: 무신사 평균 2초 → 타임아웃 10초 설정

#### 4-2. Circuit Breaker 패턴 (연속 실패 감지 및 차단)

같은 API가 연속으로 실패하면 일정 시간 동안 요청 자체를 차단합니다:

```java
@CircuitBreaker(
    failureThreshold = 5,      // 5번 실패 시 Open
    delay = 60000              // 60초 동안 요청 차단
)
public List<ProductSearchResponse> crawlMusinsa(String keyword) {
    return musinsaCrawler.crawl(keyword);
}
```

**Circuit Breaker의 상태 변화:**

1. **Closed (정상 상태):** 모든 요청 통과
2. **Open (차단 상태):** 설정된 횟수 이상 실패 시 발동, 모든 요청 즉시 거부 (요청 안 함)
3. **Half-Open (복구 확인 상태):** 차단 시간 경과 후 1회 요청으로 복구 여부 확인
   - 성공하면 → Closed (정상화)
   - 실패하면 → Open (다시 차단)

**동작 예시:**

```
14:00:00 사용자1 검색 → 무신사 API 호출 → 실패 (1/5)
14:00:01 사용자2 검색 → 무신사 API 호출 → 실패 (2/5)
...
14:00:04 사용자5 검색 → 무신사 API 호출 → 실패 (5/5) → Circuit Open!

14:00:05 ~ 14:01:04 (60초 동안):
사용자6 검색 → 무신사? → "Open 상태이므로 요청 안 함" → 즉시 반환

14:01:05 Circuit Half-Open 상태로 전환:
사용자7 검색 → 무신사 API 호출 (1회만 시도) 
         → 성공! → Circuit Close (정상화)

14:01:06 이후:
사용자8 검색 → 무신사 API 정상 작동
```

**플랫폼별 설정 예시:**

```java
// 무신사 (대규모 쇼핑몰, 신뢰도 높음)
@CircuitBreaker(failureThreshold = 5, delay = 60000)

// 29cm (중소 쇼핑몰, 신뢰도 중간)
@CircuitBreaker(failureThreshold = 3, delay = 30000)

// 신규 API (신뢰도 미지수, 보수적)
@CircuitBreaker(failureThreshold = 3, delay = 30000)
```

#### 4-3. Graceful Degradation (우아한 성능 저하)

하나의 API가 실패해도 다른 플랫폼 데이터는 정상 반환합니다:

```java
List<ProductSearchResponse> allResults = new ArrayList<>();
List<String> unavailablePlatforms = new ArrayList<>();

for (PlatformCrawler crawler : crawlers) {
    try {
        List<ProductSearchResponse> results = crawler.crawl(keyword);
        allResults.addAll(results);
    } catch (Exception e) {
        log.warn("[{}] 크롤링 실패, 계속 진행", crawler.getName());
        unavailablePlatforms.add(crawler.getName());
    }
}

// 사용자에게 반환
response.setResults(allResults);
response.setUnavailablePlatforms(unavailablePlatforms);
```

**사용자에게 표시:**
- ✅ "3개 플랫폼 검색 완료"
- ⚠️ "2개 플랫폼만 검색 완료 (무신사 데이터 없음)"
- ❌ "현재 검색 불가능합니다"

#### 4-4. 설정값 결정 방법

| 요소 | 결정 기준 | 예시 |
|------|---------|------|
| **failureThreshold** | API 회사 규모 + 신뢰도 | 무신사 5회, 29cm 3회 |
| **delay** | API 복구 예상 시간 | 무신사 60초, 29cm 30초 |
| **timeout** | API 평균 응답 시간 + 여유 | 응답 2초 → timeout 10초 |

**모니터링 기반 최적화:**
1. 초기: 보수적으로 설정 (failureThreshold=3, delay=30초)
2. 1-2주 운영하면서 Circuit Open 로그 수집
3. 자주 Open되면 → 설정값 조정
4. 운영 패턴 파악 후 최종 확정

#### 4-5. 장점 요약

- **타임아웃:** 무한 대기 방지, 빠른 실패
- **Circuit Breaker:** 불필요한 요청 차단, API 서버 부하 감소, 자동 복구 확인
- **Graceful Degradation:** 부분 서비스 가능, 사용자 경험 개선
