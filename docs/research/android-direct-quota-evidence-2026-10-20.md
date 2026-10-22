# Android 단독 quota 직접 조회 — 공식 근거 감사

- 조사일: 2026-10-20
- 관련: [Issue #15](https://github.com/fiverocks-dev/ai-usage-monitor/issues/15)
- 요구사항: Android 단독 / Windows·PC·파일 브리지·iOS·자체 서버 **제외**
- 판정 기준: **인증 승인, 개인 구독 quota 읽기 권한, 필드/윈도우 계약, 제3자 모바일 배포 승인**을 별도로 검토
- 조사 한계: 공식 공개 문서를 대상으로 한 근거 감사. 개인 계정으로 OAuth/HTTP 실호출하지 않음. 공개되지 않은 승인 프로그램의 부재를 증명하지 않음.

## 핵심 결과

| 대상 | 공식적으로 확인된 사용량 인터페이스 | 개인 구독 5h/주간 quota 직접 읽기 API | Android 앱 실제 구현 여부 | 판정 |
|---|---|---|---|---|
| Claude Code | Claude Code `/usage` 구독 잔여량 화면, 기업용 Analytics API | 제3자 개인 Pro/Max quota 접근 승인·스코프 미확인 | 미구현 | UNKNOWN |
| Codex | Codex CLI `/status`, 사용량 대시보드, 공식 SIWC 앱 로그인 | ChatGPT 구독 내 Codex 잔여 quota 접근 승인·스코프 미확인 | 미구현 | UNKNOWN |
| Antigravity Gemini | 공식 CLI Models & Quota / 상태줄 | 모바일 사용 가능 quota 엔드포인트·앱 인증 계약 미확인 | 미구현 | UNKNOWN |
| Antigravity Claude | Antigravity 모델별 quota 표시 | 독립 Claude bucket 접근·모바일 인증 계약 미확인 | 미구현 | UNKNOWN |

**공식 서비스가 직접 보여주는 UI는 외부 Android 앱이 그 수치를 읽도록 허가된 API가 있다는 증거가 아니다.**

## 공급자별 1차 근거

### Claude Code

- [Anthropic: Manage costs effectively](https://docs.anthropic.com/en/docs/claude-code/costs) — `/usage`에서 구독자에게 usage bars를 표시한다고 설명.
- 같은 문서는 Enterprise Analytics API의 `read:analytics` 범위를 설명하지만, 엔터프라이즈 사용자별 사용량·비용 리포트이며 개인 Pro/Max 구독의 5h/weekly 남은 비율을 가져온다고 명시하지 않는다.
- [Anthropic: Set up Claude Code](https://docs.anthropic.com/en/docs/claude-code/getting-started) — Console API와 Claude Pro/Max 구독 인증 구분.
- 결론: `/usage` UI·비용/토큰 Analytics를 개인 구독 quota API로 대체하지 않는다.

### Codex

- [OpenAI: Sign in with ChatGPT — Overview](https://developers.openai.com/siwc/token-sharing-open-source) — 제3자 OSS 앱에 공식 OAuth 및 적격 Responses API 요청 흐름. 이 허가는 대화/계정 임의 접근이나 구독 quota 조회를 자동 부여하지 않는다.
- [OpenAI: Codex app-server](https://developers.openai.com/siwc/token-sharing-open-source/codex-app-server) — 인증된 Responses 추론 흐름 안내. `model/list` 결과도 entitlement check로 보지 말라고 명시.
- [OpenAI: 현재 사용 한도 확인 방법](https://developers.openai.com/ko-KR/docs/pricing) — 사용량 대시보드 또는 CLI `/status` 안내.
- [OpenAI: API rate limits](https://developers.openai.com/api/docs/guides/rate-limits) — `x-ratelimit-remaining-*`은 API 요청·토큰 단위이며 개인 Codex 5h/주간 quota와 동일하다고 가정할 수 없음.
- 결론: 모바일 OAuth 성공 ≠ 개인 Codex quota 읽기 권한. 임의 호출이나 429 오류로 구독 quota 추정 금지.

### Antigravity Gemini / Claude

- [Google Antigravity Changelog](https://antigravity.google/docs/changelog/) — Models & Quota 페이지 및 statusline quota 표시 개선.
- [Antigravity Plugins](https://antigravity.google/docs/plugins/) — CLI·IDE 확장 가능성 설명, Android 개인 구독 quota 조회용 OAuth/REST 계약 제공 근거는 아님.
- 결론: CLI의 접근 가능 데이터와 외부 Android의 사용 허용 권한을 구별. Gemini/Claude bucket의 구체적 identity도 확인 필요.

## Android 제품 결정

1. 공식 Android 직접 quota 조회에 필요한 **긍정적인 권한 근거는 네 Provider 모두 아직 확보되지 않음**.
2. `ProviderCapability`를 `UNKNOWN`으로 유지하며 인증 UI, 내부 endpoint, CLI 토큰 재사용, 웹 화면 자동 스크레이핑 구현 금지.
3. 새 코드에는 실제 숫자를 제공한다고 주장하지 않는 **연결 불가·권한 미확인 UI**, `null`과 `0%` 구분, stale timestamp 처리만 확장.
4. 공개 문서에서 근거를 못 찾은 것은 명시적 금지 확정이 아님. 공식 지원 채널에 네 질문을 전달할 것: (a) 제3자 개인 구독 quota-read 허용? (b) 모바일 OAuth client/scope? (c) 잔여율 및 reset field 계약? (d) 앱 배포/사용량 제한?
5. 실계정 API 테스트는 공식적인 승인과 토큰 scope가 확인될 때까지 진행하지 않는다.

## 차단 및 다음 행동

- 현 시점에 공급자별 Android live adapter 개발 착수는 **BLOCKED_BY_EVIDENCE** (서비스 자체가 BLOCKED라는 뜻이 아님).
- Android UI/테스트/위젯 개선은 계속 가능.
- 공식 답변 또는 문서 발견 시 URL·확인일·scope·quota schema를 이 문서와 Issue #15에 첨부한 뒤 승인된 공급자만 순차 구현.
