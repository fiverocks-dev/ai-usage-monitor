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
Android Studio에서 `apps/android/` 디렉터리를 프로젝트로 열어 JDK 17과 Android SDK 35로 Gradle 동기화 후 실행합니다. Gradle wrapper 실행 파일과 jar는 아직 포함하지 않았습니다. 로컬 Gradle 8.9 또는 Android Studio의 동등한 Gradle 설정으로 실행합니다. GitHub Actions에서는 Gradle 8.9를 자동 설치합니다.

```bash
cd apps/android
gradle :app:assembleDebug :app:testDebugUnitTest
```

## GitHub Actions 빌드

PR의 **Checks → Android Mock CI**에서 APK 빌드와 JVM 단위 테스트 결과를 확인할 수 있습니다. **Actions → Android Mock CI → Run workflow**에서 수동 실행도 가능합니다. 이 워크플로는 일반 CI 검증이므로 APK 파일을 다운로드 아티팩트로 게시하지 않습니다. Android 에뮬레이터/실기기 위젯 테스트는 별도입니다.

**중요:** 소스 작성만으로 빌드 통과를 주장하지 않으며, 해당 커밋의 Actions 결과를 확인해야 합니다.

## 보안 및 범위
앱은 INTERNET 권한을 선언하지 않습니다. OAuth, 토큰, 쿠키, 비공식 API, 실제 백그라운드 polling, 백엔드, iOS 연동이 없습니다. 위젯은 Mock 저장 데이터만 읽습니다.

- 추적: [Issue #13](https://github.com/fiverocks-dev/ai-usage-monitor/issues/13)
- 설계: [PR #12](https://github.com/fiverocks-dev/ai-usage-monitor/pull/12)
