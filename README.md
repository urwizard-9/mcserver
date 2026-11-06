# ⛏️ Minecraft Server Monitoring & Bot System

마인크래프트 서버의 상태를 모니터링하고, 디스코드 봇을 통해 서버를 관리하며, 플레이어 통계를 제공하는 통합 시스템입니다.
**PC(Java Edition)** 와 **Mobile(Bedrock Edition)** 접속을 모두 지원합니다.

## 🏗️ 아키텍처

이 프로젝트는 Docker Compose를 사용하여 6개의 컨테이너를 유기적으로 연결합니다.

<img width="3344" height="889" alt="Mermaid Chart - Create complex, visual diagrams with text -2025-12-09-120724" src="https://github.com/user-attachments/assets/c1a993e7-c00e-478a-a693-1f0a24dc59ef" /><br>
1.  **Minecraft Server**: PaperMC 기반 (Geyser + Floodgate 플러그인 탑재).
2.  **Prometheus**: 서버 성능 데이터(TPS, RAM, 청크 등) 수집.
3.  **Grafana**: 수집된 데이터를 시각화하는 대시보드.
4.  **Grafana Image Renderer**: 대시보드 차트를 이미지로 변환하여 디스코드로 전송.
5.  **PostgreSQL**: 플레이어 통계(킬, 데스, 광물 채굴량 등) 영구 저장.
6.  **Bot Server (FastAPI + Discord.py)**:
    *   **RCON Client**: 서버 제어 (Kick, Ban, Whitelist).
    *   **Log Parser**: 실시간 로그 분석 (PvP/PvE 구분, 사망 원인, 접속 로그).
    *   **Stats Reader**: 마인크래프트 통계 파일(JSON) 파싱.
    *   **Grafana Integration**: 차트 이미지 조회 (`!chart`).
    *   **Permission System**: 역할(`Admin`) 기반의 엄격한 권한 관리.

## 🚀 설치 및 실행 방법

### 1. 필수 요구 사항
*   Docker & Docker Compose

### 2. 환경 설정 (.env)
프로젝트 루트에 `.env` 파일을 생성하고 다음 내용을 채워주세요.
```env
DISCORD_TOKEN=your_discord_bot_token_here
GRAFANA_TOKEN=your_grafana_service_account_token_here
```

### 3. 실행
```bash
docker-compose up -d --build
```
*   `--build`: 봇 서버 이미지를 빌드하기 위해 처음 실행 시 필수입니다.

### 4. 접속 정보
*   **PC (Java)**: `localhost:25565`
*   **Mobile (Bedrock)**: `localhost:19132` (UDP)
*   **Grafana**: `localhost:3000` (ID: admin / PW: admin)
*   **Prometheus**: `localhost:29090`
*   **Bot Server API**: `localhost:8000`

## 🎮 사용법 (Discord 명령어)

> **🔒 보안 안내**: `!leaderboard`를 제외한 **모든 명령어**는 **관리자 역할**이 필요합니다.
> 디스코드 서버에서 **`Admin`**, **`Operator`**, **`관리자`**, **`운영자`** 중 하나의 이름을 가진 역할을 보유해야 사용 가능합니다. (권한 우회 방지 적용됨)

### 📊 통계 (누구나 사용 가능)
*   `!leaderboard [항목]`: 랭킹을 확인합니다. (별칭: `!lb`, `!랭킹`)
    *   **사용 예시**: `!lb playtime`, `!lb kills`, `!lb diamonds`, `!lb blocks`
    *   **지원 항목**: `playtime`, `kills`, `deaths`, `blocks`, `diamonds`, `coal`, `iron`, `gold`, `netherite`

### 📈 모니터링 및 차트 (관리자 전용)
*   `!chart [이름]`: 그라파나 대시보드의 차트를 이미지로 불러옵니다.
    *   **사용 예시**: `!chart memory`, `!chart tps`, `!chart players`
*   `!sync`: 그라파나 대시보드 패널 목록을 봇과 동기화합니다. (새 차트 추가 시 사용)
*   `!set_dashboard [UID]`: 연동할 그라파나 대시보드 UID를 변경합니다.

### 🛠 서버 관리 (관리자 전용)
*   `!say [메시지]`: 마인크래프트 서버 전체에 공지 메시지를 방송합니다.
*   `!kick [플레이어] [사유]`: 플레이어를 서버에서 추방합니다.
*   `!ban [플레이어] [사유]`: 플레이어를 영구 차단합니다.
*   `!unban [플레이어]`: 플레이어 차단을 해제합니다.
*   `!whitelist [action] [player]`: 화이트리스트를 관리합니다. (`add`, `remove`, `list`)
    *   예: `!whitelist add Steve`

### ℹ️ 시스템 상태 (관리자 전용)
*   `!ping`: 봇 응답 속도 확인.
*   `!health`: 봇 시스템 상태 및 연결 확인.

## 📂 디렉토리 구조
```
.
├── bot/                # 봇 서버 소스코드
│   ├── cogs/           # 디스코드 명령어 모듈
│   │   ├── admin.py    # 관리자 기능 (!say, !kick, !whitelist)
│   │   ├── grafana.py  # 차트 시각화 (!chart, !sync)
│   │   └── stats.py    # 통계 랭킹 (!leaderboard)
│   ├── core/           # 핵심 로직 (DB, RCON, Config, LogParser)
│   ├── main.py         # FastAPI 및 봇 진입점 (Cog 로딩 및 권한 관리)
│   └── Dockerfile      # 봇 서버 도커 이미지 정의
├── prometheus/         # 모니터링 설정
├── docker-compose.yml  # 전체 시스템 구성
└── .env                # 환경 변수 (토큰 등)
```

## ⚡ 주요 기능 및 특징
*   **엄격한 권한 관리**: 단순 '관리자 권한' 보유 여부가 아닌, **실제 지정된 역할(Role)** 을 보유했는지를 검사하여 보안을 강화했습니다.
*   **고성능 최적화**: M4 Pro 등 고사양 하드웨어에 맞춰 메모리(6GB) 및 알림 임계값 최적화.
*   **모바일 지원**: GeyserMC를 통해 별도 계정 연동 없이 모바일 접속 가능 (`auth-type: floodgate`).
*   **스마트 로그 파싱**:
    *   **PvP 감지**: 몬스터에 의한 죽음은 제외하고, 실제 플레이어 간 킬만 DB에 기록.
    *   **재미있는 사망 메시지**: 사망 원인별(선인장, 베리, 낙사 등) 맞춤형 한국어 메시지 출력.
*   **실시간 알림**:
    *   서버 렉(TPS 저하), 메모리 부족, 엔티티 폭증 시 그라파나 -> 디스코드 알림 전송.
    *   3킬/5킬/10킬 달성 시 '학살' 알림 및 킬 스트릭 중계.
