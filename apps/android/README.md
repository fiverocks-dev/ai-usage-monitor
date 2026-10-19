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

PR의 **Checks → Android Mock CI**에서 APK 빌드와 JVM 단위 테스트 결과를 확인할 수 있습니다. **Actions → Android Mock CI → Run workflow**에서 수동 실행도 가능합니다. 워크플로는 빌드와 단위 테스트에 성공한 경우에만 **3일 보관되는 QA 전용 APK 아티팩트**를 업로드합니다. 실행 화면의 **Artifacts**에서 `ai-usage-android-mock-<전체 커밋 SHA>` ZIP을 받아 `ai-usage-android-mock.apk`를 설치합니다. ZIP에는 `source-repository.txt`, `source-commit.txt`, `SHA256SUMS`가 동봉됩니다. **PR CI는 합성 병합 커밋(`pull/.../merge`)을 빌드할 수 있으므로 아티팩트 이름의 SHA를 PR 브랜치 HEAD SHA와 동일시하지 마세요.** 실제 빌드 SHA는 `source-commit.txt`에 기록됩니다. 이는 디버그 서명 Mock 앱으로, 정식 릴리스나 Play 배포가 아닙니다. Android 에뮬레이터 자동 UI 테스트는 아직 없습니다.

**중요:** 소스 작성만으로 빌드 통과를 주장하지 않으며, 해당 커밋의 Actions 결과를 확인해야 합니다.

## 보안 및 범위
앱은 INTERNET 권한을 선언하지 않습니다. OAuth, 토큰, 쿠키, 비공식 API, 실제 백그라운드 polling, 백엔드, iOS 연동이 없습니다. 위젯은 Mock 저장 데이터만 읽습니다.

- 추적: [Issue #13](https://github.com/fiverocks-dev/ai-usage-monitor/issues/13)
- 설계: [PR #12](https://github.com/fiverocks-dev/ai-usage-monitor/pull/12)

## QA 기록 (2026-10-19)

- 검증 커밋: `ef481cfbf7dbd91dd3dfa938f6e2c4afd162d35e`.
- [Android Mock CI #39236293679](https://github.com/fiverocks-dev/ai-usage-monitor/actions/runs/39236293679): **success** — Android Debug APK 빌드 및 JVM 테스트.
- [Windows CI #39236293677](https://github.com/fiverocks-dev/ai-usage-monitor/actions/runs/39236293677): **success** — 기존 Windows 회귀 검사.
- 실기기 사용자 확인: 앱 설치, 홈 화면 위젯 추가, 앱에서 Mock 갱신, **짧은 지연 후 위젯 값 갱신**.
- 미확인: 지연 시간의 수치 측정, 여러 런처/Android 버전, 프로세스 종료·재부팅 후 복구, 장시간 안정성.
- 갱신은 `SharedPreferences.commit()` 성공 확인 후 `GlanceAppWidget.updateAll()`을 요청하며 앱 화면에 갱신 요청 결과를 표시합니다. 앱의 요청 완료 메시지는 실제 런처 렌더 완료를 보증하지 않습니다.

## 다음 단계

Android 앱과 위젯의 스냅샷 일치성·갱신 상태 진단을 강화한 뒤 실기기 회귀 테스트를 반복합니다. 실제 Provider API 및 인증정보 연동은 승인 근거가 확인되기 전까지 구현하지 않습니다.

## 위젯 갱신·접근성 보완 (2026-10-20)

- 빠르게 연속 갱신할 때 코루틴이 변경된 `sequence`를 참조하여 이전 요청을 최신 번호로 잘못 표시할 가능성을 줄였습니다. 각 요청의 번호를 별도로 캡처합니다.
- 각 quota 행에 Provider·window·잔여율·상태를 결합한 화면 읽기용 설명을 제공합니다.
- **제한 사항:** 앱의 '위젯 갱신 요청 완료'는 Glance 업데이트 호출 반환을 의미하며, 런처가 실제로 렌더링을 끝낸 시점은 아닙니다.
- 추가 실기기 QA: 빠른 연속 갱신, TalkBack 읽기, 런처 재시작, 재부팅 후 위젯 유지 및 오래된 값 표기를 점검해야 합니다.
