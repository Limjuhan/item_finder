# ItemFinder — 패션 가격 비교 웹앱

**목표**: 패션 e-커머스(무신사, 29cm 등) 플랫폼에서 상품 가격을 **실시간으로 검색하고 비교**하여, 이용자가 원하는 상품을 **어느 플랫폼에서 얼마에 구매할 수 있는지 한눈에 확인**하도록 도와주는 웹앱입니다.

> 기술 선택 이유 및 주요 문제 해결 → [docs/TECHNICAL_DECISIONS.md](docs/TECHNICAL_DECISIONS.md)

---

## 배포

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

다음 문서를 참고해주세요:
- **기술 선택 이유**: React vs JSP, HttpClient vs Jsoup, 실시간 크롤링 vs 캐싱
- **주요 문제 해결**: 트랜잭션 격리, 부분 실패 격리, 동시 요청 처리

➜ [**docs/TECHNICAL_DECISIONS.md**](docs/TECHNICAL_DECISIONS.md)

---

## 실행 방법

### 1. MySQL 설정
```sql
CREATE DATABASE itemfinder CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

**Phase 1 (현재 완성)**
- ✅ 무신사 가격 검색 및 크롤링
- ✅ 멀티 플랫폼 아키텍처 설계
- ✅ 실시간 가격 동기화
- ✅ Render 배포 + New Relic 모니터링

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
