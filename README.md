# ItemFinder — 패션 가격 비교 웹앱

무신사 등 패션 e-커머스 플랫폼의 상품 존재 유무확인 및 가격을 검색하고 확인하는 웹앱입니다.

> 기술 선택 이유 및 주요 문제 해결 → [docs/TECHNICAL_DECISIONS.md](docs/TECHNICAL_DECISIONS.md)

---

## 배포된 앱

**🌐 접속:** [https://item-finder-navy.vercel.app](https://item-finder-navy.vercel.app)

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

## 로컬 개발 환경 실행

### 1. MySQL 설정
```sql
CREATE DATABASE itemfinder CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

> `application.properties` 기준 포트: **3307**, 계정: `root` / `1234`

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
`http://localhost:5173` — 검색창에 키워드 입력 (검색마다 무신사 실시간 크롤링, 2~3초 소요)

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
│  - 크롤링 트리거                     │
└──────────┬──────────────────────────┘
           │                │
           ▼                ▼
    ┌──────────┐    ┌──────────────────┐
    │  MySQL   │    │  무신사 내부 API  │
    │  DB      │    │  (크롤링 대상)    │
    └──────────┘    └──────────────────┘
```

검색 흐름: 키워드 입력 → 무신사 API 실시간 크롤링 → MySQL upsert → DB 조회 → 최저가 정렬 반환

※ 프론트엔드에서 동일 키워드 재검색 시 5분간 서버 요청 없이 캐시 반환 (React Query staleTime)

---

## DB 설계

```sql
-- 상품 마스터
CREATE TABLE products (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_name  VARCHAR(255) NOT NULL,
  product_code  VARCHAR(100) UNIQUE,       -- 무신사 goodsNo
  category      VARCHAR(100),
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
```

**설계 포인트:** `product_prices` 테이블을 분리하여 향후 29cm, 쿠팡 등 플랫폼 추가 시 `products` 테이블 변경 없이 row만 추가

---

## 프로젝트 구조

```
ItemFinder/
├── docs/
│   └── TECHNICAL_DECISIONS.md  # 기술 선택 이유 및 문제 해결
├── backend/
│   └── src/main/java/com/itemfinder/
│       ├── config/          # CORS 설정
│       ├── crawler/         # 무신사 크롤러
│       ├── domain/
│       │   ├── product/     # 상품 엔티티, 서비스, 컨트롤러
│       │   ├── price/       # 가격 엔티티
│       │   └── search/      # 검색 히스토리 (미사용, Phase 2 캐싱 예정)
│       └── dto/             # API 응답 DTO
└── frontend/
    └── src/
        ├── api/             # Axios 호출
        ├── components/      # SearchBar, ProductCard
        ├── hooks/           # useProductSearch (React Query)
        └── pages/           # SearchPage
```
