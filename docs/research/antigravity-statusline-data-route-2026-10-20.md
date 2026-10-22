# Antigravity 공식 Status Line 기반 실제 quota 수집 경로

- 확인일: 2026-10-20
- 관련: [Issue #15](https://github.com/fiverocks-dev/ai-usage-monitor/issues/15)
- 상태: **공식 출력 인터페이스 확인 / 실제 계정 실행·모바일 전달 미검증**

## 확인된 공식 기능

[Google Antigravity CLI Status line customization](https://antigravity.google/docs/cli/statusline/)은 `statusLine.type=command`를 통해 명령을 실행하고 JSON 상태를 `stdin`에 전달한다. 공식 JSON 설명에 `quota` 버킷 객체, `remaining_fraction`, `reset_time`, `reset_in_seconds`가 명시되어 있다.

이는 **실제 CLI에서 quota 데이터를 구조화해 읽는 문서화된 확장 경로**이며, Windows Credential Manager 추출이나 비공식 엔드포인트 호출 없이 프로토타입을 만들 수 있다.

## 실제 데이터 경로

```text
사용자가 로그인한 Antigravity CLI
 → 공식 statusLine 스크립트 입력(stdin: 전체 상태 JSON)
 → 필드 허용목록에 따라 quota 버킷만 추출
 → %LOCALAPPDATA%\AIUsageMonitor\antigravity-quota.json
 → (후속) 버킷 ID 매핑 및 SnapshotImport 검증
 → Android 사용자가 파일 선택 → 앱/위젯
```

**주의:** statusLine의 전체 입력에는 email, workspace, session, transcript 등의 민감한 데이터가 섞여 있다. 이를 통째로 저장/전송하지 않고 quota 필드만 추출해야 한다. 생성되는 로컬 파일도 사용자의 구독 상태 데이터이므로 외부 공개 저장소, 로그, 텔레메트리에 업로드하지 않는다.

## 구현 PoC

[스크립트 (PR #14 브랜치)](https://github.com/fiverocks-dev/ai-usage-monitor/blob/feat/android-mock-mvp-13/scripts/antigravity_statusline_export.py)

- 네트워크 호출 없이 표준 입력만 사용
- `quota`에서 `bucket_id`, `remaining_fraction`, `reset_time`만 추출
- 버킷 개수·입력 크기·ID 길이·실수 범위 제한
- Windows `LOCALAPPDATA`에 스냅샷을 저장하고 원본 JSON을 폐기
- 최초 PoC에는 CLI 전역 설정의 자동 변경 기능 없음
- quota가 없는 경우에도 오류 원인을 무작정 인증 실패로 추정하지 않음

## 수동 연결 절차 (아직 실기기 미검증)

1. 로그인된 Antigravity CLI와 Python 실행 환경에서 스크립트의 실제 위치를 확인한다.
2. 현재 `%USERPROFILE%\.gemini\antigravity-cli\settings.json`을 백업한다.
3. 기존 `statusLine` 설정이 있다면 덮어쓰지 말고 **기존 기능과 병합 가능성**을 확인한다.
4. 공식 문서 형식대로 `statusLine: { type: "command", command: "<python 실행 파일> <스크립트 절대경로>" }`를 구성한다. Windows 셸의 공백·따옴표 처리 및 CLI 버전에 따라 동작이 달라질 수 있으므로 사용자 실행 테스트로 검증한다.
5. CLI 상태가 변경되었을 때 `%LOCALAPPDATA%\AIUsageMonitor\antigravity-quota.json`이 생성되고 `buckets`만 포함되는지 확인한다.
6. 종료/재시작 시 갱신과 기존 statusLine 복원을 확인한다.

**중요 제한:** CLI가 상태 변화를 전달할 때 실행되는 콜백으로, 1분 또는 5분 주기 수집 보장은 없다. `/usage` TUI 화면 표시도 자체적으로 위 JSON export를 약속하지 않는다.

## 아직 해결되지 않은 연결

- 생성된 원본은 `schema_version/source/buckets` 구조이며 Android 앱의 `schemaVersion/snapshots` 구조와 다르다. 정규화 및 매핑 승인 과정이 필요하다.
- 공식 예시의 `gemini-weekly`는 하나의 버킷 ID 예시일 뿐, 모든 Gemini/Claude 버킷의 명명 규칙을 보장하지 않는다. 버킷 이름만으로 임의의 모델에 강제 매핑하지 않는다.
- 모바일 파일 전달은 사용자 직접 선택 방식만 설계되어 있으며, Windows→휴대폰 자동 전송은 미구현.
- 실제 구독 CLI에서 PoC를 실행해 생성되는 키·갱신 시각을 검증해야 한다.
- 외부 배포·공유에 대한 권한 확인은 별도 게이트이며 공식 스크립트 인터페이스의 존재만으로 승인됐다고 단정하지 않는다.

## 후속 작업

1. 익명화된 실제 버킷 ID와 필드 구조 확인
2. 사용자 승인 아래 고정된 버킷 매핑 설정, 정보 없는 Provider는 미지원 처리
3. 원본 quota export → Android `SnapshotImport` 형식 변환기 테스트
4. USB/공유 파일을 통한 수동 실데이터 PoC
5. 그 다음 필요할 경우 인증된 LAN 전달 프로토콜 별도 설계
