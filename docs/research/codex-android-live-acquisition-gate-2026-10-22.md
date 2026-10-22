# Codex Android 직접 수집 1단계 — 소스 수준 판정

검증일: 2026-10-22. 대상: PC·브리지·사용자 수동 입력 없이 Android에서 Codex 5시간·주간 실데이터 수집. 상태: **기술 경로 확인 / 우리 앱 실계정 수집 성공 미확인**.

## 실제 작동을 주장하는 OSS 2곳

- [guberm/Codex-Usage-Android](https://github.com/guberm/Codex-Usage-Android): Kotlin `CodexApi.kt`, `AuthModels.kt`, `SecureTokenStore.kt`. 기기 코드 발급 → 사용자가 모바일 브라우저에서 승인 → 토큰 교환/갱신 → `GET https://chatgpt.com/backend-api/wham/usage` → 기간별 데이터 파싱. 저장소의 README가 PC 없이 Android에서 직접 조회한다고 명시.
- [maplenk/claude-usage](https://github.com/maplenk/claude-usage): Codex 기기 코드 인증과 같은 WHAM 조회 방식, Android 자체 위젯. README상 Codex 주간 수치만 보장하므로 5h+7d 동시 지원 증거로 간주하지 않음.

## 구현 코드에서 확인한 사항

1. `CodexAuthClient.requestDeviceCode()`는 기기 코드를 발급받고, `pollDeviceCode()`가 사용자 승인을 기다림.
2. OAuth `CLIENT_ID`는 다른 앱에 하드코딩된 값. *우리 앱용으로 사용이 허용된 클라이언트 식별자라는 근거가 없음.* 무단 복사하지 않음.
3. `CodexUsageApi.fetch()`는 Bearer access token과 `ChatGPT-Account-Id`를 사용해 **비공개 `/backend-api/wham/usage`** 호출.
4. `SecureTokenStore`는 Android Keystore AES-GCM으로 토큰을 암호화하여 로컬 저장. 저장소가 암호화했다는 것만으로 OAuth client/endpoint 사용 허가가 생기지는 않음.
5. 이용 방법은 **모바일 직접 호출이며 Windows나 PC가 필수 아님**. 다만 클라우드 endpoint 계약이 공개 API로 보장되지 않음.

## 결론

- **기술적 타당성: 확인됨** (오픈소스 구현·구체적 요청 경로와 기능 주장 확인).
- **사용자 실계정에서 우리 앱의 HTTP 200 응답 확보: 미실시**. 이 기기/계정에 연결할 OAuth client registration, 로그인 승인, 테스트 권한이 제공되지 않음.
- **공식 지원된 제3자 모바일 quota-read scope/endpoint: 확인되지 않음**.
- 이번 1단계 성공 조건(우리 Android 앱에서 5h·주간 live 응답 수신): **NOT PASS**.
- 계정 로그인 성공과 웹 대시보드 5h/주간 표시 성공은 사용자 확인 사항. *앱의 인증된 quota JSON 수신 증거는 아님*.

## 필요한 진짜 다음 액션

- OpenAI 공식 지원 채널에 다음 범위의 권한을 질의: 우리 Android 앱에 대해 Codex 개인 구독 잔여량의 read-only API 사용이 허용되는지, Android OAuth client/device-code 등록 수단, quota scope 및 갱신 계약, 제3자 배포 정책.
- 승인된 경로가 확보되면 최소 권한 Android 전용 OAuth·Keystore·quota-reader를 구현하고, **실기기 네트워크 성공·5h/7d 파싱·WidgetCache 갱신·401/429 테스트**를 증거로 PASS 전환.
- 승인되지 않은 OAuth client ID 복사, 타 앱의 토큰 추출, 사용자의 ChatGPT 브라우저 쿠키 탈취 또는 내부 endpoint 호출을 성공의 근거로 삼지 않음.

## 관련 참고 링크

- [OSS Android 소스 — CodexApi.kt](https://github.com/guberm/Codex-Usage-Android/blob/main/app/src/main/java/com/guberdev/codexusage/CodexApi.kt)
- [OSS Android 소스 — SecureTokenStore.kt](https://github.com/guberm/Codex-Usage-Android/blob/main/app/src/main/java/com/guberdev/codexusage/SecureTokenStore.kt)
- [OpenUsage README](https://github.com/maplenk/claude-usage)
- [공식 Codex app-server](https://github.com/openai/codex/tree/main/codex-rs/app-server): 첫째 앱의 내부 클라우드 endpoint와 동일한 공개 Android API 계약은 아님.
