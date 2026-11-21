# Parallel work

How several agent sessions work on OneMind at the same time without stepping on each other.
Read this after `AGENTS.md` and `docs/ROADMAP.md` when the owner says you are the **orchestrator**
or on a **lane**.

## Setup

One **orchestrator** session coordinates the lanes; the owner talks to the orchestrator. Each lane
is a separate Claude Code (or other agent) session in its own git worktree, so branches and build
outputs never collide:

| Session | Folder | Work | Owns |
|---------|--------|------|------|
| **orchestrator** | `onemind-orchestrator/` | Assigns items, collects reports, asks the owner, merges, keeps `ROADMAP.md` | `docs/ROADMAP.md`, this file |
| **backend** | `onemind/` (main checkout) | Backend items, in roadmap order, as assigned | `backend/` (except CI config), migrations `V006`+ |
| **ci** | `onemind-ci/` | CI items of each phase, as assigned | `.github/` |
| **mobile** | `onemind-mobile/` | Mobile items, in roadmap order, as assigned | `mobile/` |

Each worktree has a `CLAUDE.local.md` (listed in `.git/info/exclude`, never committed) that names its
role and the start-up steps: read the three docs, then the orchestrator sends `HELLO` + `STATUS` and a
lane sends a `REPORT`. Claude Code loads it on every start and after `/clear`, so the owner only types
something like "başla". Without that file, the owner starts each session with: "Read AGENTS.md,
docs/ROADMAP.md and docs/PARALLEL_WORK.md. You are the **orchestrator**." or "… You are on the
**<lane>** lane."

Create a worktree (from the main checkout):

```sh
git fetch
git worktree add -b <type>/<name> ../onemind-<lane> origin/dev
git branch --unset-upstream <type>/<name>   # never let `git push` target dev
```

The orchestrator's worktree starts detached: `git worktree add --detach ../onemind-orchestrator origin/dev`.
Remove a worktree when the lane is finished: `git worktree remove ../onemind-<lane>`.

## Orchestrator

- **Doesn't write product code.** Its own changes are `ROADMAP.md`, this file and other small
  coordination docs, on `docs/…` branches in its worktree.
- **Finds the sessions** with `ListAgents` and messages them with `SendMessage`. Session names change
  between runs: at start, the orchestrator sends each lane a `HELLO` with its own name, and lanes
  report to that name.
- **Assigns** the next roadmap item to each idle lane (one item per lane at a time), and orders work
  that crosses lanes (for example an `openapi.yaml` change before mobile `core:api` uses it).
- **Asks the owner** for every decision and every merge, in one Turkish message per topic. A lane's
  PR is merged only after the owner says yes for that PR.
- **Merges** approved PRs (squash). When another open PR is based on the merged branch, it first
  retargets that PR to `dev` (`gh pr edit <n> --base dev`), otherwise GitHub closes it when the
  branch is deleted, and then tells the owning lane to rebase onto `origin/dev`.
- **Is the only writer of `ROADMAP.md`.** It ticks items in a small `docs(repo)` PR after their PRs are
  merged and keeps the status section current. Lanes never edit `ROADMAP.md`.
- Keeps no state only in its head: what each lane is doing is visible in the open PRs and in the
  lanes' last reports; after a restart it rebuilds the picture from `gh pr list`, `ROADMAP.md` and a
  `STATUS` request to every lane.

## Messages

Plain text, first word is the type, then `key: value` lines. One message per event.

Orchestrator → lane:

```
HELLO
orchestrator: <session name>

ASSIGN
item: <roadmap line, verbatim>
branch: <type>/<name>
scope: <what is in and out>
done-when: <checks, docs, PR to dev>

REBASE
pr: #<n>
reason: <what was merged>

APPROVED
pr: #<n>          # the owner said yes; the orchestrator merges

STATUS            # asks for a REPORT
```

Lane → orchestrator:

```
REPORT
state: working | ready | blocked | idle
item: <roadmap line>
pr: #<n> (base, checks passed / failed / running)
summary: <2–5 lines: what changed, deviations from docs, follow-ups>
needs: <decision or approval needed from the owner, or "nothing">
```

A lane sends `REPORT state: ready` when its PR is open and its checks pass, `state: blocked` as soon
as it needs a decision, and answers every `STATUS` and `REBASE` with a `REPORT`.

## Rules

- **Stay in your lane.** Change only the paths your lane owns, plus the docs that describe your
  change. If you need something from another lane, report `blocked` to the orchestrator.
- **Shared files** (`AGENTS.md`, `docs/architecture/**`, `docs/api/**`): keep edits small and local.
  `docs/ROADMAP.md` belongs to the orchestrator: don't tick items in lane PRs; mention new follow-up
  items in the `REPORT` instead.
- **Migrations:** the backend lane owns the next numbers. Another lane that needs a migration asks
  the orchestrator for a number first.
- **Before a PR is merged,** the lane rebases it onto the latest `origin/dev`, resolves conflicts and
  runs its checks again.
- **One local backend at a time:** only the backend lane runs `bootRun` (port 8080) and the local
  compose DB. Testcontainers runs in two lanes at once may be slow on Colima; that is expected.
- **After a merge,** start the next assigned item on a new branch from `origin/dev` inside the same
  worktree (`git switch -c <type>/<name> origin/dev`, then `git branch --unset-upstream`).

## Talking to the owner

- Chat replies are in **Turkish**; docs, code, commits and PRs stay in English.
- Back technology choices with current web research.
- The owner talks to the orchestrator. When an item is ready, the lane commits, pushes, opens a PR to
  `dev` (template in `AGENTS.md`) and reports it; the orchestrator gives the owner a short Turkish
  summary and asks **"mergeleyeyim mi?"**. Never tag.
- If the owner talks to a lane directly, the lane answers and then sends the orchestrator a `REPORT`
  of anything that changed.
