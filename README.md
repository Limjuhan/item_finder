# ItemFinder — 패션 가격 비교 웹앱

**목표**: 패션 e-커머스(무신사, 29cm 등) 플랫폼에서 상품 가격을 검색하여, 이용자가 원하는 상품을 **어느 플랫폼에서 얼마에 구매할 수 있는지 한눈에 확인**하도록 도와주는 웹앱입니다.

> 기술 선택 이유 및 주요 문제 해결 → [docs/TECHNICAL_DECISIONS.md](docs/TECHNICAL_DECISIONS.md)

---

## 배포된 앱

**접속:** [https://item-finder-oapp.onrender.com](https://item-finder-oapp.onrender.com)

---

## 기술 스택

| 구분 | 기술 |
|------|------|
| Backend | Java 21, Spring Boot 3.5, Spring Data JPA |
| Database | MySQL 8.0 |
| 크롤링 | Java HttpClient + Jackson (무신사 내부 API) |
| Frontend | React 18, Vite, TanStack Query v5, Axios |
| 스타일 | Tailwind CSS v3 |
| 모니터링 | New Relic APM (Java Agent) |
| 배포 | Docker, Render |

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
`http://localhost:5173` — 검색창에 키워드 입력 (매번 실시간 크롤링, 1~2초 소요)

---

## 시스템 아키텍처

```
┌─────────────────────────────────────┐
│         React Frontend              │
│  localhost:5173                     │
│  - 검색창 (400ms 디바운스)           │
│  - 상품 카드 리스트                  │
└──────────────┬──────────────────────┘
               │ REST API (Axios)
               ▼
┌─────────────────────────────────────┐
│       Spring Boot Backend           │
│  localhost:8080 / Render            │
│  - 검색 API (GET /api/products/search) │
│  - 크롤러 (최대 10개 상품)           │
└──────────┬──────────────────────────┘
           │                │
           ▼                ▼
    ┌──────────┐    ┌──────────────────┐
    │  MySQL   │    │  무신사 내부 API  │
    │  DB      │    │  (JSON 응답)      │
    └──────────┘    └──────────────────┘
```

**검색 흐름**
1. 사용자가 검색어 입력 (예: "아이더")
2. Spring Boot에서 무신사 API 실시간 크롤링 (최대 10개)
3. DB에 upsert (새 상품은 INSERT, 기존 상품은 UPDATE)
4. search_history에 키워드 저장 (Phase 2 스케줄러용)
5. 결과를 가격 낮은 순으로 정렬해 반환
6. React에서 React Query 캐시 적용 (staleTime 5분) — 같은 키워드 재검색 시 네트워크 요청 스킵

**특징: 항상 최신 가격 정보**
- 매번 크롤링하므로 상품 품절, 가격 변동을 즉시 반영

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
