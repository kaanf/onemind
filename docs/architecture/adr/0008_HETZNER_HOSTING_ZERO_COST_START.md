# 0008 · Hosting on Hetzner, starting at near-zero cost

- **Status:** Accepted
- **Date:** 2026-11-20
- **Deciders:** Kaan

## Context

The backend is hosted on **Hetzner** (Germany). There are no users yet, so the goal is to pay as
little as possible until launch, without making later growth hard. Managed databases (Ubicloud,
Supabase Pro) cost money every month, and free tiers (Supabase, Neon) have small storage limits and
pause inactive projects.

Most of our data (POS sales) can be re-imported from Storyous. Only our own data (users, stock
movements, receipts, recipes, locations) would be lost for good without backups.

## Decision

### Development: local only, free

- Everything runs locally with `docker compose`: PostgreSQL, the backend, and an S3-compatible
  store (e.g. MinIO) for receipt images.
- No servers or paid services until the first release.

### First release: one small Hetzner Cloud VM

- **One VM** in Germany (Falkenstein or Nuremberg), the smallest size that runs the stack well.
  It runs, with Docker Compose:
  - **Caddy** (reverse proxy, automatic HTTPS),
  - the **backend** container,
  - **PostgreSQL** (official image, data on a persistent volume).
- **Firewall:** only 80/443 public, SSH by key only. PostgreSQL is **not** exposed to the internet.
- **Backups:** nightly `pg_dump` (compressed, encrypted) copied **off the VM** to S3-compatible
  object storage on a free tier, keeping 14 daily copies. A restore is tested at least monthly and
  before each schema-heavy release.
- **Files (receipt images):** behind a `FileStorage` interface. At first stored on the VM volume or
  the same free-tier object storage; included in backups.
- **Email (sign-in codes):** a transactional email provider on its free tier, behind an
  `EmailSender` interface. Choose one that processes data in the EU (e.g. Brevo or Resend; verify at
  implementation time).
- **Error tracking:** Sentry free tier (backend + mobile). A free uptime monitor for the health
  endpoint.
- **Secrets:** GitHub Actions secrets, written to an `.env` file on the server at deploy time.
  Never in the repo.

### CI/CD

- Repository on **GitHub**. **GitHub Actions** (free quota):
  - backend: build + tests (Docker available on runners for Testcontainers and jOOQ codegen),
  - mobile: shared tests + Android build; iOS build on macOS runners (watch the quota),
  - API spec lint + breaking-change check (ADR 0001).
- Backend images are pushed to **GitHub Container Registry**. Deploy = SSH to the VM,
  `docker compose pull && docker compose up -d`. Flyway migrations run on application start.
- Environments: **local** and **prod** to start. A **staging** environment is added when real users
  arrive (a second small VM or a second compose project).

### Growth path

Everything is standard (Docker, plain PostgreSQL, S3 API), so each piece can move without code
changes:

- Database → managed PostgreSQL (Ubicloud on Hetzner, or Supabase) by dump/restore and a new
  connection URL.
- Files → paid object storage (Hetzner Object Storage) by changing configuration.
- More load → bigger VM, then separate VMs for database and backend.

## Alternatives considered

- **Managed PostgreSQL from day one (Ubicloud, Supabase Pro):** less ops, but a monthly cost before
  there are users.
- **Free tiers (Supabase Free, Neon Free):** no cost, but small storage limits, paused projects and
  no automatic backups on Supabase Free; 90 days of sales history for a few venues may not fit.
- **AWS:** more managed services, more cost and complexity than we need now.

## Consequences

- Monthly cost at launch: the VM (a few euros), plus a few cents of AI usage. Apple and Google
  developer accounts already exist.
- We own database operations: backups, restore tests, PostgreSQL updates, disk monitoring. These
  must be scripted in the repo (`infra/`), not done by hand.
- The VM is a single point of failure at first. This is acceptable before launch; revisit with
  real users.
