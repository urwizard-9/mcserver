# 원본 및 새 저장소 안내

- 원본: https://github.com/Hanbeeen/openSolves_mc
- 가져온 소스: main, 원본 커밋 식별자 0fe1d19 (GitHub 소스 아카이브)
- 가져온 날짜: 2026-11-07 (Asia/Seoul)
- 원본 Git 이력과 remote는 복사하지 않았습니다. 이 폴더는 별도 로컬 저장소입니다.

## 라이선스 확인

가져온 원본에는 LICENSE 파일이나 README의 명시적 라이선스가 없고 GitHub API의 license 값도 null입니다.
원본의 사용·수정·재배포 조건을 확인할 수 없으므로 공개 업로드나 재배포 전에 원저작자의 허가 또는 라이선스 안내를 확인하세요.
이 파일은 출처 기록이며 이용 허가나 새 라이선스를 부여하지 않습니다.

## GitHub 연결

GitHub에서 README, .gitignore, 라이선스를 추가하지 않은 빈 새 저장소를 만드세요.
연결 전 `git remote -v`가 비어 있는지 확인하고, 사용자 저장소 URL로만 origin을 추가하세요.

```sh
git config user.name "본인 이름"
git config user.email "본인 GitHub 이메일 또는 noreply 주소"
git add .
git diff --cached --stat
git commit -m "Initial import"
git remote add origin https://github.com/YOUR_ACCOUNT/YOUR_NEW_REPO.git
git remote -v
# 출력 주소가 본인의 새 저장소인지 확인한 다음 실행
git push -u origin main
```

## 비밀정보 및 실행 설정

`.env.example`을 `.env`로 복사하여 토큰을 입력하세요. `.env` 및 변형 파일, 로컬 DB, 서버 데이터는 Git에서 제외합니다.
실제 토큰·비밀번호를 소스나 docker-compose.yml에 직접 입력하지 마세요.
원본 docker-compose.yml에는 password/admin 기본 비밀번호와 공개 포트 설정이 있습니다. 서버 실행 전에 변경·검토하세요.
이번 작업에서는 서비스를 실행하거나 GitHub에 업로드하지 않았습니다.
