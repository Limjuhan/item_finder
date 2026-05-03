# ItemFinder — 패션 가격 비교 웹앱

**목표**: 패션 e-커머스(무신사, 29cm 등) 플랫폼에서 상품 가격을 **실시간으로 검색하고 비교**하여, 이용자가 원하는 상품을 **어느 플랫폼에서 얼마에 구매할 수 있는지 한눈에 확인**하도록 도와주는 웹앱입니다.

---

## 배포

**서비스 링크:** [https://item-finder-navy.vercel.app/](https://item-finder-navy.vercel.app/)

---

## 기술 스택

| 계층 | 기술 |
|------|------|
| **Frontend** | React 18, Vite, TanStack Query v5, Axios, Tailwind CSS v3 |
| **Backend** | Java 21, Spring Boot 3.5, Spring Data JPA |
| **Database** | MySQL 8.0 |
| **크롤링** | Java HttpClient + Jackson (무신사 내부 API) |
| **배포** | Docker, Render, New Relic APM |

---

## 주요 기능

✅ **실시간 가격 크롤링**: 매 검색마다 무신사 API에서 실시간으로 최신 가격 조회 (최대 10개 상품)  
✅ **멀티 플랫폼 확장성**: `PlatformCrawler` 인터페이스로 새로운 플랫폼(29cm, 쿠팡 등) 추가 가능  
✅ **메모리 캐싱 + SSE 스트리밍**: 5분 캐시로 API 호출 최소화, 완료되는 플랫폼부터 실시간 전송  
✅ **멀티 플랫폼 가격 비교**: 같은 상품을 여러 플랫폼에서 조회하여 최저가 비교  
✅ **인기 검색어 TOP 10**: 최근 7일 검색 로그 기반으로 인기 키워드 표시, 클릭 시 바로 검색  
✅ **모니터링**: New Relic APM으로 응답 시간, 에러율, DB 성능 추적  

---

## 기술적 의사결정

### 기술 선택 이유 & 주요 트러블슈팅

다음 문서를 참고해주세요:
- **기술 선택 이유**: React vs JSP, HttpClient vs Jsoup, 실시간 크롤링 vs 캐싱
- **주요 문제 해결**: 트랜잭션 격리, 부분 실패 격리, 동시 요청 처리

➜ [**docs/TECHNICAL_DECISIONS.md**](docs/TECHNICAL_DECISIONS.md)

---

## 프로젝트 특징

**Phase 1 (현재 완성)**
- ✅ 무신사 가격 검색 및 크롤링
- ✅ 멀티 플랫폼 아키텍처 설계
- ✅ 실시간 가격 동기화
- ✅ Render 배포 + New Relic 모니터링

**Phase 2 (예정)**
- [ ] 29cm, 쿠팡 등 추가 플랫폼
- [ ] 스케줄러: 자주 검색된 키워드 자동 갱신
- [ ] 가격 변동 그래프
- [ ] 가격 알림 기능
