# 기술 선택 이유 및 주요 문제 해결

## 기술 선택 이유

### React + 별도 프론트 서버 (vs JSP + AJAX)

JSP + AJAX로도 비동기 처리와 로딩 상태 표시는 가능합니다. 하지만 React를 선택한 이유는 다음과 같습니다.

| 항목 | JSP + AJAX | React + Spring |
|------|-----------|--------|
| **HTML 생성 위치** | 서버 (매 요청마다 HTML 생성) | 클라이언트 (JS로 동적 생성) |
| **코드 관심사** | HTML/CSS/JS 혼재 | 분리된 컴포넌트 구조 |
| **상태 관리** | 수동으로 DOM 조작 | useState, Context 등 명확한 상태 관리 |
| **재사용성** | JSP 문법으로만 재사용 | React 컴포넌트는 독립적 재사용 |
| **개발 경험** | 서버/프론트 혼합 개발 | 프론트 자유도가 높음 |
| **확장성** | 웹 중심 (앱 추가 시 별도 개발) | API 기반 (모바일, 웹 등 다양한 클라이언트) |

**진짜 이유:** 
- API 기반 아키텍처로 **백엔드와 프론트엔드 완전 분리**
- React 컴포넌트 기반으로 **유지보수와 확장성 향상**
- 향후 모바일 앱, 다른 클라이언트 추가 시 **백엔드 재사용 가능**

### Java HttpClient + 무신사 내부 API (vs Jsoup)

무신사 검색 페이지(`musinsa.com/search/goods`)는 JavaScript로 렌더링되므로 Jsoup으로는 빈 HTML만 반환됩니다. 브라우저 DevTools Network 탭에서 무신사가 내부적으로 호출하는 JSON API 엔드포인트를 발견하여 직접 호출합니다.

```
발견한 API: https://api.musinsa.com/api2/dp/v1/plp/goods
파라미터: keyword, gf, pageNumber, pageSize, sortCode, caller
```

HTML 파싱 없이 JSON을 직접 파싱하므로 안정적이고 빠릅니다.

---

## 주요 기술적 문제와 해결

### 1. 검색할 때마다 크롤링하면 느리다

**문제:** 사용자가 "아디다스"를 검색할 때마다 무신사 API를 호출하면 매번 1~2초의 지연이 발생합니다.

**해결: 키워드 캐싱 (search_history)**
- 처음 검색 시에만 크롤링 → DB 저장
- 이후 6시간 이내 동일 키워드는 DB에서 즉시 반환
- 6시간 경과 시 재크롤링하여 가격 최신화

```
첫 번째 "아디다스" 검색  → 무신사 API 호출 → 2초
두 번째 "아디다스" 검색  → DB 조회         → 50ms
```

---

### 2. 트랜잭션 격리로 인한 첫 검색 빈 결과

**문제:** `ProductService.search()` 전체에 `@Transactional`을 걸면, 크롤러가 `REQUIRES_NEW`로 커밋한 데이터를 MySQL `REPEATABLE READ` 격리 수준으로 인해 바깥 트랜잭션에서 볼 수 없습니다. 첫 검색 결과가 항상 비어있는 현상이 발생했습니다.

**해결: `search()` 메서드의 `@Transactional` 제거**
`search()` 자체는 트랜잭션 범위에서 제외하고, 하위 호출(크롤러, 레포지토리)이 각자의 트랜잭션을 관리하게 했습니다. 크롤링 커밋 후 새로 시작된 DB 조회에서 최신 데이터를 읽습니다.

---

### 3. upsert 중 부분 실패 시 데이터 불일치

**문제:** 상품 50개를 하나의 트랜잭션으로 묶으면 중간 실패 시 전체 롤백됩니다. 반대로 예외만 catch하면 `products`에는 저장됐는데 `product_prices`에는 없는 고아 레코드가 생길 수 있습니다.

**해결: 상품 1건 단위 독립 트랜잭션 (`REQUIRES_NEW`)**
`upsert()` 메서드에 `@Transactional(propagation = REQUIRES_NEW)`를 적용하여 상품 1건을 원자적 단위로 처리합니다. 한 상품이 실패해도 다른 49개 저장에는 영향이 없습니다.

---

### 4. 동시 요청 시 search_history 중복 키 오류

**문제:** 두 사용자가 동시에 같은 키워드를 처음 검색하면, 둘 다 `search_history`에 해당 키워드가 없다고 판단하고 동시에 INSERT를 시도합니다. `keyword` 컬럼의 UNIQUE 제약으로 하나는 `DataIntegrityViolationException`이 발생합니다.

**해결: 예외 포착 후 무시**
두 번째 INSERT가 실패해도 크롤링 데이터는 이미 저장됐으므로 검색 결과에 영향이 없습니다. `DataIntegrityViolationException`을 catch하여 정상 처리합니다.
