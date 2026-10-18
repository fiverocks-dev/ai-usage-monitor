# AI Usage Android — Mock MVP

> **개발 중 / 예시 데이터 전용.** 실제 Claude Code, Codex, Antigravity 계정에 연결하지 않습니다.

## 포함된 기능
- Kotlin + Jetpack Compose 기반 대시보드 및 Mock 새로고침
- Claude Code, Codex, Antigravity Gemini, Antigravity Claude 각각의 quota 표시
- `null`(확인되지 않음)과 실제 `0%` 구분
- Stale, Offline, Unsupported 상태
- Glance 홈 화면 위젯과 비밀정보 없는 SharedPreferences 캐시
- 표시율 경계 검증 단위 테스트

## 빌드
Android Studio에서 `apps/android/` 디렉터리를 프로젝트로 열어 JDK 17과 Android SDK 35로 Gradle 동기화 후 실행합니다. Gradle wrapper 실행 파일과 jar는 아직 포함하지 않았으므로, 신뢰 가능한 로컬 Gradle 설치 또는 Android Studio의 Gradle 구성으로 실행해야 합니다.

```bash
cd apps/android
gradle :app:assembleDebug :app:testDebugUnitTest
```

**중요:** GitHub에 코드가 작성된 것과 Android SDK를 사용한 빌드 성공은 다릅니다. 실제 SDK/에뮬레이터 빌드 검증은 후속 작업입니다.

## 보안 및 범위
앱은 INTERNET 권한을 선언하지 않습니다. OAuth, 토큰, 쿠키, 비공식 API, 실제 백그라운드 polling, 백엔드, iOS 연동이 없습니다. 위젯은 Mock 저장 데이터만 읽습니다.

- 추적: [Issue #13](https://github.com/fiverocks-dev/ai-usage-monitor/issues/13)
- 설계: [PR #12](https://github.com/fiverocks-dev/ai-usage-monitor/pull/12)
