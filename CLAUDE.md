# ItemFinder 프로젝트 가이드

## 프로젝트 개요
**목표**: 패션 e-커머스(무신사, 29cm 등) 플랫폼에서 상품 가격을 검색 및 비교하여, 이용자가 원하는 상품을 **어느 플랫폼에서 얼마에 구매할 수 있는지 한눈에 확인**하도록 도와주는 웹앱.

**스택**: React 18 (Vite) + Spring Boot 3.5 + MySQL 8.0

---

## 기술 스택
- **Backend**: Java 21, Spring Boot 3.5, Spring Data JPA, MySQL 8.0
- **Frontend**: React 18, Vite, TanStack Query v5, Axios, Tailwind CSS v3
- **크롤링**: Java HttpClient + Jackson (Jsoup 불가 — 무신사는 JS 렌더링)

---

## 핵심 아키텍처 결정

### 검색 흐름 (메모리 캐싱 + 스트리밍)
1. **메모리 캐시 확인** — 5분 내 같은 키워드 검색이 있으면 캐시된 결과 반환 (API 중복 호출 방지)
2. **캐시 미스 또는 만료 시:**
   - 각 플랫폼에서 병렬 크롤링 시작 (최대 10개)
   - 각 플랫폼 크롤링 완료되는 대로 **클라이언트에 스트림 전송** (SSE)
   - 메모리에 크롤링 결과 캐싱 (5분)
3. **search_history에 키워드 저장** (Phase 2 스케줄러 용도)

**Why 스트리밍 + 캐싱:**
- **UX 개선**: 빠른 플랫폼 결과부터 사용자에게 보여줌 (200ms에 첫 결과 표시)
- **API 효율**: 5분 내 중복 검색은 캐시에서 반환 (API 호출 최소화)
- **동시성 안전**: 메모리만 사용하므로 DB 동시성 에러 없음
- **확장성**: 플랫폼 추가 시에도 구조 변화 없음

**search_history 용도:**
- Phase 2 예정: "자주 검색된 키워드 자동 갱신 스케줄러"
- 키워드별 검색 시간 기록 (frequency 분석용)

**주의: ProductListing 테이블은 더 이상 사용하지 않음**
- 이유: 같은 상품을 플랫폼별로 매칭할 방법이 없어서 의미 있는 데이터 수집 불가능
- 크롤링 결과는 메모리 캐시에만 보관 후 클라이언트에 전송

### 무신사 크롤링 방식
- Jsoup 사용 불가 (JS 렌더링 페이지)
- 무신사 내부 API 직접 호출
- **최대 10개 상품만** 크롤링 (성능 최적화)
- API: `https://api.musinsa.com/api2/dp/v1/plp/goods?keyword=...&gf=M&pageNumber=1&pageSize=50&sortCode=POPULAR&caller=SEARCH`
- 상세: first N=10을 파싱해서 저장 (pageNumber 파라미터 미작동 확인)

### DB 검색 방식
- `LIKE %keyword%` 로 `product_name`, `brand`, `product_code` 검색
- 카테고리 검색("상의", "팬츠" 등)은 지원 안 함 — 상품명/브랜드명 검색만 가능

---

## 디렉토리 구조

```
backend/src/main/java/com/itemfinder/
├── config/WebConfig.java              # CORS (localhost:5173, :3000 허용)
├── cache/
│   └── SearchCache.java               # 5분 메모리 캐시 (keyword → 크롤링 결과)
├── crawler/
│   └── MusinsaCrawlerService.java     # 무신사 API 호출 (DB 저장 제거)
├── domain/
│   ├── product/
│   │   └── SearchController.java      # GET /api/search/stream?query= (스트리밍 SSE)
│   └── search/
│       ├── SearchHistory.java        # keyword + last_searched_at (검색 기록용)
│       └── SearchHistoryRepository.java
└── dto/
    └── ProductSearchResponse.java     # 크롤링 결과 DTO

**제거됨:**
- `ProductListing` (DB 저장 폐기)
- `ProductService`, `ProductRepository`
- `ProductController`, `ProductPrice`

frontend/src/
├── api/productApi.js                 # Axios 호출 모음
├── components/
│   ├── SearchBar.jsx                 # 400ms 디바운스
│   └── ProductCard.jsx              # 상품명/이미지 → 무신사 링크
├── hooks/useProductSearch.js        # EventSource 기반 스트리밍 (query.length >= 2)
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

## 작업 규칙

### Git 작업 흐름 (중요!)

**새로운 기능 브랜치 시작 전:**
```bash
# 1. main 브랜치로 이동
git checkout main

# 2. 항상 최신 코드로 동기화
git pull origin main

# 3. 새 브랜치 생성 (기능명에 맞는 이름 선택)
git checkout -b feat/feature-name
# 또는
git checkout -b fix/bug-name
```

**브랜치 명명 규칙:**
- `feat/...` — 새로운 기능
- `fix/...` — 버그 수정
- `refactor/...` — 코드 리팩토링
- `docs/...` — 문서 작업

**작업 중 main이 업데이트되었으면:**
```bash
git pull origin main --rebase
```

**Why:** 
- 항상 최신 main을 바탕으로 작업해야 충돌 최소화
- PR merge 시 충돌을 예방
- 여러 개의 기능 브랜치가 동시에 개발될 때 필수

### 일반 작업 규칙
**파괴적/비가역적 작업 (git push, git reset, 파일 삭제 등) 전에는 반드시 사용자에게 물어본 후 진행하기**
- 코드 수정/커밋은 먼저 설명하고 승인받은 후 실행
- git push는 절대 무단으로 하지 않기

### Git 계정 확인 (중요!)
**git push 전에 반드시 확인:**
- 현재 git 계정이 **Limjuhan** 이어야 함 (claude 계정 절대 금지)
- push 전에 `git config user.name`과 `git config user.email` 확인
- 잘못된 계정일 경우, push 전에 사용자에게 알리고 계정 설정 후 진행

---

## 커밋 메시지 규칙

**형식:** `type: 설명`

| Type | 설명 | 예시 |
|------|------|------|
| `feat` | 새로운 기능 추가 | `feat: Musinsa 상품 크롤링 기능 추가` |
| `fix` | 버그 수정 | `fix: 검색 결과 중복 제거` |
| `refactor` | 코드 리팩토링 (기능 변화 없음) | `refactor: ProductService 구조 개선` |
| `docs` | 문서 작성/수정 | `docs: README 배포 방법 추가` |
| `chore` | 설정/의존성/빌드 관련 | `chore: New Relic APM 에이전트 추가` |
| `test` | 테스트 코드 추가/수정 | `test: ProductService 검색 테스트 추가` |

---

## Phase 2 예정
- **29cm, 쿠팡 등 플랫폼 추가** — 새 CrawlerService 구현 (기존 MusinsaCrawlerService와 동일 패턴)
- **검색 키워드 자동 갱신 스케줄러** — SearchHistory 기반으로 자주 검색된 키워드 주기적으로 갱신
- ~~가격 변동 그래프~~ — 현재 구조에서는 불가능 (과거 가격 데이터 없음)
- ~~가격 알림 기능~~ — Phase 2에서 검토
