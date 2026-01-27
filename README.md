# 🏃‍♂️ Bin-Go: 서울시 공공데이터 기반 러닝 코스 추천 서비스

**Bin-Go**는 서울시의 공공데이터(쓰레기통, 화장실, 편의점)를 활용하여 사용자의 위치와 편의시설 수요에 맞춘 최적의 러닝 경로를 생성해주는 서비스입니다. 안전하고 편리한 러닝을 위해 편의시설의 위치를 고려한 맞춤형 코스를 제공합니다.

## 📝 프로젝트 소개

- **서비스 지역:** 서울특별시 전역 (타 지역 요청 시 경로 생성 제한)
- **주요 기능:**
    - 사용자 맞춤형 러닝 코스 생성 (거리, 편의시설 필터링)
    - 실시간 러닝 트래킹 (Kakao Map 기반)
    - 러닝 기록 저장 및 캘린더/통계 시각화
    - 쓰레기통, 화장실, 편의점 등 러닝 중 필수 시설 경유 지원

## 🛠 시스템 아키텍처

시스템은 **Android 클라이언트**와 **Node.js 알고리즘 서버**로 분리되어 있으며, UI 처리는 클라이언트가, 복잡한 경로 계산은 서버가 담당합니다.

### Architecture Overview
* **Android Client**: 사용자 인터페이스, 실시간 트래킹, 로컬 데이터 저장 (MVVM 패턴)
* **Node.js Server**: POI 데이터 관리, 코스 생성 알고리즘(Sector Routing), ORS 경로 계산

## 💻 기술 스택 (Tech Stack)

| 구분 | 기술 / 라이브러리 | 설명 |
| :--- | :--- | :--- |
| **Client** | **Kotlin** | Android Native App 개발 언어 |
| | **MVVM** | 아키텍처 패턴 |
| | **Room Database** | 러닝 기록 로컬 저장 |
| | **Retrofit2 / OkHttp3** | 서버 통신 |
| | **Kakao Maps SDK v2** | 지도 및 경로 시각화 |
| | **Glide** | 이미지 로딩 |
| **Server** | **Node.js** | 런타임 환경 |
| | **Express** | 웹 프레임워크 |
| | **OpenRouteService (ORS)** | 보행자 경로 계산 API |
| | **JSON** | POI(편의시설) 데이터 관리 |

## 🧩 프로젝트 구조 (Project Structure)

```bash
BinGo/
├─ app/src/main/java/com/project/bingo/
│   ├── ui/                  # UI Layer (MVVM Views)
│   │   ├── splash/          # 초기 진입
│   │   ├── auth/            # 로그인
│   │   ├── home/            # 홈 및 대시보드
│   │   ├── running/         # 러닝 설정, 진행, 결과
│   │   ├── calendar/        # 기록 히트맵
│   │   └── mypage/          # 사용자 관리
│   ├── data/                # Data Layer
│   │   ├── local/           # Room DB (DAO, Entity)
│   │   ├── remote/          # Retrofit Service
│   │   └── repository/      # Repository Pattern
│   ├── model/               # Data Models
│   └── utils/               # 유틸리티
│
├─ data/                     # Server Side Data (POI)
│  ├─ toilets.normalized.json
│  ├─ bins.normalized.json
│  └─ stores.normalized.json
│
├─ index.js                  # Main Server Logic
├─ db.js                     # Data Loader
└─ package.json
```

## 🧠 핵심 알고리즘: 코스 생성 (Route Generation)

Bin-Go 서버는 단순한 경로 탐색이 아닌, 러닝 목적에 맞는 순환 코스를 생성하기 위해 다음과 같은 로직을 수행합니다.

1.  **섹터 라우팅 (Sector Routing):** 출발지를 기준으로 80° 부채꼴 형태로 경유지(Waypoint)를 배치하여 중앙 회귀형 코스가 아닌 자연스러운 순환 코스를 유도합니다.
2.  **슬롯 전략 (Slot Strategy):**
    * **Slot 1:** 화장실 (필수 할당)
    * **Slot 2:** 쓰레기통 (데이터 부재 시 화장실로 Fallback)
    * **Slot 3:** 사용자 옵션(편의점) 또는 화장실
3.  **검증 및 보정:** 생성된 경로가 목표 거리의 ±12% 오차 범위를 벗어나면 탐색 반경($d^*$)을 자동 튜닝하여 재생성합니다.

## 🚀 실행 방법 (Getting Started)

### 사전 준비 사항
* Android Studio
* Node.js (npm)
* Kakao Map API Key
* OpenRouteService (ORS) API Key

### 설치 및 실행 단계

1.  **Repository Clone**
    ```bash
    git clone [https://github.com/swu-guru2-team2-bin-go.git](https://github.com/swu-guru2-team2-bin-go.git)
    ```

2.  **Server Setup**
    * Node.js 디렉토리로 이동하여 의존성 설치 및 실행
    ```bash
    cd data # 또는 index.js가 있는 루트
    npm install
    # .env 파일 생성 후 ORS_API_KEY=YOUR_KEY 입력
    node index.js
    ```

3.  **Client Setup**
    * Android Studio에서 프로젝트 열기 (`Open Existing Project`)
    * `local.properties` 또는 `AndroidManifest.xml`에 Kakao Map API Key 설정
    * Gradle Sync 후 에뮬레이터 또는 실기기에서 실행 (`Run 'app'`)

## 📱 주요 화면 및 기능

| 화면 | 기능 설명 |
| :--- | :--- |
| **로그인** | 서비스 이용 약관 동의 및 로그인 수행 |
| **홈 (Home)** | 주간 러닝 그래프, 요약 카드, 바로 러닝 시작하기 |
| **러닝 설정** | 목표 거리(3~10km) 및 포함하고 싶은 시설(쓰레기통/편의점 등) 선택 |
| **추천 루트** | 알고리즘이 생성한 최적 경로 3종 미리보기 및 선택 |
| **실시간 러닝** | Kakao Map 위 경로 표시, 거리/페이스/시간 실시간 측정 |
| **러닝 완료** | 러닝 경로 썸네일 저장, 상세 기록(칼로리 등) 확인 |
| **캘린더** | 월별 운동 기록 히트맵(색상 농도 시각화) 제공 |
| **마이페이지** | 개인 정보 수정 및 주간 활동 요약 확인 |

---
*Developed by SWU Guru2 Team 2*
