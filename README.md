# ItemFinder — 패션 가격 비교 웹앱

무신사 등 패션 e-커머스 플랫폼의 상품존재 유무확인 및 가격을 검색하고 확인하는 웹앱입니다.

---

## 시스템 아키텍처

```
┌─────────────────────────────────────┐
│         React Frontend              │
│  localhost:5173                     │
│  - 검색창                           │
│  - 상품 카드 리스트                  │
└──────────────┬──────────────────────┘
               │ REST API (Vite Proxy)
               ▼
┌─────────────────────────────────────┐
│       Spring Boot Backend           │
│  localhost:8080                     │
│  - 검색 API                         │
│  - 캐시 만료 체크                    │
│  - 크롤링 트리거                     │
└──────────┬──────────────────────────┘
           │                │
           ▼                ▼
    ┌──────────┐    ┌──────────────────┐
    │  MySQL   │    │  무신사 내부 API  │
    │  DB      │    │  (크롤링 대상)    │
    └──────────┘    └──────────────────┘
```

---

## 검색 흐름

```
사용자가 키워드 입력
        │
        ▼
search_history에 키워드 있나?
        │
   ┌────┴────┐
  없음      있음
   │         │
   │    last_crawled가 6시간 초과?
   │         │
   │    ┌────┴────┐
   │   초과     이내
   │    │         │
   ▼    ▼         ▼
무신사 API 호출   DB에서 바로 조회
(실시간 크롤링)        │
   │                   │
   ▼                   │
MySQL에 저장           │
   │                   │
   └─────────┬─────────┘
             ▼
       결과 반환 (최저가 정렬)
```

---

## DB 설계

```sql
-- 상품 마스터
CREATE TABLE products (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_name  VARCHAR(255) NOT NULL,
  product_code  VARCHAR(100) UNIQUE,       -- 무신사 goodsNo
  brand         VARCHAR(100),
  image_url     TEXT,
  created_at    TIMESTAMP,
  updated_at    TIMESTAMP
);

-- 플랫폼별 가격 (플랫폼 추가 시 row 추가)
CREATE TABLE product_prices (
  id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id          BIGINT NOT NULL REFERENCES products(id),
  platform            VARCHAR(50) NOT NULL,  -- 'musinsa', '29cm', 'coupang' 등
  platform_product_id VARCHAR(255),
  price               INT NOT NULL,
  original_price      INT,
  discount_rate       INT,
  in_stock            BOOLEAN,
  url                 TEXT,
  last_updated        TIMESTAMP,
  UNIQUE KEY (product_id, platform)
);

-- 검색 키워드 캐시 관리
CREATE TABLE search_history (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  keyword      VARCHAR(255) NOT NULL UNIQUE,
  last_crawled TIMESTAMP NOT NULL
);
```

**설계 포인트:**
- `product_prices` 테이블을 분리하여 향후 29cm, 쿠팡 등 플랫폼 추가 시 `products` 테이블 변경 없이 row만 추가
- `search_history`로 키워드 단위 캐시 만료를 관리 (상품 단위가 아닌 키워드 단위)

---

## 주요 기술적 문제와 해결

### 1. 검색할 때마다 크롤링하면 느리다

**문제**
사용자가 "아디다스"를 검색할 때마다 무신사 API를 호출하면 매번 1~2초의 지연이 발생합니다.

**해결: 키워드 캐싱 (search_history)**
- 처음 검색 시에만 크롤링 → DB 저장
- 이후 6시간 이내 동일 키워드는 DB에서 즉시 반환
- 6시간 경과 시 재크롤링하여 가격 최신화

```
첫 번째 "아디다스" 검색  → 무신사 API 호출 → 2초
두 번째 "아디다스" 검색  → DB 조회         → 50ms
```

---

### 2. Jsoup으로 무신사를 크롤링할 수 없다

**문제**
무신사 검색 페이지(`musinsa.com/search/goods`)는 JavaScript로 렌더링되기 때문에 Jsoup(HTML 파서)으로는 빈 HTML만 받아옵니다.

**해결: 무신사 내부 API 직접 호출**
브라우저 DevTools Network 탭으로 무신사가 내부적으로 호출하는 API 엔드포인트를 찾았습니다.

```
발견한 API: https://api.musinsa.com/api2/dp/v1/plp/goods
파라미터: keyword, gf, pageNumber, pageSize, sortCode, caller
```

HTML 파싱 없이 JSON 응답을 직접 파싱하므로 안정적이고 빠릅니다.

---

### 3. 트랜잭션 격리로 인한 첫 검색 빈 결과

**문제**
`ProductService.search()` 전체에 `@Transactional`을 걸면, 내부에서 크롤러가 새 트랜잭션(`REQUIRES_NEW`)으로 데이터를 커밋해도 바깥 트랜잭션은 MySQL의 `REPEATABLE READ` 격리 수준으로 인해 새 데이터를 볼 수 없습니다. 첫 검색 결과가 항상 비어있는 현상이 발생했습니다.

**해결: search() 메서드의 @Transactional 제거**
`search()` 자체는 트랜잭션 범위에서 제외하고, 하위 호출(크롤러, 레포지토리)이 각자의 트랜잭션을 관리하게 했습니다. 크롤링 커밋 후 새로 시작된 DB 조회에서 최신 데이터를 읽습니다.

---

### 4. upsert 중 부분 실패 시 데이터 불일치

**문제**
상품 50개를 크롤링할 때 하나의 트랜잭션으로 묶으면, 중간에 하나가 실패할 경우 전체가 롤백됩니다. 반대로 예외를 catch만 하면 `products`에는 저장됐는데 `product_prices`에는 저장 안 된 고아 레코드가 생길 수 있습니다.

**해결: 상품 1건 단위로 독립 트랜잭션 (REQUIRES_NEW)**
`upsert()` 메서드에 `@Transactional(propagation = REQUIRES_NEW)`를 적용하여 상품 1건을 하나의 원자적 단위로 처리합니다. 한 상품이 실패해도 다른 49개 저장에는 영향이 없습니다.

---

### 5. 동시 요청 시 search_history 중복 키 오류

**문제**
두 사용자가 동시에 같은 키워드를 처음 검색하면, 둘 다 `search_history`에 해당 키워드가 없다고 판단하고 동시에 INSERT를 시도합니다. `keyword` 컬럼의 UNIQUE 제약으로 인해 하나는 `DataIntegrityViolationException`이 발생합니다.

**해결: 예외 포착 후 무시**
두 번째 INSERT가 실패해도 크롤링 데이터는 이미 저장됐으므로 검색 결과에 영향이 없습니다. `DataIntegrityViolationException`을 catch하여 정상 처리합니다.

---

## 기술 스택

| 구분 | 기술 |
|------|------|
| Backend | Java 21, Spring Boot 3.5, Spring Data JPA |
| Database | MySQL 8.0 |
| 크롤링 | Java HttpClient + Jackson (무신사 내부 API) |
| Frontend | React 18, Vite, TanStack Query, Axios |
| 스타일 | Tailwind CSS v3 |

---

## 실행 방법

### 1. MySQL 설정
```sql
CREATE DATABASE itemfinder CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'itemfinder'@'localhost' IDENTIFIED BY 'itemfinder123';
GRANT ALL PRIVILEGES ON itemfinder.* TO 'itemfinder'@'localhost';
```

### 2. 백엔드 실행
```bash
cd backend
./mvnw spring-boot:run
```

### 3. 프론트엔드 실행
```bash
cd frontend
npm run dev
```

### 4. 접속
`http://localhost:5173` — 검색창에 키워드 입력 (첫 검색은 크롤링으로 2~3초 소요)

---

## 프로젝트 구조

```
ItemFinder/
├── backend/
│   └── src/main/java/com/itemfinder/
│       ├── config/          # CORS 설정
│       ├── crawler/         # 무신사 크롤러
│       ├── domain/
│       │   ├── product/     # 상품 엔티티, 서비스, 컨트롤러
│       │   ├── price/       # 가격 엔티티
│       │   └── search/      # 검색 히스토리
│       └── dto/             # API 응답 DTO
└── frontend/
    └── src/
        ├── api/             # Axios 호출
        ├── components/      # SearchBar, ProductCard
        ├── hooks/           # useProductSearch (React Query)
        └── pages/           # SearchPage
```
