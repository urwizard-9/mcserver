# LGMC 마피아 서버 · Java & Bedrock

Paper와 LGMC를 기반으로 PC Java와 모바일 Bedrock 이용자가 같은 마피아 게임에 참가하도록 구성하는 프로젝트입니다. 게임 접속·대기실·한글화부터 검증하고, 이후 Discord 관리·독립 서버 제어·로그·성능 관측·백업으로 확장합니다.

기존 야생 서버의 통계·Discord 봇 프로젝트에서 전환 중입니다. **현재 실행 대상은 `compose.trial.yml`의 독립 시험 서버입니다.** 기존 `docker-compose.yml`과 `bot/`은 보존된 이전 구성으로, 새 운영 시스템이 완성된 것은 아닙니다.

> **현재 상태 — 2026-11-08 문서 기준**
> PC·모바일 접속 유지와 두 계정의 맵·준비 메뉴·미준비 강퇴·4명 미만 시작 방지를 확인했습니다. 한국어 파일과 읽기 전용 미리보기를 적용했습니다. **4인 이상 혼합 게임 완주, 다음 판 시작, Bedrock 전용 리소스팩은 아직 검증·완료되지 않았습니다.**

## 목차

- [전체 구성](#전체-구성)
- [폴더 구조](#폴더-구조)
- [주요 기능과 변경 이유](#주요-기능과-변경-이유)
- [버전과 업데이트](#버전과-업데이트)
- [시험 서버 실행](#시험-서버-실행)
- [명령어와 시험](#명령어와-시험)
- [소스 빌드와 검사](#소스-빌드와-검사)
- [진행 계획](#진행-계획)
- [Git 관리와 데이터 보존](#git-관리와-데이터-보존)
- [문서와 출처](#문서와-출처)

## 전체 구성

### 현재 구현한 시험 환경

```text
PC Java ────────────────┐
                        ▼
                      Paper ───── LGMC: 역할·낮밤·투표·승패
                        │
모바일 Bedrock → Geyser ─┘         LgmcLobby: 준비·대기실·시작 요청
                 │
              Floodgate           한국어 언어 파일 / 읽기 전용 미리보기
              모바일 인증
```

- 게임 서버와 월드는 하나입니다. PC와 모바일이 같은 Paper 플러그인을 사용합니다.
- Java 정식 계정 인증은 유지하며, Bedrock 이용자는 Floodgate로 인증합니다.
- 준비 상태는 별도 `LgmcLobby` 플러그인이 관리하고, 게임 진행은 LGMC가 담당합니다.
- Geyser 연결 성공은 Java 리소스팩 변환이나 모든 LGMC 기능의 모바일 호환성을 의미하지 않습니다.
- 시험 서버에는 PostgreSQL·Discord 봇·Grafana가 필요하지 않습니다.

### 이후 구현할 운영 환경

```text
Discord 관리자 → Discord 봇 → 독립 제어 서비스 → 게임 컨테이너 시작·종료
                         └→ RCON → 실행 중 게임 서버 관리

게임·봇·제어 로그 → Alloy → Loki ─────┐
성능 수집기 ───────────→ Prometheus ──┴→ Grafana ← HTTPS 관리자 접속

월드·설정 → 백업 → 별도 저장소 / 복원 검증
```

이 부분은 목표 구조입니다. 게임이 꺼져도 관리 봇을 사용할 수 있고, 관측 서비스 장애가 게임 실행을 막지 않도록 분리할 예정입니다. 같은 Mac이나 Docker 자체가 멈추는 장애의 원격 복구는 현재 범위에 포함되지 않습니다.

## 폴더 구조

주요 경로만 표시했습니다. 실행 데이터와 비밀정보는 소스와 분리합니다.

```text
.
├── README.md                         # 프로젝트 개요·구조·사용 안내
├── compose.trial.yml                  # 현재: 독립 게임 시험 구성
├── .env.trial.example                 # 비밀값 없는 시험 설정 예시
├── .env.trial                        # 로컬 전용: 실제 설정·비밀번호
├── .gitignore
├── config/
│   ├── trial-artifacts.json           # 플러그인 버전·출처·SHA-256
│   ├── maps/werewolf-trial.json        # 맵 출처·해시·위치·백업 기록
│   ├── lgmc/lang/
│   │   ├── en-reference.yml           # 원본 비교용 언어 파일
│   │   └── ko.yml                     # 한국어 언어 파일
│   └── geyser/item-correspondence.json # 조사용 아이템 대응표
├── lobby/                            # 별도 Paper 대기실 플러그인
│   ├── README.md
│   └── src/main/
│       ├── java/local/lgmc/
│       │   ├── LobbyPlugin.java       # 준비·강퇴·자동 시작 연결
│       │   └── PreviewMenu.java       # 동작 없는 한국어 견본 메뉴
│       └── resources/
│           ├── plugin.yml            # 플러그인·명령·권한 등록
│           ├── config.yml            # 기본 제한 시간·대기 좌표
│           └── messages.yml          # 대기실·미리보기 한국어 문구
├── patches/lgmc/                     # LGMC 원본 최소 수정 소스·라이선스
│   └── src/fr/lightshoro/lgmc/managers/
│       ├── ResourcePackManager.java   # 인증된 Bedrock의 Java 팩 예외
│       └── GameManager.java           # 카운트다운 취소·시작 조건 검사
├── scripts/
│   ├── build-trial-plugins.py          # 수정 LGMC·대기실 JAR 생성
│   ├── verify-trial-artifacts.py       # 설치 JAR 해시 확인
│   └── ValidateTranslations.java      # 언어 키·치환값 검사
├── docs/
│   ├── PROJECT_LOG.md                 # 진행·변경·판단 근거의 통합 기록
│   ├── phase-1-validation.md          # 시험 체크리스트
│   └── research/                     # 원본 코드·팩·맵 조사 근거
├── trial-data/                       # Git 제외: 실제 서버 데이터
│   ├── world/                        # 현재 시험 맵
│   ├── plugins/                      # 설치 JAR·실행 설정·키
│   ├── original-plugins/              # 원본·이전 시험 JAR
│   ├── lobby-build/                   # 빌드 산출물
│   └── logs/
├── backups/                          # Git 제외: 변경 전 월드·설정
├── secrets/                          # Git 제외: 별도 비밀정보
├── docker-compose.yml                # 이전 프로젝트 구성, 전환 전 참고
├── bot/                              # 이전 Discord 봇, 새 운영 방식 미적용
└── prometheus/                       # 이전 모니터링 설정
```

**소스 적용 흐름:** `lobby/`·`patches/` 수정 → 빌드 → `trial-data/lobby-build/` 생성 → 서버 정상 종료 → `trial-data/plugins/`에 교체 → 재시작·검증.

`config/`와 `lobby/src/main/resources/`를 수정하는 것만으로 기존 서버 설정이 자동 갱신되지는 않습니다. 실제 플러그인 설정은 `trial-data/plugins/` 아래에 있습니다. YAML(`.yml`)은 설정·번역을 담는 범용 형식이며, 게임 로직은 Java 코드가 실행합니다.

## 주요 기능과 변경 이유

| 기능 | 현재 구현 | 이유·범위 |
|---|---|---|
| 독립 시험 서버 | 별도 Compose·데이터 폴더 | 기존 봇·DB 의존성과 분리해 게임부터 검증 |
| 무료 마을 맵 | 중앙 캠프파이어·광장 시작 위치 12곳 | 직접 제작 시간을 줄임. 독립 오두막 12개 배치는 미확인 |
| 준비·자동 시작 | 4~12명 전원 준비 시 LGMC 시작 요청 | 원본의 접속자 전원 참가 방식 유지 |
| 미준비 강퇴 | 기본 120초, 30초·10초 구간 경고 | 준비하지 않는 접속자가 시작을 계속 막는 상황 처리 |
| 카운트다운 | 원본 10초 유지, 입장·이탈·준비 취소 시 취소 | 중복 시작과 인원 변경에 대응하도록 원본 타이머만 보완 |
| 모바일 접속 유지 | Floodgate 인증 이용자만 Java 팩 검사 제외 | 모바일이 Java 팩을 거절해 추방되던 문제 해결 |
| 한국어 | LGMC 언어 파일·대기실 메시지 파일 | 문구 수정과 게임 코드를 분리 |
| 미리보기 | 관리자용 역할·투표·마녀·단계·승리 견본 | 4인 게임 없이 표시를 확인. 실제 능력·투표는 실행 안 함 |
| 파일 고정·검사 | 버전·해시 기록, 빌드·번역 검사 | 같은 조건으로 재시험하고 변경 원인을 추적 |

준비한 사람은 제한 시간이 지나도 이 사유로 강퇴하지 않습니다. 준비 취소는 최초 마감 시간을 연장하지 않으며, 이미 시간이 지났으면 다음 점검에서 강퇴될 수 있습니다. 게임 중에는 준비 강퇴를 중지하고, 게임 종료 후 준비 상태를 초기화하도록 구현했습니다. 종료 후 다음 판 동작은 4인 실기 검증이 남아 있습니다.

LGMC의 역할·투표·승패 규칙은 이번 패치 범위에서 변경하지 않았습니다. 원본은 생존자의 게임 중 이탈을 탈락으로 처리하므로 재접속으로 역할이 복구된다고 보장하지 않습니다.

## 버전과 업데이트

현재 시험 조합이며 운영 안정성이 확정된 조합은 아닙니다. 정확한 출처·해시는 [설치 파일 기록](config/trial-artifacts.json)을 기준으로 합니다.

| 구성 | 버전 |
|---|---|
| Paper | 1.21.8 / build 60 |
| Java | 21 |
| LGMC 원본 | 5.1.3 |
| LGMC 시험 패치 | 5.1.3-bedrock-trial.2 |
| LgmcLobby | 0.3.0-trial |
| Geyser | 2.12.1 / build 1294 |
| Floodgate | 2.2.5 / build 141 |
| ProtocolLib | 5.4.0 |
| ViaVersion | 5.12.2 |

반영한 주요 업데이트는 무료 맵 교체, 준비·강퇴·시작 제어, 한국어 파일, 메시지 분리, 읽기 전용 미리보기, 아이템 23종 대응 조사입니다. 한국어 문자열·목록 항목 341개와 대기실·미리보기 문구 37개의 검사를 수행했습니다.

**아직 완료되지 않은 부분:** 일부 코드 고정 외국어 문구, 새 미리보기의 실제 양쪽 클라이언트 조작, Bedrock 팩·실행 매핑, 역할 카드 은닉, 4인 게임 완주와 다음 판 검증. `item-correspondence.json`은 조사표이며 실행 가능한 Geyser 매핑이 아닙니다.

날짜별 상세 업데이트와 선택 근거는 별도 변경 문서를 만들지 않고 [통합 기록](docs/PROJECT_LOG.md)에서 관리합니다.

## 시험 서버 실행

### 처음 복제한 환경에서의 주의 사항

**이 저장소는 아직 한 번의 명령으로 전체 시험 환경을 재현하는 설치 패키지가 아닙니다.** Git에서 제외한 맵·플러그인 JAR·Floodgate 키·생성 설정·화이트리스트 등을 별도로 준비해야 합니다. 버전 기록 파일은 자동 다운로드 도구가 아닙니다.

필요한 환경은 Docker·Docker Compose, 관리 스크립트용 Python 3, Minecraft EULA 동의입니다. 빌드는 고정 Java 21 컨테이너를 사용합니다.

1. `.env.trial.example`을 `.env.trial`로 복사합니다.
2. `config/trial-artifacts.json`을 참고해 이미지 다이제스트·Paper 버전·빌드를 채웁니다. EULA에 직접 동의한 후 값을 설정하고 고유한 RCON 비밀번호를 사용합니다.
3. 원본 및 필수 플러그인·맵·서버 생성 설정을 준비합니다. 수정 JAR는 공식 원본 다운로드 파일과 다르므로 아래 빌드 절차가 필요합니다.
4. Java 인증과 화이트리스트를 유지하고, Geyser의 `java.auth-type`을 `floodgate`로 설정합니다. 휴대폰 접속용 바인딩 주소와 Geyser 광고 주소는 실제 Mac의 LAN 주소에 맞춥니다.
5. 한글화는 `config/lgmc/lang/ko.yml`을 실행 폴더 `trial-data/plugins/lgmc/lang/ko.yml`에 배치하고 LGMC 설정을 `language: ko`로 지정합니다. 월드 변경 시 LGMC 좌표도 함께 맞춰야 합니다.

맵 반입·키·좌표·인증 설정의 전체 자동화는 아직 없으므로, 새 환경은 [통합 기록의 실제 적용 절차](docs/PROJECT_LOG.md)를 확인하며 준비해야 합니다.

### 이미 준비된 시험 환경

프로젝트 최상위 폴더에서 실행합니다. 이전 `docker-compose.yml`과 병합하지 않습니다.

```bash
# 시작
docker compose --env-file .env.trial -f compose.trial.yml up -d minecraft

# 상태
docker compose --env-file .env.trial -f compose.trial.yml ps

# 로그
docker compose --env-file .env.trial -f compose.trial.yml logs --tail 100 minecraft

# 저장을 동반하는 정상 종료
docker compose --env-file .env.trial -f compose.trial.yml stop minecraft
```

| 연결 | 주소·포트 |
|---|---|
| 같은 Mac의 Java | `localhost:25566` |
| 같은 LAN의 Bedrock | 설정한 Mac 내부 IP, 포트 `19133` |
| RCON | 컨테이너 내부에서만 사용, 외부 포트 미공개 |

현재 시험 Geyser 구성은 Bedrock용 TCP·UDP를 모두 바인딩합니다. 예시 환경 파일의 기본값은 로컬 전용입니다. 휴대폰 접속에는 LAN 설정이 필요하며 공유기 포트포워딩·인터넷 공개·HTTPS는 현재 구성에 포함되지 않습니다.

## 명령어와 시험

| 명령 | 대상·기능 |
|---|---|
| `/ready` | 준비·준비 취소 |
| `/lobby` | 준비 버튼 메뉴 |
| `/lgpreview` | 관리자 본인에게 한국어 견본 메뉴 표시 |
| `/lgpreview <정확한 접속 이름>` | 관리자가 다른 접속자에게 견본 메뉴 표시 |
| 콘솔의 `lobby` | 인원·준비 수·시작·게임 상태·제한 시간 출력 |

미리보기 권한은 `lgmclobby.preview`이며 기본은 관리자(op)입니다. 휴대폰 이용자에게 op를 주지 않아도 관리자가 해당 이용자의 화면에 메뉴를 열 수 있습니다. Floodgate 이름은 실제 서버에 표시된 이름을 사용합니다.

두 계정 시험에서는 먼저 양쪽에서 `/ready`를 입력하세요. 미리보기를 열어도 미준비 제한 시간은 멈추지 않습니다. 미리보기는 게임·카운트다운 중 사용할 수 없으며, 견본 아이템 이동을 막도록 구현했습니다. 견본은 실제 역할을 지급하거나 실제 투표·능력을 처리하지 않습니다.

| 검증 항목 | 현재 판정 |
|---|---|
| PC 로그인·모바일 접속 유지 | 확인 |
| 새 맵·준비 메뉴·미준비 강퇴·4명 미만 시작 방지 | 두 계정 사용자 확인 |
| 한국어 파일·치환값·내부 참조 검사 | 통과 |
| 새 미리보기 빌드·서버 로드·명령 등록 | 통과 |
| 새 미리보기 PC·모바일 화면·클릭 | 확인 대기 |
| 4인 역할·밤낮·투표·승패·다음 판 | 미검증 |
| 모바일 전용 팩 다운로드·정보 은닉 | 미구현·미검증 |

## 소스 빌드와 검사

```bash
python3 scripts/build-trial-plugins.py
python3 scripts/verify-trial-artifacts.py
```

빌드는 `trial-data/original-plugins/lgmc-5.1.3.jar`와 시험 서버가 받은 Paper 라이브러리를 사용합니다. 원본 JAR 해시를 확인하고 고정 Java 21 이미지로 컴파일해 `trial-data/lobby-build/`에 JAR와 보고서를 생성합니다. **자동으로 설치 파일을 교체하거나 서버를 재시작하지 않습니다.**

배포는 변경 전 파일 보관 → 정상 종료 → 새 JAR 교체 → 설정 반영 → 기록 해시 갱신 → 재시작·검사 순서로 진행합니다. 이전 버전 JAR와 새 JAR를 plugins 폴더에 동시에 두지 않습니다. 플러그인 핫리로드는 사용하지 않습니다.

`ValidateTranslations.java`는 시험 서버와 같은 SnakeYAML 라이브러리를 사용해 번역 키·치환값·참조를 검사합니다. 로컬 Java 21이 있으면 다음과 같이 실행할 수 있습니다.

```bash
java --class-path trial-data/libraries/org/yaml/snakeyaml/2.2/snakeyaml-2.2.jar scripts/ValidateTranslations.java
```

해시·컴파일·번역 구조 검사와 실제 게임 시험은 별개입니다. 빌드 결과의 해시는 빌드 보고서에서 확인하고, 설치할 파일과 관리 기록을 함께 갱신합니다.

## 진행 계획

현재 게임 준비는 **기능 조사 → 기존 맵 활용 → 한글화 → 모바일 팩 → 준비 기능 → 실기 시험 → 운영 구축**으로 진행합니다. 사용자 요청에 따라 준비 기능을 한글화·모바일 팩보다 먼저 구현했습니다.

전체 운영 구축의 진행 상황은 다음과 같습니다.

| 단계 | 목표 | 상태 |
|---|---|---|
| 1 | Paper·LGMC·PC/모바일 혼합 게임 검증 | 진행 중. 4인 완주 대기 |
| 2 | 구성 분리·버전 고정 | 독립 시험 구성을 선행 구현 |
| 3 | Discord·독립 서버 제어 | 미구현 |
| 4 | Alloy·Loki·Grafana 로그 조회 | 미구현 |
| 5 | 성능 지표·장애·복구 알림 | 새 구성 미구현 |
| 6 | 백업·복원·외부 HTTPS | 변경 전 로컬 백업만 수행 |
| 7 | 장시간 운영·재부팅·재연결 시험 | 미실시 |

다음은 한국어 미리보기 확인, 모바일 카드 1~2개 견본 팩, 본인 표시·상대 역할 은닉 시험입니다. 이 시험을 통과한 뒤 필수 자산으로 확대하고 4인 실전을 검증합니다. 최소 인원을 낮추거나 가짜 플레이어를 사용한 결과를 4인 게임 통과로 기록하지 않습니다.

## Git 관리와 데이터 보존

| Git에 포함 | Git에서 제외 |
|---|---|
| Java 소스·빌드/검사 스크립트 | 실제 `.env`·토큰·비밀번호 |
| Compose·비밀값 없는 설정 예시 | Floodgate 키·인증서 개인키 |
| 언어 파일·출처·버전·해시 | `trial-data/`·월드·실행 JAR·플레이어 데이터 |
| README·통합 기록·조사 자료 | `backups/`·로그·DB 실행 데이터 |

`.gitignore`는 새 파일의 추적을 막지만 이미 추적한 파일이나 과거 커밋의 내용을 지우지는 않습니다. 공개 저장소로 올리기 전에는 포함될 변경과 기존 이력을 확인해야 합니다. 이 문서 작성 작업은 커밋·태그·원격 푸시를 수행하지 않습니다.

소스 버전과 설치 JAR 버전을 함께 기록합니다. 시험 버전에는 `trial` 표시를 유지하고, 실제 검증 범위를 업데이트에 명시합니다. README는 저장소의 입구이며, 상세 변경 이력은 [PROJECT_LOG.md](docs/PROJECT_LOG.md) 한 곳에 유지합니다.

Git은 월드 백업을 대신하지 않습니다. 현재 변경 전 백업은 같은 Mac에 있으므로 디스크 고장 대응용 별도 사본·보존 정책·복원 실기는 추가 구현이 필요합니다. 맵 교체는 월드뿐 아니라 LGMC 좌표·대기실 설정과 함께 관리합니다.

## 문서와 출처

- [진행·변경·판단 근거·다음 계획 통합 기록](docs/PROJECT_LOG.md)
- [게임 검증 체크리스트](docs/phase-1-validation.md)
- [대기실 플러그인 사용·빌드 안내](lobby/README.md)
- [LGMC 패치 범위와 복귀 안내](patches/lgmc/README.md)
- [LGMC 원본 코드·팩 조사](docs/research/first-audit.md)
- [LGMC 원본 저장소](https://github.com/DarkShoro/lgmc) / [원본 MIT 라이선스](patches/lgmc/LICENSE)
- [무료 시험 맵 제작자 페이지 — AnnaMayStudios](https://www.planetminecraft.com/project/werewolf-mafia-game-in-minecraft-1-20-1-21/)
- [Geyser 리소스팩 안내](https://geysermc.org/wiki/geyser/packs/)

LGMC 코드의 라이선스와 맵·외부 리소스팩 자산의 이용 조건은 별개입니다. 맵 바이너리와 원본 CDN 팩을 이 저장소에 포함하지 않습니다. 모바일 팩의 원본 자산 이용 조건이 확인되지 않으면 자체 견본 자산으로 기술 시험을 진행합니다.
