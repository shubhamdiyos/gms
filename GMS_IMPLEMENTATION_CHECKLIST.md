# GMS Implementation Checklist

**Date:** 2026-11-03
**Status:** NOT STARTED -- All tasks require implementation
**Purpose:** Canonical actionable engineering checklist organized by implementation phase
**Source Documents:**
- `GMS_STAGE_J_FINAL_RELEASE_GATES5.md` -- primary source (phases and tasks)
- `GMS_STAGE_J_FINAL_ARCHITECTURE5.md` -- technical details for acceptance criteria
- `GMS_STAGE_J_FINAL_DECISION_LOG5.md` -- decision context

---

## How to Use This Checklist

Each task includes:
- **Task ID**: Sequential identifier (T-001, T-002, etc.)
- **Description**: What needs to be done
- **Dependencies**: What must be completed first (task IDs)
- **Priority**: P0 (blocker), P1 (critical), P2 (important), P3 (nice-to-have)
- **Acceptance Criteria**: Measurable conditions for completion
- **Required Evidence**: What proof demonstrates completion (test results, screenshots, logs -- NOT documentation)
- **Status**: NOT_STARTED, IN_PROGRESS, BLOCKED, or VERIFIED

**Critical Rules:**
1. A task is NOT VERIFIED until actual evidence exists (test results, screenshots, logs). Documentation alone is NOT evidence.
2. Tasks are marked BLOCKED when they depend on owner decisions or unverified prerequisites.
3. Where a task requires an owner decision, the decision ID (D-001, D-002, etc.) is noted.

---

## Phase 0: Owner Decisions and Account Setup

**Duration:** 1-2 days
**Prerequisites:** None
**Owner:** Project owner + deployment team

---

### T-001: Verify OCI Always Free Eligibility

- **Description:** Confirm OCI account qualifies for Always Free tier and ARM capacity (VM.Standard.A1.Flex: 2 OCPUs, 12 GB RAM, 100 GB boot) is available in ap-mumbai-1 region. Verify public IPv4 cost (free or approved cost). Accept single-region deployment trade-off.
- **Dependencies:** None
- **Priority:** P0 (blocker)
- **Decision Reference:** D-001 (Oracle Cloud Infrastructure Always Free as Primary Hosting)
- **Acceptance Criteria:**
  - [ ] Owner confirms OCI Always Free eligibility
  - [ ] Owner confirms ARM capacity in ap-mumbai-1 region
  - [ ] Owner confirms public IPv4 cost (free or approved cost)
  - [ ] Owner accepts single-region deployment (no multi-region failover)
- **Required Evidence:**
  - OCI account dashboard screenshot showing Always Free eligibility
  - Output of `oci compute capacity-report list --availability-domain <AD> --shape VM.Standard.A1.Flex`
- **Status:** NOT_STARTED

---

### T-002: Confirm ARM Capacity in ap-mumbai-1

- **Description:** Verify that the ap-mumbai-1 region has available ARM capacity for VM.Standard.A1.Flex instances. Run preflight check before provisioning.
- **Dependencies:** T-001
- **Priority:** P0 (blocker)
- **Decision Reference:** D-001
- **Acceptance Criteria:**
  - [ ] `./preflight.sh --compartment "<compartment-ocid>"` completes successfully
  - [ ] ARM capacity confirmed in at least one availability domain in ap-mumbai-1
  - [ ] No capacity constraints reported
- **Required Evidence:**
  - Preflight script output showing capacity availability
  - Availability domain name confirmed
- **Status:** NOT_STARTED

---

### T-003: Create Neon PostgreSQL Project

- **Description:** Create a Neon PostgreSQL project (gms-production) in ap-mumbai-1 region with PostgreSQL 15, database name `gms`. Obtain and securely transmit the connection string. Confirm free tier limits: 1 GB storage, 100 CU-hours/month compute.
- **Dependencies:** None
- **Priority:** P0 (blocker)
- **Decision Reference:** D-002 (Neon PostgreSQL as Database Provider)
- **Acceptance Criteria:**
  - [ ] Neon PostgreSQL account created (https://neon.tech)
  - [ ] Project `gms-production` created
  - [ ] Region set to ap-mumbai-1 (closest to OCI instance)
  - [ ] PostgreSQL version 15 selected
  - [ ] Database name: `gms`
  - [ ] Connection string obtained (format: `postgresql://<user>:<password>@<host>.neon.tech/gms?sslmode=require`)
  - [ ] Free tier limits confirmed (1 GB storage, 100 CU-hours/month)
  - [ ] Continuous backups enabled (7-day retention, free tier)
- **Required Evidence:**
  - Neon project dashboard screenshot
  - Connection string (securely transmitted -- NOT in plain text in any document or log)
- **Status:** NOT_STARTED

---

### T-004: Approve Domain Purchase (~INR 800-1,200/year)

- **Description:** Owner must explicitly approve domain purchase cost (~INR 800-1,200/year / $10-15/year). Without a domain, Let's Encrypt certificates cannot be obtained, HTTPS cannot be established, and production deployment is BLOCKED. HTTP over raw public IP is NOT production-ready and is a security risk. Recommended registrars: Namecheap, GoDaddy, or Cloudflare.
- **Dependencies:** None
- **Priority:** P0 (blocker -- REQUIRED for HTTPS/production)
- **Decision Reference:** D-006 (Nginx Reverse Proxy with Let's Encrypt SSL/TLS)
- **Cost:** Mandatory for production (NOT covered by INR 0 budget constraint)
- **Acceptance Criteria:**
  - [ ] Owner explicitly approves domain purchase cost
  - [ ] Domain name selected and purchased
  - [ ] Domain registration confirmation received
- **Required Evidence:**
  - Domain registration confirmation (receipt/email)
  - Owner written approval of cost
- **Status:** BLOCKED (requires OWNER DECISION -- not approved under INR 0 constraint)

---

### T-005: Approve Off-site Backup Storage (~INR 1/month)

- **Description:** Owner must approve or deny off-site backup storage cost (~INR 1/month for Backblaze B2, 10 GB). Without off-site backups, if the OCI instance is lost, local backups are also lost (total data loss risk). Without owner approval, backups are local-only (30-day retention on OCI instance).
- **Dependencies:** None
- **Priority:** P2 (important -- OPTIONAL)
- **Decision Reference:** D-009 (Daily Backups with 30-Day Retention)
- **Cost:** Optional (~INR 12/year for Backblaze B2)
- **Acceptance Criteria:**
  - [ ] Owner explicitly approves or denies off-site backup cost
  - [ ] Decision documented
  - [ ] If approved: Backblaze B2 or AWS S3 account ready for configuration
  - [ ] If denied: Owner explicitly accepts risk of local-only backups
- **Required Evidence:**
  - Owner written approval/denial of cost
  - If approved: object storage account details (securely stored)
- **Status:** NOT_STARTED (requires OWNER APPROVAL)

---

### T-006: Confirm Flutter Mobile App Architecture

- **Description:** Owner confirms that Flutter mobile app is a separate project (not in this repository), targeting 5 school roles: ADMIN, ACCOUNTANT, TEACHER, STUDENT, PARENT. SUPERADMIN is web-only (no Flutter app needed). Native app required, NOT PWA.
- **Dependencies:** None
- **Priority:** P1 (critical)
- **Decision Reference:** D-004 (No PWA -- Flutter for Mobile)
- **Acceptance Criteria:**
  - [ ] Owner confirms Flutter architecture decision (native app, NOT PWA)
  - [ ] Owner confirms 5 school roles require Flutter implementation
  - [ ] Owner confirms SUPERADMIN is web-only (no Flutter)
  - [ ] Flutter project repository planned/created (separate from GMS backend/frontend repo)
  - [ ] Flutter team resourcing decided (hire or train)
- **Required Evidence:**
  - Written owner confirmation of Flutter architecture
  - Flutter project plan or repository reference
- **Status:** NOT_STARTED

---

### T-007: Verify GitHub Actions Private Repo Allowances

- **Description:** Verify GitHub Actions minutes and GHCR storage allowances for the specific account. Free tier limits: 2,000 minutes/month (private repos), 500 MB GHCR storage (public), 1 GB/month GHCR bandwidth (from GitHub Actions). Confirm whether repository is public or private, as this affects free tier limits.
- **Dependencies:** None
- **Priority:** P1 (critical)
- **Decision Reference:** D-007 (GitHub Actions CI/CD with Feature-Branch Workflow)
- **Acceptance Criteria:**
  - [ ] GitHub account billing page checked for current minutes usage
  - [ ] Repository visibility confirmed (public or private)
  - [ ] GHCR storage usage verified against account limits
  - [ ] Owner confirms 2,000 min/month is sufficient for deployment cadence
  - [ ] Cost verified as INR 0 (within free tier)
- **Required Evidence:**
  - GitHub Actions billing page screenshot
  - GHCR storage usage screenshot
- **Status:** NOT_STARTED (UNKNOWN -- must verify for specific account)

---

## Phase 1: Infrastructure Provisioning

**Duration:** 2-3 days
**Prerequisites:** Phase 0 complete (T-001, T-002, T-003)
**Owner:** Deployment team

---

### T-008: Create OCI Account and Verify Always Free Tier

- **Description:** Create Oracle Cloud account at https://cloud.oracle.com with home region ap-mumbai-1. Verify Always Free tier eligibility. Configure compartment. Generate SSH key pair. Record compartment OCID.
- **Dependencies:** T-001, T-002
- **Priority:** P0 (blocker)
- **Decision Reference:** D-001
- **Acceptance Criteria:**
  - [ ] OCI account created with home region: ap-mumbai-1
  - [ ] Always Free tier eligibility confirmed
  - [ ] Compartment OCID recorded
  - [ ] SSH key pair generated and securely stored
  - [ ] Owner's public IP recorded for SSH access restriction
- **Required Evidence:**
  - OCI account dashboard screenshot
  - Compartment OCID (recorded securely)
  - SSH public key file path
- **Status:** BLOCKED (depends on T-001, T-002 owner decisions)

---

### T-009: Configure UFW Firewall (22, 80, 443)

- **Description:** Configure UFW firewall on OCI instance: default deny incoming, allow outgoing, allow only TCP ports 22 (SSH, restricted to owner's IP /32), 80 (HTTP, for redirect to HTTPS), and 443 (HTTPS). Ports NOT exposed: 5432 (PostgreSQL -- on Neon, not local), 8080 (Spring Boot -- internal only via Nginx), 3000 (Vite dev server -- not used in production).
- **Dependencies:** T-008
- **Priority:** P0 (blocker)
- **Decision Reference:** D-001
- **Acceptance Criteria:**
  - [ ] `ufw default deny incoming` executed
  - [ ] `ufw default allow outgoing` executed
  - [ ] `ufw allow 22/tcp` (SSH, restricted to owner's IP /32 in OCI security list)
  - [ ] `ufw allow 80/tcp` (HTTP)
  - [ ] `ufw allow 443/tcp` (HTTPS)
  - [ ] `ufw --force enable` executed
  - [ ] Port 5432 NOT in firewall rules
  - [ ] Port 8080 NOT in firewall rules
  - [ ] OCI security list configured: TCP/22 from owner-ip/32, TCP/80 from 0.0.0.0/0, TCP/443 from 0.0.0.0/0
- **Required Evidence:**
  - `ufw status` output showing only 22, 80, 443 allowed
  - OCI security list screenshot
- **Status:** BLOCKED (depends on T-008)

---

### T-010: Install Docker and Docker Compose on OCI Instance

- **Description:** Install Docker Engine and Docker Compose v2 on the OCI instance (Ubuntu 24.04 ARM64). Verify Docker daemon is running. Ensure Docker daemon is NOT exposed remotely. Cloud-init script handles this during `tofu apply`.
- **Dependencies:** T-008
- **Priority:** P0 (blocker)
- **Decision Reference:** D-005 (Docker and Docker Compose for Containerization)
- **Acceptance Criteria:**
  - [ ] Docker Engine installed (`docker --version` returns version)
  - [ ] Docker Compose v2 installed (`docker compose version` returns version)
  - [ ] Docker daemon running (`systemctl status docker` shows active)
  - [ ] Docker daemon NOT exposed remotely (no TCP binding)
  - [ ] Non-root user `gmsdeploy` added to `docker` group
  - [ ] Cloud-init script executed during instance provisioning
- **Required Evidence:**
  - `docker --version` output
  - `docker compose version` output
  - `systemctl status docker` output
  - `groups gmsdeploy` output showing docker group membership
- **Status:** BLOCKED (depends on T-008)

---

### T-011: Create gmsdeploy User and Group

- **Description:** Create non-root system user `gmsdeploy` with group `gmsdeploy` on the OCI instance. This user owns application files, runs Docker Compose, and has SSH access. Root login must be disabled.
- **Dependencies:** T-008
- **Priority:** P1 (critical)
- **Decision Reference:** D-001
- **Acceptance Criteria:**
  - [ ] Group `gmsdeploy` created (`groupadd gmsdeploy`)
  - [ ] User `gmsdeploy` created (`useradd -r -g gmsdeploy -s /bin/bash -m gmsdeploy`)
  - [ ] SSH authorized key configured for gmsdeploy user
  - [ ] Root SSH login disabled (`PermitRootLogin no` in `/etc/ssh/sshd_config`)
  - [ ] Password SSH disabled (`PasswordAuthentication no`)
  - [ ] gmsdeploy added to docker group (`usermod -aG docker gmsdeploy`)
- **Required Evidence:**
  - `id gmsdeploy` output
  - `groups gmsdeploy` output
  - SSH login test as gmsdeploy user
- **Status:** BLOCKED (depends on T-008)

---

### T-012: Set Up /etc/gms/.env with Secure Permissions (0640)

- **Description:** Create the canonical environment file at `/etc/gms/.env` with permissions 0640, owned by gmsdeploy:docker. This file contains ALL secrets: database credentials (JDBC URL format), JWT secret, CORS origins, superadmin credentials, and deployment tracking. Uses canonical JDBC URL-based contract: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.
- **Dependencies:** T-011, T-003
- **Priority:** P0 (blocker)
- **Decision Reference:** D-002, D-008
- **Acceptance Criteria:**
  - [ ] Directory `/etc/gms/` created with permissions 0750
  - [ ] File `/etc/gms/.env` created with permissions 0640
  - [ ] Owner: gmsdeploy, Group: docker
  - [ ] `DB_URL` set (format: `jdbc:postgresql://<neon-host>:5432/gms?sslmode=require`)
  - [ ] `DB_USERNAME` set (Neon database user)
  - [ ] `DB_PASSWORD` set (Neon database password)
  - [ ] `JWT_SECRET` generated (`openssl rand -base64 48`, minimum 48 characters)
  - [ ] `CORS_ALLOWED_ORIGINS` set to `https://gms.yourdomain.com`
  - [ ] `APP_SUPERADMIN_ENABLED=true`
  - [ ] `APP_SUPERADMIN_USERNAME=admin`
  - [ ] `APP_SUPERADMIN_PASSWORD` set (minimum 12 characters, mixed case, numbers, symbols)
  - [ ] `APP_SUPERADMIN_EMAIL` set
  - [ ] `APP_SUPERADMIN_FULL_NAME=Platform Administrator`
  - [ ] `DEPLOYMENT_SHA` placeholder present
  - [ ] No secrets in source code, logs, or Docker image layers
- **Required Evidence:**
  - `ls -la /etc/gms/.env` showing permissions 0640 and ownership gmsdeploy:docker
  - `stat -c "%a %U:%G" /etc/gms/.env` output
  - File content with secrets REDACTED (show structure only)
- **Status:** BLOCKED (depends on T-003, T-011)

---

### T-013: Configure UFW Logging

- **Description:** Enable and configure UFW logging for security audit trail. Log blocked connections and allowed connections for forensic analysis.
- **Dependencies:** T-009
- **Priority:** P2 (important)
- **Decision Reference:** D-001
- **Acceptance Criteria:**
  - [ ] UFW logging enabled (`ufw logging on`)
  - [ ] Log level set appropriately (low or medium for production)
  - [ ] Log rotation configured for UFW logs
  - [ ] Log location verified (`/var/log/ufw.log` or `/var/log/syslog`)
- **Required Evidence:**
  - `ufw status verbose` output showing logging enabled
  - Log file existence confirmed
- **Status:** BLOCKED (depends on T-009)

---

### T-014: Set Up fail2ban (SSH Protection)

- **Description:** Install and configure fail2ban to protect SSH access. Ban IPs after repeated failed login attempts. Configure for port 22 only (other ports are not exposed).
- **Dependencies:** T-009, T-011
- **Priority:** P1 (critical)
- **Decision Reference:** D-001
- **Acceptance Criteria:**
  - [ ] fail2ban installed (`apt install fail2ban`)
  - [ ] SSH jail configured (`/etc/fail2ban/jail.local`)
  - [ ] Max retries set (recommended: 3-5 attempts)
  - [ ] Ban time set (recommended: 1 hour for first offense)
  - [ ] fail2ban service running (`systemctl status fail2ban`)
  - [ ] SSH jail active (`fail2ban-client status sshd`)
- **Required Evidence:**
  - `fail2ban-client status sshd` output
  - `systemctl status fail2ban` output
  - Jail configuration file content
- **Status:** BLOCKED (depends on T-009, T-011)

---

### T-015: Configure Automatic Security Updates

- **Description:** Configure unattended-upgrades for automatic security patching on Ubuntu 24.04. Ensure critical security updates are applied automatically without manual intervention.
- **Dependencies:** T-008
- **Priority:** P1 (critical)
- **Decision Reference:** D-001
- **Acceptance Criteria:**
  - [ ] `unattended-upgrades` package installed
  - [ ] Automatic security updates enabled (`/etc/apt/apt.conf.d/20auto-upgrades`)
  - [ ] Security-only updates configured (not all updates)
  - [ ] Update package lists automatically (`APT::Periodic::Update-Package-Lists "1"`)
  - [ ] Unattended upgrade enabled (`APT::Periodic::Unattended-Upgrade "1"`)
  - [ ] Email notification configured (optional)
- **Required Evidence:**
  - `/etc/apt/apt.conf.d/20auto-upgrades` content
  - `/etc/apt/apt.conf.d/50unattended-upgrades` relevant sections
  - `systemctl status unattended-upgrades` output
- **Status:** BLOCKED (depends on T-008)

---

### T-015.1: Provision OCI Instance via OpenTofu

- **Description:** Execute the full infrastructure provisioning using OpenTofu. Configure `terraform.tfvars` with compartment_ocid, ssh_public_key_path, ssh_source_cidr (owner's public IP /32). Run preflight, plan, and apply. Instance spec: VM.Standard.A1.Flex, Ubuntu 24.04 ARM64, 2 OCPUs, 12 GB RAM, 100 GB boot volume.
- **Dependencies:** T-001, T-002, T-008
- **Priority:** P0 (blocker)
- **Decision Reference:** D-001
- **Commands:**
  ```bash
  cd infra/oci
  cp terraform.tfvars.example terraform.tfvars
  # Edit terraform.tfvars with:
  # - compartment_ocid (from OCI console)
  # - ssh_public_key_path (absolute path to .pub file)
  # - ssh_source_cidr (owner's public IP /32)
  tofu init
  ./preflight.sh --compartment "<compartment-ocid>"
  tofu plan  # Review plan carefully
  tofu apply  # Provision infrastructure
  ```
- **Acceptance Criteria:**
  - [ ] `terraform.tfvars` configured with correct values
  - [ ] OpenTofu plan reviewed and approved (no unexpected resources)
  - [ ] `tofu apply` executed successfully
  - [ ] OCI instance provisioned (VM.Standard.A1.Flex)
  - [ ] Public IP assigned and recorded
  - [ ] SSH access verified (`ssh gmsdeploy@<public-ip>`)
  - [ ] Instance running Ubuntu 24.04 ARM64
  - [ ] VCN configured: gms-prod-vcn (10.0.0.0/16)
  - [ ] Internet gateway enabled
  - [ ] Security list configured (TCP/22 owner-only, TCP/80, TCP/443)
- **Required Evidence:**
  - `tofu apply` output (redacted for secrets)
  - SSH session screenshot (`ssh gmsdeploy@<public-ip>`)
  - `hostnamectl` output showing Ubuntu 24.04 ARM64
  - Public IP recorded securely
- **Status:** BLOCKED (depends on T-001, T-002, T-008)
- **Rollback Procedure:** `tofu destroy` (decommissions all OCI resources)

---

## Phase 2: Backend Production Readiness

**Duration:** 2-3 days
**Prerequisites:** Phase 1 complete (T-008 through T-015.1)
**Owner:** Deployment team

---

### T-016: Set spring.flyway.enabled=false in application-prod.properties

- **Description:** REQUIRED CODE CHANGE. Set `spring.flyway.enabled=false` in `gms_backend/src/main/resources/application-prod.properties`. This prevents Flyway from running migrations on application startup. Migrations must run as a controlled deployment step via `/opt/gms/migrate.sh` BEFORE the new application version starts. Current state: `spring.flyway.enabled=true` (UNSAFE for production).
- **Dependencies:** None
- **Priority:** P0 (blocker)
- **Decision Reference:** D-008 (Flyway Migrations via Docker Container)
- **Acceptance Criteria:**
  - [ ] `spring.flyway.enabled=false` set in `gms_backend/src/main/resources/application-prod.properties`
  - [ ] `spring.flyway.clean-disabled=true` confirmed (already set)
  - [ ] `spring.jpa.hibernate.ddl-auto=validate` confirmed (already set)
  - [ ] `spring.flyway.baseline-on-migrate=false` confirmed (already set)
  - [ ] Change committed to repository
  - [ ] Build passes with change (`mvn clean package -DskipTests`)
  - [ ] Application starts without attempting migration
- **Required Evidence:**
  - File content of `application-prod.properties` showing `spring.flyway.enabled=false`
  - Successful `mvn clean package -DskipTests` build output
  - Application startup log showing no Flyway migration attempt
- **Status:** NOT_STARTED

---

### T-017: Configure HikariCP Connection Pooling for Neon

- **Description:** Configure HikariCP connection pool in `application-prod.properties` for Neon PostgreSQL. Set maximum pool size to 10 (Neon free tier allows 20 connections per project). Configure SSL connection (`sslmode=require`). Set appropriate timeout values for Neon serverless cold-start behavior.
- **Dependencies:** T-016, T-003
- **Priority:** P1 (critical)
- **Decision Reference:** D-002
- **Acceptance Criteria:**
  - [ ] `spring.datasource.hikari.maximum-pool-size=10` configured
  - [ ] `spring.datasource.hikari.minimum-idle=2` configured
  - [ ] `spring.datasource.hikari.idle-timeout=300000` (5 minutes) configured
  - [ ] `spring.datasource.hikari.max-lifetime=1800000` (30 minutes) configured
  - [ ] `spring.datasource.hikari.connection-timeout=30000` (30 seconds, accounts for cold start) configured
  - [ ] Database URL includes `sslmode=require`
  - [ ] Pool size verified against Neon free tier limit (20 connections max)
- **Required Evidence:**
  - `application-prod.properties` content showing HikariCP configuration
  - Application startup log showing HikariCP pool initialization
  - Neon dashboard showing connection count
- **Status:** BLOCKED (depends on T-003)

---

### T-018: Set JVM Memory Limits (-XX:MaxRAMPercentage=75.0)

- **Description:** Configure JVM memory limits in the Dockerfile for the production container. Set `-XX:MaxRAMPercentage=75.0` to cap JVM heap at 75% of container memory limit. Container limit: 12 GB (OCI instance RAM). Expected JVM heap: ~9 GB. Actual usage: ~4 GB estimated for 2,000 students.
- **Dependencies:** T-010
- **Priority:** P1 (critical)
- **Decision Reference:** D-005
- **Acceptance Criteria:**
  - [ ] Dockerfile ENTRYPOINT includes `-XX:MaxRAMPercentage=75.0`
  - [ ] Multi-stage build uses `eclipse-temurin:21-jre-alpine` for runtime
  - [ ] Non-root user configured (`addgroup -S spring && adduser -S spring -G spring; USER spring:spring`)
  - [ ] Image size < 300 MB
  - [ ] JVM memory settings verified in container (`docker exec gms-backend java -XX:+PrintFlagsFinal -version 2>&1 | grep MaxRAMPercentage`)
- **Required Evidence:**
  - Dockerfile content showing JVM configuration
  - `docker images` output showing image size
  - JVM flag verification output from running container
- **Status:** BLOCKED (depends on T-010)

---

### T-019: Configure Actuator Endpoints (health, info -- private only)

- **Description:** Configure Spring Boot Actuator to expose only `health` and `info` endpoints. Ensure `management.endpoint.health.show-details=never` (no sensitive data in health response). Actuator endpoints must be accessible on port 8080 (internal only) and MUST NOT be exposed publicly via Nginx. A separate lightweight `/health` endpoint (returning `200 OK` text only) is created in Nginx for external monitoring.
- **Dependencies:** T-016
- **Priority:** P1 (critical)
- **Decision Reference:** D-011 (Application Security Posture)
- **Acceptance Criteria:**
  - [ ] `management.endpoints.web.exposure.include=health,info` in application-prod.properties
  - [ ] `management.endpoint.health.show-details=never` configured
  - [ ] `management.endpoint.health.show-components=never` configured
  - [ ] `/actuator/health` returns `{"status": "UP"}` from localhost (port 8080)
  - [ ] `/actuator/info` does NOT expose build version, environment variables, or sensitive configuration
  - [ ] `/actuator/env` NOT exposed (or returns 404)
  - [ ] `/actuator/heapdump` NOT exposed (or returns 404)
  - [ ] `/actuator/threaddump` NOT exposed (or returns 404)
- **Required Evidence:**
  - `application-prod.properties` content showing actuator configuration
  - `curl http://127.0.0.1:8080/actuator/health` output (from localhost)
  - `curl http://127.0.0.1:8080/actuator/env` output showing 403/404
- **Status:** NOT_STARTED

---

### T-020: Build Production Docker Image (multi-stage, non-root)

- **Description:** Build production Docker image for Spring Boot backend using multi-stage build. Stage 1: Maven build with `maven:3.9-eclipse-temurin-21`. Stage 2: Runtime with `eclipse-temurin:21-jre-alpine`. Run as non-root user `spring:spring`. Tag with immutable Git SHA (NOT `:latest`). Push to GitHub Container Registry (GHCR).
- **Dependencies:** T-016, T-017, T-018, T-019
- **Priority:** P0 (blocker)
- **Decision Reference:** D-005, D-007
- **Commands:**
  ```bash
  cd gms_backend
  docker build -t gms-backend:latest .
  GIT_SHA=$(git rev-parse --short HEAD)
  docker tag gms-backend:latest ghcr.io/<owner>/gms-backend:$GIT_SHA
  echo $GITHUB_TOKEN | docker login ghcr.io -u <owner> --password-stdin
  docker push ghcr.io/<owner>/gms-backend:$GIT_SHA
  ```
- **Acceptance Criteria:**
  - [ ] Docker image built successfully (multi-stage build)
  - [ ] Image size < 300 MB
  - [ ] Image runs as non-root user (`spring:spring`)
  - [ ] Image tagged with Git SHA (`gms-backend:<commit-sha>`)
  - [ ] `:latest` tag NOT used for production deployment
  - [ ] Image pushed to GitHub Container Registry (GHCR)
  - [ ] Image accessible in GHCR dashboard
  - [ ] Image pullable from OCI instance (`docker pull ghcr.io/<owner>/gms-backend:<sha>`)
  - [ ] Digest recorded for immutable reference (`docker inspect --format='{{index .RepoDigests 0}}'`)
- **Required Evidence:**
  - `docker images` output showing image and size
  - GHCR package screenshot
  - `docker pull` test from OCI instance
  - Deployment manifest with digest
- **Status:** NOT_STARTED

---

### T-021: Test Backend Startup with Neon Connection

- **Description:** Start the backend Docker container against the Neon PostgreSQL database. Verify successful startup, database connection, Flyway schema history table present (from migration script, not app startup), and health check returning UP. Verify no Flyway migration runs on startup (since `spring.flyway.enabled=false`).
- **Dependencies:** T-020, T-003, T-012
- **Priority:** P0 (blocker)
- **Decision Reference:** D-002, D-008
- **Acceptance Criteria:**
  - [ ] Backend container starts without errors
  - [ ] Database connection established to Neon (SSL verified)
  - [ ] No Flyway migration runs on application startup (confirmed in logs)
  - [ ] `/actuator/health` returns `{"status": "UP"}` via localhost
  - [ ] Spring Boot application started on port 8080 (internal only)
  - [ ] Container bound to `127.0.0.1:8080` (NOT `0.0.0.0:8080`)
  - [ ] No errors in container logs (`docker logs gms-backend`)
  - [ ] StartupSecurityValidator passes all checks
- **Required Evidence:**
  - `docker ps` output showing container running
  - `docker logs gms-backend` output (last 50 lines, no errors)
  - `curl http://127.0.0.1:8080/actuator/health` output
  - Log showing `spring.flyway.enabled=false` is respected (no migration attempt)
- **Status:** BLOCKED (depends on T-003, T-012, T-020)

---

### T-022: Verify All REST Endpoints Respond Correctly

- **Description:** Test all REST API endpoints via the Nginx proxy (HTTPS). Verify login, authenticated endpoints, CORS headers, JWT token issuance, tenant isolation, and response times.
- **Dependencies:** T-021
- **Priority:** P1 (critical)
- **Decision Reference:** D-011
- **Acceptance Criteria:**
  - [ ] `POST /api/v1/auth/login` returns JWT token
  - [ ] JWT token expiration configured (3600 seconds)
  - [ ] Authenticated endpoints accessible with valid Bearer token
  - [ ] CORS headers present (`Access-Control-Allow-Origin: https://gms.yourdomain.com`)
  - [ ] Tenant isolation working (school-scoped queries; cross-school access returns 404, not 403)
  - [ ] API response time < 1 second (95th percentile)
  - [ ] Database query time < 500ms (95th percentile)
  - [ ] No N+1 query issues detected
  - [ ] All CRUD operations work for core entities (students, employees, attendance, fees)
- **Required Evidence:**
  - `curl` output for login endpoint (JWT token redacted)
  - `curl` output for authenticated endpoint with Bearer token
  - Browser developer tools screenshot (Network tab showing CORS headers)
  - Response time measurements
- **Status:** BLOCKED (depends on T-021)

---

## Phase 3: Frontend Production Readiness

**Duration:** 1-2 days
**Prerequisites:** Phase 2 complete (T-021, T-022)
**Owner:** Deployment team

---

### T-023: Set VITE_API_BASE_URL=/api/v1 in .env.production

- **Description:** Configure the production environment file for the React frontend. Set `VITE_API_BASE_URL=/api/v1` so all API calls are routed through the same origin (Nginx proxy). This uses relative paths, avoiding CORS preflight for same-origin requests.
- **Dependencies:** None
- **Priority:** P0 (blocker)
- **Decision Reference:** D-003 (Self-Hosted Frontend on OCI)
- **Acceptance Criteria:**
  - [ ] `.env.production` file exists in `gms_frontend/` directory
  - [ ] `VITE_API_BASE_URL=/api/v1` configured
  - [ ] No hardcoded API URLs in frontend source code
  - [ ] Environment variable verified in build output
- **Required Evidence:**
  - `.env.production` file content
  - Build output showing correct API base URL
- **Status:** NOT_STARTED

---

### T-024: Set VITE_MOCK_AUTH=false in .env.production

- **Description:** Disable mock authentication in production. Set `VITE_MOCK_AUTH=false` in `.env.production` to ensure the frontend uses real JWT authentication via the backend API, not mock/local authentication.
- **Dependencies:** T-023
- **Priority:** P0 (blocker)
- **Decision Reference:** D-011
- **Acceptance Criteria:**
  - [ ] `VITE_MOCK_AUTH=false` configured in `.env.production`
  - [ ] Mock auth code paths NOT included in production build
  - [ ] Frontend uses real backend `/api/v1/auth/login` for authentication
- **Required Evidence:**
  - `.env.production` file content showing `VITE_MOCK_AUTH=false`
  - Production build verification (no mock auth in bundle)
- **Status:** NOT_STARTED

---

### T-025: Build Production React Bundle (vite build)

- **Description:** Build the React frontend for production using Vite. Verify build artifacts in `dist/` directory, build size < 10 MB, no build errors or warnings.
- **Dependencies:** T-023, T-024
- **Priority:** P1 (critical)
- **Decision Reference:** D-003
- **Commands:**
  ```bash
  cd gms_frontend
  npm ci
  npm run build
  ls -lh dist/
  ```
- **Acceptance Criteria:**
  - [ ] `npm ci` completes successfully (no dependency conflicts)
  - [ ] `npm run build` completes without errors
  - [ ] Build artifacts generated in `dist/` directory
  - [ ] Total build size < 10 MB
  - [ ] No build errors or warnings
  - [ ] Static assets have cache-busting hashes in filenames
  - [ ] `index.html` present in `dist/` root
- **Required Evidence:**
  - `npm run build` output (full log)
  - `ls -lh dist/` output showing artifact sizes
  - `du -sh dist/` total size
- **Status:** NOT_STARTED

---

### T-026: Deploy to /opt/gms/frontend/dist/

- **Description:** Copy React build artifacts to the OCI instance at `/opt/gms/frontend/dist/`. Set correct file permissions (644 for files, 755 for directories). Verify Nginx can serve the frontend.
- **Dependencies:** T-025, T-008
- **Priority:** P1 (critical)
- **Decision Reference:** D-003
- **Commands:**
  ```bash
  rsync -avz gms_frontend/dist/ gmsdeploy@<oci-ip>:/opt/gms/frontend/dist/
  ssh gmsdeploy@<oci-ip>
  ls -lh /opt/gms/frontend/dist/
  ```
- **Acceptance Criteria:**
  - [ ] Frontend deployed to OCI instance via rsync
  - [ ] Files present in `/opt/gms/frontend/dist/`
  - [ ] File permissions: 644 for files, 755 for directories
  - [ ] Directory ownership: gmsdeploy:gmsdeploy
  - [ ] `index.html` accessible at `/opt/gms/frontend/dist/index.html`
- **Required Evidence:**
  - `rsync` output showing transferred files
  - SSH session: `ls -lhR /opt/gms/frontend/dist/` output
  - File permission verification
- **Status:** BLOCKED (depends on T-008, T-025)

---

### T-027: Configure Nginx SPA Routing (try_files)

- **Description:** Configure Nginx to serve the React SPA with proper routing. Use `try_files $uri $uri/ /index.html` to handle client-side routing. Configure static asset caching (1 year, immutable). Set up reverse proxy for `/api/` to backend on `127.0.0.1:8080`. Configure security headers (HSTS, X-Frame-Options, X-Content-Type-Options, Referrer-Policy, CSP). Block sensitive paths (.git, .env, .htaccess). Create public `/health` endpoint for monitoring. Restrict `/actuator/` to localhost only.
- **Dependencies:** T-026
- **Priority:** P0 (blocker)
- **Decision Reference:** D-006 (Nginx Reverse Proxy with Let's Encrypt SSL/TLS)
- **Configuration file:** `/etc/nginx/sites-available/gms`
- **Acceptance Criteria:**
  - [ ] Nginx configuration created at `/etc/nginx/sites-available/gms`
  - [ ] Site enabled: `ln -s /etc/nginx/sites-available/gms /etc/nginx/sites-enabled/`
  - [ ] `nginx -t` passes (syntax valid)
  - [ ] SPA routing configured: `try_files $uri $uri/ /index.html`
  - [ ] Static asset caching: `expires 1y; add_header Cache-Control "public, immutable"`
  - [ ] API proxy: `location /api/ { proxy_pass http://127.0.0.1:8080/api/; }`
  - [ ] Public `/health` endpoint: returns `200 'OK\n'` (lightweight, no sensitive data)
  - [ ] `/actuator/` restricted: `allow 127.0.0.1; deny all;`
  - [ ] Sensitive paths blocked: `location ~ /\.(git|env|htaccess) { deny all; }`
  - [ ] Security headers configured: HSTS, X-Frame-Options, X-Content-Type-Options, Referrer-Policy
  - [ ] CSP header configured (PROPOSED -- requires testing with actual application)
  - [ ] Nginx restarted: `systemctl restart nginx`
- **Required Evidence:**
  - `nginx -t` output (syntax ok)
  - Full Nginx configuration file content
  - `systemctl status nginx` output
- **Status:** BLOCKED (depends on T-026)

---

### T-028: Test All Frontend Routes

- **Description:** Comprehensive frontend testing: verify all React routes render correctly, SPA routing works (deep links, browser refresh), API calls route to backend, static assets load with correct caching headers, no console errors.
- **Dependencies:** T-027
- **Priority:** P1 (critical)
- **Decision Reference:** D-003
- **Acceptance Criteria:**
  - [ ] Frontend accessible via `https://gms.yourdomain.com`
  - [ ] Login page renders correctly
  - [ ] Dashboard page renders after login
  - [ ] All role-specific pages render (SUPERADMIN, ADMIN, ACCOUNTANT, TEACHER, STUDENT, PARENT)
  - [ ] Deep links work (e.g., `https://gms.yourdomain.com/students/123`)
  - [ ] Browser refresh on any route returns correct page (SPA fallback works)
  - [ ] API calls from frontend reach backend (no CORS errors)
  - [ ] Static assets load with correct caching headers (`Cache-Control: public, immutable`)
  - [ ] No JavaScript console errors
  - [ ] Frontend load time < 3 seconds (95th percentile)
- **Required Evidence:**
  - Browser screenshots of each major route/page
  - Browser developer tools: Network tab showing successful API calls
  - Browser console log (no errors)
  - Load time measurements
- **Status:** BLOCKED (depends on T-027)

---

## Phase 4: Security Hardening

**Duration:** 3-5 days
**Prerequisites:** Phases 1-3 complete
**Owner:** Deployment team

---

### T-029: Migrate JWT from localStorage to HttpOnly Cookies

- **Description:** PRODUCTION BLOCKER. Migrate JWT token storage from localStorage (vulnerable to XSS token theft) to HttpOnly cookies. CSP headers reduce but do NOT eliminate XSS risk. This is NOT owner-waivable. Backend sets `Set-Cookie: token=<jwt>; HttpOnly; Secure; SameSite=Strict; Path=/`. React sends cookies automatically with `withCredentials: true` on axios. Remove all localStorage JWT read/write code from frontend.
- **Dependencies:** T-022, T-028
- **Priority:** P0 (blocker -- PRODUCTION BLOCKER)
- **Decision Reference:** D-011 (Application Security Posture)
- **Acceptance Criteria:**
  - [ ] Backend login endpoint modified to set HttpOnly cookie (`Set-Cookie: token=<jwt>; HttpOnly; Secure; SameSite=Strict; Path=/`)
  - [ ] Backend logout endpoint modified to clear HttpOnly cookie
  - [ ] Frontend axios configured with `withCredentials: true`
  - [ ] Frontend no longer reads JWT from localStorage
  - [ ] Frontend no longer writes JWT to localStorage
  - [ ] Frontend no longer sends `Authorization: Bearer` header
  - [ ] Login flow works with HttpOnly cookie
  - [ ] Logout flow clears cookie
  - [ ] Token refresh mechanism works with cookies
  - [ ] 401 response handling redirects to login
  - [ ] Browser developer tools confirm cookie attributes: HttpOnly=true, Secure=true, SameSite=Strict
- **Required Evidence:**
  - Code diff showing localStorage removal
  - Code diff showing HttpOnly cookie implementation (backend Set-Cookie)
  - Browser developer tools screenshot showing cookie attributes
  - Login/logout flow test results
  - Network tab showing cookies sent automatically with requests
- **Status:** NOT_STARTED (PRODUCTION BLOCKER -- not yet implemented)

---

### T-030: Implement CSRF Protection

- **Description:** Implement CSRF protection, which is REQUIRED when using cookie-based authentication (after T-029). Current JWT-based auth (localStorage + Bearer header) is NOT vulnerable to CSRF. However, once auth migrates to HttpOnly cookies, CSRF protection becomes mandatory. Backend issues CSRF token via `GET /api/v1/csrf-token` (non-HttpOnly cookie or JSON response). Frontend includes `X-CSRF-Token` header on all state-changing requests (POST/PUT/DELETE). Backend validates CSRF token before processing.
- **Dependencies:** T-029
- **Priority:** P0 (blocker -- required with cookie auth)
- **Decision Reference:** D-011
- **Acceptance Criteria:**
  - [ ] Backend CSRF token endpoint created (`GET /api/v1/csrf-token`)
  - [ ] CSRF token delivered via separate non-HttpOnly cookie OR JSON response
  - [ ] Frontend fetches CSRF token on login
  - [ ] Frontend includes `X-CSRF-Token` header on all mutating requests (POST/PUT/DELETE)
  - [ ] Backend validates CSRF token before processing state-changing requests
  - [ ] CSRF validation returns 403 for missing/invalid tokens
  - [ ] CSRF protection does not break normal application workflows
  - [ ] Nginx CORS configuration updated to allow `X-CSRF-Token` header
- **Required Evidence:**
  - Code diff showing CSRF implementation (backend + frontend)
  - API test: request without CSRF token returns 403
  - API test: request with valid CSRF token succeeds
  - Browser developer tools screenshot showing X-CSRF-Token header
- **Status:** NOT_STARTED (depends on T-029 cookie migration)

---

### T-031: Implement Token Revocation (Per-User Version Counter)

- **Description:** PROPOSED DESIGN -- NOT IMPLEMENTED. Implement server-side token revocation mechanism using Option B (per-user version counter). Store a `token_version` integer per user in the database. Include `token_version` in JWT claims at issuance. On each request, validate that JWT `token_version` matches database. On logout/password change/compromise/account disablement: increment `token_version` to invalidate all old tokens. This addresses the critical gap that JWT tokens are currently stateless with no revocation mechanism.
- **Dependencies:** T-029
- **Priority:** P1 (critical)
- **Decision Reference:** D-012 (Token Revocation Strategy -- PROPOSED)
- **Acceptance Criteria:**
  - [ ] `token_version` column added to users table (via Flyway migration)
  - [ ] JWT claims include `token_version` at issuance
  - [ ] Authentication filter validates `token_version` on each request (one DB lookup, can cache briefly)
  - [ ] Logout increments `token_version` (invalidates all tokens for user)
  - [ ] Password change increments `token_version`
  - [ ] Suspected compromise: admin can increment `token_version` (immediate revocation)
  - [ ] Secret rotation: incrementing all users' `token_version` revokes all tokens system-wide
  - [ ] Account disablement increments `token_version`
  - [ ] All 5 revocation scenarios tested and working
- **Required Evidence:**
  - Database migration script adding `token_version` column
  - Code diff showing JWT filter changes (version check)
  - Test results for each of the 5 revocation scenarios
  - API test showing revoked token returns 401
- **Status:** NOT_STARTED (PROPOSED DESIGN -- not yet implemented)

---

### T-032: Configure Nginx Security Headers

- **Description:** Configure Nginx to add security headers to all responses. Headers: HSTS (Strict-Transport-Security with max-age=31536000 and includeSubDomains), X-Frame-Options (SAMEORIGIN), X-Content-Type-Options (nosniff), Referrer-Policy (strict-origin-when-cross-origin). Note: X-XSS-Protection is deprecated and should NOT be used (removed per Revision 4 audit).
- **Dependencies:** T-027
- **Priority:** P0 (blocker)
- **Decision Reference:** D-006, D-011
- **Acceptance Criteria:**
  - [ ] `Strict-Transport-Security: max-age=31536000; includeSubDomains` header present
  - [ ] `X-Frame-Options: SAMEORIGIN` header present
  - [ ] `X-Content-Type-Options: nosniff` header present
  - [ ] `Referrer-Policy: strict-origin-when-cross-origin` header present
  - [ ] X-XSS-Protection header NOT present (deprecated)
  - [ ] Headers verified in response: `curl -I https://gms.yourdomain.com`
- **Required Evidence:**
  - `curl -I https://gms.yourdomain.com` output showing all security headers
  - Nginx configuration file showing header directives
- **Status:** BLOCKED (depends on T-027, which requires TLS certificate)

---

### T-033: Configure CSP (Content-Security-Policy)

- **Description:** Configure Content-Security-Policy header in Nginx. PROPOSED CSP (requires testing with actual application before production deployment): `default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self' data:; connect-src 'self' https://*.neon.tech; frame-ancestors 'self';`. CRITICAL: CSP reduces XSS attack surface but does NOT eliminate localStorage token theft risk. CSP must be tested with the actual React application to ensure it does not break functionality.
- **Dependencies:** T-027, T-028
- **Priority:** P1 (critical)
- **Decision Reference:** D-011
- **Acceptance Criteria:**
  - [ ] CSP header configured in Nginx
  - [ ] CSP tested with actual React application (no broken functionality)
  - [ ] `unsafe-inline` and `unsafe-eval` usage documented (required by React or third-party libraries?)
  - [ ] `connect-src` includes `https://*.neon.tech` for database connections
  - [ ] `frame-ancestors 'self'` prevents clickjacking
  - [ ] Browser console shows no CSP violation errors during testing
  - [ ] CSP does NOT make localStorage safe (acknowledged limitation)
- **Required Evidence:**
  - Nginx configuration showing CSP header
  - Browser console log showing no CSP violations during full app testing
  - List of any `unsafe-inline`/`unsafe-eval` requirements with justification
- **Status:** BLOCKED (requires testing with actual application -- depends on T-027, T-028)

---

### T-034: Block Public Access to /actuator/* Endpoints

- **Description:** Ensure ALL Spring Boot Actuator endpoints (`/actuator/*`) are blocked from public access via Nginx. Actuator endpoints expose sensitive operational information: `/actuator/health` (database status, disk space), `/actuator/env` (environment variables, may contain secrets), `/actuator/configprops`, `/actuator/metrics`, `/actuator/threaddump`, `/actuator/heapdump`. Only localhost access (127.0.0.1) is permitted for Docker health checks and internal monitoring.
- **Dependencies:** T-027
- **Priority:** P0 (blocker)
- **Decision Reference:** D-011
- **Nginx Configuration:**
  ```nginx
  location /actuator/ {
      allow 127.0.0.1;
      deny all;
      proxy_pass http://127.0.0.1:8080;
      proxy_set_header Host $host;
      proxy_set_header X-Real-IP $remote_addr;
      proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
      proxy_set_header X-Forwarded-Proto $scheme;
      access_log off;
  }
  ```
- **Acceptance Criteria:**
  - [ ] Nginx `/actuator/` location block configured with `allow 127.0.0.1; deny all;`
  - [ ] External request to `/actuator/health` returns 403 Forbidden
  - [ ] External request to `/actuator/env` returns 403 Forbidden
  - [ ] External request to `/actuator/metrics` returns 403 Forbidden
  - [ ] Localhost request to `/actuator/health` returns `{"status": "UP"}`
  - [ ] Docker health check still works (uses localhost)
- **Required Evidence:**
  - `curl -s -o /dev/null -w "%{http_code}" https://gms.yourdomain.com/actuator/health` returns 403
  - `ssh <oci> "curl -sf http://127.0.0.1:8080/actuator/health"` returns `{"status":"UP"}`
  - Nginx configuration showing actuator restriction
- **Status:** BLOCKED (depends on T-027)

---

### T-035: Create Public /health Endpoint

- **Description:** Create a separate lightweight `/health` endpoint in Nginx for external monitoring services (UptimeRobot, etc.). This is NOT the Spring Boot Actuator `/actuator/health` endpoint. Returns `200 OK` with minimal information (text/plain, body: `OK\n`). No sensitive data exposed.
- **Dependencies:** T-027
- **Priority:** P1 (critical)
- **Decision Reference:** D-010 (UptimeRobot Monitoring)
- **Nginx Configuration:**
  ```nginx
  location /health {
      return 200 'OK\n';
      add_header Content-Type text/plain;
      access_log off;
  }
  ```
- **Acceptance Criteria:**
  - [ ] `/health` endpoint returns `200 OK` with body `OK\n`
  - [ ] Content-Type is `text/plain`
  - [ ] No sensitive data in response (no database status, no disk space, no version info)
  - [ ] Access logging disabled for this endpoint
  - [ ] Endpoint accessible from external IP (for UptimeRobot)
  - [ ] Response time < 100ms
- **Required Evidence:**
  - `curl -v https://gms.yourdomain.com/health` output showing 200 OK
  - Response body: `OK`
  - Response headers showing Content-Type: text/plain
- **Status:** BLOCKED (depends on T-027)

---

### T-036: Block Access to Sensitive Paths (.git, .env, .htaccess)

- **Description:** Configure Nginx to block public access to sensitive file paths: `.git` (source code repository), `.env` (environment variables with secrets), `.htaccess` (Apache configuration, information disclosure). Return 404 for all blocked paths. Disable access logging for these paths to prevent log pollution.
- **Dependencies:** T-027
- **Priority:** P0 (blocker)
- **Decision Reference:** D-011
- **Nginx Configuration:**
  ```nginx
  location ~ /\.(git|env|htaccess) {
      deny all;
      access_log off;
      log_not_found off;
  }
  ```
- **Acceptance Criteria:**
  - [ ] Request to `/.git/config` returns 403/404
  - [ ] Request to `/.env` returns 403/404
  - [ ] Request to `/.htaccess` returns 403/404
  - [ ] Access logging disabled for blocked paths
  - [ ] Log-not-found disabled for blocked paths
  - [ ] Nginx configuration tested and validated
- **Required Evidence:**
  - `curl -s -o /dev/null -w "%{http_code}" https://gms.yourdomain.com/.git/config` returns 403/404
  - `curl -s -o /dev/null -w "%{http_code}" https://gms.yourdomain.com/.env` returns 403/404
  - Nginx configuration showing blocked paths
- **Status:** BLOCKED (depends on T-027)

---

### T-037: Obtain Let's Encrypt Certificate

- **Description:** Install Certbot and obtain Let's Encrypt SSL/TLS certificate for the domain. TLS bootstrap sequence: (1) deploy HTTP-only Nginx config first, (2) run certbot to obtain certificates, (3) switch to full HTTPS config, (4) add HTTP-to-HTTPS redirect. Certificate valid for 90 days with auto-renewal.
- **Dependencies:** T-004, T-027
- **Priority:** P0 (blocker -- requires domain)
- **Decision Reference:** D-006
- **Commands:**
  ```bash
  sudo apt install certbot python3-certbot-nginx -y
  # Step 1: Deploy HTTP-only bootstrap config first (before certificates exist)
  # Step 2: Obtain certificate
  sudo certbot --nginx -d gms.yourdomain.com
  # Step 3: Verify auto-renewal
  sudo certbot renew --dry-run
  # Step 4: Check cron/timer
  sudo systemctl list-timers | grep certbot
  ```
- **Acceptance Criteria:**
  - [ ] Certbot installed (`certbot --version`)
  - [ ] HTTP-only bootstrap Nginx config deployed first (for Certbot challenge)
  - [ ] SSL certificate obtained for gms.yourdomain.com
  - [ ] Certificate valid for 90 days
  - [ ] Auto-renewal configured (`certbot renew --dry-run` succeeds)
  - [ ] Cron/systemd timer verified (`systemctl list-timers | grep certbot`)
  - [ ] Full HTTPS Nginx configuration deployed (after certificate obtained)
  - [ ] HTTP-to-HTTPS redirect enabled (only after HTTPS verified working)
- **Required Evidence:**
  - `certbot certificates` output
  - `curl -I https://gms.yourdomain.com` showing SSL certificate details
  - `certbot renew --dry-run` output
  - Browser screenshot showing valid HTTPS padlock
- **Status:** BLOCKED (depends on T-004 domain purchase -- OWNER DECISION required)

---

### T-038: Configure HTTPS in Nginx

- **Description:** Configure Nginx for HTTPS after Let's Encrypt certificate is obtained. TLS configuration: TLSv1.2 and TLSv1.3 only, strong cipher suites (ECDHE-ECDSA-AES128-GCM-SHA256, ECDHE-RSA-AES128-GCM-SHA256, ECDHE-ECDSA-AES256-GCM-SHA384, ECDHE-RSA-AES256-GCM-SHA384), ssl_prefer_server_ciphers off, SSL session cache (shared:SSL:10m), SSL session timeout (10m).
- **Dependencies:** T-037
- **Priority:** P0 (blocker)
- **Decision Reference:** D-006
- **Nginx Configuration:**
  ```nginx
  listen 443 ssl http2;
  ssl_certificate /etc/letsencrypt/live/gms.yourdomain.com/fullchain.pem;
  ssl_certificate_key /etc/letsencrypt/live/gms.yourdomain.com/privkey.pem;
  ssl_protocols TLSv1.2 TLSv1.3;
  ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384;
  ssl_prefer_server_ciphers off;
  ssl_session_cache shared:SSL:10m;
  ssl_session_timeout 10m;
  ```
- **Acceptance Criteria:**
  - [ ] Nginx listening on port 443 with SSL and HTTP/2
  - [ ] SSL certificate path correct (`/etc/letsencrypt/live/gms.yourdomain.com/`)
  - [ ] TLS 1.0 and TLS 1.1 disabled (only TLSv1.2 and TLSv1.3)
  - [ ] Strong cipher suites configured
  - [ ] SSL session cache configured
  - [ ] `nginx -t` passes
  - [ ] HTTPS serving content successfully
- **Required Evidence:**
  - `nginx -t` output
  - `curl -I https://gms.yourdomain.com` showing TLS details
  - SSL Labs test results (https://www.ssllabs.com/ssltest/)
- **Status:** BLOCKED (depends on T-037 certificate)

---

### T-039: Configure HTTP-to-HTTPS Redirect

- **Description:** Configure Nginx to redirect all HTTP (port 80) requests to HTTPS (port 443) with a 301 permanent redirect. Only enable this AFTER HTTPS is verified working.
- **Dependencies:** T-038
- **Priority:** P0 (blocker)
- **Decision Reference:** D-006
- **Nginx Configuration:**
  ```nginx
  server {
      listen 80;
      server_name gms.yourdomain.com;
      return 301 https://$server_name$request_uri;
  }
  ```
- **Acceptance Criteria:**
  - [ ] HTTP (port 80) returns 301 redirect to HTTPS
  - [ ] Redirect preserves request URI
  - [ ] HTTPS verified working BEFORE redirect enabled
  - [ ] `curl -I http://gms.yourdomain.com` shows `301 Moved Permanently` with `Location: https://...`
  - [ ] Browser test: HTTP URL automatically redirects to HTTPS
- **Required Evidence:**
  - `curl -I http://gms.yourdomain.com` output showing 301 redirect
  - Browser screenshot showing automatic redirect
  - Nginx configuration showing redirect block
- **Status:** BLOCKED (depends on T-038 HTTPS working)

---

### T-040: Run Security Test Suite

- **Description:** Comprehensive security testing: positive tests (authorized access works), negative tests (unauthorized access blocked), XSS protection verification, CSRF protection verification, tenant isolation verification, actuator restriction verification, security headers verification, SSL/TLS configuration verification.
- **Dependencies:** T-029, T-030, T-032, T-033, T-034, T-035, T-036, T-038, T-039
- **Priority:** P0 (blocker)
- **Decision Reference:** D-011
- **Security Tests:**
  1. **Positive:** `curl https://gms.yourdomain.com/health` returns `200 OK`
  2. **Negative:** `curl https://gms.yourdomain.com/actuator/health` returns `403 Forbidden` (from external IP)
  3. **Negative:** `curl https://gms.yourdomain.com/actuator/env` returns `403 Forbidden`
  4. **Internal:** `curl http://127.0.0.1:8080/actuator/health` returns `{"status": "UP"}` (from localhost)
  5. **API:** `curl https://gms.yourdomain.com/api/v1/auth/login` returns `200 OK` (not SPA HTML)
  6. **SPA fallback:** `curl https://gms.yourdomain.com/nonexistent` returns SPA `index.html`
  7. **Security headers:** Verify HSTS, X-Frame-Options, CSP headers present
  8. **HTTPS enforced:** `curl http://gms.yourdomain.com` returns 301 redirect
  9. **JWT cookie:** Browser confirms HttpOnly, Secure, SameSite=Strict attributes
  10. **CSRF:** Request without X-CSRF-Token returns 403
  11. **Tenant isolation:** Cross-school data access returns 404
- **Acceptance Criteria:**
  - [ ] All 11 security tests pass
  - [ ] No critical vulnerabilities found
  - [ ] All security headers present (CSP, X-Content-Type-Options, X-Frame-Options, HSTS)
  - [ ] No hardcoded secrets in source code
  - [ ] Tenant isolation verified
  - [ ] Actuator NOT publicly accessible
  - [ ] JWT migrated from localStorage to HttpOnly cookie (PRODUCTION BLOCKER resolved)
  - [ ] CSRF protection implemented and working
  - [ ] Secret rotation procedure documented
  - [ ] Role authorization verified for all 6 web roles with negative tests
- **Required Evidence:**
  - Test results for all 11 security tests (output/logs)
  - SSL Labs test results (https://www.ssllabs.com/ssltest/)
  - Browser developer tools screenshots (cookies, headers)
  - API test output for each security scenario
- **Status:** BLOCKED (depends on all security implementation tasks)

---

## Phase 5: CI/CD Pipeline

**Duration:** 2-3 days
**Prerequisites:** Phases 1-4 complete
**Owner:** Deployment team

---

### T-041: Verify GitHub Action SHAs

- **Description:** Replace all NOT VERIFIED placeholder SHAs in deployment workflow with actual verified SHAs from official GitHub repositories. Verified SHAs from existing `.github/workflows/ci.yml`: `actions/checkout@11d5960a326750d5838078e36cf38b85af677262` (v4.4.0), `actions/setup-java@cf277c60eb25467037889841efdb72551f06f6c3` (v4.9.1), `actions/setup-node@49933ea5288caeca8642d1e84afbd3f7d6820020` (v4.4.0), `actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02` (v4.6.2), `actions/download-artifact@d3f86a106a0bac45b974a628896c90dbdf5c8093` (v4.3.0). UNVERIFIED (must be checked): `docker/setup-buildx-action`, `docker/login-action`, `docker/build-push-action`, `appleboy/ssh-action`, `appleboy/scp-action`.
- **Dependencies:** None
- **Priority:** P0 (blocker -- supply chain security)
- **Decision Reference:** D-007
- **Acceptance Criteria:**
  - [ ] All action SHAs verified from official GitHub repositories
  - [ ] `appleboy/ssh-action` SHA verified and pinned to full commit SHA
  - [ ] `appleboy/scp-action` SHA verified and pinned to full commit SHA
  - [ ] `docker/setup-buildx-action` SHA verified and pinned
  - [ ] `docker/login-action` SHA verified and pinned
  - [ ] `docker/build-push-action` SHA verified and pinned
  - [ ] No placeholder `<verified-sha>` or `@NOT-VERIFIED` values remain
  - [ ] All actions use immutable full commit SHAs (not version tags like `@v4`)
  - [ ] Each SHA verified against the official GitHub repository's commit history
- **Required Evidence:**
  - Workflow file with all verified SHAs
  - Links to official GitHub repositories confirming each SHA (commit hash page)
  - Verification log showing each SHA was checked
- **Status:** NOT_STARTED (SHAs are UNKNOWN/unverified -- MUST verify before production)

---

### T-042: Configure GitHub Actions Workflow File

- **Description:** Create `.github/workflows/deploy-production.yml` with `workflow_dispatch` trigger (manual only -- NEVER deploy on push to master). Include: commit SHA input (must be on master), Docker image build and push to GHCR, migration file extraction from same commit, SSH deployment to OCI instance, health verification after deployment. Use concurrency group to prevent parallel deployments. Use least-privilege permissions (`contents: read`, `packages: write`).
- **Dependencies:** T-041
- **Priority:** P0 (blocker)
- **Decision Reference:** D-007
- **Workflow Configuration:**
  ```yaml
  name: Deploy to Production
  on:
    workflow_dispatch:
      inputs:
        environment:
          description: 'Deployment environment'
          required: true
          default: 'production'
          type: choice
          options: [production, staging]
        git_sha:
          description: 'Git commit SHA to deploy (must be on master)'
          required: true
          type: string
  permissions:
    contents: read
    packages: write
  concurrency:
    group: production-deployment
    cancel-in-progress: false
  ```
- **Acceptance Criteria:**
  - [ ] Workflow file created at `.github/workflows/deploy-production.yml`
  - [ ] Trigger is `workflow_dispatch` only (NOT `push` to master)
  - [ ] Owner must manually trigger deployment and specify git_sha
  - [ ] Workflow verifies commit is on master before deploying (`git merge-base --is-ancestor`)
  - [ ] All Actions pinned to verified immutable commit SHAs (from T-041)
  - [ ] Container images tagged with Git SHA only (no `:latest` tag)
  - [ ] Concurrency group prevents parallel deployments
  - [ ] Least-privilege permissions configured
  - [ ] Migration runs BEFORE application restart (deployment-step, not app-startup)
  - [ ] Migration files extracted from same commit as application image
  - [ ] Health verification step after deployment
  - [ ] Rollback procedure: `workflow_dispatch` with previous known-good SHA
  - [ ] No secrets printed to workflow logs (`::add-mask::` used)
- **Required Evidence:**
  - GitHub Actions workflow file content
  - Screenshot of workflow run showing successful deployment
  - Deployment log: trigger -> verify -> migrate -> deploy -> health check
- **Status:** BLOCKED (depends on T-041 SHA verification)

---

### T-043: Set Up GHCR (GitHub Container Registry)

- **Description:** Configure GitHub Container Registry for storing production Docker images. Images tagged with immutable Git SHA (NOT `:latest`). Set up authentication for CI/CD pipeline to push images. Configure image retention policies.
- **Dependencies:** T-042
- **Priority:** P1 (critical)
- **Decision Reference:** D-007
- **Commands:**
  ```bash
  # Login to GHCR
  echo $GITHUB_TOKEN | docker login ghcr.io -u <owner> --password-stdin
  # Tag with Git SHA
  GIT_SHA=$(git rev-parse --short HEAD)
  docker tag gms-backend:latest ghcr.io/<owner>/gms-backend:$GIT_SHA
  # Push
  docker push ghcr.io/<owner>/gms-backend:$GIT_SHA
  # Record digest (immutable reference)
  DIGEST=$(docker inspect --format='{{index .RepoDigests 0}}' ghcr.io/<owner>/gms-backend:$GIT_SHA)
  echo "$DIGEST" > deployment-manifest.txt
  ```
- **Acceptance Criteria:**
  - [ ] GHCR repository created for gms-backend
  - [ ] Authentication configured (GITHUB_TOKEN in CI/CD)
  - [ ] Images tagged with Git SHA only (no `:latest` for production)
  - [ ] Image digest recorded in deployment-manifest.txt
  - [ ] Image pullable from OCI instance
  - [ ] GHCR storage within free tier limits (500 MB for public repos)
  - [ ] Image retention policy configured
- **Required Evidence:**
  - GHCR package page screenshot
  - `docker pull` test from OCI instance
  - Deployment manifest with digest
- **Status:** BLOCKED (depends on T-042)

---

### T-044: Configure Git SHA Tagging

- **Description:** Ensure all Docker images are tagged with immutable Git commit SHAs. Production deployments reference images by SHA tag (or digest), NEVER by `:latest`. Record the mapping between Git SHA, image tag, and image digest for audit trail.
- **Dependencies:** T-043
- **Priority:** P1 (critical)
- **Decision Reference:** D-007
- **Acceptance Criteria:**
  - [ ] Docker build uses Git SHA as tag: `docker build -t gms-backend:${GIT_SHA} .`
  - [ ] Image pushed with Git SHA tag: `ghcr.io/<owner>/gms-backend:${GIT_SHA}`
  - [ ] `:latest` tag NOT used for production deployment
  - [ ] Image digest recorded: `docker inspect --format='{{index .RepoDigests 0}}'`
  - [ ] Deployment manifest maps Git SHA -> image tag -> image digest
  - [ ] Rollback uses previous Git SHA tag (not `:latest`)
- **Required Evidence:**
  - `docker images` output showing SHA-tagged images
  - Deployment manifest content
  - Rollback test using previous SHA tag
- **Status:** BLOCKED (depends on T-043)

---

### T-045: Set Up SSH Deployment Key for OCI Instance

- **Description:** Create SSH deployment key for CI/CD pipeline to access OCI instance. Key must have minimal privileges: SSH access for gmsdeploy user only (no root), ability to run docker compose and migration scripts. Store key securely in GitHub Secrets (OCI_HOST, OCI_USERNAME, OCI_SSH_KEY).
- **Dependencies:** T-011, T-042
- **Priority:** P0 (blocker)
- **Decision Reference:** D-007
- **Acceptance Criteria:**
  - [ ] SSH key pair generated for deployment (separate from owner's personal key)
  - [ ] Public key added to gmsdeploy user's `~/.ssh/authorized_keys` on OCI instance
  - [ ] Private key stored in GitHub Secret: `OCI_SSH_KEY`
  - [ ] `OCI_HOST` secret configured (OCI instance public IP or hostname)
  - [ ] `OCI_USERNAME` secret configured (gmsdeploy)
  - [ ] SSH key has minimal privileges (no root access, no sudo without password)
  - [ ] SSH connection test successful from GitHub Actions runner
  - [ ] Key rotation procedure documented
- **Required Evidence:**
  - GitHub Secrets screenshot (redacted -- showing key names only)
  - SSH connection test log
  - `authorized_keys` file content on OCI instance (public key only)
- **Status:** BLOCKED (depends on T-011, T-042)

---

### T-046: Configure Protected Environment Approvals

- **Description:** Configure GitHub protected environment (`production`) with required reviewers (owner only). This ensures every production deployment requires explicit owner approval via the GitHub Environments feature. Deployment workflow references `environment: production`.
- **Dependencies:** T-042
- **Priority:** P1 (critical)
- **Decision Reference:** D-007
- **Acceptance Criteria:**
  - [ ] `production` environment created in GitHub repository settings
  - [ ] Owner configured as required reviewer for `production` environment
  - [ ] Deployment workflow references `environment: production`
  - [ ] Branch protection rules: only merged PRs reach `master`; direct push not permitted
  - [ ] Environment protection rules verified (deployment pauses for approval)
  - [ ] Test: trigger `workflow_dispatch` -- approval required before deployment proceeds
- **Required Evidence:**
  - GitHub repository settings screenshot showing environment configuration
  - GitHub protected environment rules screenshot
  - Test deployment log showing approval gate
- **Status:** BLOCKED (depends on T-042)

---

### T-047: Test CI/CD Pipeline End-to-End

- **Description:** Full end-to-end test of the CI/CD pipeline: trigger deployment via `workflow_dispatch`, verify migration runs, verify application deploys, verify health check passes. Test rollback by deploying previous known-good SHA. Verify feature-branch workflow: create branch, push, create PR, merge, deploy.
- **Dependencies:** T-042, T-043, T-044, T-045, T-046
- **Priority:** P0 (blocker)
- **Decision Reference:** D-007
- **Feature-Branch Release Sequence:**
  1. Fetch latest `master`: `git fetch origin && git checkout master && git pull origin master`
  2. Create feature branch: `git checkout -b feature/<description>`
  3. Run tests locally: `mvn -B clean verify` (backend), `npm run build` (frontend)
  4. Push feature branch: `git push -u origin feature/<description>`
  5. Create pull request; CI runs automatically (ci.yml)
  6. Code review and approval
  7. Merge to master (all CI gates must pass)
  8. **Owner triggers production deployment via `workflow_dispatch`** with merged commit SHA
  9. Deployment workflow verifies SHA is on master, then deploys
- **Acceptance Criteria:**
  - [ ] Manual deployment trigger works (`workflow_dispatch`)
  - [ ] Commit SHA verification works (rejects SHA not on master)
  - [ ] Migration runs as separate deployment step (before app restart)
  - [ ] Application container updates to new image
  - [ ] Health check passes after deployment
  - [ ] Rollback to previous SHA tested and successful (via `workflow_dispatch`)
  - [ ] Rollback does NOT reverse database migrations
  - [ ] Deployment log shows complete sequence: trigger -> verify -> migrate -> deploy -> health
  - [ ] Feature-branch workflow tested (branch -> PR -> merge -> deploy)
  - [ ] No secrets leaked in workflow logs
- **Required Evidence:**
  - Full deployment log output
  - Rollback test log
  - Health check output before and after rollback
  - Feature-branch PR merge log
  - Workflow run screenshot
- **Status:** BLOCKED (depends on all CI/CD tasks T-041 through T-046)

---

## Phase 6: Database Migration

**Duration:** 1-2 days
**Prerequisites:** Phase 1 (T-008, T-012), Phase 2 (T-016)
**Owner:** Deployment team

---

### T-048: Create /opt/gms/migrate.sh (Canonical Flyway Migration Script)

- **Description:** Create the canonical Flyway migration script at `/opt/gms/migrate.sh` implementing the Revision 4 procedure. Key features: flock-based atomic deployment locking, JDBC URL-based environment contract (DB_URL, DB_USERNAME, DB_PASSWORD), pinned Flyway Docker image (flyway/flyway:10.15.2 -- PROPOSED, requires verification), explicit error handling (`set -uo pipefail`, NOT `set -e`), temporary credentials file (mode 0600, deleted on EXIT via trap), database host validation (Neon or localhost only), deployment status tracking.
- **Dependencies:** T-016, T-012
- **Priority:** P0 (blocker)
- **Decision Reference:** D-008 (Flyway Migrations via Docker Container)
- **Script:** See Architecture document Section 2.2.2 for the complete canonical migration procedure.
- **Key Features:**
  - Sources `/etc/gms/.env` for credentials
  - Validates required env vars: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DEPLOYMENT_SHA`
  - Extracts PG_HOST, PG_PORT, PG_DATABASE from JDBC URL
  - Validates target: host must contain "neon" or "localhost"
  - Uses `flock -n` for atomic deployment lock (`/tmp/gms-migration.lock`)
  - Creates temp credentials file (mode 0600, deleted on EXIT)
  - Runs Flyway in Docker: `docker run --rm --network host -v "$MIGRATION_DIR:/flyway/sql:ro" --env-file "$CREDS_FILE" flyway/flyway:10.15.2 migrate`
  - Captures exit code explicitly (NOT using `set -e`)
  - On failure: writes `FAILED` to `/opt/gms/last-deployment-status.txt`, records failed SHA
  - On success: writes `SUCCESS`, updates `/opt/gms/current-deployment-sha.txt`
- **Acceptance Criteria:**
  - [ ] Script created at `/opt/gms/migrate.sh`
  - [ ] Script is executable (`chmod +x /opt/gms/migrate.sh`)
  - [ ] Uses `set -uo pipefail` (NOT `set -e`)
  - [ ] `flock`-based deployment lock implemented
  - [ ] Temporary credentials file created with mode 0600
  - [ ] Credentials file deleted on EXIT (trap)
  - [ ] Database host validation (Neon or localhost only)
  - [ ] Deployment status tracking (`/opt/gms/last-deployment-status.txt`)
  - [ ] Failed deployments do NOT update `current-deployment-sha.txt`
  - [ ] Flyway Docker image pinned to exact version (10.15.2 or verified alternative)
  - [ ] Migration files mounted read-only from `/opt/gms/migrations/$DEPLOYMENT_SHA`
- **Required Evidence:**
  - Migration script content (matching Architecture Section 2.2.2 canonical procedure)
  - `ls -la /opt/gms/migrate.sh` showing executable permissions
  - Script syntax check: `bash -n /opt/gms/migrate.sh`
- **Status:** NOT_STARTED

---

### T-049: Pin Flyway Docker Image

- **Description:** Verify and pin the Flyway Docker image to an exact version. PROPOSED: `flyway/flyway:10.15.2`. Must verify: (1) image exists on Docker Hub, (2) version is compatible with PostgreSQL 15 and Flyway 10.x, (3) `flyway-database-postgresql` module is included. For maximum security, pin to image digest: `flyway/flyway@sha256:<digest>`.
- **Dependencies:** T-048
- **Priority:** P1 (critical)
- **Decision Reference:** D-008
- **Commands:**
  ```bash
  docker pull flyway/flyway:10.15.2
  docker inspect --format='{{index .RepoDigests 0}}' flyway/flyway:10.15.2
  # Verify flyway-database-postgresql module is included
  docker run --rm flyway/flyway:10.15.2 -help 2>&1 | grep -i postgresql
  ```
- **Acceptance Criteria:**
  - [ ] `flyway/flyway:10.15.2` image pulled successfully (or alternative version verified)
  - [ ] Image digest recorded for immutable reference
  - [ ] `flyway-database-postgresql` module confirmed present
  - [ ] Flyway version compatible with PostgreSQL 15
  - [ ] Flyway version matches Spring Boot 3.3.3 managed version (10.10.0 in pom.xml)
  - [ ] Image pre-pulled on OCI instance (avoid pull failures during deployment)
- **Required Evidence:**
  - `docker pull` output
  - `docker inspect` output showing digest
  - Flyway version verification output
- **Status:** NOT_STARTED (PROPOSED version -- requires verification)

---

### T-050: Test Migration on Isolated Database

- **Description:** Execute the migration script against an isolated test database (NOT production). Verify all migration files (V1 through V10) execute successfully. Verify schema version after migration. Verify deployment status tracking works. Test must use a separate Neon branch or local PostgreSQL instance.
- **Dependencies:** T-048, T-049, T-003
- **Priority:** P0 (blocker)
- **Decision Reference:** D-008
- **Acceptance Criteria:**
  - [ ] Isolated test database created (separate Neon branch or local PostgreSQL)
  - [ ] Migration files (V1 through V10) placed in `/opt/gms/migrations/<test-sha>/`
  - [ ] `DEPLOYMENT_SHA` set to test value
  - [ ] Migration script executed: `/opt/gms/migrate.sh`
  - [ ] All migration files (V1-V10) execute successfully
  - [ ] `flyway info` shows correct schema version after migration
  - [ ] `/opt/gms/last-deployment-status.txt` contains `SUCCESS`
  - [ ] `/opt/gms/current-deployment-sha.txt` updated with test SHA
  - [ ] No errors in migration log
- **Required Evidence:**
  - Migration execution log (full output)
  - `flyway info` output showing schema version
  - Deployment status file content
  - Test database connection details (isolated, not production)
- **Status:** BLOCKED (depends on T-003, T-048, T-049)

---

### T-051: Verify flock-Based Atomic Locking

- **Description:** Test the flock-based deployment lock mechanism. Verify that: (1) only one migration can run at a time, (2) concurrent migration attempts are rejected, (3) lock is released on script exit (normal or abnormal), (4) stale locks do not block future migrations.
- **Dependencies:** T-048
- **Priority:** P1 (critical)
- **Decision Reference:** D-008
- **Acceptance Criteria:**
  - [ ] Lock file created at `/tmp/gms-migration.lock` during migration
  - [ ] Second concurrent migration attempt fails with "Migration already in progress"
  - [ ] Lock released on normal script exit (fd 9 closed)
  - [ ] Lock released on abnormal exit (script killed, trap fires)
  - [ ] Stale lock file from crashed migration does not block future migrations
  - [ ] `flock -n` non-blocking behavior verified
- **Required Evidence:**
  - Test log showing concurrent migration rejection
  - Lock file state before, during, and after migration
  - Stale lock recovery test output
- **Status:** BLOCKED (depends on T-048)

---

### T-052: Test Migration Failure Handling

- **Description:** Test migration failure scenarios: (1) non-zero exit code from Flyway, (2) deployment abort (app startup prevented), (3) failed deployment recorded in status file, (4) old application version continues running safely.
- **Dependencies:** T-048, T-050
- **Priority:** P0 (blocker)
- **Decision Reference:** D-008
- **Acceptance Criteria:**
  - [ ] Simulated migration failure (invalid SQL file) causes non-zero exit
  - [ ] `/opt/gms/last-deployment-status.txt` contains `FAILED`
  - [ ] `/opt/gms/last-failed-sha.txt` contains the failed SHA and timestamp
  - [ ] `/opt/gms/current-deployment-sha.txt` NOT updated on failure
  - [ ] Deployment script aborts (application NOT started)
  - [ ] Old application version continues running (if was running before)
  - [ ] Error message logged with: timestamp, SHA, database host, failure reason
  - [ ] Manual intervention required message displayed
- **Required Evidence:**
  - Failed migration test log
  - Status file contents after failure
  - Verification that application was NOT restarted
- **Status:** BLOCKED (depends on T-048, T-050)

---

### T-053: Verify Credential Protection

- **Description:** Verify that database credentials are protected throughout the migration process: (1) credentials stored in `/etc/gms/.env` with permissions 0640, (2) temporary credentials file created with mode 0600, (3) credentials passed via `--env-file` (not command-line `-e` flags), (4) temporary file deleted on EXIT via trap, (5) no credentials in Docker image layers, (6) no credentials in logs.
- **Dependencies:** T-048, T-012
- **Priority:** P0 (blocker)
- **Decision Reference:** D-008, D-011
- **Acceptance Criteria:**
  - [ ] `/etc/gms/.env` has permissions 0640 (owner read/write, group read)
  - [ ] `/etc/gms/.env` owned by gmsdeploy:docker
  - [ ] Temporary credentials file created with `mktemp` and `chmod 0600`
  - [ ] Credentials passed to Docker via `--env-file` (not `-e` command-line flags)
  - [ ] `trap "rm -f '$CREDS_FILE'" EXIT` present in script
  - [ ] Docker container uses `--rm` flag (removed after execution)
  - [ ] No credentials in migration log output
  - [ ] No credentials in Docker image layers (verified with `docker history`)
  - [ ] No credentials in process listing (`ps aux` during migration)
- **Required Evidence:**
  - `stat -c "%a %U:%G" /etc/gms/.env` output
  - Migration script content showing credential handling
  - Log output verified clean of credentials
  - `docker history` output showing no secrets in layers
- **Status:** BLOCKED (depends on T-012, T-048)

---

### T-054: Run Production Migration for V1 to V10 Chain

- **Description:** Execute the migration script against the production Neon database to apply the V1 through V10 migration chain. This is the actual production migration. Must be done AFTER all testing (T-050 through T-053) passes. Record deployment SHA and timestamp.
- **Dependencies:** T-050, T-051, T-052, T-053
- **Priority:** P0 (blocker)
- **Decision Reference:** D-008
- **Acceptance Criteria:**
  - [ ] All test migrations (T-050 through T-053) passed
  - [ ] Migration files V1 through V10 placed in `/opt/gms/migrations/<production-sha>/`
  - [ ] `DEPLOYMENT_SHA` set to production commit SHA
  - [ ] Migration executed: `/opt/gms/migrate.sh`
  - [ ] All migrations (V1-V10) applied successfully
  - [ ] `flyway info` shows schema version 10
  - [ ] `/opt/gms/last-deployment-status.txt` contains `SUCCESS`
  - [ ] `/opt/gms/current-deployment-sha.txt` updated with production SHA
  - [ ] Database row counts verified (sanity check)
  - [ ] Application starts successfully against migrated schema
- **Required Evidence:**
  - Production migration execution log
  - `flyway info` output showing schema version 10
  - Deployment status file content
  - Application health check after migration
  - Database row count verification
- **Status:** BLOCKED (depends on T-050, T-051, T-052, T-053)

---

## Phase 7: Backup and Recovery

**Duration:** 2-3 days
**Prerequisites:** Phase 1 (T-008, T-012), Phase 6 (T-003)
**Owner:** Deployment team

---

### T-055: Create /opt/gms/backup.sh (Canonical -- Derives PG_* from DB_URL)

- **Description:** Create the canonical backup script at `/opt/gms/backup.sh`. Uses PostgreSQL connection parameters derived from `DB_URL` (NOT separate PG_* variables in .env). Credentials via PGPASSWORD environment variable (unset immediately after use). Backup integrity verified with both `gzip -t` AND `pg_restore --list` (validates PostgreSQL custom-format archive). Secure file permissions (0600 for files, 0700 for directory). 30-day retention with automatic cleanup. No credentials in logs.
- **Dependencies:** T-012
- **Priority:** P0 (blocker)
- **Decision Reference:** D-009 (Daily Backups with 30-Day Retention)
- **Script Features:**
  - Sources `/etc/gms/.env` for `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
  - Extracts PG_HOST, PG_PORT, PG_DATABASE from JDBC URL
  - Uses `pg_dump -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DATABASE" --format=custom --compress=6`
  - Credentials via `PGPASSWORD` environment variable (NOT command-line arguments)
  - `PGPASSWORD` unset immediately after use
  - Integrity: `gzip -t` AND `pg_restore --list`
  - Permissions: `chmod 600` for files, `chmod 700` for directory
  - Retention: `find ... -mtime +30 -delete`
- **Acceptance Criteria:**
  - [ ] Script created at `/opt/gms/backup.sh`
  - [ ] Script is executable (`chmod +x`)
  - [ ] Uses PostgreSQL connection parameters (-h, -p, -U, -d), NOT JDBC URL directly
  - [ ] Credentials passed via PGPASSWORD environment variable
  - [ ] PGPASSWORD unset immediately after use
  - [ ] Backup integrity verified with `gzip -t`
  - [ ] Backup integrity verified with `pg_restore --list`
  - [ ] Backup file permissions: 0600
  - [ ] Backup directory permissions: 0700
  - [ ] 30-day retention configured
  - [ ] No credentials in logs
- **Required Evidence:**
  - Backup script content
  - `ls -la /opt/gms/backup.sh` showing executable permissions
  - Script syntax check: `bash -n /opt/gms/backup.sh`
- **Status:** NOT_STARTED

---

### T-056: Configure pg_restore --list Validation in Backup Script

- **Description:** Ensure the backup script validates PostgreSQL custom-format archive integrity using `pg_restore --list`. This goes beyond simple `gzip -t` compression check -- it validates the PostgreSQL archive format is intact and restorable. The command: `gunzip -c "$BACKUP_FILE" | pg_restore --list -- >/dev/null 2>&1`.
- **Dependencies:** T-055
- **Priority:** P1 (critical)
- **Decision Reference:** D-009
- **Acceptance Criteria:**
  - [ ] `pg_restore --list` validation integrated into backup.sh
  - [ ] Validation runs after gzip compression
  - [ ] Backup rejected if `pg_restore --list` fails
  - [ ] Error message logged on validation failure
  - [ ] Empty backup file detected and rejected
- **Required Evidence:**
  - Backup script section showing pg_restore --list validation
  - Test: corrupt backup file -> validation fails
  - Test: valid backup file -> validation passes
- **Status:** BLOCKED (depends on T-055)

---

### T-057: Set Up Cron Job (Daily 2 AM)

- **Description:** Configure cron job to run backup script daily at 2 AM. Use `/etc/cron.d/gms-backup` for system-level cron. Log output to `/var/log/gms-backup.log`. Monitor backup success via log file.
- **Dependencies:** T-055
- **Priority:** P1 (critical)
- **Decision Reference:** D-009
- **Cron Configuration:**
  ```bash
  # /etc/cron.d/gms-backup
  0 2 * * * root /opt/gms/backup.sh >> /var/log/gms-backup.log 2>&1
  ```
- **Acceptance Criteria:**
  - [ ] Cron file created at `/etc/cron.d/gms-backup`
  - [ ] Cron schedule: daily at 2:00 AM
  - [ ] Runs as root (required for accessing /etc/gms/.env)
  - [ ] Output logged to `/var/log/gms-backup.log`
  - [ ] Cron job executes successfully (verify next day)
  - [ ] Backup file created in `/backups/` after cron execution
  - [ ] Log file shows no errors
- **Required Evidence:**
  - `/etc/cron.d/gms-backup` content
  - `crontab -l` or cron file verification
  - Backup log output showing successful execution
  - `ls -lh /backups/` showing timestamped backup file
- **Status:** BLOCKED (depends on T-055)

---

### T-058: Create /opt/gms/restore.sh (Isolated Database, Target Identity Verification)

- **Description:** Create the canonical restore script at `/opt/gms/restore.sh` with Revision 5 safety features: (1) NEVER restore directly to production database by default, (2) always restore to isolated test database first, (3) verify target database identity before any destructive operation, (4) require explicit affirmative confirmation (type database name to confirm, not just countdown timer), (5) validate backup integrity with `gzip -t` AND `pg_restore --list` before restore, (6) use `pg_restore` (NOT `psql`) for custom-format archives with `--clean --if-exists --no-owner --no-privileges`.
- **Dependencies:** T-055
- **Priority:** P0 (blocker)
- **Decision Reference:** D-009
- **Script Features:**
  - Default restore target: `gms_restore_test` (isolated, NOT production)
  - If target equals production database: requires typing database name to confirm + "yes" confirmation
  - Validates backup integrity before restore
  - Uses `pg_restore` with `--clean --if-exists --no-owner --no-privileges`
  - Measures and records restore duration (RTO tracking)
  - Handles encrypted backups (.gpg files -- decrypt first)
- **Acceptance Criteria:**
  - [ ] Script created at `/opt/gms/restore.sh`
  - [ ] Script is executable (`chmod +x`)
  - [ ] Default restore target is isolated test database (NOT production)
  - [ ] Target database identity verification implemented
  - [ ] Production restore requires typing database name + "yes" confirmation
  - [ ] Backup integrity validated before restore (`gzip -t` + `pg_restore --list`)
  - [ ] Uses `pg_restore` with correct flags
  - [ ] Credentials via PGPASSWORD (NOT command-line arguments)
  - [ ] Restore duration measured and recorded
  - [ ] Encrypted backup handling (if .gpg, decrypt first)
- **Required Evidence:**
  - Restore script content
  - `ls -la /opt/gms/restore.sh` showing executable permissions
  - Script syntax check: `bash -n /opt/gms/restore.sh`
- **Status:** NOT_STARTED

---

### T-059: Test Backup Creation and Validation

- **Description:** Execute backup script manually. Verify backup file created, both integrity checks pass (gzip + pg_restore --list), file permissions correct, retention policy works. Verify no credentials in backup logs.
- **Dependencies:** T-055, T-056
- **Priority:** P1 (critical)
- **Decision Reference:** D-009
- **Acceptance Criteria:**
  - [ ] Manual backup execution: `/opt/gms/backup.sh`
  - [ ] Backup file created in `/backups/`
  - [ ] `gzip -t` passes on backup file
  - [ ] `pg_restore --list` passes on backup file
  - [ ] Backup file permissions: 0600
  - [ ] Backup directory permissions: 0700
  - [ ] Backup size reasonable (not empty, not excessively large)
  - [ ] No credentials in backup log output
  - [ ] Retention policy works (old backups cleaned up)
- **Required Evidence:**
  - Backup execution log (full output)
  - `ls -lh /backups/` showing backup file with size
  - `gzip -t` output on backup file
  - `pg_restore --list` output on backup file
  - `stat -c "%a" /backups/<file>` showing permissions
- **Status:** BLOCKED (depends on T-055, T-056)

---

### T-060: Test Restore to Isolated Database

- **Description:** Execute full restore test on isolated database (NOT production). Verify data integrity after restore (row counts, checksums). Verify application connectivity after restore. Measure and record restore duration (RTO). This is the Tested Restore Acceptance Gate required before production.
- **Dependencies:** T-058, T-059
- **Priority:** P0 (blocker)
- **Decision Reference:** D-009
- **Acceptance Criteria:**
  - [ ] Isolated test database created (separate Neon branch or local PostgreSQL)
  - [ ] Restore executed: `/opt/gms/restore.sh /backups/<latest-backup> gms_restore_test`
  - [ ] Restore completes without errors
  - [ ] Data integrity verified (row counts match pre-backup state)
  - [ ] Application connectivity verified after restore (login, API calls work)
  - [ ] Tenant isolation verified after restore
  - [ ] Restore time measured and recorded: RTO = _____ seconds
  - [ ] RTO < 4 hours target met (or gap documented)
- **Required Evidence:**
  - Restore test log with timestamps (start and end)
  - RTO measurement: `_____` seconds
  - Data verification query results (row counts before backup and after restore)
  - Application health check after restore
  - Tenant isolation test after restore
- **Status:** BLOCKED (depends on T-058, T-059)

---

### T-061: Verify Restore Target Identity Check

- **Description:** Test the restore script's safety mechanism that prevents accidental production restore. Verify: (1) default target is isolated database, (2) production target requires explicit confirmation, (3) typing database name confirmation works, (4) abort works correctly.
- **Dependencies:** T-058
- **Priority:** P0 (blocker)
- **Decision Reference:** D-009
- **Acceptance Criteria:**
  - [ ] Running restore without arguments defaults to isolated test database
  - [ ] Running restore with production database as target triggers enhanced confirmation
  - [ ] Confirmation requires typing exact database name
  - [ ] Confirmation requires typing "yes" as second prompt
  - [ ] Incorrect database name aborts restore
  - [ ] Typing "no" at second prompt aborts restore
  - [ ] Abort does NOT modify any database
  - [ ] Isolated database restore does NOT require enhanced confirmation
- **Required Evidence:**
  - Test log showing production restore abort (with confirmation prompts)
  - Test log showing isolated restore success
  - Script output showing safety messages
- **Status:** BLOCKED (depends on T-058)

---

### T-062: Configure Off-site Backup Upload (OPTIONAL -- Requires T-005 Approval)

- **Description:** If owner approved off-site backup cost (T-005), configure encrypted backup upload to Backblaze B2 or AWS S3. GPG encryption must be configured before upload. Unencrypted backups must NOT be uploaded off-site. 12-month retention for off-site backups.
- **Dependencies:** T-059, T-005
- **Priority:** P3 (nice-to-have -- OPTIONAL, requires owner approval)
- **Decision Reference:** D-009
- **Acceptance Criteria:**
  - [ ] Owner approval for off-site backup cost obtained (T-005 approved)
  - [ ] GPG key configured for backup encryption
  - [ ] Object storage account configured (Backblaze B2 or AWS S3)
  - [ ] Upload integrated into backup.sh (post-backup step)
  - [ ] Unencrypted backups NOT uploaded (security requirement)
  - [ ] 12-month retention configured for off-site backups
  - [ ] Upload test successful
  - [ ] Download test successful (verify restorability from off-site)
- **Required Evidence:**
  - Owner approval document (from-005)
  - Object storage account details (securely stored)
  - Upload test log
  - Download test log
  - Verification that unencrypted backups are not uploaded
- **Status:** BLOCKED (depends on T-005 owner approval -- OPTIONAL)

---

### T-063: Test Full Backup-to-Restore Cycle Quarterly

- **Description:** Establish and execute quarterly disaster recovery drill: create backup, restore to isolated database, verify data integrity, verify application functionality, measure RTO, document results. Schedule recurring quarterly test.
- **Dependencies:** T-060, T-061
- **Priority:** P1 (critical)
- **Decision Reference:** D-009
- **Tested Restore Acceptance Gate:**
  - [ ] Restore test date: _______________
  - [ ] Backup file used: _______________
  - [ ] Restore executed on: Isolated test database (NOT production)
  - [ ] Data integrity verified: Row counts match pre-backup state
  - [ ] Application functional after restore: Login, API calls, tenant isolation verified
  - [ ] Restore time measured: _______________ minutes
  - [ ] Test performed by: _______________
  - [ ] Test evidence: Screenshots/logs attached
- **Acceptance Criteria:**
  - [ ] Quarterly DR drill schedule established
  - [ ] Drill executed and documented
  - [ ] RTO measured and recorded
  - [ ] RPO verified (< 24 hours from daily backup schedule)
  - [ ] Data integrity verified after restore
  - [ ] Application functionality verified after restore
  - [ ] Drill results reviewed by owner
- **Required Evidence:**
  - DR drill report with timestamps
  - RTO measurement
  - Data integrity verification results
  - Application functionality test results
  - Quarterly schedule document
- **Status:** BLOCKED (depends on T-060, T-061)

---

## Phase 8: Production Release

**Duration:** 2-3 days
**Prerequisites:** All previous phases complete
**Owner:** Deployment team + project owner

---

### T-064: Configure Nginx (Production HTTPS Config)

- **Description:** Deploy the final production Nginx configuration with full HTTPS, security headers, API proxy, SPA routing, health endpoint, actuator restriction, and sensitive path blocking. This is the canonical production configuration from Architecture Section 2.2.1 Phase 3.
- **Dependencies:** T-037, T-038, T-039, T-027
- **Priority:** P0 (blocker)
- **Decision Reference:** D-006
- **Acceptance Criteria:**
  - [ ] Production Nginx config deployed at `/etc/nginx/sites-available/gms`
  - [ ] HTTP (port 80) redirects to HTTPS (port 443) with 301
  - [ ] HTTPS configured with Let's Encrypt certificates
  - [ ] TLS 1.2+ enforced (TLSv1.2, TLSv1.3 only)
  - [ ] Strong cipher suites configured
  - [ ] Security headers: HSTS, X-Frame-Options, X-Content-Type-Options, Referrer-Policy, CSP
  - [ ] Frontend served from `/opt/gms/frontend/dist/` with SPA routing
  - [ ] API proxied: `location /api/ { proxy_pass http://127.0.0.1:8080/api/; }`
  - [ ] Public `/health` endpoint returns `200 OK`
  - [ ] `/actuator/` restricted to localhost only
  - [ ] Sensitive paths blocked (.git, .env, .htaccess)
  - [ ] Static asset caching configured (1 year, immutable)
  - [ ] `nginx -t` passes
  - [ ] `systemctl reload nginx` successful
- **Required Evidence:**
  - `nginx -t` output
  - Full production Nginx configuration file
  - `curl -I https://gms.yourdomain.com` showing all security headers
  - `systemctl status nginx` output
- **Status:** BLOCKED (depends on T-037, T-038, T-039)

---

### T-065: Set Up Docker Compose for Production

- **Description:** Create production Docker Compose file at `/opt/gms/docker-compose.prod.yml` with backend service configuration. Image tagged with Git SHA (NOT `:latest`). Environment variables from `/etc/gms/.env`. Backend bound to `127.0.0.1:8080` only. Health check configured. Log rotation configured. Restart policy: unless-stopped.
- **Dependencies:** T-020, T-012
- **Priority:** P0 (blocker)
- **Decision Reference:** D-005
- **Docker Compose Configuration:**
  ```yaml
  version: '3.8'
  services:
    backend:
      image: ghcr.io/<owner>/gms-backend:<git-sha>
      container_name: gms-backend
      restart: unless-stopped
      environment:
        - SPRING_PROFILES_ACTIVE=prod
        - DB_URL=${DB_URL}
        - DB_USERNAME=${DB_USERNAME}
        - DB_PASSWORD=${DB_PASSWORD}
        - SECURITY_JWT_SECRET=${JWT_SECRET}
        - CORS_ALLOWED_ORIGINS=${CORS_ALLOWED_ORIGINS}
        - APP_SUPERADMIN_ENABLED=${APP_SUPERADMIN_ENABLED}
        - APP_SUPERADMIN_USERNAME=${APP_SUPERADMIN_USERNAME}
        - APP_SUPERADMIN_PASSWORD=${APP_SUPERADMIN_PASSWORD}
        - APP_SUPERADMIN_EMAIL=${APP_SUPERADMIN_EMAIL}
        - APP_SUPERADMIN_FULL_NAME=${APP_SUPERADMIN_FULL_NAME}
      ports:
        - "127.0.0.1:8080:8080"
      networks:
        - gms-network
      healthcheck:
        test: ["CMD", "wget", "--spider", "-q", "http://localhost:8080/actuator/health"]
        interval: 30s
        timeout: 10s
        retries: 3
        start_period: 60s
      logging:
        driver: "json-file"
        options:
          max-size: "50m"
          max-file: "5"
  networks:
    gms-network:
      driver: bridge
  ```
- **Acceptance Criteria:**
  - [ ] Docker Compose file created at `/opt/gms/docker-compose.prod.yml`
  - [ ] Image reference uses Git SHA tag (NOT `:latest`)
  - [ ] Environment variables reference `/etc/gms/.env`
  - [ ] Backend bound to `127.0.0.1:8080` (NOT `0.0.0.0:8080`)
  - [ ] Health check configured (wget --spider to actuator/health)
  - [ ] Log rotation configured (max-size: 50m, max-file: 5)
  - [ ] Restart policy: unless-stopped
  - [ ] Network: gms-network (bridge driver)
  - [ ] `docker compose -f /opt/gms/docker-compose.prod.yml config` validates
- **Required Evidence:**
  - Docker Compose file content
  - `docker compose config` validation output
  - `docker ps` output showing container running with correct configuration
- **Status:** BLOCKED (depends on T-012, T-020)

---

### T-066: Configure Health Checks (Docker, Nginx, Application)

- **Description:** Configure and verify all three layers of health checks: (1) Docker container health check (wget to actuator/health), (2) Nginx public `/health` endpoint (lightweight, for UptimeRobot), (3) Application-level health (Spring Boot Actuator /actuator/health from localhost). Verify all health checks report healthy.
- **Dependencies:** T-064, T-065
- **Priority:** P1 (critical)
- **Decision Reference:** D-010
- **Acceptance Criteria:**
  - [ ] Docker health check: `docker inspect --format='{{.State.Health.Status}}' gms-backend` returns `healthy`
  - [ ] Nginx public health: `curl https://gms.yourdomain.com/health` returns `200 OK` with body `OK`
  - [ ] Application health (localhost): `curl http://127.0.0.1:8080/actuator/health` returns `{"status":"UP"}`
  - [ ] Application health (external): `curl https://gms.yourdomain.com/actuator/health` returns `403 Forbidden`
  - [ ] Docker health check interval: 30s, timeout: 10s, retries: 3, start_period: 60s
  - [ ] All health checks passing simultaneously
- **Required Evidence:**
  - Docker health status output
  - Curl output for all 4 health check endpoints
  - Health check configuration from Docker Compose and Nginx
- **Status:** BLOCKED (depends on T-064, T-065)

---

### T-067: Test Deployment Procedure

- **Description:** Execute full production deployment procedure: (1) run migration script, (2) pull new Docker image, (3) start containers with docker compose, (4) verify health checks, (5) verify frontend accessible, (6) verify API accessible, (7) verify HTTPS working. Document the complete procedure.
- **Dependencies:** T-054, T-064, T-065, T-066
- **Priority:** P0 (blocker)
- **Decision Reference:** D-007, D-008
- **Deployment Sequence:**
  1. CI/CD extracts migration files from Git commit to `/opt/gms/migrations/<sha>/`
  2. CI/CD writes `<sha>` to deployment context
  3. Stop old application (or mark as draining)
  4. Run `/opt/gms/migrate.sh`
  5. Verify migration success (exit code 0)
  6. On success: script updates `/opt/gms/current-deployment-sha.txt` and writes `SUCCESS` to `/opt/gms/last-deployment-status.txt`
  7. Pull new Docker image: `docker compose -f docker-compose.prod.yml pull backend`
  8. Start new application: `docker compose -f docker-compose.prod.yml up -d backend`
  9. Verify health check (wait 60s for start_period)
  10. Mark deployment complete
- **Acceptance Criteria:**
  - [ ] Migration runs successfully (exit code 0)
  - [ ] Docker image pulled from GHCR
  - [ ] Container starts without errors
  - [ ] Health check returns UP within start_period (60s)
  - [ ] Frontend accessible via HTTPS
  - [ ] API accessible via HTTPS
  - [ ] All security headers present
  - [ ] Deployment status file shows SUCCESS
  - [ ] Current deployment SHA file updated
- **Required Evidence:**
  - Full deployment log (migration -> pull -> compose up -> health check)
  - Deployment status file content
  - Health check output after deployment
  - Browser screenshot of deployed application
- **Status:** BLOCKED (depends on T-054, T-064, T-065, T-066)

---

### T-068: Test Rollback Procedure

- **Description:** Test deployment rollback: deploy previous known-good image tag. Verify application reverts to previous version. Verify database is NOT rolled back (migrations are forward-only). Verify health checks pass after rollback.
- **Dependencies:** T-067
- **Priority:** P0 (blocker)
- **Decision Reference:** D-007
- **Rollback Commands:**
  ```bash
  # Read previous known-good digest
  PREVIOUS_DIGEST=$(cat /opt/gms/deployment-manifest.txt.previous)
  # Stop current container
  docker compose -f /opt/gms/docker-compose.prod.yml down
  # Pull and start previous version
  docker pull "$PREVIOUS_DIGEST"
  # Update compose file with previous digest
  docker compose -f /opt/gms/docker-compose.prod.yml up -d
  # Verify health
  curl -f http://127.0.0.1:8080/actuator/health || exit 1
  ```
- **Acceptance Criteria:**
  - [ ] Rollback to previous image tag tested
  - [ ] Previous container starts without errors
  - [ ] Health check passes after rollback
  - [ ] Application version confirmed as previous (via /actuator/info or version endpoint)
  - [ ] Database NOT rolled back (schema version unchanged)
  - [ ] Frontend accessible after rollback
  - [ ] API accessible after rollback
  - [ ] Rollback completed within RTO target (< 4 hours)
- **Required Evidence:**
  - Rollback execution log
  - Health check output before and after rollback
  - Application version verification
  - Database schema version verification (unchanged)
- **Status:** BLOCKED (depends on T-067)

---

### T-069: Set Up UptimeRobot Monitoring

- **Description:** Configure UptimeRobot monitoring (free tier, 50 monitors). Add monitors: (1) GMS Frontend: `https://gms.yourdomain.com` (5-minute interval), (2) GMS Backend API: `https://gms.yourdomain.com/health` (5-minute interval -- public endpoint, NOT `/actuator/health`). Configure alert contacts (email, SMS). Verify monitors active and reporting.
- **Dependencies:** T-066, T-004
- **Priority:** P1 (critical)
- **Decision Reference:** D-010 (UptimeRobot Monitoring)
- **Acceptance Criteria:**
  - [ ] UptimeRobot account created (free tier)
  - [ ] Frontend monitor active: `https://gms.yourdomain.com` (5-minute interval)
  - [ ] Backend API monitor active: `https://gms.yourdomain.com/health` (5-minute interval)
  - [ ] Backend monitor uses public `/health` endpoint (NOT `/actuator/health`)
  - [ ] Alert contacts configured (email, SMS)
  - [ ] Test alert received and verified
  - [ ] Both monitors showing "Up" status
  - [ ] Monitor interval: 5 minutes (free tier default)
- **Required Evidence:**
  - UptimeRobot dashboard screenshot showing active monitors
  - Test alert email/screenshot
  - Monitor configuration details
- **Status:** BLOCKED (depends on T-004 domain, T-066 health checks)

---

### T-070: Configure Log Rotation

- **Description:** Configure log rotation for all application logs: Docker container logs, Nginx access/error logs, backup logs, migration logs. Prevent disk exhaustion from log growth. Use logrotate with appropriate rotation schedule.
- **Dependencies:** T-064, T-065
- **Priority:** P2 (important)
- **Decision Reference:** D-001
- **Acceptance Criteria:**
  - [ ] Docker log rotation configured (max-size: 50m, max-file: 5 in docker-compose.prod.yml)
  - [ ] Nginx access log rotation configured (`/etc/logrotate.d/nginx`)
  - [ ] Nginx error log rotation configured
  - [ ] Backup log rotation configured (`/etc/logrotate.d/gms-backup`)
  - [ ] Migration log rotation configured
  - [ ] Total log disk usage monitored (alert at 80% of boot volume)
  - [ ] Log rotation tested (verify old logs are compressed and deleted)
- **Required Evidence:**
  - Logrotate configuration files
  - `logrotate --debug /etc/logrotate.conf` test output
  - Disk usage monitoring setup
- **Status:** BLOCKED (depends on T-064, T-065)

---

### T-071: Run Production Smoke Tests

- **Description:** Execute comprehensive smoke tests in production: all roles (SUPERADMIN, ADMIN, ACCOUNTANT, TEACHER, STUDENT, PARENT), all critical paths (login, student registration, attendance marking, grade entry, fee management, announcements). Verify tenant isolation. Verify performance under normal load.
- **Dependencies:** T-067, T-040
- **Priority:** P0 (blocker)
- **Decision Reference:** D-011
- **Acceptance Criteria:**
  - [ ] SUPERADMIN login and platform management tested
  - [ ] ADMIN login and school management tested
  - [ ] ACCOUNTANT login and fee management tested
  - [ ] TEACHER login and attendance/grading tested
  - [ ] STUDENT login and academic portal tested
  - [ ] PARENT login and child monitoring tested
  - [ ] All critical paths tested: login, student registration, attendance, grades, fees
  - [ ] Tenant isolation verified (cross-school access returns 404)
  - [ ] Role authorization verified (unauthorized access returns 403)
  - [ ] API response time < 1 second under normal load
  - [ ] Frontend load time < 3 seconds
  - [ ] No critical errors in application logs
  - [ ] No critical errors in Nginx logs
  - [ ] All monitors showing healthy
- **Required Evidence:**
  - Smoke test results for each role (screenshots/logs)
  - API response time measurements
  - Frontend load time measurements
  - Application log excerpt (no errors)
  - Nginx log excerpt (no errors)
  - UptimeRobot monitor status
- **Status:** BLOCKED (depends on T-067, T-040)

---

### T-072: Owner Final Approval for Production Launch

- **Description:** Obtain explicit owner approval for production launch. Review all phase completion status, all security gates, all performance gates, all evidence. Confirm all production blockers resolved. Confirm all owner decisions made (D-001, D-002, D-004, D-009, D-010, D-011). Sign off on GO/NO-GO decision.
- **Dependencies:** T-071 (all previous tasks)
- **Priority:** P0 (blocker)
- **Decision Reference:** All decisions
- **GO Criteria (ALL must be true):**
  1. All phases completed successfully
  2. All security gates passed
  3. All performance gates passed
  4. All infrastructure gates passed
  5. Owner decisions made (D-001, D-002, D-004, D-009)
  6. All acceptance criteria met
  7. All evidence provided
  8. All procedures documented and tested
  9. No critical vulnerabilities
  10. System stable under load
- **NO-GO Triggers (ANY one triggers NO-GO):**
  1. Any security gate failed
  2. Any critical vulnerability found
  3. System unstable under load
  4. Owner decisions not made
  5. Critical acceptance criteria not met
  6. Evidence not provided
  7. Procedures not tested
  8. Tenant isolation not verified
  9. Data loss risk identified
  10. Compliance violation identified
  11. JWT localStorage not migrated to HttpOnly cookie (PRODUCTION BLOCKER)
  12. Flyway migration mechanism not tested with Docker container
- **Acceptance Criteria:**
  - [ ] All phases verified (evidence provided for each task)
  - [ ] All production blockers resolved
  - [ ] Owner explicit GO decision documented
  - [ ] Launch date and time scheduled
  - [ ] Support plan established (first week daily monitoring)
  - [ ] Rollback plan confirmed
  - [ ] Emergency contact list established
- **Required Evidence:**
  - Owner written GO decision (email/document)
  - Complete checklist with all tasks VERIFIED
  - Launch plan document
  - Support plan document
- **Status:** BLOCKED (depends on all previous tasks)

---

## Phase 9: Flutter Mobile Application

**Duration:** 5-10 days
**Prerequisites:** Phase 2 (T-022 backend API verified), Phase 0 (T-006 owner confirmation)
**Owner:** Flutter development team (separate project)
**Status:** NOT IMPLEMENTED -- Flutter app does not exist in this repository

---

### T-073: Set Up Flutter Project Structure

- **Description:** Create Flutter project in a separate repository. Configure Flutter 3.x / Dart. Set up project structure: lib/, test/, assets/. Configure HTTP client for backend API integration. Set up flutter_secure_storage for JWT token management. Configure environment-specific API base URLs.
- **Dependencies:** T-006, T-022
- **Priority:** P1 (critical)
- **Decision Reference:** D-004 (No PWA -- Flutter for Mobile)
- **Acceptance Criteria:**
  - [ ] Flutter project created (separate repository from GMS backend/frontend)
  - [ ] Flutter 3.x / Dart configured
  - [ ] Project structure follows Flutter conventions (lib/, test/, assets/)
  - [ ] HTTP client configured (http package or dio)
  - [ ] API base URL configurable per environment (dev/staging/production)
  - [ ] flutter_secure_storage dependency added
  - [ ] Project builds for Android and iOS
  - [ ] Basic app shell runs on emulator/device
- **Required Evidence:**
  - Flutter project repository reference
  - `flutter build apk` and `flutter build ios` successful output
  - App running on emulator screenshot
- **Status:** NOT_STARTED (NOT IMPLEMENTED -- Flutter app does not exist)

---

### T-074: Implement Authentication (HttpOnly Cookie Handling)

- **Description:** Implement authentication flow in Flutter app. POST to `/api/v1/auth/login` with credentials. Store JWT token in flutter_secure_storage (platform keychain on iOS, keystore on Android -- acceptable, NOT a production blocker like web localStorage). Include Bearer token in subsequent API requests. Handle 401 responses (clear token, redirect to login). Implement token refresh mechanism.
- **Dependencies:** T-073
- **Priority:** P0 (blocker)
- **Decision Reference:** D-004, D-011
- **Authentication Flow:**
  1. User enters credentials on login screen
  2. Flutter app POST `/api/v1/auth/login`
  3. Backend validates, returns JWT token
  4. Flutter stores token in flutter_secure_storage
  5. Flutter navigates to home screen
  6. Subsequent requests include `Authorization: Bearer <token>`
  7. On 401 response, Flutter clears token and navigates to login
- **Acceptance Criteria:**
  - [ ] Login screen implemented
  - [ ] POST to `/api/v1/auth/login` working
  - [ ] JWT token stored in flutter_secure_storage (NOT plain shared preferences)
  - [ ] Bearer token included in all authenticated API requests
  - [ ] 401 response handling: clear token, redirect to login
  - [ ] Token refresh mechanism implemented
  - [ ] Logout clears token from secure storage
  - [ ] Biometric authentication option (optional, if supported)
- **Required Evidence:**
  - Login flow screenshots (iOS and Android)
  - flutter_secure_storage implementation code
  - API integration test results
  - Token refresh test results
- **Status:** NOT_STARTED (NOT IMPLEMENTED)

---

### T-075: Implement Role-Based Navigation

- **Description:** Implement role-based navigation for 5 school roles: ADMIN, ACCOUNTANT, TEACHER, STUDENT, PARENT. SUPERADMIN is web-only (no Flutter app needed). Each role sees a different home screen and different navigation options based on their permissions.
- **Dependencies:** T-074
- **Priority:** P1 (critical)
- **Decision Reference:** D-004
- **Role/Feature Matrix:**
  | Role | Mobile Access | Features |
  |------|---------------|----------|
  | ADMIN | Yes | School management (subset of web) |
  | ACCOUNTANT | Yes | Fee collection, payment history |
  | TEACHER | Yes | Attendance marking, grade entry |
  | STUDENT | Yes | View attendance, grades, fees |
  | PARENT | Yes | View child's attendance, grades, fees |
- **Acceptance Criteria:**
  - [ ] ADMIN: School management home screen and navigation
  - [ ] ACCOUNTANT: Financial dashboard home screen and navigation
  - [ ] TEACHER: Classroom management home screen and navigation
  - [ ] STUDENT: Academic portal home screen and navigation
  - [ ] PARENT: Child monitoring home screen and navigation
  - [ ] Role determined from JWT claims (same as web)
  - [ ] Unauthorized navigation attempts blocked
  - [ ] Role-specific UI elements shown/hidden based on permissions
- **Required Evidence:**
  - Screenshots of each role's home screen
  - Navigation flow diagrams
  - Role-based access test results
- **Status:** NOT_STARTED (NOT IMPLEMENTED)

---

### T-076: Implement Core Features

- **Description:** Implement core mobile features for all 5 roles: attendance marking/viewing, grade entry/viewing, fee management/viewing, announcements. Use same backend API endpoints as web frontend. Implement offline capability (local cache) for critical features.
- **Dependencies:** T-075
- **Priority:** P1 (critical)
- **Decision Reference:** D-004
- **Acceptance Criteria:**
  - [ ] Attendance: TEACHER can mark attendance, STUDENT/PARENT can view attendance
  - [ ] Grades: TEACHER can enter grades, STUDENT/PARENT can view grades
  - [ ] Fees: ACCOUNTANT can manage fees, STUDENT/PARENT can view fee history
  - [ ] Announcements: ADMIN/TEACHER can create, all roles can view
  - [ ] Offline capability: critical data cached locally (encrypted)
  - [ ] Push notifications configured (future feature, basic setup)
  - [ ] All features use same backend API as web frontend
  - [ ] Tenant isolation enforced (same as web)
- **Required Evidence:**
  - Feature screenshots for each role
  - API integration test results
  - Offline capability test results
  - Tenant isolation test results
- **Status:** NOT_STARTED (NOT IMPLEMENTED)

---

### T-077: Test on iOS and Android

- **Description:** Comprehensive testing on both iOS and Android platforms. Test all 5 roles, all core features, authentication flow, offline capability, push notifications. Test on multiple device sizes and OS versions. Verify performance (app startup time, API response time, memory usage).
- **Dependencies:** T-076
- **Priority:** P0 (blocker)
- **Decision Reference:** D-004
- **Acceptance Criteria:**
  - [ ] All 5 roles tested on iOS (positive and negative access tests)
  - [ ] All 5 roles tested on Android (positive and negative access tests)
  - [ ] Authentication flow tested on both platforms
  - [ ] Offline capability tested on both platforms
  - [ ] Push notifications tested on both platforms
  - [ ] Performance acceptable: app startup < 3 seconds, API response < 1 second
  - [ ] No critical bugs on either platform
  - [ ] Memory usage within acceptable limits
  - [ ] UI responsive on both platforms
  - [ ] Tenant isolation verified on both platforms
- **Required Evidence:**
  - Test results for each role on each platform
  - Performance measurements (startup time, API response time)
  - Bug reports (if any) and resolution status
  - Device/OS version matrix tested
- **Status:** NOT_STARTED (NOT IMPLEMENTED)

---

### T-078: Submit to App Stores

- **Description:** Prepare and submit Flutter app to Apple App Store and Google Play Store. Follow app store guidelines. Prepare app store listings (description, screenshots, privacy policy). Configure app signing. Submit for review. Address any review feedback.
- **Dependencies:** T-077
- **Priority:** P1 (critical)
- **Decision Reference:** D-004
- **Acceptance Criteria:**
  - [ ] Apple Developer account configured
  - [ ] Google Play Developer account configured
  - [ ] App signing configured for both platforms
  - [ ] App store listings prepared (description, screenshots, privacy policy)
  - [ ] App submitted to Apple App Store for review
  - [ ] App submitted to Google Play Store for review
  - [ ] App approved and published on both stores
  - [ ] Certificate pinning configured for production (security)
  - [ ] App store guidelines compliance verified
- **Required Evidence:**
  - App Store Connect screenshot showing submitted app
  - Google Play Console screenshot showing submitted app
  - App store listing URLs (once published)
  - Review approval confirmation
- **Status:** NOT_STARTED (NOT IMPLEMENTED)

---

## Status Summary

| Phase | Total Tasks | NOT_STARTED | IN_PROGRESS | BLOCKED | VERIFIED |
|-------|-------------|-------------|-------------|---------|----------|
| Phase 0: Owner Decisions | 7 | 6 | 0 | 1 | 0 |
| Phase 1: Infrastructure | 9 | 0 | 0 | 9 | 0 |
| Phase 2: Backend | 7 | 3 | 0 | 4 | 0 |
| Phase 3: Frontend | 6 | 3 | 0 | 3 | 0 |
| Phase 4: Security | 12 | 3 | 0 | 9 | 0 |
| Phase 5: CI/CD | 7 | 1 | 0 | 6 | 0 |
| Phase 6: Database Migration | 7 | 2 | 0 | 5 | 0 |
| Phase 7: Backup/Recovery | 9 | 2 | 0 | 7 | 0 |
| Phase 8: Production Release | 9 | 0 | 0 | 9 | 0 |
| Phase 9: Flutter Mobile | 6 | 6 | 0 | 0 | 0 |
| **Total** | **79** | **26** | **0** | **53** | **0** |

---

## Blocking Dependencies Summary

### Tasks BLOCKED on Owner Decisions:
- **T-004** (domain purchase): BLOCKED on D-010 owner approval (~INR 800-1,200/year)
- **T-005** (off-site backups): BLOCKED on D-009 owner approval (~INR 1/month) -- OPTIONAL
- **T-008** through **T-015.1** (infrastructure): BLOCKED on T-001, T-002 (D-001 OCI eligibility)
- **T-037** (TLS certificate): BLOCKED on T-004 (domain purchase)
- **T-038**, **T-039** (HTTPS, redirect): BLOCKED on T-037 (certificate)
- **T-062** (off-site backup): BLOCKED on T-005 (owner approval) -- OPTIONAL
- **T-069** (UptimeRobot): BLOCKED on T-004 (domain)

### Tasks BLOCKED on Production Blockers (not yet implemented):
- **T-029** (JWT cookie migration): NOT IMPLEMENTED -- PRODUCTION BLOCKER
- **T-030** (CSRF protection): NOT IMPLEMENTED -- depends on T-029
- **T-031** (token revocation): NOT IMPLEMENTED -- PROPOSED DESIGN
- **T-040** (security test suite): BLOCKED on T-029, T-030

### Tasks BLOCKED on Unverified Prerequisites:
- **T-041** (GitHub Action SHAs): UNKNOWN -- SHAs not yet verified
- **T-042** through **T-047** (CI/CD): BLOCKED on T-041
- **T-049** (Flyway image): PROPOSED version -- requires verification
- **T-050** through **T-054** (migrations): BLOCKED on T-048, T-049

### Tasks NOT_STARTED (no blocking dependencies):
- **T-001**, **T-002**, **T-003**, **T-006**, **T-007** (owner decisions/verifications)
- **T-016** (Flyway config change -- required code change)
- **T-019** (Actuator configuration)
- **T-023**, **T-024** (Frontend env configuration)
- **T-048** (Migration script creation)
- **T-055**, **T-058** (Backup/restore script creation)

### Tasks NOT_IMPLEMENTED (Flutter):
- **T-073** through **T-078** (entire Flutter mobile application)

---

## Mandatory vs Optional Costs

### Mandatory (INR 0 budget -- free tiers only):
- OCI Always Free tier (D-001)
- Neon PostgreSQL free tier (D-002)
- GitHub Actions free tier (D-007)
- GHCR free tier (public repo)
- UptimeRobot free tier (D-010)
- Let's Encrypt certificates (D-006)
- Cloudflare DNS free tier (optional)

### Required for Production (owner must approve):
- Domain purchase: ~INR 800-1,200/year (T-004, D-010) -- REQUIRED for HTTPS
- Flutter development effort (T-073 through T-078, D-004) -- REQUIRED for mobile

### Optional (owner approval required):
- Off-site backup storage: ~INR 1/month (T-005, T-062, D-009) -- RECOMMENDED for disaster recovery
- Without off-site backups: OCI instance loss = total data loss

---

## Critical Path

1. **Phase 0:** Owner decisions (1-2 days) -- BLOCKS all infrastructure work
2. **Phase 1:** Infrastructure provisioning (2-3 days) -- BLOCKS all deployment work
3. **Phase 2:** Backend production readiness (2-3 days) -- BLOCKS frontend integration
4. **Phase 3:** Frontend production readiness (1-2 days) -- BLOCKS security verification
5. **Phase 4:** Security hardening (3-5 days) -- BLOCKS production release (includes JWT cookie migration)
6. **Phase 5:** CI/CD pipeline (2-3 days) -- BLOCKS automated deployment
7. **Phase 6:** Database migration (1-2 days) -- BLOCKS production release
8. **Phase 7:** Backup and recovery (2-3 days) -- BLOCKS production release
9. **Phase 8:** Production release (2-3 days) -- FINAL GATE
10. **Phase 9:** Flutter mobile (5-10 days) -- Can proceed in parallel after Phase 2

**Total Duration:** 20-35 days (excluding buffer)
**Total Effort:** 150-200 hours (19-25 full working days)

---

## Production Blockers (Must Resolve Before GO)

1. **JWT in localStorage** (T-029) -- XSS token theft risk. MUST migrate to HttpOnly cookie + CSRF (T-029, T-030) before production. NOT owner-waivable. (D-011)
2. **Domain not purchased** (T-004) -- HTTPS requires domain. NOT approved under INR 0 constraint. BLOCKS T-037, T-038, T-039. (D-010)
3. **Flyway migration not tested** (T-050 through T-054) -- Deployment-step mechanism is PROPOSED DESIGN only. (D-008)
4. **CI/CD action SHAs not verified** (T-041) -- Placeholder values must be replaced with verified SHAs. (D-007)
5. **Backup/restore not tested** (T-060) -- No restore drill performed. RTO unverified. (D-009)
6. **Role authorization not verified** (T-040) -- All 6 roles need comprehensive positive and negative tests. (D-011)
7. **Token revocation not implemented** (T-031) -- No server-side revocation mechanism exists. PROPOSED DESIGN. (D-012)
8. **Flutter app not implemented** (T-073 through T-078) -- 5 school roles require mobile app. (D-004)
9. **spring.flyway.enabled not changed** (T-016) -- REQUIRED CODE CHANGE not yet applied. (D-008)

---

## GO/NO-GO Decision Authority

- **GO Decision:** Project owner + deployment team lead
- **NO-GO Decision:** Any team member can trigger NO-GO
- **Escalation:** Disagreements escalated to project owner

---

**Document Version:** 2.0 (Canonical)
**Created:** 2026-11-03
**Source Documents:**
- `GMS_STAGE_J_FINAL_RELEASE_GATES5.md`
- `GMS_STAGE_J_FINAL_ARCHITECTURE5.md`
- `GMS_STAGE_J_FINAL_DECISION_LOG5.md`
**Status:** NOT STARTED -- 79 total tasks, 0 verified, 53 blocked, 26 not started
