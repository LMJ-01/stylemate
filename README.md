# StyleMate 👕✨
Spring Boot로 만든 패션 커뮤니티이자 가상 피팅 서비스입니다. 옷을 올리고 피드를 공유하며, 아바타로 착용 모습을 바로 확인할 수 있습니다.

## 주요 기능
- 회원가입·로그인: 이메일 인증을 거친 기본 회원 관리, 프로필 편집
- 피드: 글/사진 등록, 수정, 삭제와 검색, 태그 기반 필터
- 인터랙션: 좋아요, 댓글/대댓글, 알림
- 관리자 모드: 유저/게시글 관리, 신고 처리, 통계 대시보드
- 아바타 피팅룸: 신체 사이즈 기반 아바타 생성, 아이템 착용/해제, 룩 저장

## 기술 스택
- Backend: Spring Boot, JPA, MySQL
- Frontend: Thymeleaf, Tailwind CSS
- Infra: Gradle, Spring Security, Spring Data JPA, AWS S3(이미지 저장 가정), JWT 세션/토큰 관리

## 아키텍처 개요
- 계층형 구조(Controller → Service → Repository) 위에 DTO/Entity 분리
- 도메인: `user`, `post`, `comment`, `like`, `admin`, `fitting`
- 전역 예외 처리와 공통 응답 포맷으로 API 일관성 유지
- 이미지 업로드는 S3 프리사인 URL 또는 로컬 스토리지로 대체 가능하도록 추상화

## 주요 화면 흐름
- 홈/피드: 최신순·인기순 피드 노출, 태그 필터
- 게시글 상세: 이미지 슬라이드, 댓글/대댓글, 좋아요
- 마이페이지: 프로필 편집, 내가 쓴 글/좋아요/스크랩 모아보기
- 관리자: 신고 목록, 유저 차단, 게시글 숨김
- 피팅룸: 사이즈 입력 → 아바타 생성 → 아이템 착용/해제 → 룩 저장/공유

## 실행 방법
1) 환경 변수: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` 지정  
2) 의존성 설치: `./gradlew clean build`  
3) 로컬 실행: `./gradlew bootRun`  
4) 접속: `http://localhost:8080`

## 개발 노트
- 엔티티에는 비즈니스 규칙을 담고, 컨트롤러는 DTO로 입출력을 캡슐화
- Tailwind로 기본 레이아웃을 잡고, 컴포넌트별 커스텀 유틸 클래스로 톤앤매너 통일
- 테스트: 서비스/레포지토리 단위 테스트를 중심으로 핵심 도메인 검증, WebMvcTest로 API 슬라이스 확인

## 앞으로 할 일
- 소셜 로그인 연동(Google/Kakao)
- 피드 추천 로직 고도화(선호 태그/좋아요 기반)
- 피팅룸 아이템 카탈로그 확장 및 룩 공유 링크 생성
