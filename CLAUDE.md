# NEXT-CHAPTER

## Project Overview

NEXT CHAPTER는 사용자가 고른 주제에 대해 AI가 교육 콘텐츠를 처음부터 끝까지 생성해
제공하는 학습 서비스다. 차별점은 생성 자체가 아니라 **학습자의 반응이 다음에 볼 콘텐츠를
결정한다**는 데 있다.

### 콘텐츠 구조

주제 하나마다 **뼈대(skeleton)** 가 하나 만들어진다.

- 주제가 선택되면 AI가 해당 주제의 전 범위를 한 번에 생성하고 챕터 단위로 분할한다
- 챕터들은 선형 목차가 아니라 **서로 연관 관계를 갖는 그래프**로 연결된다
- 뼈대는 공용이다. 주제 수만큼만 존재하며 사용자 수와 무관하다

사용자는 그래프의 임의 지점에서 진입해 소비하고, 그 과정의 응답·퀴즈 결과·질문이
**사용자 레이어**로 따로 쌓인다.

실제로 화면에 보이는 것은 `뼈대 + 사용자 레이어`를 합성한 결과다. 개인화는 별도 챕터를
덧붙이는 방식이 아니라 공용 뼈대에 대한 변형으로 일어난다. 이 분리 덕분에 축적(뼈대는
주제당 하나)과 개인화(레이어는 사용자당 하나)가 서로 충돌하지 않는다.

### 두 개의 피드백 루프

같은 신호를 두 방향으로 쓴다.

| 루프 | 주기 | 신호의 행선지 | 효과 |
|------|------|---------------|------|
| 개인 | 즉시 | 그 사용자의 레이어 | 다음 챕터가 그 사람의 이해도·관심에 맞춰 결정된다 |
| 집단 | 누적 | 공용 뼈대 | 뼈대 자체가 수정되어 이후 모든 사용자가 혜택을 본다 |

집단 루프는 다음 원칙 위에 선다.

> **사용자의 질문은 그 사용자의 부족함이 아니라 콘텐츠의 결함 신고다.**

여러 사람이 같은 지점에서 묻거나 같은 문제를 반복해 틀린다면, 그 챕터가 설명에 실패한
것으로 간주한다. 따라서 별도의 평가 UI가 필요 없다 — 품질 신호는 소비 과정에서 이미
전부 발생한다.

### 대상 사용자

누구나 쓸 수 있는 공개 서비스. 특정 기업·조직 전용이 아니며, 다수 사용자 규모를 상정한다.
조직/테넌트 분리, 학습 배정, 관리자 리포팅 같은 B2B 요구사항은 범위에 없다.

### 필수 제약: 주제 통합

집단 루프는 **신호가 한 뼈대에 모여야** 임계치를 넘는다. 사용자가 자유롭게 주제를 입력하면
"머신러닝 / 기계학습 / ML 입문"이 각각 별도 뼈대를 만들고, 신호가 분산되어 개선이 영원히
트리거되지 않는다. 동시에 생성 비용은 배로 늘고, 콘텐츠 개수는 증가하지만 실제 커버리지는
늘지 않는다.

그러므로 **의미가 같은 주제를 하나의 뼈대로 묶는 것은 부가 기능이 아니라 개선 루프의 전제
조건이다.** 구현 방식(입력 정규화, 임베딩 유사도 매칭, 큐레이션된 주제 목록 등)은 미정이나,
이 요구사항 자체를 후순위로 미루면 제품의 핵심 가설을 검증할 수 없게 된다.

### 3개월 성공 기준

콘텐츠 개수는 지표로 쓰지 않는다. AI가 생성하므로 버튼을 누르면 늘어나며 변별력이 없다.
증명해야 할 것은 **개선 루프가 실제로 한 바퀴 이상 돌았다는 사실**이다.

- [ ] (필수) 사용자 신호가 집계되어 뼈대 수정을 트리거한 사례가 존재한다
- [ ] 수정 이후 해당 챕터의 오답률·질문 집중도가 실제로 하락했다 — 개선이 개선이었음의 확인
- [ ] 생성된 뼈대 중 끝까지 소비된 비율이 측정되고 있다

첫 항목만 달성해도 제품의 핵심 가설은 증명된다.

### 아직 정하지 않은 것

- **기술 스택** — 미정
- **주제 통합 방식** — 필요성은 확정, 방법은 미정
- **뼈대 수정 시 정합성** — 이미 그 챕터를 학습한 사용자에게 수정본을 어떻게 보일 것인가
- **생성 정확성** — AI가 생성한 콘텐츠를 AI가 다시 수정하므로 오류가 누적될 수 있다.
  검증 수단 미정
- **생성 시간·비용** — 주제 전 범위를 한 번에 생성하는 비용과 대기 시간이 UX에 미치는 영향

## Rules

These are always-follow guidelines. Read them before writing code.

@.claude/rules/security.md
@.claude/rules/coding-style.md
@.claude/rules/testing.md
@.claude/rules/git-workflow.md
@.claude/rules/patterns.md
@.claude/rules/performance.md
@.claude/rules/agents.md
@.claude/rules/hooks.md

## Tooling

This project uses the [everything-claude-code](https://github.com/mindplates/everything-claude-code)
plugin, declared in `.claude/settings.json`. That repo is our fork of
[worldflowai/everything-claude-code](https://github.com/worldflowai/everything-claude-code);
we fork rather than consume upstream directly because Claude Code merges hooks across sources
and offers no way to disable an individual one, so removing a hook means editing the plugin
source. To pull upstream changes:

```bash
git remote add upstream https://github.com/worldflowai/everything-claude-code.git
git fetch upstream && git merge upstream/main
```

It provides:

- **Commands**: `/plan`, `/tdd`, `/verify`, `/code-review`, `/build-fix`, `/refactor-clean`,
  `/e2e`, `/learn`, `/checkpoint`, `/orchestrate`, `/test-coverage`, `/setup-pm`, `/eval`,
  `/update-docs`, `/update-codemaps`
- **Agents**: planner, architect, tdd-guide, code-reviewer, security-reviewer,
  build-error-resolver, e2e-runner, refactor-cleaner, doc-updater
- **Skills**: coding-standards, backend-patterns, frontend-patterns, tdd-workflow,
  security-review, verification-loop, eval-harness, continuous-learning, strategic-compact
- **Hooks**: session memory persistence, strategic compaction, console.log warnings,
  Prettier/tsc on edit, PR URL logging

### Typical feature flow

```
/plan          -> plan the approach
/tdd           -> tests first, then implementation
/verify        -> validate the change works
/code-review   -> self-review before PR
```

### Hook notes

Hooks are enabled as shipped by the fork, with one upstream hook removed: the one that
blocked `npm run dev` unless it ran inside tmux, which does not exist on Windows.

Still active and worth knowing about:

- Creating any `.md` file other than README/CLAUDE/AGENTS/CONTRIBUTING is **blocked**.
- A non-blocking "consider running in tmux" reminder prints on `npm install`/`test`,
  `cargo build`, `docker`, `pytest`, and similar. Harmless noise on Windows.

There is no way to disable a single hook from settings — hooks merge across sources and
only `disableAllHooks` (all-or-nothing) exists. To drop another one, edit `hooks/hooks.json`
in the fork and push.
