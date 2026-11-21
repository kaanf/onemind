# Parallel work

How several agent sessions work on OneMind at the same time without stepping on each other.
Read this after `AGENTS.md` and `docs/ROADMAP.md` when the owner says you are on a **lane**.

## Setup

Each lane is a separate Claude Code (or other agent) session in its own git worktree, so branches
and build outputs never collide:

| Lane | Folder | Work (see `docs/ROADMAP.md`) | Owns |
|------|--------|------------------------------|------|
| **backend** | `onemind/` (main checkout) | Phase 1 in order: logging + tracing → error handling → idempotency | `backend/` (except CI config), migrations `V006`+ |
| **ci** | `onemind-ci/` | Phase 1 GitHub Actions item | `.github/` |
| **mobile** | `onemind-mobile/` | Phase 2 in order | `mobile/` |

The owner starts each session with: "Read AGENTS.md, docs/ROADMAP.md and docs/PARALLEL_WORK.md.
You are on the **<lane>** lane."

Create a worktree (from the main checkout):

```sh
git fetch
git worktree add -b <type>/<name> ../onemind-<lane> origin/dev
git branch --unset-upstream <type>/<name>   # never let `git push` target dev
```

Remove it when the lane is finished: `git worktree remove ../onemind-<lane>`.

## Rules

- **Stay in your lane.** Change only the paths your lane owns, plus the docs that describe your
  change. If you need something from another lane, stop and tell the owner.
- **Shared files** (`docs/ROADMAP.md`, `AGENTS.md`, `docs/architecture/**`): keep edits small and
  local; in `ROADMAP.md` tick only your own items.
- **Migrations:** the backend lane owns the next numbers (`V006__platform_idempotency.sql` is
  reserved). Another lane that needs a migration asks the owner for a number first.
- **Before merging,** rebase onto the latest `origin/dev`, resolve conflicts, and run your checks
  again.
- **One local backend at a time:** only the backend lane runs `bootRun` (port 8080) and the local
  compose DB. Testcontainers runs in two lanes at once may be slow on Colima; that is expected.
- **After a merge,** start the next item on a new branch from `origin/dev` inside the same
  worktree (`git switch -c <type>/<name> origin/dev`, then `git branch --unset-upstream`).

## Talking to the owner

- Chat replies are in **Turkish**; docs, code, commits and PRs stay in English.
- Back technology choices with current web research.
- When an item is done and checks pass, give a short Turkish summary and ask
  **"pushlayıp mergeleyeyim mi?"**. Only after a yes for that PR: commit, push, open a PR to `dev`
  (template in `AGENTS.md`), squash-merge with branch delete. Never tag.
