# 기술 선택 이유 및 주요 문제 해결

## 기술 선택 이유

### React + 별도 프론트 서버 (vs JSP + AJAX)

JSP + AJAX로도 비동기 처리와 로딩 상태 표시는 기술적으로 가능합니다. React를 선택한 진짜 이유는 다음과 같습니다:

**진짜 이유:**
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
