# Codex Android 직접 quota 조회 — 공식 OAuth 경계 판정

- 확인일: 2026-10-22
- 목표: PC·중계 서버·수동 입력 없이 Android 앱에서 Codex 개인 구독 5h/주간 남은 quota 조회
- 상태: **공식 OAuth OSS 클라이언트 등록 경로 확인 / Codex quota 읽기 스코프 및 공식 조회 API 미확인 → 실데이터 수집 NOT PASS**

## 공식 확인 사실

1. [OpenAI Sign in with ChatGPT — Registration and sign-in](https://developers.openai.com/siwc/token-sharing-open-source/sign-in)은 OSS 앱에 `client_id=dynamic_agent_client`를 사용한 동적 등록 경로를 안내한다. 발급받은 앱 전용 `client_id`를 저장하며 타 앱의 정적 client ID를 복사할 필요가 없다.
2. 공식 문서는 `openid profile email offline_access resource.invoke chatgpt.tokens.use.direct` 등을 설명한다. 여기에는 **Codex 개인 구독 quota 잔여량 읽기 스코프가 명시돼 있지 않다**.
3. 문서의 콜백 규약은 `http://127.0.0.1:<port>/auth/callback` 루프백으로 경로가 고정돼 있다. Android에서 이 루프백 방식의 안정적인 등록·복구·브라우저 전환을 실기기 검증 없이 성공으로 간주할 수 없다.
4. [OpenAI Accounts and sessions](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions)는 사용량 확인을 **ChatGPT Settings → Usage**로 안내한다. 공개 읽기 전용 개인 Codex 한도 조회 API가 따로 정의되어 있지는 않다.
5. [OpenAI Errors and recovery](https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery)는 `subscription_sharing_usage_limit_exceeded`가 앱 자체 한도일 수도 있으므로 **전체 사용자 구독 quota가 0%임을 추정하지 말라**고 안내한다.

## 확인한 OSS와의 차이

[guberm/Codex-Usage-Android](https://github.com/guberm/Codex-Usage-Android)의 Android 구현은 타 앱용 OAuth client ID 및 비공개 `https://chatgpt.com/backend-api/wham/usage`를 사용한다. 이는 Android 직접 조회의 기술적 선례지만, **우리 앱의 정식 OAuth/Endpoint 권한 근거가 아니다**.

## 승인 게이트

| 기준 | 현재 | 조치 |
|---|---|---|
| Android에서 공식 클라이언트 등록 | OSS 동적 등록 문서 확인 | Android 콜백 지원범위·스토어 정책 확인 필요 |
| 개인 Codex quota 읽기 scope | 문서상 미확인 | OpenAI 공식 답변 요청 |
| 5시간/주간 남은 비율 응답 | app-server 내부 프로토콜만 확인 | 승인된 클라우드 endpoint 필요 |
| 실제 계정 Android 앱에서 HTTP 응답 | 미실시 | 앞의 2개 확인 후 실시 |
| 0원 운영 서버 | 설계 가능 | 루프백·디바이스 보안만 허용 |

**중단 조건:** 로그인 권한만 받았거나 내부 API 호출 사례가 있더라도 quota 읽기 권한이 없으면 실계정 네트워크 연결을 활성화하지 않는다.

## 공식 지원 문의문 (영문)

Subject: Read-only Codex subscription quota access for a local Android open-source app

Hello OpenAI Developer Support,

We are developing a local-only, open-source Android quota widget. The user wants to view the remaining Codex five-hour and weekly subscription allowances, including reset timestamps, on their own phone, without any desktop bridge, hosted backend, manual entry, or third-party token/cookie reuse.

We reviewed Sign in with ChatGPT dynamic OSS client registration (dynamic_agent_client) and the documented scopes for identity and eligible Responses API usage. We could not find a documented read-only API/scope for the user's Codex subscription quota.

1. Is a read-only Codex subscription quota endpoint supported and authorized for third-party local Android apps? If so, what is the endpoint and OAuth resource/scope?
2. Is the documented dynamic OSS client registration and HTTP 127.0.0.1 loopback callback supported for native Android apps, or should another public-client OAuth flow be used?
3. Do responses contain separate five-hour and weekly remaining allowances and reset timestamps? What are the rate limits?
4. Are third-party store distribution and local-only open-source use permitted for such a quota monitor?

We will not reuse a Codex CLI client ID, extract browser cookies, or call undocumented account endpoints. We can share the public project repository and a minimal test plan if useful.

Thank you.

## 성공 판정에 필요한 증거

**PASS 조건:** 위 항목에 대한 공식 승인 → 우리 Android APK의 OAuth 인증 성공 → 공식 quota endpoint 실제 200 응답(비밀정보 제거 증거) → 5h/주간 필드 표시 → 위젯 동기화. 현재는 **NOT PASS**.
