# ItemFinder 프로젝트 가이드

## 프로젝트 개요
무신사 등 패션 e-커머스의 상품 가격을 비교하는 웹앱.
React(Vite) + Spring Boot + MySQL 구성.

---

## 기술 스택
- **Backend**: Java 21, Spring Boot 3.5, Spring Data JPA, MySQL 8.0
- **Frontend**: React 18, Vite, TanStack Query v5, Axios, Tailwind CSS v3
- **크롤링**: Java HttpClient + Jackson (Jsoup 불가 — 무신사는 JS 렌더링)

---

## 핵심 아키텍처 결정

### 검색 흐름 (매 요청마다 실시간 크롤링)
1. 검색어로 무신사 API 실시간 크롤링 → DB upsert
2. DB에서 조회 후 반환 (캐시 없음, search_history 미사용)

### 무신사 크롤링 방식
- Jsoup 사용 불가 (JS 렌더링 페이지)
- 무신사 내부 API 직접 호출:
  `https://api.musinsa.com/api2/dp/v1/plp/goods?keyword=...&gf=M&pageNumber=1&pageSize=50&sortCode=POPULAR&caller=SEARCH`

### DB 검색 방식
- `LIKE %keyword%` 로 `product_name`, `brand`, `product_code` 검색
- 카테고리 검색("상의", "팬츠" 등)은 지원 안 함 — 상품명/브랜드명 검색만 가능

---

## 트랜잭션 주의사항

### ProductService.search()
- `@Transactional` **없음** (의도적)
- 이유: MySQL REPEATABLE READ 특성상 외부 트랜잭션 안에서 크롤러가 커밋한 데이터가 보이지 않음
- 하위 호출(MusinsaCrawlerService)이 각자 트랜잭션 관리

### MusinsaCrawlerService.upsert()
- `@Transactional(propagation = REQUIRES_NEW)` — 상품 1건 단위 독립 트랜잭션
- 이유: products + product_prices 원자성 보장, 한 건 실패가 다른 건에 영향 없도록

### MusinsaCrawlerService.crawl()
- `@Transactional` 없음 — upsert() 루프를 돌며 각각 독립 커밋

---

## 디렉토리 구조

```
backend/src/main/java/com/itemfinder/
├── config/WebConfig.java              # CORS (localhost:5173, :3000 허용)
├── crawler/
│   ├── MusinsaCrawlerService.java     # 무신사 API 호출 + upsert
│   └── CrawlerController.java        # POST /api/admin/crawl/musinsa (수동 트리거)
├── domain/
│   ├── product/
│   │   ├── Product.java
│   │   ├── ProductRepository.java    # searchByNameOrBrand LIKE 쿼리
│   │   ├── ProductService.java       # 검색 + 캐시 체크 + 크롤링 트리거
│   │   └── ProductController.java    # GET /api/products/search?query=
│   ├── price/
│   │   ├── ProductPrice.java
│   │   └── ProductPriceRepository.java
│   └── search/
│       ├── SearchHistory.java        # keyword + last_crawled
│       └── SearchHistoryRepository.java
└── dto/
    ├── ProductSearchResponse.java
    └── PriceInfoDto.java

frontend/src/
├── api/productApi.js                 # Axios 호출 모음
├── components/
│   ├── SearchBar.jsx                 # 400ms 디바운스
│   └── ProductCard.jsx              # 상품명/이미지 → 무신사 링크
├── hooks/useProductSearch.js        # React Query (query.length >= 2)
└── pages/SearchPage.jsx
```

---

## 실행

```bash
# 백엔드
cd backend && ./mvnw spring-boot:run

# 프론트엔드
cd frontend && npm run dev
```

접속: `http://localhost:5173`

---

## DB 연결 정보 (application.properties)
- URL: `jdbc:mysql://127.0.0.1:3307/itemfinder`
- User: `root`
- Password: `1234`
- DDL: `spring.jpa.hibernate.ddl-auto=update`

---

## Phase 2 예정
- 29cm, 쿠팡 등 플랫폼 추가 (`product_prices` 테이블에 row 추가만 하면 됨)
- 가격 변동 그래프
- 가격 알림 기능
- 스케줄러: 자주 검색된 키워드 자동 갱신
