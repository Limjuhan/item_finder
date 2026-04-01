# ItemFinder — 패션 가격 비교 웹앱

무신사 등 패션 e-커머스 플랫폼의 상품존재 유무확인 및 가격을 검색하고 확인하는 웹앱입니다.

> 기술 선택 이유 및 주요 문제 해결 → [docs/TECHNICAL_DECISIONS.md](docs/TECHNICAL_DECISIONS.md)

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
├── docs/
│   └── TECHNICAL_DECISIONS.md  # 기술 선택 이유 및 문제 해결
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
