# NEXT-CHAPTER

## Project Overview

TODO: describe what this project does and its tech stack.

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
