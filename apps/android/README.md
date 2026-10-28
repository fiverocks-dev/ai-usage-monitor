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

## 데이터 신선도 표시 (2026-10-20)

- 마지막 성공 조회 시각이 15분보다 오래된 `FRESH` 데이터는 앱과 위젯에서 `오래된 데이터`로 표시합니다.
- `OFFLINE`, `UNSUPPORTED`, `AUTH_REQUIRED` 등 명시적 상태는 시간 경과만으로 변경하지 않습니다.
- 마지막 조회 시각이 없는 `FRESH` 스냅샷은 정상으로 표시하지 않습니다.
- 시간 경과만으로 홈 화면 위젯이 자동 재렌더링되는 것은 **보장하지 않습니다**. 현재는 앱 내 Mock 갱신과 시스템의 위젯 재생성 시점에 신선도를 다시 평가합니다. 실시간 신선도 갱신 예약은 후속 구현 대상입니다.
- 정책 판단 단위 테스트를 추가했습니다. 자동 CI 결과와 실기기 QA는 별도로 판단합니다.

## 위젯 상태 표시 정확성 (2026-10-20)

- 기존 위젯은 첫 번째 비정상 상태만 별도 표시하므로 같은 Provider의 여러 기간에 다른 상태가 있는 경우 혼동될 수 있었습니다.
- 이제 기간별로 `5h: 0% (정상)`, `7d: 45% (오래된 데이터)`, `model: — (미지원)`처럼 각각의 상태를 출력합니다.
- 이 표현은 `widgetText()` 순수 함수에서 만들고 경계값 단위 테스트를 실행합니다.
- **재부팅·런처 재생성·15분 시점의 자동 리렌더링은 아직 실기기 검증 또는 구현 완료로 간주하지 않습니다.** 위젯은 Glance의 시스템 갱신 요청과 앱 갱신 시 표시를 다시 계산합니다.
- 사용자 실기기 QA에선 앱 갱신과 위젯 반영 사이 짧은 시간차가 관찰되었습니다. 정량적 지연 보장 및 주기적 자동 갱신 기능은 아직 없습니다.

## Provider 권한 게이트 (2026-10-20)

- `ProviderCapability`에 **인증 허용**, **quota 조회 허용**, **배포 허용**의 독립 판정을 추가했습니다.
- 네 Provider 모두 현재 `UNKNOWN`이며, 단일 로그인 가능성만으로 실제 quota 어댑터를 활성화할 수 없습니다.
- 코드에는 실제 인증/HTTP 연결을 추가하지 않았습니다. JUnit 테스트로 게이트 조건을 확인합니다.
- 상세 근거와 구현 단계: [Provider 연동 계획 v1](https://github.com/fiverocks-dev/ai-usage-monitor/blob/docs/mobile-oss-research-20261015/docs/architecture/provider-integration-plan-v1.md), [Issue #15](https://github.com/fiverocks-dev/ai-usage-monitor/issues/15).

## 로컬 JSON 가져오기 (2026-10-20)

1. [예시 JSON](./sample-quota.json)을 Android 휴대폰에 저장합니다.
2. 앱에서 **JSON 가져오기**를 누르고 해당 파일을 선택합니다.
3. 앱의 `데이터 출처: LOCAL_JSON`과 위젯 상단 `LOCAL_JSON` 표시를 확인합니다.
4. 새로 `Mock 갱신`을 누르면 예시 데이터로 전환됩니다.

- 로컬 파일 선택기(`ACTION_OPEN_DOCUMENT`)만 사용하며, 네트워크·OAuth·인증정보 수집은 없습니다.
- 사용자 선택 파일이므로 **값의 진위·제공자 승인 여부가 검증되었다는 의미가 아닙니다**. 가져온 값도 실시간 연동으로 표시하지 않습니다.
- 최대 64KB, 스키마 버전 1, 1~32개 스냅샷, 알려진 Provider/상태만 허용합니다. JSON에 인증정보 등 계약에 없는 필드가 있으면 거부합니다.
- 파일을 영구적으로 감시하거나 외부 변경을 자동 반영하지 않습니다. 캐시에는 UI 표시용 데이터만 저장합니다.
- JSON 샘플의 날짜는 예시 타임스탬프이므로 과거 자료면 `오래된 데이터`로 표시됩니다.
- **현재는 최초 구현 단계:** Android CI 성공과 별개로 파일 선택·오류 UI·위젯 반영은 실기기에서 추가 검증해야 합니다.

## 제품 범위 확정 — Android 단독 직접 조회 (2026-10-20)

- 현재 단계의 목표 플랫폼은 **Android만** 해당합니다. iOS는 일정 미정 보류.
- **Windows/PC 중계, 데스크톱 CLI 추출, LAN 브리지, PC 파일 전달, 자체 서버는 최종 제품에서 사용하지 않습니다.** 과거 연구상 대안이었던 경로는 폐기했습니다.
- 기존 `JSON 가져오기`는 **오프라인 개발 및 UI QA fixture** 기능이며 실제 Provider 연동 또는 권장 데이터 수집 경로로 취급하지 않습니다.
- 모바일에서 승인된 quota endpoint가 확인되기 전까지 네 Provider의 실제 데이터 조회는 비활성 상태로 유지됩니다. 앱은 각 Provider에 `직접 조회 미연결`을 명시합니다.
- 인증 성공만으로 구독 quota의 잔여율 조회 권한이 확보되었다고 판단하지 않습니다.

## Android에서 실제 사용량 열기 (2026-10-20)

- **Claude 실제 사용량 보기**: 휴대폰의 기본 브라우저로 `https://claude.ai/settings/usage`를 엽니다. 구독 상태와 로그인 계정에 따라 표시가 다를 수 있습니다.
- **Codex 실제 사용량 보기**: 휴대폰의 기본 브라우저로 `https://chatgpt.com/codex/cloud/settings/analytics#usage`를 엽니다. 계정/워크스페이스에 따라 리디렉션되거나 화면이 다를 수 있습니다.
- **Antigravity**: Android 브라우저에서 검증된 공식 quota 조회 페이지가 없어 사용량 조회 버튼을 임의로 만들지 않았습니다.
- 브라우저의 세션 쿠키는 앱이 읽거나 저장하지 않습니다. 기존 Glance 위젯은 Mock 또는 수동 테스트 데이터만 표시하며 브라우저 사용량을 자동 동기화하지 않습니다.
- 이는 **실제 데이터를 계정의 공식 UI에서 직접 확인하는 동작**이며, 직접 quota API 연동 구현 완료를 의미하지 않습니다.

## 공식 사용량 링크 오류 수정 (2026-10-21)

- 사용자가 이전에 제공한 사용량 딥링크가 휴대폰에서 열리지 않는다고 보고함. 실제로 로그인/워크스페이스/모바일 라우팅에서 URL이 지원된다고 보증할 수 없어 딥링크를 철회함.
- 이제 `https://claude.ai/` 및 `https://chatgpt.com/`의 서비스 첫 화면만 엽니다. 로그인 후 **설정 → 사용량**으로 수동 이동합니다. 계정·요금제에 따라 사용량 메뉴의 이름과 표시 여부는 다를 수 있습니다.
- 이 접근법은 **브라우저 수동 확인용**일 뿐 Android 카드·위젯의 실제 quota 자동 조회 기능이 아닙니다.
- 브라우저를 열 수 없는 기기/프로필 또는 로그인 실패는 앱에서 해결할 수 없습니다. 모바일 사용성은 실기기로 다시 확인해야 합니다.

## 브라우저 실행 실패 처리 / CI 외부 다운로드 장애 (2026-10-21)

- 외부 브라우저 앱이 없으면 `브라우저 앱을 찾을 수 없습니다`, 기기 정책으로 차단되면 `기기 정책에 의해 차단`으로 표시합니다. 앱의 링크 실행은 공식 사이트 진입만 요청하며 사용량 화면을 자동으로 읽지 않습니다.
- CI [#39516125109](https://github.com/fiverocks-dev/ai-usage-monitor/actions/runs/39516125109)는 Maven Central의 `jsr305-3.0.2.pom` 다운로드 HTTP 403으로 실패했습니다. 동일 브랜치 앞선 [#39516105952](https://github.com/fiverocks-dev/ai-usage-monitor/actions/runs/39516105952)는 성공했습니다. 403은 정책/접근 거부일 수도 있으므로 원인 확인 없이 반복 재시도하거나 비공식 미러로 우회하지 않습니다.

## Codex 사용량 수동 입력 → 위젯 (2026-10-22)

1. Android 앱에서 **ChatGPT 열기**를 누르고 본인의 Codex 사용량 화면에서 5시간·주간 수치를 확인합니다.
2. **남은 비율**을 각각 0~100의 정수로 입력하고 **Codex 수동값 위젯에 적용**을 누릅니다. 웹 화면이 사용한 비율이라면 `100 - 사용한 비율`로 변환해야 합니다.
3. 위젯 상단 **MANUAL_CODEX**와 하단 **사용자 수동 입력 · 자동 갱신 안 됨** 표시를 확인합니다.
4. 이 상태에선 저장한 Codex의 5h/7d 두 항목만 표시되며 다른 Provider의 Mock 값과 섞지 않습니다.

- 이 기능은 실제 계정 수치를 **사용자가 직접 입력**해 위젯에 반영하는 보조 경로입니다. 앱이 사용량을 자동 조회하거나 웹 로그인 쿠키를 읽는 기능이 아닙니다.
- 새로고침되지 않으면 15분 경과 후 기존 stale 표시 정책에 따라 오래된 데이터가 됩니다(위젯 리렌더 시 계산). reset 시간은 확인·입력하지 않았으므로 표시하지 않습니다.
- 저장은 앱 로컬 캐시에만 적용되고 백업은 꺼져 있습니다. 공식 Provider 조회 권한과는 무관합니다.
- Android 빌드와 별도로 실기기에서 키보드 입력·캐시 유지·위젯 갱신을 테스트해야 합니다.

## Codex app-server Rate Limit 응답 디코더 (2026-10-22)

- 공식 Codex 앱서버 JSON-RPC `account/rateLimits/read`의 공개 응답 구조를 참고해 `CodexRateLimitParser` 순수 파서를 추가했습니다.
- 300분 윈도우를 `5h`, 10080분을 `7d`로 식별하고 **사용한 비율(usedPercent) → 남은 비율(remainingFraction)**로 변환합니다. 순서로 5h/7d를 추정하지 않습니다.
- 응답 오류, 알 수 없는 기간, 비율 범위 오류, 중복은 거부하며 단위 테스트를 포함합니다.
- 이 코드는 **파서만 구현**한 것입니다. 공개 app-server 프로토콜을 외부 Android 공개 API 또는 모바일 OAuth quota 권한과 혼동하지 않습니다. 아직 인증·네트워크 어댑터나 자동 동기화는 없습니다.
- 실제 계정에서 인증된 스냅샷을 전달받는 합법적 모바일 경로가 확보되기 전에는 `CodexRateLimitParser.parse`를 위젯의 자동 새로고침 경로에 연결하지 않습니다.
- 출처: [Codex 공식 공개 app-server protocol](https://github.com/openai/codex/blob/main/codex-rs/app-server-protocol/src/protocol/common.rs).

## Codex 여러 모델 사용량 혼동 방지 (2026-10-22)
- `rateLimitsByLimitId`가 존재하면 `codex` 버킷의 존재를 필수로 검사합니다.
- `codex`가 없는데 과거 `rateLimits` 단일 버킷으로 대체하면 **다른 모델 한도를 Codex로 오인할 위험**이 있으므로 거부합니다.
- 회귀 테스트를 추가했습니다. 이 조치는 **파서 안전성 개선**이며 모바일 실계정 API 연결은 아직 수행하지 않았습니다.

## Codex 사용량 URL 확정 (2026-10-23)

- 사용자 실기기에서 로그인 및 5시간·주간 한도 표시가 확인된 주소: `https://chatgpt.com/settings/usage?tab=overview`.
- Android 앱의 Codex 브라우저 버튼을 이 주소로 변경. 브라우저 로그인·표시 성공과 앱의 직접 데이터 수집 성공을 구별합니다.
- 공식 Help Center도 **Settings → Usage**에서 남은 quota/초기화 시간을 확인하라고 안내합니다: https://help.openai.com/en/articles/20001516-managing-usage-with-gpt-6-astra-in-work-and-codex
- 인증되지 않은 URL 직접 요청에서는 계정별 잔여율이 반환되지 않았으므로 브라우저 쿠키를 자동으로 읽거나 크롤링한 것으로 취급하지 않습니다.
- **남은 문제:** 공식적으로 허용된 Android 인증 세션과 quota 데이터 접근 인터페이스가 필요. 현재 실제 사용량 자동 수집은 미구현입니다.

## 3방식 Android 통합 진단 APK (2026-10-25)

이 빌드는 **개발자·실기기 QA 전용**입니다. 공개 Play 배포를 위한 접근성 승인 및 개인정보 평가를 완료한 버전이 아닙니다.

1. **OCR:** Chrome에서 Codex 사용량 화면 캡처 → 앱의 `1. 스크린샷 OCR 테스트` → 이미지 선택 → 로컬 OCR로 사용량 텍스트 후보 표시. 스크린샷 전체를 업로드하지 않음.
2. **접근성:** 앱의 `Chrome 화면 접근성 진단 허용` 체크 → `2. 접근성 권한 설정 열기` → AI Usage 접근성 서비스 승인 → Chrome에서 사용량 화면 확인 → 앱 복귀 후 `접근성 진단 결과 보기` 클릭. 테스트 후 **앱 체크박스 해제 및 OS 접근성 권한 해제**. Chrome에 표시된 UI 텍스트만 후보로 분석하며 Chrome을 닫았을 때 주기적 새 데이터를 가져올 수 없음.
3. **WebView:** `3. WebView 로그인/화면 테스트` → 사용량 페이지가 표시되면 `현재 보이는 페이지 텍스트 진단` 클릭. Chrome과 WebView 쿠키는 별개이며 제공사 로그인 정책상 차단될 수 있음. 인증 우회·쿠키 추출은 구현하지 않음.

화면 읽기는 **후보 진단**만 제공하고 자동으로 사용량 percentage를 확정하거나 위젯에 저장하지 않습니다. 잘못된 텍스트를 quota로 저장할 위험을 막기 위한 단계적 QA입니다. 이 3가지 방식 모두 API 없이 작동하지만 백그라운드 자동 조회를 보장하지 않습니다.

**주의:** 접근성 서비스는 Chrome 화면의 텍스트를 읽을 수 있는 민감한 권한입니다. 앱에서 진단 허용을 켠 동안에는 Chrome의 사용량 화면만 열어 테스트하고, 이후 두 권한을 모두 해제하세요. 네트워크 전송·임의 화면 원문 영구 저장·비밀번호 추출은 의도하지 않았으며, 현재 구현은 실기기 보안 검증 전의 QA 버전입니다.


## QA APK 보관 기간 및 재생성 (2026-10-28)

- Android CI가 성공했더라도 QA ZIP 아티팩트의 GitHub 보관 기간은 **3일**입니다. 보관 기간이 끝나면 해당 실행의 Artifacts 목록이 비게 됩니다.
- 필요한 경우 GitHub Actions에서 `Android Mock CI`의 `Run workflow`를 `feat/android-mock-mvp-13` 브랜치로 수동 실행하거나, 새 PR 변경에 따른 최신 실행의 아티팩트를 사용하세요.
- 새로 내려받은 QA ZIP에서 `source-commit.txt` 및 `SHA256SUMS`를 확인한 뒤 APK를 설치하세요. 오래된 아티팩트를 실제 최신 빌드로 오인하지 마세요.
