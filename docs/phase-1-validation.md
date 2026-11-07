# 1단계: LGMC PC·모바일 혼합 플레이 검증

## 범위와 통과 기준

독립 시험 환경에서 Java PC와 Bedrock 휴대폰이 같은 판을 완료하고 다음 판을 시작해야 통과다. 프로세스 실행, 플러그인 로딩, 접속 성공만으로 통과 처리하지 않는다. 기본 최소 인원은 4명이며 실제 참가 계정과 단말이 필요하다. 운영자는 별도로 관찰하거나 관전자 역할을 시험한다.

확인된 사용자 조건: DarkShoro/lgmc 사용, PC Java 26.3, 휴대폰 버전 미확인. Minecraft EULA 동의 및 시험 서버 실행 승인 완료(2026-11-07).

## 시험 조합

| 구성 | 후보 | 선택 이유 |
|---|---|---|
| Paper | 1.21.8 build 60 | ProtocolLib 안정 배포판의 명시된 지원 범위 |
| Java | 21 | LGMC 및 Geyser 요구 조건 |
| LGMC | 5.1.3 | 실제 공개 배포 JAR 확인; README의 5.1.4와 다름 |
| ProtocolLib | 5.4.0 | LGMC plugin.yml의 필수 의존성 |
| ViaVersion | 5.12.2 | Java 26.3 클라이언트와 구버전 서버 연결 후보 |
| Geyser | 2.12.1 build 1294 | 조회 시 공식 배포 빌드 |
| Floodgate | 2.2.5 build 141 | 조회 시 공식 배포 빌드 |

이 표는 시험 조합이며 운영 호환성을 보증하지 않는다. 배포 파일은 고정 URL로 받아 SHA-256을 확인하고 `config/trial-artifacts.json`에 기록한다. 서버 이미지도 digest로 고정한다. 자동 최신 플러그인 다운로드는 사용하지 않는다.

## 격리와 접속

- `compose.trial.yml`을 단독 사용한다. 기존 Compose와 합치지 않는다.
- 시험 월드와 플러그인은 Git에서 제외한 `trial-data/`에 둔다. 기존 `mc-data/`와 분리한다.
- 기본 접속은 이 Mac에서만 Java `localhost:25566`. 휴대폰의 localhost는 휴대폰 자신이므로 사용하지 않는다.
- 다른 Java PC 시험 시 Mac의 LAN IP를 `.env.trial`의 `TRIAL_BIND_IP`에 넣는다. 휴대폰 시험은 별도 `TRIAL_BEDROCK_BIND_IP`에 Mac LAN IP를 넣고 재생성한다. Geyser의 `bedrock.signaling.advertise-addresses`도 `["Mac의LAN주소:19133"]`으로 함께 바꾼다. 같은 Wi-Fi에서 Mac LAN IP / 19133으로 접속한다.
- 이번 Geyser 빌드의 기본 전송 방식은 NetherNet이다. 내장 신호 서버는 TCP 19132, 실제 연결은 UDP 19132를 쓰므로 시험 호스트에 TCP·UDP 19133을 모두 매핑한다. 기존 계획의 UDP 단독 공개는 이 조합에 충분하지 않다. 휴대폰 버전을 확인한 뒤 실제 연결 방식을 검증한다. 시험 설정은 STUN 검색을 끄고 명시적인 로컬 주소를 알린다.
- 이번 단계에서는 공유기 포트포워딩을 하지 않는다. RCON 포트는 호스트에 공개하지 않는다.
- Java `online-mode=true`를 유지한다. Bedrock 인증은 Geyser의 `auth-type: floodgate`로 설정한다.
- Bedrock 채팅을 위해 `enforce-secure-profile=false`를 사용한다. Java 정품 계정 인증 해제와는 다르다.
- 화이트리스트를 켠다. 참가자 계정을 확인한 후 Java와 Floodgate 식별자를 구분해 등록한다. 비밀 역할 시험 계정에는 OP를 주지 않는다.

## 운영 명령

버전·비밀번호 설정 및 플러그인 설치 후 프로젝트 루트에서 실행한다.

```sh
docker compose --env-file .env.trial -f compose.trial.yml config --quiet
docker compose --env-file .env.trial -f compose.trial.yml up -d
docker compose --env-file .env.trial -f compose.trial.yml logs --tail=100 minecraft
docker compose --env-file .env.trial -f compose.trial.yml exec -T minecraft rcon-cli plugins
docker compose --env-file .env.trial -f compose.trial.yml stop
```

비밀번호가 출력될 수 있으므로 `config`를 `--quiet` 없이 공유하지 않는다. 정상 종료 명령은 월드 저장 시간을 최대 2분 제공한다. 실제 종료 로그도 확인한다.

## 게임 준비

생성된 설정을 확인하고 영어를 초기 시험 언어로 사용한다. 한국어화는 별도 작업이다. LGMC의 WebSocket 및 SkinsRestorer 연동은 초기 시험에서 끈다. 개인별 전적은 초기 범위 밖이므로 `advanced.save-stats`도 끈다. 스폰 위치는 기본 좌표를 신뢰하지 않고 현장에서 안전한 지형에 설정한다.

LGMC 명령 도움말을 먼저 확인한다. 공식 README 기준 `/lg setup spawn <번호>`, `/lg setup campfire`, `/lg setup chasseurtp`로 참가자 수만큼 위치를 등록하고 `/lg start`로 시작한다. 최소 4명인데 기본 설정 스폰은 2곳뿐이므로 추가 설정이 필요하다.

## 실기 판정표

아래는 판정 양식이다. 실제 수행 결과는 [프로젝트 통합 기록](PROJECT_LOG.md)에 기록한다. 각 결과에 시간(KST), 참가 계정 유형, 클라이언트 버전, 재현 절차와 로그 위치를 기록한다. 특정 역할이 등장하지 않은 경우 통과 대신 미실시로 남긴다.

| 항목 | 확인 내용 | 결과 |
|---|---|---|
| 플러그인 | LGMC 및 필수 플러그인 활성화, 로딩 오류 없음 | 미실시 |
| PC 접속 | Java 26.3 정식 계정으로 접속 | 미실시 |
| 모바일 접속 | 휴대폰 버전 기록, Floodgate 인증, 화이트리스트 | 미실시 |
| 투표 | 터치 선택·취소·마감·동점 처리 | 미실시 |
| 능력 | 모바일에서 등장한 역할의 능력 사용 | 미실시 |
| 비밀 정보 | 역할·늑대 채팅·연인 채팅의 수신 대상 확인 | 미실시 |
| 관전자 | 생존자에게 정보 전달·투표·능력 간섭 제한 | 미실시 |
| 재접속 | 밤·낮 이탈 및 재접속 후 역할과 진행 유지 | 미실시 |
| 판 종료 | 정상 승패 판정 후 다음 판 시작 | 미실시 |
| 저장·재시작 | 정상 종료 및 재시작 후 설정 유지 | 미실시 |

실기 시험에서 실패하면 2단계로 넘어가지 않고 재현 기록을 기준으로 수정하거나 버전을 변경한다. 진행 중 게임의 서버 재시작 복구는 보장하지 않는다.

## 공식 확인 출처

- https://github.com/DarkShoro/lgmc
- https://api.github.com/repos/DarkShoro/lgmc/releases
- https://github.com/dmulloy2/ProtocolLib/releases/tag/5.4.0
- https://github.com/ViaVersion/ViaVersion/releases/tag/5.12.2
- https://geysermc.org/wiki/geyser/supported-versions/
- https://geysermc.org/wiki/floodgate/setup/
- https://docker-minecraft-server.readthedocs.io/en/latest/types-and-platforms/server-types/paper/
