# SWU-GURU2-team2-Bin-Go

# Bin-Go 러닝 앱

## 프로젝트 개요
쓰레기통, 화장실, 편의점을 고려한 맞춤형 러닝 코스를 제공하는 안드로이드 앱

## 기술 스택
- Kotlin
- XML Layouts
- MVVM Architecture
- Room Database
- Navigation Component
- Kakao Maps SDK v2
- Material Design Components

## 프로젝트 구조
```
app/src/main/
├── java/com/project/bingo/
│   ├── ui/                  # UI Layer (Activities, Fragments)
│   │   ├── splash/          # 스플래시 화면
│   │   ├── auth/            # 로그인/회원가입
│   │   ├── home/            # 홈 화면
│   │   ├── running/         # 러닝 관련 화면들
│   │   ├── calendar/        # 캘린더 (히트맵)
│   │   └── mypage/          # 마이페이지
│   ├── data/                # Data Layer
│   │   ├── local/           # Room Database
│   │   │   ├── dao/         # DAO
│   │   │   └── entity/      # Entity
│   │   ├── remote/          # 네트워크 (TODO)
│   │   └── repository/      # Repository
│   ├── model/               # Data Models
│   ├── utils/               # Utility Classes
│   └── base/                # Base Classes
└── res/                     # Resources
    ├── layout/              # XML Layouts
    ├── values/              # Strings, Colors, Themes
    ├── menu/                # Bottom Navigation Menu
    └── navigation/          # Navigation Graph
```

## 주요 기능
1. **러닝 코스 설정**: 쓰레기통/화장실/편의점 포함 여부, 목표 거리 설정
2. **AI 코스 추천**: 3가지 맞춤 코스 추천 (현재 더미 데이터)
3. **실시간 러닝**: Kakao Map으로 현재 위치 및 경로 표시
4. **러닝 기록**: Room DB에 저장
5. **캘린더 히트맵**: 날짜별 러닝 거리에 따라 색상 진하기 조절
6. **마이페이지**: 전체 러닝 통계