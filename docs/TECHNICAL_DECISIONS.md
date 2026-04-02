# 기술 선택 이유 및 주요 문제 해결

## 기술 선택 이유

### React 18 + Vite (vs JSP)

JSP는 검색할 때마다 서버에서 전체 페이지를 다시 렌더링합니다. 이 프로젝트는 검색어 입력 시 결과만 업데이트되는 인터랙티브한 UX가 필요했기 때문에 적합하지 않았습니다.

| 항목 | JSP | React + Spring |
|------|-----|--------|
| 검색 시 | 페이지 전체 교체 (깜빡임) | 결과만 업데이트 (자연스러움) |
| 크롤링 대기 중 | 흰 화면 | 로딩 상태 처리 가능 |
| Spring Boot 역할 | 화면 + API 모두 담당 | API만 담당 (관심사 분리) |
| 향후 확장 | 모바일 앱 추가 시 구조 변경 필요 | API 서버 재활용 가능 |

**추가 선택:**
- **Vite**: Create React App 대비 빌드 속도 3배 이상 빠름
- **TanStack Query (React Query)**: 서버 상태 관리 + 자동 캐싱
- **Tailwind CSS v3**: 유틸리티 기반 스타일링으로 빠른 개발

### Java HttpClient + 무신사 내부 API (vs Jsoup)

무신사 검색 페이지(`musinsa.com/search/goods`)는 JavaScript로 렌더링되므로 Jsoup으로는 빈 HTML만 반환됩니다. 브라우저 DevTools Network 탭에서 무신사가 내부적으로 호출하는 JSON API 엔드포인트를 발견하여 직접 호출합니다.

```
발견한 API: https://api.musinsa.com/api2/dp/v1/plp/goods
파라미터: keyword, gf=M, pageNumber=1, pageSize=50, sortCode=POPULAR, caller=SEARCH
주의: pageNumber는 작동하지 않음 (무신사 API 제한)
```

**Why 이 방식:**
- HTML 파싱 없이 JSON을 직접 파싱하므로 안정적이고 빠름
- 정규식이나 DOM 변경 추적 불필요

---

## 핵심 아키텍처 결정

### 1. 매번 실시간 크롤링 (vs 캐싱)

**선택: 매번 크롤링**

**Why:**
- 프로젝트 목표: "어느 플랫폼에서 얼마에 구매할 수 있는지 정확히 파악"
- 가격 변동, 품절 여부를 즉시 반영해야 함
- 응답 시간: 1~2초 (허용 범위)

**성능 최적화:**
- 크롤링 상품 수 제한: **최대 10개** (무신사는 pageNumber 미작동이므로 limit 설정)
- 프론트엔드 React Query: staleTime 5분 (같은 키워드 재검색 시 네트워크 요청 스킵)

**search_history 용도:**
- 캐싱 ❌
- Phase 2 예정: "자주 검색된 키워드 자동 갱신 스케줄러" 기록용
- keyword별 last_crawled 시간 추적으로 자주 검색되는 키워드 파악

---

### 2. 트랜잭션 격리 문제 해결

**문제:**
`ProductService.search()` 전체에 `@Transactional`을 걸면, 크롤러가 `REQUIRES_NEW`로 커밋한 데이터를 MySQL `REPEATABLE READ` 격리 수준에서 바깥 트랜잭션이 볼 수 없음 → 첫 검색 결과 빈 상태

**해결:**
```java
@Service
public class ProductService {
    // @Transactional 없음 (의도적)
    public List<ProductSearchResponse> search(String query) {
        crawlers.forEach(c -> c.crawl(query));  // REQUIRES_NEW
        return queryFromDb(query);               // 별도 트랜잭션
    }
}
```

크롤러가 REQUIRES_NEW로 커밋한 후, queryFromDb()가 새로운 트랜잭션을 시작하므로 최신 데이터를 읽을 수 있음.

---

### 3. 크롤링 중 부분 실패 격리

**문제:**
상품 10개를 하나의 트랜잭션으로 묶으면 1개 실패 시 나머지 9개도 롤백. 반대로 catch만 하면 products는 저장되고 product_prices는 없는 고아 레코드 발생.

**해결:**
```java
@Transactional(propagation = REQUIRES_NEW)
public void upsert(CrawledProduct cp) {
    // 각 상품을 원자적 단위로 처리
}

// crawl()에서
for (CrawledProduct cp : crawledProducts) {
    try {
        upsert(cp);  // REQUIRES_NEW로 독립 커밋
    } catch (Exception e) {
        log.warn("Failed: {}", e.getMessage());
    }
}
```

1개 실패 ≠ 나머지 9개 롤백. products + product_prices 원자성 보장.

---

### 4. 동시 요청 시 search_history 중복 키 처리

**문제:**
두 사용자가 동시에 "아이더" 검색 → 둘 다 search_history에 없다고 판단 → 동시 INSERT 시도 → `DataIntegrityViolationException`

**해결:**
```java
try {
    searchHistoryRepository.findByKeyword(keyword)
            .ifPresentOrElse(
                sh -> sh.updateCrawledTime(),
                () -> searchHistoryRepository.save(new SearchHistory(keyword))
            );
} catch (DataIntegrityViolationException e) {
    log.debug("Search history already exists");
}
```

INSERT 실패해도 크롤링 데이터는 이미 저장되었으므로 검색 결과에 영향 없음.

---

## 배포 및 모니터링

### Docker 2단계 빌드
- Builder 스테이지: Maven으로 패킹
- Runtime 스테이지: JRE만 포함 (JDK 제외하여 이미지 용량 감소)

### New Relic APM
- Agent 버전: 8.4.0
- 모니터링 항목: 응답 시간, 에러율, 처리량, DB 쿼리 성능, 외부 API 호출
- 배포: Render Docker 환경변수로 라이센스 키 주입

---

## Phase 2 예정

- [ ] 29cm, 쿠팡 등 플랫폼 추가 (PlatformCrawler 구현체 추가)
- [ ] 검색 히스토리 기반 스케줄러 (자주 검색된 키워드 자동 갱신)
- [ ] 가격 변동 그래프
- [ ] 가격 알림 기능
- [ ] 병렬 크롤링 (@Async + CompletableFuture)
