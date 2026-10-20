# 실제 Provider 연동 구현 계획 v1

- 검토일: 2026-10-20
- 추적: [Issue #15](https://github.com/fiverocks-dev/ai-usage-monitor/issues/15)
- 상태: **공식 권한 검증 진행 중 / 모바일 실계정 연동 미승인**
- 관련 설계: [모바일 아키텍처 v1](../architecture/mobile-architecture-v1.md)

## 1. 현재 판정

| 제공자 | 공식 확인 가능한 경로 | 제3자 모바일 잔여 quota API | 판정 |
|---|---|---|---|
| Claude Code | 공식 Claude Code 로그인 및 로컬 CLI | 구독 잔여량을 읽는 제3자 모바일 권한 미확인 | UNKNOWN |
| Codex | 공식 Sign in with ChatGPT; 허용된 Responses API 요청 | Codex 5h/주간 잔여량 조회 허용 근거 미확인 | UNKNOWN |
| Antigravity Gemini | **공식 Antigravity CLI `/usage` 또는 `/quota`** 명령으로 모델별 quota 패널 표시 | 외부 앱용 구조화된 quota API 및 계정 인증 허용 근거 미확인 | UNKNOWN |
| Antigravity Claude | Antigravity 내 제3자 모델 노출 가능 | Gemini 모델과 독립적인 Claude quota 필드·앱 접근 권한 미확인 | UNKNOWN |

**UNKNOWN은 사용 금지 확정이 아니다.** 다만 실제 계정 자격증명 수집이나 비공식 HTTP 요청 코드를 배포할 수 있는 근거는 아니다.

## 2. 확인된 공식 근거

- [Google Antigravity CLI — Model quotas /usage](https://www.antigravity.google/docs/cli/commands/usage): CLI 패널이 모델별 잔여 사용량과 갱신을 보여줌.
- [Google Antigravity — Plans](https://www.antigravity.google/docs/plans): 플랜별 5시간 및 주간 한도. 실제 수치는 계획·용량·모델에 따라 차이.
- [OpenAI — Sign in with ChatGPT](https://developers.openai.com/siwc/quickstart): 로그인과 선택적 ChatGPT 플랜 기반 AI 요청 지원. 이것만으로 Codex quota 조회 허용 여부를 증명하지 않음.
- [OpenAI — Accounts and sessions](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions): 사용량 확인은 ChatGPT Settings → Usage 안내. 모바일에서 Codex 각 window의 잔여율을 읽는 응답 계약은 확인되지 않음.
- [Anthropic — Claude Code getting started](https://docs.anthropic.com/en/docs/claude-code/getting-started): 공식 구독/Console/Enterprise 로그인 경로. 제3자 앱의 subscription quota API 근거와 구별.

## 3. 구현 순서

### P1 — 모델 및 Capability 게이트 (외부 네트워크 없음)
- `ProviderCapability`를 각 Provider별로 모델링: `authAllowed`, `quotaReadAllowed`, `distributionAllowed`, `evidenceUrls`, `checkedAt`.
- 권한이 불명확하면 런타임 상태와 분리한 `UNKNOWN` capability를 표시.
- 개별 window(`5h`, `7d`, 모델별 bucket)를 분리하고 정보가 없을 때 0%로 만들지 않음.
- 외부 요청 및 credential 저장 없이 fixture·UI·단위 테스트로 검증.

### P2 — 공식 데스크톱 CLI 출력 경로 검토
- **Antigravity CLI `/usage`는 현재 대화형 TUI 패널**. 스크레이핑 자동화의 합법성/안정성, JSON 출력 또는 공식 export 유무를 별도 검증.
- 가능하다면 사용자 명시 동의로 데스크톱이 정규화된 표시 데이터만 내보내고 휴대폰이 가져오는 **수동 로컬 파일 가져오기**부터 PoC 검토.
- JSON export가 공식 지원되지 않는다면 대화형 TUI를 억지로 파싱하거나 세션 토큰을 재사용하지 않음.
- 이 단계에서는 로컬 파일 샘플 import와 schema 검증만 먼저 구현할 수 있음.

### P3 — 승인된 연동만 구현
- Provider별 공식 권한·인증·quota 조회 계약을 확인한 뒤 해당 Provider adapter만 활성화.
- 유저의 기존 CLI token, session cookie, Credential Manager 항목을 무단 복사하지 않음.
- `401/403/429`, offline, stale, partial error, token revoke, 로그 마스킹 검증.

### P4 — 배포
- Android 실기기 테스트와 위젯 일관성 검증.
- iOS WidgetKit 구현은 Android 데이터 계약 검증 이후 분리.
- 승인 범위·스코프·약관·개인정보 처리 여부를 재점검한 뒤 배포 판단.

## 4. 서버 비용 0원 대안 비교

| 경로 | 서버 비용 | 보안 경계 | 현재 결론 |
|---|---|---|---|
| 공식 모바일 API 직접 조회 | 0원 가능 | 앱 토큰·공급자 약관 | quota endpoint 허가 미확인 |
| 사용자 수동 파일 가져오기 | 0원 | 사용자가 직접 데이터 선택, 인증정보 제외 | 다음 Mock→데이터 전환 PoC 후보 |
| LAN 데스크톱 브리지 | 0원 | 페어링, 연결 인증, 범위 제한, 로컬망 위협 모델 필요 | 수동 파일 다음 검토 |
| 클라우드 중계 서비스 | 서버/운영 비용 발생 가능 | 계정·네트워크 공격면 증가 | 현재 제외 |

## 5. 게이트와 중단 조건

- P0 공식 제3자 조회·재배포 승인 → 없으면 Provider live adapter 금지
- P1 공식 인증 클라이언트/권한 → 없으면 계정 로그인 UI 금지
- P2 잔여량/갱신 시간 공식 schema → 없으면 수치 추정 금지
- P3 로컬 저장·삭제·암호화·로그 마스킹 → 실계정 연결 전 필수
- P4 앱스토어 배포권한 확인 → 대중 배포 전 필수

**다음 개발 가능 범위:** 인증·HTTP 네트워크 호출 없는 `ProviderCapability` 및 사용자 선택 샘플 파일 가져오기 구현. 이 기능을 실제 Provider 연결로 오인하지 않도록 데이터 출처를 명시한다.
