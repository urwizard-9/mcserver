# 첫 조사 결과와 적용 방향

조사일 2026-11-07 KST. 대상은 공식 LGMC v5.1.3 소스, 공개 1.21 리소스팩, 현재 시험 빌드다. 운영 게임 완주 검증과 구분한다.

## 자동 시작: 명령 호출만으로는 부족한 부분

공식 소스 기준:

- `commands/LGCommand.java:95` — 시작 명령은 온라인 전원 수를 세며 최소 4명을 직접 검사한다.
- `managers/GameManager.java:734` — 시작 시 리셋 후 별도 10초 카운트다운을 실행한다. 설정의 countdown-duration 값을 이 경로에서 쓰지 않는다.
- `GameManager.java:767,988` — 역할 배정과 참가 목록 모두 온라인 전원 기반이다.
- `GameManager.java:1646,1876` — 공개 `isInGame()`과 `getIsStarting()`이 있어 상태 읽기가 가능하다. 별도 게임 수명주기 Bukkit 이벤트는 조사한 소스에서 찾지 못했다.
- 원본은 시작 타이머 취소 핸들을 보관하지 않으며 `gameReset`이 시작 플래그·시작 타이머를 정리하지 않는다. 시작 직전 4명 미만이 되면 inGame을 먼저 true로 만든 뒤 역할 배정을 중단하는 경로가 있다.

사용자 결정: 별도 참가자 목록을 만들지 않고 온라인 전원을 참가자로 유지. 미준비자는 일정 시간 후 강퇴. 이를 `LgmcLobby`에 구현하고 LGMC는 시작 중복 방지·카운트다운 취소·인원 변경 검사·검증 이후 inGame 전환만 보완한다. 모바일 리소스팩 예외 처리는 유지한다.

초기 제한 시간은 설정 가능한 120초. 원본 10초 카운트다운 유지. 원본의 역할·투표·승패 로직은 이번 수정에서 변경하지 않는다.

## 맵: 반드시 스폰 12곳

`LocationManager.java:173-185`는 스폰 수가 12 이상인지 검사한다. 따라서 4명이 게임해도 12곳을 등록해야 한다. 이전의 '4곳부터' 안내를 정정한다. 스폰 키는 1~12 연속으로 구성한다. `GameManager`는 시간 변경에서 world 이름을 직접 사용하므로 시험 맵도 `world`를 유지한다.

## 이탈과 재접속

`listeners/GameListener.java:153`는 살아 있는 플레이어의 이탈을 `killPlayer(...,"disconnect")`로 처리한다. 재접속으로 생존 역할 복구가 보장되는 구조가 아니다. 초기 운영 규칙에는 '게임 중 이탈은 탈락'을 명시하고 유예 복구는 별도 기능으로 다룬다.

## 한글화

`LanguageManager.java:68-143`는 존재하는 `lang/ko.yml`을 읽을 수 있다. config의 language를 ko로 바꾸면 된다. 기본 자동 배포 목록은 fr/en뿐이므로 ko 파일을 별도 관리해야 한다. 영어 파일은 472줄이며 역할·GUI·오류·안내 메시지가 포함되어 있다. 키 개수는 별도 측정하지 않았다.

`ChatListener`의 빈 연인 메시지, `LGCommand`의 스킨 시험 안내, `UpdateChecker` 안내 등 일부 문장은 코드에 고정돼 있다. 번역은 파일 기반 메시지부터 진행하고 실제 출력의 누락 키·치환값·색상·줄바꿈을 검사한다. PC와 모바일은 같은 ko.yml을 쓴다. 이번에 추가한 대기실 안내는 한국어지만 기존 LGMC 전체 번역은 아직 하지 않았다.

`/lg gui`는 debug 권한으로 화면을 열지만 게임 데이터가 없는 상태에서 버튼을 누르면 안전하다고 보장할 수 없다. 예를 들어 VoteGUI의 기권 처리는 getGamePlayer 반환값을 사용한다. 2인 메뉴 시험은 별도 안전한 미리보기나 시험 데이터를 준비해 진행해야 한다.

## 리소스팩: 그림 외에 정보 은닉도 필요

현재 URL `https://cdn.eradium.fr/lgrsp/public/1.21.zip`을 다운로드해 검사했다. 파일 619개, 모델 JSON 20개, PNG 589개, OGG 2개. 전체 목록과 SHA-256은 resource-pack-inventory.json에 기록했다.

- CustomModelData 중심 구조가 아니라 red_dye/green_dye 등 바닐라 아이템 모델 자체를 덮어쓴다.
- 역할 카드를 offhand에 배치한다. red_dye 모델은 third-person 양손 및 head scale이 0이다. 모바일에서는 카드 외형뿐 아니라 다른 사람이 역할을 보지 못하는지도 확인해야 한다.
- wooden_hoe/iron_hoe 모델과 `custom:gun`, `custom:coin_flip` 소리가 있다.
- VoteGUI는 플레이어 머리와 기권 아이템이 있는 ChestGui를 사용한다. 화면을 열 수 있는지와 실제 투표 처리 성공은 별도 검증이다.
- pack.mcmeta는 제작자를 Mister Nox로 표시한다. 팩 내 별도 LICENSE/README/credits 파일은 찾지 못했다. LGMC 코드의 MIT 라이선스가 이 별도 CDN 자산의 변환·재배포 조건까지 증명하는 것은 아니다.

Geyser 공식 문서는 Java 팩 및 매핑을 자동 변환하지 않는다고 명시한다. 바닐라 텍스처 변경과 커스텀 모델·비밀 카드 표현을 분리해 대응한다. 현재 구조에서는 일반적인 CustomModelData 예제를 그대로 적용하지 않는다. 작은 샘플 카드로 본인 표시/상대 은닉을 먼저 검증한 후 팩과 custom_mappings를 확장한다. 배포 조건이 불분명한 자산은 재배포 조건 확인 또는 자체 대체 자산을 선택한다.

## 근거

- https://github.com/DarkShoro/lgmc/tree/v5.1.3/src/main
- https://github.com/DarkShoro/lgmc/blob/v5.1.3/src/main/java/fr/lightshoro/lgmc/managers/GameManager.java
- https://github.com/DarkShoro/lgmc/blob/v5.1.3/src/main/java/fr/lightshoro/lgmc/managers/LocationManager.java
- https://github.com/DarkShoro/lgmc/blob/v5.1.3/src/main/java/fr/lightshoro/lgmc/managers/LanguageManager.java
- https://geysermc.org/wiki/geyser/packs/
- https://geysermc.org/wiki/geyser/custom-items/
- https://geysermc.org/wiki/other/rainbow/

이번 서버 적용 범위는 무료 맵·스폰·몹 제한·별도 준비 플러그인·최소 시작 안전장치다. LGMC 한글화와 Bedrock 팩 변환은 조사 결과를 바탕으로 진행할 다음 작업이다.
