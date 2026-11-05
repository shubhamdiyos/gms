# GMS Stage J — Final Decision Log

**Date:** 2026-11-03  
**Revision:** 5.0  
**Status:** CONSOLIDATED BLUEPRINT  
**Author:** Principal Software Architect, Senior DevOps Engineer, Cloud Architect  
**Repository:** GMS (test/playwright-e2e branch)  
**Purpose:** Record all major architecture and infrastructure decisions with rationale, alternatives, and trade-offs

---

## Decision Framework

Each decision follows this structure:
- **Decision ID:** Unique identifier (D-XXX)
- **Category:** Architecture, Infrastructure, Security, Cost, Operations
- **Context:** Why this decision was needed
- **Decision:** What was chosen
- **Rationale:** Why this choice over alternatives
- **Alternatives Rejected:** Other options considered
- **Trade-offs:** What was gained and what was lost
- **Residual Risks:** Known risks that remain
- **Owner Approval Required:** Yes/No
- **Repository Evidence:** Files, configurations, or tests that support this decision

---

## D-001: Oracle Cloud Infrastructure Always Free as Primary Hosting

**Category:** Infrastructure, Cost  
**Context:** Owner requires ₹0 initial infrastructure budget. Previous reports recommended DigitalOcean ($6/month) or Hetzner (€4.50/month).  
**Decision:** Use Oracle Cloud Infrastructure (OCI) Always Free tier for production hosting.

**Rationale:**
- OCI Always Free provides ARM-based compute (VM.Standard.A1.Flex) with 2 OCPUs, 24 GB RAM (we allocate 12 GB), and 200 GB block storage (we allocate 100 GB)
- Region: ap-mumbai-1 (closest to target users in India)
- No cost for pilot scale (2,000 students)
- Repository already contains OpenTofu configurations (`infra/oci/`)
- Cloud-init hardening script implements security best practices
- Always Free tier is permanent (not a time-limited trial)

**Alternatives Rejected:**
1. **DigitalOcean Droplet ($6/month)**
   - Rejected: Violates ₹0 budget requirement
   - Would provide 1 GB RAM, 1 vCPU, 25 GB SSD
   - Less resources than OCI Always Free

2. **Hetzner Cloud (€4.50/month)**
   - Rejected: Violates ₹0 budget requirement
   - European data centers (not optimal for Indian users)
   - Less resources than OCI Always Free

3. **AWS Free Tier (12 months)**
   - Rejected: Time-limited (expires after 12 months)
   - Would incur unexpected costs after trial period
   - More complex pricing model

4. **Google Cloud Free Tier**
   - Rejected: Limited resources (1 f1-micro instance)
   - Not sufficient for production workload

**Trade-offs:**
- ✅ **Gains:**
  - Zero infrastructure cost (₹0/month)
  - Generous resources (2 OCPUs, 12 GB RAM, 100 GB storage)
  - ARM-based compute (better performance per watt)
  - Permanent free tier (not time-limited)
  - Existing OpenTofu configurations reduce setup time

- ❌ **Losses:**
  - Single point of failure (no multi-region deployment)
  - Manual recovery procedures (no automated failover)
  - OCI learning curve (less common than AWS/DigitalOcean)
  - ARM architecture requires compatible Docker images (already supported)

**Residual Risks:**
1. **OCI Always Free policy change:** Oracle could modify or discontinue the free tier
   - **Mitigation:** Monitor Oracle announcements, have fallback plan to migrate to paid VPS
   - **Probability:** Low (Oracle has maintained free tier for 3+ years)
   - **Impact:** High (would require infrastructure migration)

2. **ARM capacity constraints:** ap-mumbai-1 may run out of ARM capacity
   - **Mitigation:** preflight.sh checks capacity before provisioning
   - **Probability:** Low (OCI has multiple availability domains)
   - **Impact:** Medium (would delay deployment)

3. **Public IP cost:** OCI may charge for public IPv4 addresses
   - **Mitigation:** Verify cost before `tofu apply` (§9.1 requirement)
   - **Probability:** Low (public IP is free for running instances)
   - **Impact:** Low (~$3/month if charged)

**Owner Approval Required:** ✅ Yes  
**Owner must verify:**
- OCI account eligibility for Always Free tier
- ARM capacity in ap-mumbai-1 region
- Public IPv4 cost (if any)
- Acceptance of single-region deployment

**Repository Evidence:**
- `infra/oci/README.md` — OpenTofu stack documentation
- `infra/oci/compute.tf` — VM.Standard.A1.Flex instance definition
- `infra/oci/network.tf` — VCN, subnet, security list configuration
- `infra/oci/data.tf` — ARM image selection and validation
- `infra/oci/cloud-init.yaml.tftpl` — Server hardening script
- `infra/oci/variables.tf` — Input validation guards

---

## D-002: Neon PostgreSQL as Database Provider

**Category:** Infrastructure, Cost  
**Context:** Owner requires owner-controlled PostgreSQL database, initially free tier. Previous reports assumed Neon free tier without verifying limits.  
**Decision:** Use Neon PostgreSQL (owner-controlled project, free tier initially).

**Rationale:**
- Neon free tier provides 1 GB storage per project, 100 CU-hours/month compute
- Owner controls the project (not tied to GMS repository)
- Serverless architecture (auto-scaling, sleep when idle)
- Continuous backups with point-in-time recovery (7-day retention)
- SSL/TLS encryption for all connections
- Compatible with PostgreSQL 15 (matches development environment)
- Flyway migrations work without modification

**Alternatives Rejected:**
1. **Self-managed PostgreSQL on OCI instance**
   - Rejected: Adds operational burden (backups, monitoring, updates)
   - Would consume OCI instance resources (RAM, CPU, disk)
   - No point-in-time recovery (manual backups only)

2. **AWS RDS Free Tier (12 months)**
   - Rejected: Time-limited (expires after 12 months)
   - Would incur unexpected costs after trial period
   - Over-provisioned for pilot scale (db.t4g.micro = 2 GB RAM)

3. **Supabase Free Tier**
   - Rejected: 500 MB storage limit (less than Neon)
   - Includes unnecessary features (Auth, Realtime, Storage)
   - Less mature than Neon for production use

4. **PlanetScale Free Tier**
   - Rejected: PlanetScale removed free tier in 2024
   - MySQL-compatible (not PostgreSQL)

**Trade-offs:**
- ✅ **Gains:**
  - Zero database cost (₹0/month on free tier)
  - Managed service (no operational burden)
  - Automatic backups and point-in-time recovery
  - SSL encryption by default
  - Serverless scaling (pay only for what you use)
  - Owner controls the project (can migrate if needed)

- ❌ **Losses:**
  - 1 GB storage limit (sufficient for 2,000 students, but may require upgrade)
  - 100 CU-hours/month compute limit (sufficient for pilot, but may require upgrade)
  - Database sleeps after 5 minutes of inactivity (cold start latency)
  - Dependent on Neon's service availability

**Residual Risks:**
1. **Storage exhaustion:** Database exceeds 1 GB storage
   - **Mitigation:** Monitor storage usage, alert at 800 MB
   - **Probability:** Medium (depends on data growth)
   - **Impact:** High (database writes will fail)
   - **Action required:** Owner approval to upgrade to paid plan ($19/month for 10 GB)

2. **Compute exhaustion:** Database exceeds 100 CU-hours/month
   - **Mitigation:** Monitor compute usage, alert at 80 CU-hours
   - **Probability:** Low (pilot scale should not exceed limit)
   - **Impact:** Medium (database will throttle or sleep)
   - **Action required:** Owner approval to upgrade to paid plan

3. **Cold start latency:** Database wakes from sleep after inactivity
   - **Mitigation:** Acceptable for pilot scale (5–10 second delay)
   - **Probability:** High (will occur after 5 minutes of inactivity)
   - **Impact:** Low (users expect some delay after idle period)
   - **Action required:** None (acceptable trade-off)

4. **Neon service outage:** Database unavailable
   - **Mitigation:** Daily backups to OCI instance, restore procedure documented
   - **Probability:** Low (Neon has 99.9% uptime SLA on paid plans)
   - **Impact:** Critical (application cannot function without database)
   - **Action required:** Restore from backup to new Neon project or self-managed PostgreSQL

**Owner Approval Required:** ✅ Yes  
**Owner must:**
- Create Neon project (https://neon.tech)
- Provide connection string to deployment team
- Monitor storage and compute usage
- Approve upgrade to paid plan if limits exceeded

**Repository Evidence:**
- `gms_backend/src/main/resources/application-prod.properties` — Database configuration
- `gms_backend/src/main/resources/db/migration/` — Flyway migrations (V1–V10)
- `infra/oci/cloud-init.yaml.tftpl` — Comment: "5432 never listens on a public interface"

---

## D-003: Self-Hosted Frontend on OCI (NOT Vercel)

**Category:** Architecture, Cost, Legal  
**Context:** Previous reports recommended Vercel Hobby plan for frontend hosting. Investigation revealed Vercel Hobby prohibits commercial SaaS usage.  
**Decision:** Self-host React frontend on OCI instance via Nginx (NOT Vercel).

**Rationale:**
- Vercel Hobby plan is restricted to **non-commercial personal use only** (Fair Use Guidelines)
- GMS is a commercial SaaS product (multi-tenant school management)
- Using Vercel Hobby for commercial SaaS violates terms of service
- Vercel Pro plan costs $20/month (violates ₹0 budget requirement)
- Self-hosting on OCI instance costs ₹0 (already paying for the instance)
- Nginx can serve static files efficiently (no separate hosting required)
- Simplifies architecture (frontend and backend on same instance)

**Alternatives Rejected:**
1. **Vercel Hobby Plan**
   - Rejected: **Prohibits commercial SaaS usage** (terms of service violation)
   - Would require upgrade to Vercel Pro ($20/month) for commercial use
   - Violates ₹0 budget requirement

2. **Vercel Pro Plan ($20/month)**
   - Rejected: Violates ₹0 budget requirement
   - Unnecessary cost when self-hosting is viable

3. **Netlify Free Tier**
   - Rejected: Similar commercial usage restrictions as Vercel Hobby
   - Would require paid plan for commercial SaaS

4. **Cloudflare Pages Free Tier**
   - Considered: Allows commercial usage
   - Rejected: Adds complexity (separate hosting from backend)
   - Unnecessary when OCI instance can serve static files

5. **GitHub Pages**
   - Rejected: Public repository only (GMS is private)
   - Limited to static sites (no server-side rendering)

**Trade-offs:**
- ✅ **Gains:**
  - Zero frontend hosting cost (₹0/month)
  - Compliant with terms of service (no legal risk)
  - Simplified architecture (single instance for frontend and backend)
  - Full control over deployment and configuration
  - No vendor lock-in

- ❌ **Losses:**
  - No automatic preview deployments (Vercel feature)
  - No global CDN (Cloudflare Pages feature)
  - Manual deployment process (rsync or CI/CD pipeline)
  - Single point of failure (frontend and backend on same instance)

**Residual Risks:**
1. **No CDN:** Static assets served from single region (ap-mumbai-1)
   - **Mitigation:** Acceptable for pilot scale (2,000 students in India)
   - **Probability:** N/A (design limitation, not a risk)
   - **Impact:** Low (slightly slower load times for distant users)
   - **Action required:** Add Cloudflare CDN if performance becomes an issue

2. **Deployment complexity:** Manual deployment process
   - **Mitigation:** CI/CD pipeline automates deployment (GitHub Actions)
   - **Probability:** Low (CI/CD is standard practice)
   - **Impact:** Medium (manual deployment is error-prone)
   - **Action required:** Implement automated deployment in Phase 4

3. **Single point of failure:** Frontend and backend on same instance
   - **Mitigation:** Daily backups, restore procedure documented
   - **Probability:** Low (OCI instances are reliable)
   - **Impact:** High (complete outage if instance fails)
   - **Action required:** Accept risk for pilot scale, plan multi-instance deployment for production

**Owner Approval Required:** ❌ No (legal compliance decision)

**Repository Evidence:**
- `gms_frontend/nginx.conf` — Nginx configuration (requires production adaptation)
- `gms_frontend/Dockerfile` — Frontend build process
- `gms_frontend/package.json` — React dependencies and build scripts

---

## D-004: No PWA — Flutter for Mobile

**Category:** Architecture, Product  
**Context:** Previous reports recommended PWA implementation for mobile support. Owner explicitly requires native Flutter application, NOT PWA.  
**Decision:** No PWA implementation. Use Flutter for native mobile application.

**Rationale:**
- Owner requirement: "Native mobile: Flutter application"
- Owner requirement: "No PWA"
- Flutter provides native performance and user experience
- PWA is limited by browser capabilities (no background sync, limited offline)
- Flutter can access device features (camera, GPS, notifications)
- Single backend API serves both web and mobile clients
- SUPERADMIN role is web-only (no mobile app needed)
- ADMIN, ACCOUNTANT, TEACHER, STUDENT, PARENT roles use both web and Flutter

**Alternatives Rejected:**
1. **PWA (Progressive Web App)**
   - Rejected: **Owner explicitly prohibits PWA**
   - Limited offline capabilities
   - No access to device features (camera, GPS, push notifications)
   - Browser-dependent performance

2. **React Native**
   - Rejected: Owner specified Flutter
   - Would require learning new technology
   - Flutter has better performance and UI consistency

3. **Native iOS + Native Android**
   - Rejected: Excessive development effort (two codebases)
   - Flutter provides cross-platform solution with native performance

4. **Xamarin**
   - Rejected: Deprecated (Microsoft recommends MAUI)
   - Smaller community than Flutter

**Trade-offs:**
- ✅ **Gains:**
  - Native mobile performance
  - Access to device features (camera, GPS, notifications)
  - Cross-platform codebase (iOS + Android)
  - Better user experience than PWA
  - Aligns with owner's product vision

- ❌ **Losses:**
  - Separate codebase (Flutter is not in this repository)
  - Additional development effort (Flutter team or learning curve)
  - No instant updates (app store deployment required)
  - Larger app size than PWA

**Residual Risks:**
1. **Flutter app not in this repository:** Separate project management
   - **Mitigation:** Define API contract between backend and Flutter
   - **Probability:** N/A (architectural decision)
   - **Impact:** Medium (requires coordination between teams)
   - **Action required:** Create API documentation for Flutter team

2. **App store deployment:** iOS and Android app store approval
   - **Mitigation:** Follow app store guidelines, prepare for review process
   - **Probability:** Medium (app stores may reject certain features)
   - **Impact:** Medium (delays mobile app release)
   - **Action required:** Review app store policies before submission

3. **Flutter learning curve:** Team may need to learn Flutter
   - **Mitigation:** Hire Flutter developer or train existing team
   - **Probability:** High (if team is React/Java-only)
   - **Impact:** Medium (delays mobile app development)
   - **Action required:** Owner decision on Flutter team resourcing

**Owner Approval Required:** ✅ Yes (product architecture decision)

**Repository Evidence:**
- None (Flutter app is external to this repository)
- Backend API must support Flutter client (same endpoints as web)

---

## D-005: Docker and Docker Compose for Containerization

**Category:** Architecture, Operations  
**Context:** Application must be containerized for reproducible deployments. Existing Dockerfiles are present in repository.  
**Decision:** Use Docker and Docker Compose for containerization.

**Rationale:**
- Existing Dockerfiles in repository (`gms_backend/Dockerfile`, `gms_frontend/Dockerfile`)
- Docker provides consistent runtime environment (dev = prod)
- Docker Compose simplifies multi-container orchestration
- GitHub Actions CI/CD can build and push Docker images
- Docker Hub or GitHub Container Registry for image storage
- Rollback is trivial (deploy previous image tag)
- Industry standard for containerization

**Alternatives Rejected:**
1. **Bare metal deployment (no containers)**
   - Rejected: Inconsistent environments (dev vs prod)
   - Difficult rollback (manual process)
   - No isolation between services

2. **Kubernetes**
   - Rejected: Overkill for single-instance deployment
   - Complex setup and maintenance
   - Violates "no Kubernetes" owner requirement

3. **Podman**
   - Rejected: Docker is more widely adopted
   - Podman is Docker-compatible but less documentation available

4. **Serverless (AWS Lambda, Google Cloud Functions)**
   - Rejected: Cold start latency
   - Vendor lock-in
   - Not suitable for long-running processes (Spring Boot)

**Trade-offs:**
- ✅ **Gains:**
  - Consistent environments (dev = staging = prod)
  - Easy rollback (deploy previous image tag)
  - Isolation between services (backend, frontend, database)
  - Automated builds via CI/CD
  - Industry standard (well-documented, large community)

- ❌ **Losses:**
  - Docker daemon overhead (~100 MB RAM)
  - Learning curve for team (if new to Docker)
  - Image storage costs (if using private registry)
  - Network complexity (container networking)

**Residual Risks:**
1. **Docker daemon security:** Docker runs as root by default
   - **Mitigation:** Use rootless Docker or run containers as non-root user
   - **Probability:** Low (Dockerfile already uses non-root user)
   - **Impact:** Medium (container escape vulnerability)
   - **Action required:** Verify non-root execution in production

2. **Image size:** Docker images can be large
   - **Mitigation:** Use multi-stage builds (already implemented)
   - **Probability:** Low (multi-stage builds reduce image size)
   - **Impact:** Low (slower deployment, but acceptable)
   - **Action required:** Monitor image size, optimize if > 500 MB

3. **Container orchestration:** Docker Compose is not suitable for multi-instance deployment
   - **Mitigation:** Plan migration to Kubernetes if scaling beyond single instance
   - **Probability:** Low (pilot scale does not require multi-instance)
   - **Impact:** High (would require architecture redesign)
   - **Action required:** Accept limitation for pilot, plan for production scale

**Owner Approval Required:** ❌ No (standard practice)

**Repository Evidence:**
- `gms_backend/Dockerfile` — Backend container definition (35 lines)
- `gms_frontend/Dockerfile` — Frontend container definition (36 lines)
- `docker-compose.yml` — Development orchestration
- `infra/oci/cloud-init.yaml.tftpl` — Installs Docker and Docker Compose

---

## D-006: Nginx Reverse Proxy with Let's Encrypt SSL/TLS

**Category:** Security, Architecture  
**Context:** Application must use HTTPS in production. Previous reports identified SSL/TLS as critical gap.  
**Decision:** Use Nginx reverse proxy with Let's Encrypt SSL/TLS certificates.

**Rationale:**
- Nginx is industry-standard reverse proxy (high performance, well-documented)
- Let's Encrypt provides free SSL/TLS certificates
- Certbot automates certificate issuance and renewal
- Nginx terminates TLS, backend receives HTTP (simplifies backend configuration)
- Nginx can serve static files (frontend) and proxy API requests (backend)
- Existing `gms_frontend/nginx.conf` provides starting point

**Alternatives Rejected:**
1. **Caddy**
   - Considered: Automatic HTTPS by default
   - Rejected: Less widely adopted than Nginx
   - Smaller community and fewer resources available

2. **Traefik**
   - Considered: Native Docker integration
   - Rejected: More complex configuration than Nginx
   - Overkill for single-instance deployment

3. **HAProxy**
   - Rejected: Primarily a load balancer (not a web server)
   - Cannot serve static files (frontend)

4. **Spring Boot with embedded SSL**
   - Rejected: Backend should not handle TLS termination
   - Adds complexity to Spring Boot configuration
   - Nginx is better suited for static file serving

**Trade-offs:**
- ✅ **Gains:**
  - Free SSL/TLS certificates (₹0 cost)
  - Automated certificate renewal (no manual intervention)
  - Industry standard (well-documented, large community)
  - High performance (Nginx handles thousands of concurrent connections)
  - Simplifies backend configuration (no TLS handling in Spring Boot)

- ❌ **Losses:**
  - Additional component to manage (Nginx + Certbot)
  - Certificate renewal can fail (requires monitoring)
  - Nginx configuration complexity (learning curve)

**Residual Risks:**
1. **Certificate renewal failure:** Let's Encrypt certificates expire every 90 days
   - **Mitigation:** Certbot auto-renewal, monitoring alerts
   - **Probability:** Low (Certbot is reliable)
   - **Impact:** High (HTTPS will fail if certificate expires)
   - **Action required:** Test renewal process, monitor certificate expiry

2. **Nginx misconfiguration:** Incorrect Nginx config can expose backend
   - **Mitigation:** Configuration review, security headers, rate limiting
   - **Probability:** Medium (human error)
   - **Impact:** High (security vulnerability)
   - **Action required:** Validate configuration with `nginx -t`, security audit

3. **TLS version support:** Older clients may not support TLS 1.2/1.3
   - **Mitigation:** Configure Nginx to support TLS 1.2 minimum
   - **Probability:** Low (modern browsers support TLS 1.2+)
   - **Impact:** Medium (older clients cannot connect)
   - **Action required:** Test with older browsers, adjust configuration if needed

**Owner Approval Required:** ❌ No (security best practice)

**Repository Evidence:**
- `gms_frontend/nginx.conf` — Nginx configuration (requires production adaptation)
- `infra/oci/cloud-init.yaml.tftpl` — Installs Nginx and Certbot
- `infra/oci/network.tf` — Security list allows TCP/80 and TCP/443

---

## D-007: GitHub Actions CI/CD with Feature-Branch Workflow (REVISED Revision 3)

**Category:** Operations, Automation, Security  
**Context:** Repository already has extensive CI/CD pipeline (6 gate jobs). Production deployment requires automated build and deploy process with explicit owner approval.  
**Previous Decision (v1.0/v2.0):** Automated deployment on push to `master` branch.  
**⚠️ REVISED Decision (v3.0):** Feature-branch workflow with `workflow_dispatch` trigger for production deployment. **NEVER deploy automatically on push to master.**

**Why the revision:**
- Push-to-master deployment bypasses owner approval
- No manual gate between merge and production deployment
- Feature branches allow code review via pull requests
- `workflow_dispatch` requires explicit owner action to deploy
- Prevents accidental deployments from automated merges or hotfixes

**Decision:** Extend existing GitHub Actions CI/CD pipeline with feature-branch workflow and manual production deployment trigger.

**Release Sequence (REVISED v3.0):**
1. Fetch latest `master` branch
2. Create feature branch (`feature/GMS-123-description`)
3. Run required tests and checks locally
4. Push feature branch to GitHub
5. Create pull request for code review
6. Merge only through approved PR/check process (all CI gates must pass)
7. **After merge, owner explicitly triggers production deployment via `workflow_dispatch`**
8. Production deployment workflow runs with owner approval

**Deployment Workflow Configuration:**
```yaml
# .github/workflows/deploy-production.yml
name: Deploy to Production

on:
  workflow_dispatch:  # Manual trigger only — requires owner approval
    inputs:
      environment:
        description: 'Deployment environment'
        required: true
        default: 'production'
        type: choice
        options:
          - production
          - staging
      git_sha:
        description: 'Git commit SHA to deploy (from merged master)'
        required: true
        type: string

permissions:
  contents: read
  packages: write

concurrency:
  group: production-deployment
  cancel-in-progress: false  # Queue deployments, don't cancel

jobs:
  deploy:
    runs-on: ubuntu-latest
    environment: production  # GitHub Environment protection rules
    steps:
      - uses: actions/checkout@11d5960a326750d5838078e36cf38b85af677262 # v4.4.0
        with:
          ref: ${{ inputs.git_sha }}
      
      - name: Verify commit is on master
        run: |
          git fetch origin master
          if ! git merge-base --is-ancestor ${{ inputs.git_sha }} origin/master; then
            echo "ERROR: Commit ${{ inputs.git_sha }} is not on master branch"
            exit 1
          fi
      
      # Build, test, deploy steps...
```

**Verified GitHub Action SHAs (from existing ci.yml):**
- `actions/checkout@11d5960a326750d5838078e36cf38b85af677262 # v4.4.0`
- `actions/setup-java@cf277c60eb25467037889841efdb72551f06f6c3 # v4.9.1`
- `actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02 # v4.6.2`
- `actions/download-artifact@d3f86a106a0bac45b974a628896c90dbdf5c8093 # v4.3.0`
- `actions/setup-node@49933ea5288caeca8642d1e84afbd3f7d6820020 # v4.4.0`

**Alternatives Rejected:**
1. **Automated deployment on push to master (previous v1.0/v2.0 decision)**
   - Rejected: **Bypasses owner approval**
   - No manual gate between merge and production
   - ⚠️ **SUPERSEDED by this revision**

2. **Jenkins**
   - Rejected: Requires self-hosted server (additional infrastructure cost)
   - More complex setup and maintenance

3. **GitLab CI/CD**
   - Rejected: Repository is on GitHub (would require migration)

4. **Manual deployment (SSH + docker commands)**
   - Rejected: Error-prone, time-consuming
   - No audit trail

**Trade-offs:**
- ✅ **Gains:**
  - Explicit owner approval required for each production deployment
  - Feature branches enable code review via pull requests
  - Audit trail (GitHub Actions logs + PR history)
  - Rollback is trivial (deploy previous image tag via workflow_dispatch)
  - Zero-cost CI/CD (GitHub Actions free tier for public repositories)
  - Concurrency protection prevents parallel deployments

- ❌ **Losses:**
  - Manual deployment step (owner must trigger workflow_dispatch)
  - Slower deployment process (requires owner action)
  - GitHub Actions free tier limit (2,000 minutes/month)
  - Vendor lock-in (GitHub-specific workflow syntax)

**Residual Risks:**
1. **Owner forgets to trigger deployment:** Code merged but not deployed
   - **Mitigation:** GitHub notifications for merged PRs, deployment checklist
   - **Probability:** Medium (human forgetfulness)
   - **Impact:** Medium (delayed feature release)
   - **Action required:** Owner monitors merged PRs

2. **GitHub Actions minute exhaustion:** Free tier limit exceeded
   - **Mitigation:** Monitor usage, optimize workflows, path filtering
   - **Probability:** Low (existing workflows use path filtering)
   - **Impact:** Medium (CI/CD paused until next month)
   - **Action required:** Monitor usage, upgrade to paid plan if needed (owner approval)

3. **Deployment of wrong commit:** Owner specifies incorrect git_sha
   - **Mitigation:** Workflow verifies commit is on master, displays commit message before deployment
   - **Probability:** Low (workflow validation)
   - **Impact:** High (wrong version deployed)
   - **Action required:** Rollback via workflow_dispatch with previous git_sha

4. **Concurrency conflict:** Two deployments triggered simultaneously
   - **Mitigation:** GitHub Actions concurrency group queues deployments
   - **Probability:** Low (single owner operator)
   - **Impact:** Medium (delayed deployment)
   - **Action required:** Wait for queue

**Owner Approval Required:** ✅ Yes (workflow change from automated to manual deployment)

**Repository Evidence:**
- `.github/workflows/ci.yml` — Existing CI/CD pipeline (1017 lines, 6 jobs)
- `.github/workflows/deploy-production.yml` — Production deployment workflow (TO BE CREATED)
- `.github/workflows/gate-coverage.yml` — Coverage enforcement
- `.github/workflows/validate-workflows.yml` — Workflow validation
- Architecture document Section 9.5 — Feature-branch deployment workflow (REVISED v3.0)

---

## D-008: Flyway Migrations via Docker Container (REVISION 5)

**Category:** Architecture, Operations  
**Context:** Database schema must be version-controlled and migrations must be applied safely. Existing Flyway configuration is present in repository.  
**Previous Decision (v1.0):** Execute Flyway migrations on Spring Boot backend startup.  
**Previous Decision (v2.0):** Execute Flyway migrations as a separate deployment step using `java -jar ... org.flywaydb.core.Flyway migrate`.  
**Previous Decision (v3.0):** Execute Flyway migrations using the **official Flyway CLI Docker container** (`flyway/flyway:10`) as a separate deployment step BEFORE application start.  
**⚠️ REVISED Decision (v4.0):** Use **canonical JDBC URL-based environment contract**, **flock-based atomic locking**, **pinned Flyway image version (10.15.2 PROPOSED)**, and **explicit error handling** (not `set -e`).

**Why the v4.0 revision:**
- The v3.0 script used unsafe file-exists locking (`touch`/`trap`) instead of atomic `flock`
- The v3.0 script used `set -euo pipefail` with exit code assignment that cannot execute after `set -e` aborts
- The v3.0 script used mutable `flyway/flyway:10` tag instead of pinned exact version
- The v3.0 script used separate `PG_HOST`/`PG_PORT`/`PG_DATABASE` variables instead of canonical JDBC URL contract
- The v3.0 script passed credentials via command-line `-e` flags instead of temporary `--env-file`
- The v3.0 script did not validate target database host before proceeding
- The v3.0 script did not record deployment success/failure status for audit trail

**Why the v3.0 revision (still valid):**
- The v2.0 command `java -jar /opt/gms/backend.jar ... org.flywaydb.core.Flyway migrate` is **not valid** — Spring Boot fat JARs do not expose Flyway's main class for direct invocation
- The bare `flyway` CLI command assumes Flyway is installed on the host, which it is not
- Using `classpath:db/migration` only works inside the Spring Boot classloader, not with standalone Flyway CLI
- The Docker container approach is the official Flyway recommendation for CI/CD pipelines

**Why v2.0 revision (still valid):** Application-startup migrations are unsafe in production because:
- Multiple backend containers could attempt concurrent migrations (race condition)
- A failed migration leaves the database in an inconsistent state with the application
- Application rollback does NOT reverse database migrations
- No deployment locking prevents concurrent migration attempts
- Neon cold-start latency can cause migration timeouts

**REQUIRED CODE CHANGE (not yet applied):**
```properties
# gms_backend/src/main/resources/application-prod.properties
# CURRENT: spring.flyway.enabled=true
# MUST CHANGE TO:
spring.flyway.enabled=false
```
This prevents the application from running migrations on startup. Migrations are handled by the Docker container.

**Rationale (REVISION 4):**
- Existing Flyway configuration in repository (V1–V10 migrations)
- `flyway/flyway:10.15.2` Docker image is the official Flyway CLI distribution (PROPOSED version — requires verification)
- Flyway 10.x matches the version managed by Spring Boot 3.3.3 parent POM
- `flyway-database-postgresql` module is required for PostgreSQL 15+ support (Flyway 10 separates dialect modules)
- Migration files are mounted from the same Git commit as the application image
- Canonical JDBC URL-based environment contract (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) ensures consistency across all components
- Credentials passed via temporary file with `--env-file` (never in command-line arguments or logs)
- Atomic `flock`-based deployment lock prevents concurrent migrations (replaces unsafe file-exists check)
- Explicit error handling with exit code capture (replaces unsafe `set -e` pattern)
- Exit code detection: non-zero exit = migration failure = deployment abort
- Failed deployments recorded in `/opt/gms/last-deployment-status.txt` for audit trail

**Migration Strategy (REVISION 4 — Docker Container with Canonical Procedure):**

**How migration files are packaged:**
- Migration SQL files (V1–V10) are in `gms_backend/src/main/resources/db/migration/`
- During CI/CD deployment, the same Git commit SHA is used for both:
  1. Building the Spring Boot Docker image (`ghcr.io/<owner>/gms-backend:<sha>`)
  2. Extracting migration files to `/opt/gms/migrations/<sha>/` on the OCI instance
- This guarantees migration files and application code are from the same commit

**How database credentials are supplied securely (REVISION 4):**
- Credentials stored in `/etc/gms/.env` (permissions: 0640, owner: gmsdeploy:docker)
- Canonical JDBC URL-based contract: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- Migration script sources the env file and extracts PostgreSQL connection parameters from `DB_URL`
- Credentials written to temporary file (mode 0600) and passed to Docker container via `--env-file`
- Docker container has no persistent access to credentials (`--rm` removes container after execution)
- Temporary credentials file deleted via `trap` on EXIT
- No credentials in Docker image layers, no credentials in logs

**How application startup is prevented after migration failure (REVISION 4):**
- Deployment sequence: `migrate.sh` → verify success → `deploy.sh` → health check
- If `migrate.sh` exits non-zero, CI/CD pipeline stops; `deploy.sh` never runs
- Failed deployment recorded: `FAILED` written to `/opt/gms/last-deployment-status.txt`, failed SHA recorded
- Old application version continues running (no change to running containers)
- Owner is notified of failure; manual intervention required

**How compatibility is maintained between old and new application versions:**
- All migrations must be backward-compatible (can run against old application code)
- Expand-contract pattern for schema changes:
  1. Add new column/table (migration V11)
  2. Deploy new application code that writes to both old and new columns
  3. Migrate data from old to new column (migration V12)
  4. Deploy code that reads only from new column
  5. Drop old column (migration V13)
- Never drop or rename columns in a single migration
- New columns must be nullable or have defaults (old code doesn't know about them)

**How failed migrations are handled:**
- Flyway records failed migrations in `flyway_schema_history` table
- Failed migration does NOT automatically roll back (Flyway does not support automatic DDL rollback)
- Owner must manually fix the migration SQL file and re-run `migrate.sh`
- In extreme cases, manual database intervention may be required (restore from backup)
- **Never use `flyway clean` in production** (already disabled: `spring.flyway.clean-disabled=true`)

**Why database rollback is not equivalent to application image rollback:**
- Application rollback: deploy previous Docker image tag (`ghcr.io/<owner>/gms-backend:<previous-sha>`)
- Database rollback: cannot be done by deploying a previous migration file
- If migration V11 succeeds but application deployment fails:
  - Database is at schema version 11
  - Old application code expects schema version 10
  - Old application code may not work with schema version 11
- **Mitigation:** Write forward-only rollback migrations (V11.1) that undo V11 if needed
- **Better mitigation:** Ensure all migrations are backward-compatible (old code works with new schema)

**Migration Script (REVISION 4 — Canonical Procedure):**

See `GMS_STAGE_J_FINAL_ARCHITECTURE.md` section 2.2.2 for the complete canonical migration procedure.

**Key Revision 4 changes from Revision 3:**
- **Environment contract:** Uses canonical JDBC URL-based contract (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) instead of separate `PG_HOST`, `PG_PORT`, `PG_DATABASE`, `PG_USER`, `PG_PASSWORD` variables
- **Locking mechanism:** Uses `flock` for atomic deployment locking instead of unsafe file-exists check with `touch`/`trap`
- **Error handling:** Uses `set -uo pipefail` (NOT `set -e`) with explicit exit code capture instead of relying on `set -e` to abort
- **Image pinning:** Uses `flyway/flyway:10.15.2` (PROPOSED, requires verification) instead of mutable `flyway/flyway:10` tag
- **Deployment tracking:** Uses `DEPLOYMENT_SHA` instead of `GIT_SHA`; records success/failure in `/opt/gms/last-deployment-status.txt`
- **Credential handling:** Uses temporary credentials file (mode 0600) passed via `--env-file` instead of command-line `-e` flags
- **Target validation:** Validates database host is Neon or localhost before proceeding
- **Same-commit guarantee:** Migration files and application JAR extracted from same Git commit by CI/CD pipeline

**Alternatives Rejected:**
1. **Hibernate auto-ddl**
   - Rejected: Unsafe for production (can drop columns, tables)
   - No audit trail (schema changes not version-controlled)
   - Owner requirement: `ddl-auto=validate`

2. **Manual SQL scripts**
   - Rejected: Error-prone (manual execution)
   - No version control for schema changes
   - Difficult rollback

3. **Application-startup migrations (previous v1.0 decision)**
   - Rejected: Race conditions with multiple containers
   - Failed migration leaves inconsistent state
   - No deployment locking
   - ⚠️ **SUPERSEDED by v2.0 revision**

4. **`java -jar backend.jar ... org.flywaydb.core.Flyway migrate` (previous v2.0 decision)**
   - Rejected: **Spring Boot fat JARs do not expose Flyway's main class**
   - Not a valid command; will fail with ClassNotFoundException
   - ⚠️ **SUPERSEDED by v3.0 revision**

5. **Bare `flyway` CLI on host**
   - Rejected: Flyway CLI is not installed on the OCI instance
   - Would require manual installation and version management
   - Docker container is cleaner and version-pinned

6. **Revision 3 migration script (file-exists lock, `set -e`, mutable tag)**
   - Rejected: Unsafe file-exists locking pattern (race condition possible)
   - `set -euo pipefail` with exit code assignment is unreliable (exit code never captured after `set -e` aborts)
   - Mutable `flyway/flyway:10` tag can change without notice
   - Separate `PG_HOST`/`PG_PORT`/`PG_DATABASE` variables inconsistent with canonical JDBC URL contract
   - ⚠️ **SUPERSEDED by this v4.0 revision**

7. **Liquibase**
   - Considered: Similar to Flyway
   - Rejected: Flyway is already implemented in repository
   - No significant advantage over Flyway

**Trade-offs:**
- ✅ **Gains:**
  - Version-controlled schema changes
  - Controlled migration execution (single step, not automatic)
  - Deployment locking prevents race conditions
  - Audit trail (Flyway schema_history table)
  - Industry standard (well-documented, large community)
  - Clear separation between migration and application deployment
  - Docker container is version-pinned (reproducible)
  - Same-commit guarantee (migrations match application code)
  - No Flyway installation required on host

- ❌ **Losses:**
  - Additional deployment step (migrate.sh before deploy.sh)
  - Migration failures block deployment (manual intervention required)
  - Requires careful migration design (backward-compatible changes only)
  - Application rollback does NOT reverse database migrations
  - Rollback migrations must be written manually (forward-only)
  - Docker image pull required for each migration (network dependency)
  - Migration files must be extracted to host filesystem before migration

**Residual Risks:**
1. **Migration failure:** Flyway migration fails during deployment step
   - **Mitigation:** Test migrations in staging, deployment stops on failure
   - **Probability:** Low (migrations are tested in CI)
   - **Impact:** High (deployment blocked, manual intervention required)
   - **Action required:** Fix migration, re-run migrate.sh

2. **Backward-incompatible changes:** Migration breaks existing code
   - **Mitigation:** Code review, expand-contract pattern, backward-compatible only
   - **Probability:** Medium (human error)
   - **Impact:** High (application malfunction)
   - **Action required:** Write forward-only rollback migration (V11.1)

3. **Partial deployment:** Migration succeeds but application deployment fails
   - **Mitigation:** Application must work with both old and new schema during transition
   - **Probability:** Medium (deployment infrastructure issues)
   - **Impact:** High (database at new version, application at old version)
   - **Action required:** Fix application deployment; do NOT reverse migration

4. **Docker image pull failure:** Cannot pull flyway/flyway:10 from Docker Hub
   - **Mitigation:** Pre-pull image during infrastructure setup, cache locally
   - **Probability:** Low (Docker Hub is reliable)
   - **Impact:** High (migration cannot run)
   - **Action required:** Wait for Docker Hub recovery, or use cached image

5. **Migration timeout:** Large migration takes too long
   - **Mitigation:** Optimize queries, add indexes, batch updates
   - **Probability:** Low (pilot scale data volume is small)
   - **Impact:** Medium (delayed deployment)
   - **Action required:** Optimize migration, split into smaller batches

**Owner Approval Required:** ❌ No (standard practice — revised for correctness)

**Repository Evidence:**
- `gms_backend/src/main/resources/db/migration/` — Flyway migrations (V1–V10)
- `gms_backend/src/main/resources/application-prod.properties` — `spring.flyway.enabled=true` (MUST CHANGE TO false)
- `gms_backend/pom.xml` — Flyway 10.10.0 dependency (managed by Spring Boot 3.3.3 parent)
- `/opt/gms/migrate.sh` — Production migration script (TO BE CREATED)
- Architecture document Section 2.2.2 — Flyway migration strategy (REVISED v3.0)

---

## D-009: Daily Backups with 30-Day Retention (REVISED Revision 2)

**Category:** Operations, Disaster Recovery  
**Context:** Previous reports identified backups as critical gap. No backup strategy exists in current repository.  
**⚠️ REVISED (v2.0):** Backup procedure corrected to use PostgreSQL connection parameters (NOT JDBC URL). Credentials protected via environment variables. RTO claim removed (requires timed restore test). Off-site backup cost explicitly flagged for owner approval.

**Decision:** Implement daily backups with 30-day retention, plus off-site storage (owner approval required for off-site cost).

**Rationale:**
- Neon provides continuous backups (7-day retention, free tier) — **provider feature, not owner-controlled**
- Daily `pg_dump` backups to OCI instance (`/backups/` directory) — **owner-controlled**
- `pg_dump` uses explicit PostgreSQL connection parameters (`-h`, `-p`, `-U`, `-d`), NOT JDBC URL
- Credentials passed via environment variables (`PGPASSWORD`), NOT command-line arguments
- Backup integrity verified after creation (gzip test + file size check)
- 30-day retention for daily backups (configurable)
- Off-site storage (Backblaze B2 or AWS S3) for disaster recovery — **~₹1/month, owner approval required**
- Quarterly restore testing to verify backup integrity
- RPO < 24 hours (daily backups at 2 AM)
- RTO: **UNVERIFIED** — requires timed restore drill before claiming any target

**⚠️ Neon Provider Recovery vs Owner-Controlled Backups:**
- **Neon continuous backups:** Point-in-time recovery within 7 days (Neon dashboard or `neonctl` CLI)
  - This is a **provider feature**, not under owner's direct control
  - If Neon project is deleted or account compromised, these backups are lost
  - Documentation: https://neon.tech/guides/backup
- **Owner-controlled pg_dump:** Independent daily backups, encrypted, stored locally + off-site
  - Owner has full control and can restore independently of Neon
  - **Required for production:** Protects against Neon account loss or catastrophic failure

**Backup Procedure (CORRECTED):**
```bash
# /opt/gms/backup.sh — CORRECTED procedure

# Credentials via environment (NOT command-line arguments)
export PGPASSWORD="$PG_PASSWORD"

# pg_dump with explicit connection parameters (NOT JDBC URL)
pg_dump -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DATABASE" \
  --format=custom --compress=6 | gzip > "$BACKUP_FILE"

unset PGPASSWORD

# Verify integrity
gzip -t "$BACKUP_FILE" || { echo "ERROR: Backup corrupted" >&2; exit 1; }
chmod 600 "$BACKUP_FILE"
```

**Alternatives Rejected:**
1. **Neon backups only**
   - Rejected: 7-day retention is insufficient (owner may want longer history)
   - No off-site storage (Neon outage = data loss)
   - Not owner-controlled

2. **Hourly backups**
   - Rejected: Excessive storage cost
   - RPO < 1 hour is overkill for pilot scale

3. **Real-time replication**
   - Rejected: Complex setup, expensive
   - Overkill for pilot scale (2,000 students)

4. **No backups**
   - Rejected: Unacceptable risk (data loss)
   - Owner requirement: automated backups

5. **JDBC URL in pg_dump (previous v1.0 error)**
   - Rejected: `pg_dump` does NOT understand JDBC connection strings
   - ⚠️ **SUPERSEDED by this revision**

**Trade-offs:**
- ✅ **Gains:**
  - RPO < 24 hours (daily backups)
  - Owner-controlled backup independent of Neon
  - Credential protection (environment variables, not process args)
  - Backup integrity verification (gzip test + size check)
  - Off-site storage (disaster recovery) — if owner approves cost
  - Quarterly restore testing (backup verification)

- ❌ **Losses:**
  - Off-site storage cost (~₹1/month for Backblaze B2) — **owner approval required**
  - Operational burden (monitoring backups, testing restores)
  - RTO not yet verified (requires timed restore drill)
  - Local-only backups without off-site storage (risk of losing both instance and backups)

**⚠️ Off-Site Backup Cost Conflict:**
- Without off-site backups: OCI instance loss = total data loss (local backups lost too)
- With off-site backups: ~₹1/month (Backblaze B2) — **owner approval required**
- **If owner does not approve off-site backup cost, this risk must be explicitly accepted**

**Residual Risks:**
1. **Backup failure:** Daily backup script fails
   - **Mitigation:** Monitor backup logs, alert on failure
   - **Probability:** Low (pg_dump is reliable)
   - **Impact:** High (no recent backup available)
   - **Action required:** Fix script, run manual backup

2. **Restore failure:** Backup is corrupted or incomplete
   - **Mitigation:** Quarterly restore testing, verify backup integrity after creation
   - **Probability:** Low (integrity check catches most corruption)
   - **Impact:** Critical (cannot recover from disaster)
   - **Action required:** Use older backup, investigate corruption

3. **Off-site storage cost:** Backblaze B2 costs ~₹1/month
   - **Mitigation:** Monitor storage usage, retain only necessary backups
   - **Probability:** Certain (if owner approves)
   - **Impact:** Low (₹12/year)
   - **Action required:** Owner approval before enabling

4. **RTO unverified:** No timed restore drill has been performed
   - **Mitigation:** Perform restore drill before production, measure actual restore time
   - **Probability:** Certain (RTO cannot be claimed without test)
   - **Impact:** High (actual recovery time unknown)
   - **Action required:** Timed restore drill, document actual RTO

5. **Neon account loss:** Neon project deleted or account compromised
   - **Mitigation:** Owner-controlled pg_dump backups (independent of Neon)
   - **Probability:** Very Low
   - **Impact:** Critical (Neon point-in-time recovery unavailable)
   - **Action required:** Restore from owner-controlled backup to new Neon project

**Owner Approval Required:**
- ✅ Yes — for off-site storage cost (~₹1/month)
- ✅ Yes — for accepting RTO as "unverified" until restore drill is performed

**Repository Evidence:**
- `/opt/gms/backup.sh` — Backup script (TO BE CREATED)
- `/opt/gms/restore.sh` — Restore script (TO BE CREATED)
- `/etc/gms/.env` — PostgreSQL connection parameters (PG_HOST, PG_PORT, etc.)
- `infra/oci/cloud-init.yaml.tftpl` — Installs logrotate for backup rotation
- Architecture document Section 7.1 — Backup strategy (REVISED)

---

## D-010: UptimeRobot Monitoring (Free Tier)

**Category:** Operations, Monitoring  
**Context:** Previous reports identified monitoring as critical gap. No monitoring exists in current repository.  
**Decision:** Use UptimeRobot (free tier) for uptime monitoring and alerting.

**Rationale:**
- UptimeRobot free tier provides 50 monitors (sufficient for GMS)
- 5-minute monitoring interval (acceptable for pilot scale)
- Email and SMS alerts on downtime
- HTTP(s) monitoring (check public `/health` endpoint — NOT `/actuator/health`, which must remain private)
- Zero cost (aligns with ₹0 budget requirement)
- No setup or maintenance burden (SaaS monitoring)

**Alternatives Rejected:**
1. **Prometheus + Grafana (self-hosted)**
   - Rejected: Requires additional infrastructure (OCI instance resources)
   - Complex setup and maintenance
   - Overkill for pilot scale

2. **Datadog**
   - Rejected: Free tier is limited (5 hosts)
   - Paid plan is expensive ($15/host/month)
   - Violates ₹0 budget requirement

3. **New Relic**
   - Rejected: Free tier is limited (100 GB/month)
   - Paid plan is expensive
   - Violates ₹0 budget requirement

4. **OCI Cloud Agent**
   - Considered: Built-in monitoring for OCI instances
   - Rejected: Limited to OCI resources (does not monitor Neon database)
   - Less user-friendly than UptimeRobot

**Trade-offs:**
- ✅ **Gains:**
  - Zero monitoring cost (₹0/month)
  - Easy setup (no infrastructure required)
  - Email and SMS alerts
  - 50 monitors (sufficient for GMS)
  - 5-minute interval (acceptable for pilot)

- ❌ **Losses:**
  - Limited metrics (uptime only, no performance monitoring)
  - 5-minute interval (not real-time)
  - No custom dashboards (free tier limitation)

**Residual Risks:**
1. **False positives:** UptimeRobot reports downtime when service is up
   - **Mitigation:** Configure multiple monitors, verify before taking action
   - **Probability:** Low (UptimeRobot is reliable)
   - **Impact:** Medium (unnecessary alert)
   - **Action required:** Verify service status, ignore false positive

2. **False negatives:** UptimeRobot reports uptime when service is down
   - **Mitigation:** Configure multiple monitors from different regions
   - **Probability:** Very Low (UptimeRobot uses multiple locations)
   - **Impact:** Critical (unaware of outage)
   - **Action required:** Investigate monitor configuration

3. **UptimeRobot service outage:** Monitoring service unavailable
   - **Mitigation:** Accept risk (monitoring is not critical for service operation)
   - **Probability:** Low (UptimeRobot has 99.9% uptime)
   - **Impact:** Medium (no alerts during outage)
   - **Action required:** Wait for UptimeRobot to recover

**Owner Approval Required:** ❌ No (free tier, no cost)

**Repository Evidence:**
- None (external monitoring service)
- `gms_backend/src/main/resources/application-prod.properties` — Actuator health endpoint

---

## D-011: Application Security Posture (REVISION 3)

**Category:** Security  
**Context:** Owner review identified incorrect security claims in v1.0 documents. JWT storage risks, CSP limitations, and actuator exposure were understated.  
**Decision:** Document actual security posture with explicit risk acknowledgments. **localStorage JWT is a PRODUCTION BLOCKER.**

**Security Corrections (v3.0):**

1. **JWT Token Storage — PRODUCTION BLOCKER:**
   - Web (React): JWT stored in `localStorage` — **vulnerable to XSS attacks**
   - CSP headers **reduce** XSS risk but do **NOT eliminate** localStorage token theft
   - CSP cannot prevent all XSS vectors (vulnerable third-party libraries, DOM-based XSS)
   - **Status:** 🚫 **PRODUCTION BLOCKER** — must be resolved before production deployment
   - **Required fix:** Migrate to HttpOnly cookie-based authentication with CSRF protection
   - **Owner-waivable?** **NO** — this is a non-negotiable security requirement

   **Authentication Approach Comparison:**

   | Approach | XSS Risk | CSRF Risk | Implementation Complexity | Recommendation |
   |----------|----------|-----------|--------------------------|----------------|
   | localStorage + Bearer header | **HIGH** — any XSS steals token | None (not cookie-based) | Low | ❌ **BLOCKED for production** |
   | HttpOnly cookie + SameSite=Strict | **LOW** — cookie not accessible to JS | **LOW** — SameSite=Strict prevents cross-site | Medium | ✅ **RECOMMENDED** |
   | HttpOnly cookie + SameSite=Lax + CSRF token | **LOW** — cookie not accessible to JS | **NONE** — CSRF token validates origin | Medium-High | ✅ **RECOMMENDED** (most secure) |
   | Short-lived access + refresh token in HttpOnly cookie | **LOW** — access token expires quickly | **LOW** — refresh token is HttpOnly | High | ✅ **BEST** (but complex) |

   **Recommended Migration Path:**
   1. Backend: Add `/api/v1/auth/refresh` endpoint that issues new JWT from HttpOnly refresh cookie
   2. Backend: Set JWT cookie with `HttpOnly; Secure; SameSite=Strict; Path=/`
   3. Backend: Add CSRF token endpoint (`/api/v1/csrf-token`) for state-changing requests
   4. Frontend: Remove localStorage JWT storage; use `withCredentials: true` in Axios
   5. Frontend: Include CSRF token in request headers for POST/PUT/DELETE
   6. Nginx: Add `X-CSRF-Token` to allowed headers in CORS configuration

2. **Flutter Token Storage:**
   - Mobile (Flutter): JWT stored in `flutter_secure_storage` (Keychain/Keystore)
   - Significantly lower risk than web localStorage — hardware-backed encryption
   - **Status:** ✅ Acceptable (Flutter app not yet implemented)

3. **Actuator Endpoint Exposure:**
   - `/actuator/health` exposed via Nginx — returns `{"status": "UP"}` only (show-details=never)
   - **⚠️ CORRECTION:** Detailed health info (database status, disk space) must NOT be public
   - **Recommendation:** Restrict `/actuator/health` to localhost monitoring only (deny external access)
   - `/actuator/info` should not expose build version or environment variables

4. **CORS and CSRF:**
   - CORS configured for production domain only — correct
   - CSRF not required for current stateless JWT API (localStorage + Bearer header) — correct
   - **⚠️ REQUIRED CHANGE:** When migrating to HttpOnly cookie auth, CSRF protection **MUST** be added
   - CSRF implementation is part of the JWT migration path (see item 1 above)

5. **Secret Rotation:**
   - Secrets injected via environment variables — correct (no hardcoded values)
   - **⚠️ Missing:** No documented rotation procedure for JWT secret, database password, API keys
   - **Required:** Define rotation frequency, procedure, and zero-downtime strategy

6. **Role Authorization:**
   - SecurityConfig exists with RBAC
   - **⚠️ UNVERIFIED:** Comprehensive negative tests for all 6 roles not confirmed
   - **Required:** Test that unauthorized roles receive 403/404 for each endpoint

**Role Coverage Matrix Reference (REVISION 5):**
See `GMS_STAGE_J_FINAL_ARCHITECTURE5.md` Section 2.3 for the authoritative six-role web and five-role Flutter coverage matrix. Web roles: SUPERADMIN, ADMIN, TEACHER, STUDENT, PARENT, GUEST. Flutter roles: ADMIN, TEACHER, STUDENT, PARENT, GUEST.

**Owner Approval Required:**
- ✅ Yes — Approve actuator restriction to localhost-only
- 🚫 **NOT WAIVABLE** — localStorage JWT migration to HttpOnly cookie is a production blocker

**Repository Evidence:**
- `gms_backend/src/main/java/com/gms/config/SecurityConfig.java` — RBAC configuration
- `gms_backend/src/main/resources/application-prod.properties` — Actuator config
- Architecture document Section 2.4 — Security boundaries (REVISED v3.0)
- Architecture document Section 2.4.2 — JWT localStorage PRODUCTION BLOCKER

---

## D-012: Token Revocation Strategy (REVISION 5 — PROPOSED)

**Category:** Security  
**Context:** JWT tokens are currently stateless with no server-side revocation. This is a critical gap for production — compromised tokens, user logout, and password changes cannot invalidate existing tokens.  
**Decision (PROPOSED):** Implement per-user token version counter.  
**Status:** PROPOSED DESIGN — NOT IMPLEMENTED  
**Details:** See `GMS_STAGE_J_FINAL_ARCHITECTURE5.md` Section 2.4.2 item 5 for the full token revocation design.

---

## Summary of Owner Decisions Required

| Decision ID | Description | Owner Action Required |
|-------------|-------------|----------------------|
| D-001 | Oracle Cloud Infrastructure Always Free | Verify eligibility, capacity, and public IP cost |
| D-002 | Neon PostgreSQL free tier | Create project, provide connection string, monitor usage |
| D-003 | Self-hosted frontend (NOT Vercel) | No action (legal compliance decision) |
| D-004 | Flutter for mobile (NOT PWA) | Confirm product architecture, resource Flutter team |
| D-005 | Docker and Docker Compose | No action (standard practice) |
| D-006 | Nginx + Let's Encrypt SSL/TLS | No action (security best practice) |
| D-007 | GitHub Actions with feature-branch workflow (REVISED v3.0) | No action (standard practice — was: automated push-to-master deployment) |
| D-008 | Flyway migrations via Docker container (REVISED v3.0) | Change `spring.flyway.enabled` from `true` to `false` in code |
| D-009 | Daily backups with corrected pg_dump (REVISED v2.0) | Approve off-site storage cost (~₹1/month); accept RTO as unverified |
| D-010 | UptimeRobot monitoring (free tier) | No action (free tier, no cost) |
| D-011 | Application security posture (REVISED v3.0) | 🚫 **MUST** migrate localStorage JWT to HttpOnly cookie (production blocker) |

**Total Owner Decisions Required:** 3 (D-001, D-002, D-004)  
**Total Owner Approvals Required:** 3 (D-009 off-site cost, D-009 RTO acceptance, D-011 security)  
**Production Blockers:** 1 (D-011 localStorage JWT — must resolve before production)

---

## Contradictions Resolved

| # | Contradiction | Resolution | Decision ID |
|---|---------------|------------|-------------|
| 1 | Oracle vs DigitalOcean/Hetzner | Use Oracle Always Free (owner requirement) | D-001 |
| 2 | Vercel Hobby for commercial SaaS | Self-host on OCI (Vercel prohibits commercial use) | D-003 |
| 3 | PWA vs Flutter | Flutter for mobile, no PWA (owner requirement) | D-004 |
| 4 | Flyway on startup vs deployment step | Deployment-step migrations via Docker container (REVISED v3.0) | D-008 |
| 5 | pg_dump with JDBC URL vs PostgreSQL params | PostgreSQL connection parameters (REVISED v2.0) | D-009 |
| 6 | CSP eliminates XSS risk vs CSP reduces XSS risk | CSP reduces but does NOT eliminate localStorage token theft; **PRODUCTION BLOCKER** (REVISED v3.0) | D-011 |
| 7 | Neon free tier assumptions | 1 GB storage, 100 CU-hours, owner monitors usage | D-002 |
| 8 | Docker Compose suitability | Suitable for single-instance deployment | D-005 |
| 9 | API routing | Nginx reverse proxy on OCI instance | D-006 |
| 10 | Flyway execution mechanism | `flyway/flyway:10` Docker container (REVISED v3.0 — was: invalid `java -jar` command in v2.0) | D-008 |
| 11 | Backup retention | Daily backups, 30-day retention, off-site storage | D-009 |
| 12 | CI/CD deployment trigger | Feature-branch + `workflow_dispatch` (REVISED v3.0 — was: push-to-master in v2.0) | D-007 |
| 13 | Performance claims | Verify at pilot scale (2,000 students) | N/A (testing required) |
| 14 | `spring.flyway.enabled` value | Currently `true` in code; MUST change to `false` before production (REVISED v3.0) | D-008 |
| 15 | JWT localStorage safety | **PRODUCTION BLOCKER** — must migrate to HttpOnly cookie (REVISED v3.0 — was: "acceptable for pilot" in v2.0) | D-011 |

---

**Document Version:** 5.0 (Revision 5 — Final Reconciliation and Correction)  
**Last Updated:** 2026-11-03  
**Next Review:** After owner decisions and technical verification complete  
**Status:** NO-GO — PRODUCTION BLOCKERS UNRESOLVED

**Revision 4 Changes:**
- D-007: Added GitHub Environment setup instructions, documented complete artifact transfer chain, marked unverified action SHAs as REQUIRES_VERIFICATION
- D-008: Aligned with canonical Flyway migration procedure from Architecture doc, pinned Flyway image to exact version (10.15.2 PROPOSED), added environment variable validation
- D-011: Explicitly labeled HttpOnly cookie migration as REQUIRED FUTURE IMPLEMENTATION
- Throughout: Added evidence classification labels (VERIFIED / PROPOSED / REQUIRED / UNKNOWN / OWNER DECISION)
- Cost claims: Added Mandatory / Optional / Unknown classification
- Role readiness: Added explicit Flutter NOT IMPLEMENTED status

**Revision 5 Changes:**
- Added role coverage matrix reference
- Added token revocation strategy decision (PROPOSED)
- Updated version to 5.0
- Aligned all references to Revision 5 canonical procedures
