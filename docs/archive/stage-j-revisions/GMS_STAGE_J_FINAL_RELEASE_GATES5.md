# GMS Stage J — Final Release Gates

**Date:** 2026-11-03  
**Revision:** 5.0
**Status:** CONSOLIDATED BLUEPRINT  
**Author:** Principal Software Architect, Senior DevOps Engineer, Cloud Architect  
**Repository:** GMS (test/playwright-e2e branch)  
**Purpose:** Ordered implementation phases with mandatory acceptance criteria, security gates, and GO/NO-GO decision framework

---

## Release Gate Framework

Each phase has:
- **Phase ID:** Sequential identifier (Phase 0, Phase 1, etc.)
- **Duration:** Estimated time to complete
- **Prerequisites:** What must be completed before starting
- **Owner:** Who is responsible for execution
- **Acceptance Criteria:** Mandatory conditions for phase completion
- **Security Gates:** Security-specific validation requirements
- **Performance Gates:** Performance-specific validation requirements
- **Infrastructure Gates:** Infrastructure-specific validation requirements
- **Rollback Procedure:** How to revert if phase fails
- **Evidence Required:** What proof demonstrates phase completion
- **GO/NO-GO Decision:** Explicit decision point before proceeding

**Gate Status Definitions (REVISED Revision 5):**
- **NOT STARTED** — Work has not begun; no evidence exists
- **IN PROGRESS** — Work is underway; partial evidence may exist
- **VERIFIED** — Criteria met, evidence provided, approved to proceed
- **BLOCKED** — Cannot validate; dependency not available or production blocker unresolved
- **NOT APPLICABLE** — Gate does not apply to this phase or component

A document or proposed command is **NOT** proof of successful execution. Only actual test results, screenshots, logs, or measurements constitute evidence.

---

## Phase 0: Owner Decisions and Account Setup

**Duration:** 1–2 days  
**Prerequisites:** None  
**Owner:** Project owner + deployment team

### 0.1 Objectives

1. Owner makes all required decisions (D-001, D-002, D-004, D-009)
2. Create cloud service accounts (OCI, Neon, UptimeRobot)
3. Purchase domain name (⚠️ OWNER APPROVAL REQUIRED — NOT APPROVED under ₹0 constraint)
4. Configure DNS

### 0.2 Tasks

#### Task 0.1: Owner Decisions

**Actions:**
- Review `GMS_STAGE_J_FINAL_DECISION_LOG.md`
- Make decisions on:
  - **D-001:** Oracle Cloud Infrastructure Always Free eligibility
  - **D-002:** Neon PostgreSQL project creation
  - **D-004:** Flutter mobile app architecture confirmation
  - **D-009:** Off-site backup storage approval (~₹1/month) — OWNER APPROVAL REQUIRED

**Acceptance Criteria:**
- [ ] Owner confirms OCI Always Free eligibility
- [ ] Owner confirms ARM capacity in ap-mumbai-1 region
- [ ] Owner confirms public IPv4 cost (₹0 or approved cost)
- [ ] Owner creates Neon PostgreSQL project
- [ ] Owner provides Neon connection string
- [ ] Owner confirms Flutter mobile app architecture
- [ ] Owner approves off-site backup storage cost

**Evidence Required:**
- Email or document with owner decisions
- Neon project connection string (securely transmitted)
- OCI account screenshot showing Always Free eligibility

**Gate Status:** **NOT STARTED** (owner decisions pending)

---

#### Task 0.2: Create Cloud Service Accounts

**Actions:**
- Create Oracle Cloud account (https://cloud.oracle.com)
  - Select home region: ap-mumbai-1
  - Verify Always Free tier eligibility
- Create Neon PostgreSQL account (https://neon.tech)
  - Create project: gms-production
  - Region: ap-mumbai-1 (closest to OCI)
  - PostgreSQL version: 15
  - Database name: gms
- Create UptimeRobot account (https://uptimerobot.com)
  - Free tier (50 monitors)
- (Optional) Create Cloudflare account (https://cloudflare.com)
  - Free tier for DNS and CDN

**Acceptance Criteria:**
- [ ] OCI account created and verified
- [ ] OCI Always Free tier confirmed (ARM capacity available)
- [ ] Neon project created and verified
- [ ] Neon connection string obtained
- [ ] UptimeRobot account created and verified
- [ ] (Optional) Cloudflare account created

**Evidence Required:**
- OCI account dashboard screenshot
- Neon project dashboard screenshot
- UptimeRobot account dashboard screenshot

**Gate Status:** **NOT STARTED** (accounts not yet created)

---

#### Task 0.3: Purchase Domain and Configure DNS

**Actions:**
- ⚠️ **OWNER APPROVAL REQUIRED — NOT APPROVED under ₹0 constraint**
- Purchase domain name (e.g., gms.yourdomain.com)
  - Recommended registrar: Namecheap, GoDaddy, or Cloudflare
  - Cost: ~₹800–₹1,200/year ($10–$15/year)
  - Alternative: HTTP over raw public IP (NOT production-ready, security risk)
- Configure DNS records:
  - `gms.yourdomain.com` → OCI instance public IP (A record)
  - `www.gms.yourdomain.com` → OCI instance public IP (CNAME)
  - `api.gms.yourdomain.com` → OCI instance public IP (CNAME)

**Acceptance Criteria:**
- [ ] Domain purchased
- [ ] DNS records configured
- [ ] DNS propagation verified (`dig gms.yourdomain.com`)
- [ ] Domain resolves to OCI instance IP (after Phase 1)

**Evidence Required:**
- Domain registration confirmation
- DNS records screenshot
- `dig` output showing correct resolution

**Gate Status:** **NOT STARTED** (domain not yet purchased)

**Cost Implications:**
- Domain name: ~₹800–₹1,200/year (owner decision)
- DNS hosting: ₹0 (Cloudflare free tier or registrar default)

**GO/NO-GO Decision:**
- **GO** if all acceptance criteria met
- **NO-GO** if owner decisions not made or accounts not created

---

## Phase 1: Infrastructure Provisioning

**Duration:** 2–3 days  
**Prerequisites:** Phase 0 complete  
**Owner:** Deployment team

### 1.1 Objectives

1. Provision OCI instance using OpenTofu
2. Configure firewall and security groups
3. Install Docker, Nginx, Certbot
4. Obtain SSL/TLS certificate
5. Verify instance accessibility

### 1.2 Tasks

#### Task 1.1: Provision OCI Instance

**Actions:**
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

**Acceptance Criteria:**
- [ ] OpenTofu plan reviewed and approved
- [ ] OCI instance provisioned (VM.Standard.A1.Flex)
- [ ] Public IP assigned and recorded
- [ ] SSH access verified (`ssh gmsdeploy@<public-ip>`)
- [ ] Instance running Ubuntu 24.04 ARM64
- [ ] Docker installed and running
- [ ] Nginx installed and running
- [ ] UFW firewall configured (22, 80, 443 only)

**Evidence Required:**
- `tofu apply` output (redacted for secrets)
- SSH session screenshot
- `docker --version` output
- `nginx -v` output
- `ufw status` output

**Security Gates:**
- [ ] SSH access restricted to owner's IP (/32)
- [ ] Password SSH disabled (if authorized key present)
- [ ] Non-root user created (gmsdeploy)
- [ ] Docker daemon not exposed remotely
- [ ] UFW default deny incoming

**Gate Status:** **NOT STARTED** (infrastructure not yet provisioned)

**Rollback Procedure:**
```bash
tofu destroy  # Decommission all OCI resources
```

---

#### Task 1.2: Configure Nginx Reverse Proxy

**Actions:**
- Create Nginx configuration: `/etc/nginx/sites-available/gms`
- Enable site: `ln -s /etc/nginx/sites-available/gms /etc/nginx/sites-enabled/`
- Test configuration: `nginx -t`
- Restart Nginx: `systemctl restart nginx`

**Nginx Configuration:**
**⚠️ IMPORTANT — Task 1.2 Nginx Configuration (REVISION 4 AUDIT CORRECTION):**

The following Nginx configuration is the **final production HTTPS configuration**. It must NOT be applied until:
1. The domain has been acquired and DNS configured (OWNER APPROVAL REQUIRED — not approved under ₹0 constraint)
2. Let's Encrypt certificates have been obtained (Task 1.3)
3. HTTP-first bootstrap configuration has been tested (see Architecture §2.2.1 Phase 1)

**For initial bootstrap (before certificates), use the HTTP-only configuration from Architecture §2.2.1 Phase 1.**

**⚠️ REVISION 5 — TLS BOOTSTRAP SEQUENCE:**
The HTTPS Nginx configuration above requires SSL certificates that do not exist at initial setup. The correct bootstrap sequence is:
1. Deploy with HTTP-only configuration first (port 80 only, no SSL server block)
2. Run Certbot to obtain certificates: `certbot --nginx -d gms.yourdomain.com`
3. After certificates are obtained, switch to the full HTTPS configuration above
4. Verify HTTPS works, then add HTTP→HTTPS redirect

**HTTP-only bootstrap configuration:**
```nginx
server {
    listen 80;
    server_name gms.yourdomain.com;
    # ... serve content over HTTP for Certbot validation ...
}
```
This HTTP-only configuration is NOT production-ready — it is a bootstrap step only.

```nginx
# /etc/nginx/sites-available/gms
# REVISION 4 AUDIT CORRECTION: Production HTTPS configuration
# Aligns with Architecture §2.2.1 Phase 3 canonical config

# HTTP → HTTPS redirect (only after HTTPS is verified working)
server {
    listen 80;
    server_name gms.yourdomain.com;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name gms.yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/gms.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/gms.yourdomain.com/privkey.pem;
    
    # TLS configuration
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384;
    ssl_prefer_server_ciphers off;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 10m;
    
    # Security headers (REVISION 4: Removed deprecated X-XSS-Protection)
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;
    
    # Content Security Policy (REVISION 4: PROPOSED — requires testing with actual application)
    # This CSP is a starting point and MUST be tested before production deployment
    add_header Content-Security-Policy "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self' data:; connect-src 'self' https://*.neon.tech; frame-ancestors 'self';" always;
    
    # Frontend (React SPA)
    location / {
        root /opt/gms/frontend/dist;
        try_files $uri $uri/ /index.html;
        
        # Cache static assets
        location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
            expires 1y;
            add_header Cache-Control "public, immutable";
        }
    }
    
    # Backend API (REVISION 4: proxy_pass preserves /api/ path prefix)
    # Backend routes are /api/v1/*, so proxy must forward /api/ → /api/
    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # Timeout settings
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }
    
    # REVISION 4: Public monitoring endpoint (minimal, no sensitive data)
    # This is NOT the Spring Boot Actuator /actuator/health endpoint
    # This is a separate lightweight endpoint for external monitoring (UptimeRobot, etc.)
    location /health {
        return 200 'OK\n';
        add_header Content-Type text/plain;
        access_log off;
    }
    
    # REVISION 4: Block all Actuator endpoints from public access
    # Spring Boot Actuator /actuator/* endpoints are PRIVATE and must not be exposed
    location /actuator/ {
        # Only allow localhost access (for internal health checks)
        allow 127.0.0.1;
        deny all;
        
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        access_log off;
    }
    
    # Block access to sensitive paths
    location ~ /\.(git|env|htaccess) {
        deny all;
        access_log off;
        log_not_found off;
    }
}
```

**Acceptance Criteria:**
- [ ] Nginx configuration created and validated
- [ ] HTTP redirects to HTTPS
- [ ] Security headers present in responses
- [ ] Nginx serving static files (placeholder)
- [ ] Nginx proxying to backend (placeholder)

**Evidence Required:**
- `nginx -t` output (syntax ok)
- `curl -I http://gms.yourdomain.com` (301 redirect)
- `curl -I https://gms.yourdomain.com` (200 OK with security headers)

**Security Gates:**
- [ ] HTTPS enforced (HTTP redirects to HTTPS)
- [ ] HSTS header present
- [ ] Security headers configured
- [ ] Backend not directly accessible (only via Nginx)

**Gate Status:** **NOT STARTED** (Nginx not yet configured)

---

#### Task 1.3: Obtain SSL/TLS Certificate

**Actions:**
```bash
# Install Certbot
sudo apt install certbot python3-certbot-nginx -y

# Obtain certificate
sudo certbot --nginx -d gms.yourdomain.com

# Verify auto-renewal
sudo certbot renew --dry-run

# Check cron job
sudo systemctl list-timers | grep certbot
```

**Acceptance Criteria:**
- [ ] Certbot installed
- [ ] SSL certificate obtained for gms.yourdomain.com
- [ ] Certificate valid for 90 days
- [ ] Auto-renewal configured and tested
- [ ] HTTPS working with valid certificate

**Evidence Required:**
- `certbot certificates` output
- `curl -I https://gms.yourdomain.com` (SSL certificate details)
- Browser screenshot showing valid HTTPS

**Security Gates:**
- [ ] TLS 1.2+ enforced
- [ ] Strong cipher suites configured
- [ ] Certificate auto-renewal working

**Gate Status:** **NOT STARTED** (certificate not yet obtained)

---

#### Task 1.4: Verify Infrastructure

**Actions:**
- Test SSH access from owner's machine
- Test HTTP and HTTPS access from browser
- Test firewall rules (only 22, 80, 443 open)
- Verify Docker, Nginx, Certbot services running
- Check system logs for errors

**Acceptance Criteria:**
- [ ] SSH access working (owner's machine only)
- [ ] HTTP redirects to HTTPS
- [ ] HTTPS serving placeholder page
- [ ] Firewall rules verified (ufw status)
- [ ] All services running (docker, nginx, certbot)
- [ ] No errors in system logs

**Evidence Required:**
- SSH session screenshot
- Browser screenshot of HTTPS page
- `ufw status` output
- `systemctl status docker nginx` output
- `journalctl -xe` output (last 50 lines)

**Performance Gates:**
- [ ] SSH response time < 1 second
- [ ] HTTPS response time < 2 seconds
- [ ] No service crashes or restarts

**Gate Status:** **NOT STARTED** (infrastructure not yet verified)

**GO/NO-GO Decision:**
- **GO** if all acceptance criteria met and security gates passed
- **NO-GO** if any security gate failed or infrastructure unstable

---

## Phase 2: Backend Deployment

**Duration:** 2–3 days  
**Prerequisites:** Phase 1 complete  
**Owner:** Deployment team

### 2.1 Objectives

1. Build production Docker image
2. Push image to GitHub Container Registry
3. Deploy backend container on OCI instance
4. Configure environment variables
5. Run Flyway migrations
6. Verify backend health

### 2.2 Tasks

#### Task 2.1: Build and Push Docker Image

**Actions:**
```bash
# Build image
cd gms_backend
docker build -t gms-backend:latest .

# Tag with Git SHA
GIT_SHA=$(git rev-parse --short HEAD)
docker tag gms-backend:latest ghcr.io/<owner>/gms-backend:$GIT_SHA

# Push to GHCR
echo $GITHUB_TOKEN | docker login ghcr.io -u <owner> --password-stdin
docker push ghcr.io/<owner>/gms-backend:$GIT_SHA
```

⚠️ REVISION 5: The `latest` tag must NOT be used for production deployment. Use immutable Git SHA tags (`gms-backend:<commit-sha>`) or digest references (`gms-backend@sha256:<digest>`).

**Acceptance Criteria:**
- [ ] Docker image built successfully
- [ ] Image size < 300 MB
- [ ] Image tagged with Git SHA
- [ ] Image pushed to GitHub Container Registry
- [ ] Image accessible in GHCR dashboard

**Evidence Required:**
- `docker images` output
- GHCR package screenshot
- `docker pull` test from OCI instance

**Gate Status:** **NOT STARTED** (image not yet built)

---

#### Task 2.2: Deploy Backend Container

**Actions:**
- Create production Docker Compose file: `/opt/gms/docker-compose.prod.yml`
- Create environment file: `/etc/gms/.env`
- Pull and start backend container
- Verify container health

**Docker Compose Configuration:**
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

**Environment File:**
```bash
# /etc/gms/.env (permissions: 0640)

# Database (Neon PostgreSQL)
DB_URL=jdbc:postgresql://<neon-host>:5432/gms?sslmode=require
DB_USERNAME=<neon-user>
DB_PASSWORD=<neon-password>

# JWT
JWT_SECRET=<generate-with-openssl-rand-base64-48>

# CORS
CORS_ALLOWED_ORIGINS=https://gms.yourdomain.com

# Superadmin (initial setup)
APP_SUPERADMIN_ENABLED=true
APP_SUPERADMIN_USERNAME=admin
APP_SUPERADMIN_PASSWORD=<strong-password>
APP_SUPERADMIN_EMAIL=admin@yourdomain.com
APP_SUPERADMIN_FULL_NAME=Platform Administrator

# Git SHA (for deployment)
GIT_SHA=<git-sha>
```

**Acceptance Criteria:**
- [ ] Docker Compose file created
- [ ] Environment file created with correct permissions (0640)
- [ ] Backend container running
- [ ] Flyway migrations executed via deployment-step script (NOT application startup)
- [ ] Health check returning `{"status": "UP"}`
- [ ] No errors in container logs

**Evidence Required:**
- `docker ps` output
- `docker logs gms-backend` output (last 50 lines)
- `curl http://localhost:8080/actuator/health` output
- `/opt/gms/migrate.sh` execution log
- `spring.flyway.enabled=false` confirmed in production config

**Security Gates:**
- [ ] Environment file permissions: 0640
- [ ] JWT secret strong (48+ characters, base64 encoded)
- [ ] Superadmin password strong (12+ characters, mixed case, numbers, symbols)
- [ ] Backend only accessible via localhost (127.0.0.1:8080)
- [ ] Database connection uses SSL (sslmode=require)

**Gate Status:** **NOT STARTED** (backend not yet deployed)

---

#### Task 2.2.1: Flyway Deployment-Step Migration (REVISION 4)

**⚠️ CRITICAL CORRECTION (Revision 4):**
The Revision 3 migration script used unsafe patterns: file-exists locking, `set -e` with exit code assignment, mutable `flyway:10` tag, and separate `PG_HOST`/`PG_PORT`/`PG_DATABASE` variables. Revision 4 uses **canonical JDBC URL-based contract**, **flock-based atomic locking**, **pinned image version (10.15.2 PROPOSED)**, and **explicit error handling**.

Flyway migrations must NOT run on application startup. They must run as a controlled deployment step BEFORE the new application version starts.

**REQUIRED CODE CHANGE (not yet applied):**
```properties
# gms_backend/src/main/resources/application-prod.properties
# CURRENT: spring.flyway.enabled=true
# MUST CHANGE TO:
spring.flyway.enabled=false
```

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

**Why database rollback ≠ application image rollback:**
- Application rollback: deploy previous Docker image tag
- Database rollback: cannot be done by deploying a previous migration file
- If migration V11 succeeds but application deployment fails, database is at V11 while old code expects V10
- **Mitigation:** All migrations must be backward-compatible; write forward-only undo migrations if needed

**Migration Safety Rules:**
1. **Backward-compatible only:** New migrations must not break the old application version
2. **Expand-contract pattern:** Add new columns/tables first, migrate data later, drop old schema last
3. **Single execution:** Migration script runs once per deployment, not on every app restart
4. **Deployment lock:** Only one migration can run at a time (lock file prevents race)
5. **No automatic rollback:** If migration fails, deployment halts — manual intervention required
6. **Schema compatibility:** Old app version must still work with new schema during rolling deploy
7. **Never use `flyway clean` in production** (already disabled: `spring.flyway.clean-disabled=true`)

**Deployment Sequence (REVISION 4):**
1. CI/CD extracts migration files from Git commit to `/opt/gms/migrations/<sha>/`
2. CI/CD writes `<sha>` to deployment context (NOT to `current-deployment-sha.txt` yet)
3. Stop old application (or mark as draining)
4. Run `/opt/gms/migrate.sh` (canonical Revision 4 procedure with flock locking)
5. Verify migration success (exit code 0)
6. On success: script updates `/opt/gms/current-deployment-sha.txt` and writes `SUCCESS` to `/opt/gms/last-deployment-status.txt`
7. On failure: script writes `FAILED` to `/opt/gms/last-deployment-status.txt` and records failed SHA — deployment aborts
8. Start new application version (only if migration succeeded)
9. Verify health check
10. Mark deployment complete

**Acceptance Criteria (REVISION 4):**
- [ ] Canonical migration script created and executable (see Architecture §2.2.2)
- [ ] `spring.flyway.enabled=false` changed in `application-prod.properties` (REQUIRED CODE CHANGE)
- [ ] `flyway/flyway:10.15.2` Docker image pulled on OCI instance (PROPOSED version — requires verification)
- [ ] Environment file uses canonical JDBC URL contract (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`)
- [ ] Migration tested on isolated staging database
- [ ] `flock`-based deployment lock mechanism verified
- [ ] Rollback procedure documented (restore backup, NOT reverse migration)

**Evidence Required (REVISION 4):**
- Canonical migration script content (matching Architecture §2.2.2)
- Production config showing `spring.flyway.enabled=false`
- Environment file showing JDBC URL-based contract (DB_URL, DB_USERNAME, DB_PASSWORD)
- Migration execution log (from staging test)
- Schema version after migration (`flyway info` output)
- Deployment status file content (`/opt/gms/last-deployment-status.txt`)

**Gate Status:** **NOT STARTED** (canonical Revision 4 migration procedure not yet implemented)

---

#### Task 2.3: Verify Backend API

**Actions:**
- Test API via Nginx proxy: `https://gms.yourdomain.com/api/v1/auth/login`
- Test authenticated endpoint: `https://gms.yourdomain.com/api/v1/schools`
- Verify CORS headers
- Verify JWT token issuance

**Acceptance Criteria:**
- [ ] API accessible via HTTPS
- [ ] Login endpoint working
- [ ] JWT token issued successfully
- [ ] Authenticated endpoints accessible
- [ ] CORS headers present in responses
- [ ] Tenant isolation working (school-scoped queries)

**Evidence Required:**
- `curl` output for login endpoint
- JWT token (redacted)
- `curl` output for authenticated endpoint
- Browser developer tools screenshot (Network tab)

**Security Gates:**
- [ ] HTTPS enforced (no HTTP access)
- [ ] JWT token expiration configured (3600 seconds)
- [ ] CORS restricted to production domain only
- [ ] Tenant isolation verified (cross-school access returns 404)

**Performance Gates:**
- [ ] API response time < 1 second (95th percentile)
- [ ] Database query time < 500ms (95th percentile)
- [ ] No N+1 query issues

**Gate Status:** **NOT STARTED** (API not yet verified)

**Rollback Procedure:**
```bash
# Stop backend container
docker compose -f /opt/gms/docker-compose.prod.yml down

# Rollback to previous image
export GIT_SHA=<previous-sha>
docker compose -f /opt/gms/docker-compose.prod.yml up -d backend
```

**GO/NO-GO Decision:**
- **GO** if all acceptance criteria met and security gates passed
- **NO-GO** if any security gate failed or API unstable

---

## Phase 3: Frontend Deployment

**Duration:** 1–2 days  
**Prerequisites:** Phase 2 complete  
**Owner:** Deployment team

### 3.1 Objectives

1. Build React frontend for production
2. Deploy frontend to OCI instance
3. Configure Nginx for SPA routing
4. Verify frontend-backend integration

### 3.2 Tasks

#### Task 3.1: Build React Frontend

**Actions:**
```bash
# Build frontend
cd gms_frontend
npm ci
npm run build

# Verify build artifacts
ls -lh dist/
```

**Acceptance Criteria:**
- [ ] Frontend built successfully
- [ ] Build artifacts in `dist/` directory
- [ ] Build size < 10 MB
- [ ] No build errors or warnings

**Evidence Required:**
- `npm run build` output
- `ls -lh dist/` output
- Build artifact sizes

**Gate Status:** **NOT STARTED** (frontend not yet built)

---

#### Task 3.2: Deploy Frontend to OCI Instance

**Actions:**
```bash
# Copy build artifacts to OCI instance
rsync -avz gms_frontend/dist/ gmsdeploy@<oci-ip>:/opt/gms/frontend/dist/

# Verify deployment
ssh gmsdeploy@<oci-ip>
ls -lh /opt/gms/frontend/dist/
```

**Acceptance Criteria:**
- [ ] Frontend deployed to OCI instance
- [ ] Files in `/opt/gms/frontend/dist/`
- [ ] File permissions correct (644 for files, 755 for directories)
- [ ] Nginx serving frontend

**Evidence Required:**
- `rsync` output
- SSH session showing deployed files
- Browser screenshot of frontend

**Gate Status:** **NOT STARTED** (frontend not yet deployed)

---

#### Task 3.3: Verify Frontend-Backend Integration

**Actions:**
- Access frontend: `https://gms.yourdomain.com`
- Test login with superadmin credentials
- Verify API calls successful
- Check browser console for errors
- Test role-based access control

**Acceptance Criteria:**
- [ ] Frontend accessible via HTTPS
- [ ] Login working
- [ ] API calls successful (no CORS errors)
- [ ] No console errors in browser
- [ ] Role-based access control working
- [ ] Tenant isolation verified

**Evidence Required:**
- Browser screenshot of frontend
- Browser developer tools screenshot (Network tab)
- Browser console log (no errors)
- Login flow video or screenshots

**Security Gates:**
- [ ] No sensitive data in frontend code
- [ ] 🚫 **BLOCKED:** JWT tokens in localStorage — PRODUCTION BLOCKER; must migrate to HttpOnly cookie
- [ ] CORS configured correctly
- [ ] XSS protection headers present (CSP, X-Content-Type-Options, X-Frame-Options)
- [ ] Actuator endpoints NOT publicly accessible (restricted to localhost via Nginx)

**🚫 SECURITY BLOCKER (Revision 3):**
- localStorage JWT storage is a **PRODUCTION BLOCKER** — CSP headers reduce but do NOT eliminate XSS token theft risk
- **Must migrate to HttpOnly cookie + CSRF protection before production launch**
- See Decision Log D-011 for authentication approach comparison and recommended migration path
- For Flutter mobile, use `flutter_secure_storage` (platform keychain/keystore) — acceptable
- Actuator `/actuator/health` must be proxied only to 127.0.0.1, never exposed publicly
- Secret rotation procedure must be documented before production launch

**Performance Gates:**
- [ ] Frontend load time < 3 seconds (95th percentile)
- [ ] API response time < 1 second (95th percentile)
- [ ] No console errors or warnings

**Gate Status:** **NOT STARTED** (integration not yet verified)

**Rollback Procedure:**
```bash
# Restore previous frontend build
rsync -avz <previous-build>/ gmsdeploy@<oci-ip>:/opt/gms/frontend/dist/
```

**GO/NO-GO Decision:**
- **GO** if all acceptance criteria met and security gates passed
- **NO-GO** if any security gate failed or integration unstable

---

#### Task 3.4: API Routing Acceptance Test (NEW Revision 2)

**⚠️ CRITICAL ADDITION (Revision 2):**
The frontend deployment model uses host-Nginx serving static React assets with backend API proxied through Nginx. This test proves that API requests reach Spring Boot rather than the React SPA fallback.

**Deployment Model:** Host-Nginx (NOT container-Nginx)
- Nginx runs on the OCI host, not in a container
- Static React assets served from `/opt/gms/frontend/dist/`
- API requests to `/api/v1/*` proxied to backend container on `127.0.0.1:8080`
- Actuator health at `/actuator/health` proxied to `127.0.0.1:8080` (localhost only, NOT public)

**API Routing Acceptance Tests:**
```bash
# Test 1: API endpoint reaches Spring Boot (not SPA fallback)
curl -sf https://gms.yourdomain.com/api/v1/auth/login \
  -X POST -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test"}' \
  | grep -q "error\|token\|message"
# Expected: JSON response from Spring Boot (NOT HTML from React)

# Test 2: API returns JSON content-type
curl -sI https://gms.yourdomain.com/api/v1/auth/login \
  | grep -qi "content-type: application/json"
# Expected: Content-Type is application/json

# Test 3: Non-API path returns React SPA
curl -sf https://gms.yourdomain.com/dashboard \
  | grep -q "<div id=\"root\">"
# Expected: HTML with React root element

# Test 4: Actuator NOT publicly accessible
curl -sf https://gms.yourdomain.com/actuator/health
# Expected: 403 or 404 (must NOT return health JSON publicly)

# Test 5: Actuator accessible from localhost
ssh <oci-instance> "curl -sf http://127.0.0.1:8080/actuator/health"
# Expected: {"status":"UP"} from localhost only
```

**Acceptance Criteria:**
- [ ] Test 1 passes: API returns Spring Boot JSON
- [ ] Test 2 passes: API Content-Type is application/json
- [ ] Test 3 passes: Non-API returns React SPA HTML
- [ ] Test 4 passes: Actuator NOT publicly accessible
- [ ] Test 5 passes: Actuator accessible from localhost only

**Evidence Required:**
- Curl output for all 5 tests
- Nginx configuration file showing `/api/v1/` proxy_pass
- Nginx configuration showing `/actuator/` restricted to localhost

**Gate Status:** **NOT STARTED** (API routing not yet tested)

---

#### Task 3.5: Role Coverage Verification (NEW Revision 2)

**⚠️ CRITICAL ADDITION (Revision 2):**
All six roles must be verified in the React web frontend. All five school roles must be verified in the Flutter mobile app. Do NOT assume a role is implemented merely because it appears in a requirements table.

**Role/Client Coverage Matrix:**

| Role | React Web | Flutter Mobile | Status |
|------|-----------|----------------|--------|
| SUPERADMIN | ✅ Required | ❌ NOT required (web only) | 🔍 UNVERIFIED |
| ADMIN | ✅ Required | ✅ Required | 🔍 UNVERIFIED |
| ACCOUNTANT | ✅ Required | ✅ Required | 🔍 UNVERIFIED |
| TEACHER | ✅ Required | ✅ Required | 🔍 UNVERIFIED |
| STUDENT | ✅ Required | ✅ Required | 🔍 UNVERIFIED |
| PARENT | ✅ Required | ✅ Required | 🔍 UNVERIFIED |

**React Web Verification (6 roles):**
- [ ] SUPERADMIN: Platform management UI accessible and authorized
- [ ] ADMIN: School management UI accessible and authorized
- [ ] ACCOUNTANT: Financial dashboard UI accessible and authorized
- [ ] TEACHER: Classroom management UI accessible and authorized
- [ ] STUDENT: Academic portal UI accessible and authorized
- [ ] PARENT: Child monitoring UI accessible and authorized

**Flutter Mobile Verification (5 school roles):**
- ❌ Flutter mobile app NOT YET IMPLEMENTED
- [ ] ADMIN: School management mobile screens
- [ ] ACCOUNTANT: Financial mobile screens
- [ ] TEACHER: Classroom mobile screens (attendance, grading)
- [ ] STUDENT: Academic mobile screens
- [ ] PARENT: Child monitoring mobile screens

**Negative Authorization Tests:**
- [ ] SUPERADMIN cannot access school-scoped data of other schools
- [ ] ADMIN cannot access platform-level settings
- [ ] ACCOUNTANT cannot modify academic records
- [ ] TEACHER cannot modify fee structures
- [ ] STUDENT cannot access other students' data
- [ ] PARENT cannot access other children's data

**Acceptance Criteria:**
- [ ] All 6 roles verified in React web with positive and negative tests
- [ ] All 5 school roles designed for Flutter (implementation pending)
- [ ] Role-based authorization tested for each endpoint
- [ ] Tenant isolation verified for each school-scoped role

**Evidence Required:**
- Test results for each role's positive access
- Test results for each role's negative access (403 responses)
- Flutter design documents showing role-specific screens

**Gate Status:** **NOT STARTED** (role coverage not yet comprehensively tested)

---

## Phase 4: Monitoring, Backups, and CI/CD

**Duration:** 3–4 days  
**Prerequisites:** Phases 1–3 complete  
**Owner:** Deployment team

### 4.1 Objectives

1. Configure UptimeRobot monitoring
2. Implement automated backups
3. Set up CI/CD pipeline for automated deployments
4. Document procedures

### 4.2 Tasks

#### Task 4.1: Configure UptimeRobot Monitoring

**Actions:**
- Add monitors in UptimeRobot dashboard:
  - **GMS Frontend:** `https://gms.yourdomain.com` (5-minute interval)
  - **GMS Backend API:** `https://gms.yourdomain.com/health` (5-minute interval) — public endpoint, NOT `/actuator/health`
- Configure alert contacts (email, SMS)
- Verify monitors active

**Acceptance Criteria:**
- [ ] UptimeRobot monitors active
- [ ] Frontend monitor showing "Up" status
- [ ] Backend API monitor showing "Up" status
- [ ] Alert contacts configured
- [ ] Test alert received

**Evidence Required:**
- UptimeRobot dashboard screenshot
- Test alert email/screenshot

**Gate Status:** **NOT STARTED** (monitoring not yet configured)

---

#### Task 4.2: Implement Automated Backups

**Actions:**
- Create backup script: `/opt/gms/backup.sh`
- Configure cron job: Daily at 2 AM
- Test backup and restore procedure
- (Optional) Configure off-site storage

**Backup Script (REVISED Revision 2):**
```bash
#!/bin/bash
# /opt/gms/backup.sh

set -euo pipefail

BACKUP_DIR="/backups"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$BACKUP_DIR/gms_backup_$TIMESTAMP.sql.gz"

# Load environment
source /etc/gms/.env

# Create backup directory
mkdir -p $BACKUP_DIR
chmod 700 $BACKUP_DIR

# Perform backup using PostgreSQL connection parameters (NOT JDBC URL)
echo "Starting backup at $(date)"
export PGPASSWORD="$PG_PASSWORD"
pg_dump -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DATABASE" \
  --format=custom --compress=6 | gzip > "$BACKUP_FILE"
unset PGPASSWORD

# Verify backup integrity
gzip -t "$BACKUP_FILE" || {
  echo "ERROR: Backup integrity check failed" >&2
  rm -f "$BACKUP_FILE"
  exit 1
}

# Set secure permissions
chmod 600 "$BACKUP_FILE"

echo "Backup completed: $BACKUP_FILE"

# Get backup size
BACKUP_SIZE=$(du -h "$BACKUP_FILE" | cut -f1)
echo "Backup size: $BACKUP_SIZE"

# Retention (30 days)
echo "Removing backups older than 30 days"
find $BACKUP_DIR -name "gms_backup_*.sql.gz" -mtime +30 -delete

# List remaining backups
echo "Remaining backups:"
ls -lh $BACKUP_DIR/gms_backup_*.sql.gz

echo "Backup process completed at $(date)"
```

**⚠️ CRITICAL CORRECTIONS (Revision 2):**
- Uses PostgreSQL connection parameters (`-h`, `-p`, `-U`, `-d`), NOT JDBC URL
- Credentials passed via `PGPASSWORD` environment variable, NOT command-line arguments
- `PGPASSWORD` unset immediately after use to prevent credential leakage
- Backup integrity verified with `gzip -t`
- Secure file permissions (0600) and directory permissions (0700) enforced

**Cron Job:**
```bash
# /etc/cron.d/gms-backup
0 2 * * * root /opt/gms/backup.sh >> /var/log/gms-backup.log 2>&1
```

**Acceptance Criteria:**
- [ ] Backup script created and executable
- [ ] Cron job configured
- [ ] Manual backup test successful
- [ ] Backup file created in `/backups/`
- [ ] Restore procedure tested and documented
- [ ] (Optional) Off-site storage configured

**Evidence Required:**
- Backup script content
- `crontab -l` output
- Backup log output
- Restore test log

**Security Gates:**
- [ ] Backup file permissions: 0600
- [ ] Backup directory permissions: 0700
- [ ] Database credentials not logged
- [ ] Backup encrypted at rest (if off-site)

**Gate Status:** **NOT STARTED** (backups not yet implemented)

---

#### Task 4.2.1: Restore Procedure and Acceptance Gate (NEW Revision 2)

**⚠️ CRITICAL ADDITION (Revision 2):**
Backup without tested restore is not disaster recovery. This gate validates that backups can be restored within acceptable time bounds.

**Restore Script:**
```bash
#!/bin/bash
# /opt/gms/restore.sh

set -euo pipefail

BACKUP_FILE="$1"

if [ -z "$BACKUP_FILE" ]; then
  echo "Usage: $0 <backup-file.sql.gz>" >&2
  exit 1
fi

if [ ! -f "$BACKUP_FILE" ]; then
  echo "ERROR: Backup file not found: $BACKUP_FILE" >&2
  exit 1
fi

# Load environment
source /etc/gms/.env

# Verify backup integrity before restore
echo "Verifying backup integrity..."
gzip -t "$BACKUP_FILE" || {
  echo "ERROR: Backup integrity check failed" >&2
  exit 1
}

# Record restore start time
RESTORE_START=$(date +%s)

echo "Starting restore from: $BACKUP_FILE"

# Stop application (optional, depends on migration strategy)
# systemctl stop gms-backend

# Drop and recreate database (DANGEROUS — confirm before running)
echo "WARNING: This will drop the existing database. Press Ctrl+C to abort."
read -t 10 -p "Continuing in 10 seconds..." || true

export PGPASSWORD="$PG_PASSWORD"
dropdb -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" --if-exists "$PG_DATABASE"
createdb -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" "$PG_DATABASE"

# Restore backup
gunzip -c "$BACKUP_FILE" | pg_restore -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DATABASE" \
  --format=custom --no-owner --no-privileges --exit-on-error

unset PGPASSWORD

# Record restore end time
RESTORE_END=$(date +%s)
RESTORE_DURATION=$((RESTORE_END - RESTORE_START))

echo "Restore completed in ${RESTORE_DURATION} seconds"

# Verify database connectivity
echo "Verifying database connectivity..."
psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DATABASE" -c "SELECT 1;" || {
  echo "ERROR: Database verification failed" >&2
  exit 1
}

# Restart application
# systemctl start gms-backend

echo "Restore procedure completed successfully"
echo "RTO observed: ${RESTORE_DURATION} seconds"
```

**Restore Acceptance Gate:**
- [ ] Restore script created and executable
- [ ] Restore tested on fresh database (not production)
- [ ] Restore duration measured and recorded (RTO = ? seconds)
- [ ] Data integrity verified after restore (row counts, checksums)
- [ ] Application connectivity verified after restore
- [ ] Restore procedure documented in runbook

**Evidence Required:**
- Restore script content
- Restore test log with timestamps
- RTO measurement: `_____` seconds
- Data verification query results
- Application health check after restore

**⚠️ RTO Status:**
- **RTO (Recovery Time Objective):** 🔍 **UNVERIFIED**
- **Reason:** No timed restore drill has been performed
- **Action Required:** Execute restore test and record duration
- **Target:** RTO < 1 hour (owner decision pending)

**Gate Status:** **NOT STARTED** (restore not yet tested)

---

#### Task 4.3: Set Up CI/CD Pipeline (REVISED Revision 3)

**Actions:**
- Create production deployment workflow with `workflow_dispatch` trigger
- Add feature-branch workflow (NEVER deploy on push to master)
- Configure SSH deployment
- Test automated deployment

**⚠️ CRITICAL CHANGE (Revision 3):**
Production deployment requires **explicit owner approval** via `workflow_dispatch`. The Revision 2 workflow used `push: branches: [master]`, which would deploy automatically on every merge — bypassing owner approval. This is NOT acceptable.

**Verified GitHub Action SHAs (from existing `.github/workflows/ci.yml`):**
- `actions/checkout@11d5960a326750d5838078e36cf38b85af677262 # v4.4.0`
- `actions/setup-java@cf277c60eb25467037889841efdb720020 # v4.9.1`
- `actions/setup-node@49933ea5288caeca8642d1e84afbd3f7d6820020 # v4.4.0`

**Note:** The following action SHAs used in Revision 2 were **NOT verified** from the existing ci.yml and must be independently confirmed before use:
- `docker/setup-buildx-action` — SHA unverified
- `docker/login-action` — SHA unverified
- `docker/build-push-action` — SHA unverified
- `appleboy/ssh-action` — SHA unverified

**GitHub Actions Workflow (REVISED v3.0 — Feature-Branch + Manual Trigger):**
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
        description: 'Git commit SHA to deploy (must be on master)'
        required: true
        type: string

# Restrict default permissions (least-privilege)
permissions:
  contents: read
  packages: write

concurrency:
  group: production-deployment
  cancel-in-progress: false  # Queue deployments, don't cancel

jobs:
  deploy:
    runs-on: ubuntu-latest
    environment: ${{ inputs.environment }}  # Protected environment requires approval
    
    steps:
      - name: Checkout code at specified SHA
        uses: actions/checkout@11d5960a326750d5838078e36cf38b85af677262 # v4.4.0
        with:
          ref: ${{ inputs.git_sha }}
      
      - name: Verify commit is on master
        run: |
          git fetch origin master
          if ! git merge-base --is-ancestor ${{ inputs.git_sha }} origin/master; then
            echo "ERROR: Commit ${{ inputs.git_sha }} is not on master branch"
            exit 1
          fi
          echo "Commit verified on master branch"
      
      - name: Set up JDK 21
        uses: actions/setup-java@cf277c60eb25467037889841efdb720020 # v4.9.1
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Build backend
        run: |
          cd gms_backend
          mvn clean package -DskipTests
      
      - name: Build and push Docker image
        run: |
          echo ${{ secrets.GITHUB_TOKEN }} | docker login ghcr.io -u ${{ github.repository_owner }} --password-stdin
          docker build -t ghcr.io/${{ github.repository_owner }}/gms-backend:${{ inputs.git_sha }} gms_backend/
          docker push ghcr.io/${{ github.repository_owner }}/gms-backend:${{ inputs.git_sha }}
      
      - name: Extract migration files for deployment
        uses: appleboy/ssh-action@NOT-VERIFIED # UNVERIFIED SHA
        with:
          host: ${{ secrets.OCI_HOST }}
          username: ${{ secrets.OCI_USERNAME }}
          key: ${{ secrets.OCI_SSH_KEY }}
          script: |
            mkdir -p /opt/gms/migrations/${{ inputs.git_sha }}
            # Migration files from same commit as application image
            scp gms_backend/src/main/resources/db/migration/*.sql \
              /opt/gms/migrations/${{ inputs.git_sha }}/
            echo "${{ inputs.git_sha }}" > /opt/gms/current-deployment-sha.txt
      
      - name: Run database migrations (deployment step)
        uses: appleboy/ssh-action@NOT-VERIFIED # UNVERIFIED SHA
        with:
          host: ${{ secrets.OCI_HOST }}
          username: ${{ secrets.OCI_USERNAME }}
          key: ${{ secrets.OCI_SSH_KEY }}
          script: |
            /opt/gms/migrate.sh
      
      - name: Deploy to OCI instance
        uses: appleboy/ssh-action@NOT-VERIFIED # UNVERIFIED SHA
        with:
          host: ${{ secrets.OCI_HOST }}
          username: ${{ secrets.OCI_USERNAME }}
          key: ${{ secrets.OCI_SSH_KEY }}
          script: |
            cd /opt/gms
            docker compose -f docker-compose.prod.yml pull backend
            docker compose -f docker-compose.prod.yml up -d backend
      
      - name: Verify deployment health
        uses: appleboy/ssh-action@NOT-VERIFIED # UNVERIFIED SHA
        with:
          host: ${{ secrets.OCI_HOST }}
          username: ${{ secrets.OCI_USERNAME }}
          key: ${{ secrets.OCI_SSH_KEY }}
          script: |
            sleep 15
            curl -sf http://127.0.0.1:8080/actuator/health || exit 1
            echo "Deployment verified healthy"
```

**CI/CD Hardening Checklist:**
- [ ] Workflow uses `workflow_dispatch` trigger (NOT `push` to master)
- [ ] Owner must manually trigger deployment and specify git_sha
- [ ] Workflow verifies commit is on master before deploying
- [ ] All Actions pinned to immutable commit SHAs (verified from ci.yml)
- [ ] Container images tagged with git SHA only (no `:latest` tag)
- [ ] Protected `production` environment configured in GitHub with manual approval
- [ ] Concurrency group prevents parallel deployments
- [ ] Least-privilege permissions (`contents: read`, `packages: write`)
- [ ] Deployment credentials use least-privilege SSH key (no root access)
- [ ] No secrets printed to workflow logs
- [ ] Migration runs BEFORE application restart (deployment-step, not app-startup)
- [ ] Migration files extracted from same commit as application image
- [ ] Health verification step after deployment
- [ ] Rollback procedure: `workflow_dispatch` with previous known-good SHA
- [ ] GitHub Actions minutes usage verified against account limits
- [ ] GHCR storage usage verified against account limits

**Feature-Branch Release Sequence:**
1. Fetch latest `master`
2. Create feature branch (`feature/GMS-XXX-description`)
3. Implement changes, run tests locally
4. Push feature branch to GitHub
5. Create pull request; CI runs automatically (ci.yml)
6. Code review and approval
7. Merge to master (all CI gates must pass)
8. **Owner triggers production deployment via `workflow_dispatch`** with merged commit SHA
9. Deployment workflow verifies SHA is on master, then deploys

**Acceptance Criteria:**
- [ ] GitHub Actions workflow created with `workflow_dispatch` trigger
- [ ] All action SHAs verified from official sources
- [ ] Secrets configured (OCI_HOST, OCI_USERNAME, OCI_SSH_KEY)
- [ ] Protected environment requires owner approval
- [ ] Automated deployment working (manual trigger → deploy)
- [ ] Migration runs as separate deployment step via Docker container
- [ ] Health check passes after deployment
- [ ] Rollback to previous SHA tested (via workflow_dispatch)

**Evidence Required:**
- GitHub Actions workflow file with verified SHA pins
- GitHub protected environment configuration screenshot
- GitHub secrets screenshot (redacted)
- Deployment log output showing: trigger → verify → migrate → deploy → health check
- Rollback test log

**Security Gates:**
- [ ] SSH key stored securely (GitHub Secrets)
- [ ] Deployment user has minimal privileges (no root)
- [ ] No secrets in workflow logs
- [ ] Owner must manually trigger each production deployment
- [ ] Actions pinned to verified immutable SHAs (supply chain protection)
- [ ] Actuator health restricted to localhost (127.0.0.1)
- [ ] Commit SHA verified to be on master before deployment

**Gate Status:** **NOT STARTED** (CI/CD v3.0 workflow not yet implemented)

---

#### Task 4.4: Document Procedures

**Actions:**
- Create operations runbook
- Create disaster recovery plan
- Document rollback procedures
- Create troubleshooting guide

**Acceptance Criteria:**
- [ ] Operations runbook created
- [ ] Disaster recovery plan created
- [ ] Rollback procedures documented
- [ ] Troubleshooting guide created
- [ ] All procedures tested

**Evidence Required:**
- Documentation files (Markdown)
- Test logs for each procedure

**Gate Status:** **NOT STARTED** (documentation not yet created)

**GO/NO-GO Decision:**
- **GO** if all acceptance criteria met and procedures tested
- **NO-GO** if any procedure untested or documentation incomplete

---

## Phase 5: Production Launch

**Duration:** 2–3 days  
**Prerequisites:** Phases 1–4 complete  
**Owner:** Deployment team + project owner

### 5.1 Objectives

1. Final security review
2. Performance testing
3. User acceptance testing
4. Production launch
5. Post-launch monitoring

### 5.2 Tasks

#### Task 5.1: Final Security Review (REVISED Revision 3)

**Actions:**
- Review firewall rules
- Review SSL configuration
- Review environment variables
- Review CORS configuration
- Review superadmin account
- Review JWT token storage and XSS mitigations
- Review actuator endpoint exposure
- Review secret rotation procedure
- (Optional) Run security scan

**Acceptance Criteria:**
- [ ] Firewall rules verified (only 22, 80, 443 open)
- [ ] SSL configuration verified (TLS 1.2+, strong ciphers)
- [ ] Environment variables secured (no hardcoded secrets)
- [ ] CORS configuration verified (production domain only)
- [ ] Superadmin password changed from default
- [ ] 🚫 **BLOCKED:** JWT localStorage XSS risk — MUST migrate to HttpOnly cookie before production
- [ ] ⚠️ Actuator endpoints restricted to localhost (NOT publicly accessible)
- [ ] ⚠️ Secret rotation procedure documented
- [ ] ⚠️ Role authorization verified for all 6 roles with negative tests
- [ ] (Optional) Security scan completed

**🚫 PRODUCTION BLOCKER (Revision 3):**
1. **JWT in localStorage:** This is a **PRODUCTION BLOCKER**, not an owner-waivable risk. CSP headers reduce but do NOT eliminate XSS token theft. **Must migrate to HttpOnly cookie + CSRF before production launch.** See Decision Log D-011 for recommended migration path.

**⚠️ SECURITY CORRECTIONS (still applicable from Revision 2):**
2. **Actuator exposure:** `/actuator/health` and all actuator endpoints must be restricted to localhost via Nginx config. NEVER expose publicly.
3. **CSP limitations:** Content-Security-Policy reduces XSS attack surface but does NOT make localStorage safe. Do not claim otherwise.
4. **CSRF:** Current JWT-based auth (localStorage + Bearer header) is NOT vulnerable to CSRF. However, when migrating to HttpOnly cookie auth, CSRF protection **MUST** be added.
5. **Secret rotation:** No procedure currently exists. Must be documented before production launch.
6. **Role authorization:** UNVERIFIED for all 6 roles. Comprehensive negative tests required.

**Evidence Required:**
- `ufw status` output
- SSL Labs test results (https://www.ssllabs.com/ssltest/)
- Environment file permissions
- CORS configuration
- Superadmin account settings
- CSP header configuration
- Nginx config showing actuator restricted to localhost
- Secret rotation procedure document
- Role authorization test results (all 6 roles)
- **JWT authentication migration proof** (HttpOnly cookie implementation)

**Security Gates:**
- [ ] No critical vulnerabilities found
- [ ] All security headers present (CSP, X-Content-Type-Options, X-Frame-Options, HSTS)
- [ ] No hardcoded secrets
- [ ] Tenant isolation verified
- [ ] Actuator NOT publicly accessible
- [ ] 🚫 **JWT migrated from localStorage to HttpOnly cookie** (PRODUCTION BLOCKER)
- [ ] CSRF protection implemented (required when using cookie-based auth)
- [ ] Secret rotation procedure documented
- [ ] Role authorization verified for all 6 web roles

**Gate Status:** **BLOCKED** (JWT localStorage is a production blocker; must migrate to HttpOnly cookie before this gate can pass)

---

#### Task 5.2: Performance Testing

**Actions:**
- Load testing (optional)
- Response time benchmarks
- Resource utilization monitoring
- Database query optimization

**Acceptance Criteria:**
- [ ] API response time < 1 second (95th percentile)
- [ ] Frontend load time < 3 seconds (95th percentile)
- [ ] Database query time < 500ms (95th percentile)
- [ ] CPU usage < 80% (sustained)
- [ ] Memory usage < 80% (sustained)
- [ ] Disk usage < 80%

**Evidence Required:**
- Load test results (if performed)
- Response time benchmarks
- Resource utilization graphs
- Database query logs

**Performance Gates:**
- [ ] No performance bottlenecks identified
- [ ] System stable under load
- [ ] Database queries optimized

**Gate Status:** **NOT STARTED** (performance testing not yet performed)

---

#### Task 5.3: User Acceptance Testing

**Actions:**
- Create test accounts (all roles)
- Test core workflows
- Test role-based access
- Test error scenarios
- Collect feedback

**Acceptance Criteria:**
- [ ] Test accounts created (all roles)
- [ ] Core workflows tested (login, student registration, attendance, grades, fees)
- [ ] Role-based access verified
- [ ] Error scenarios tested
- [ ] Feedback collected and documented

**Evidence Required:**
- Test account credentials (securely stored)
- Test case results
- Bug reports (if any)
- User feedback

**Gate Status:** **NOT STARTED** (UAT not yet performed performed)

---

#### Task 5.4: Production Launch

**Actions:**
- Announce production launch
- Monitor launch closely
- Verify functionality
- Provide support

**Acceptance Criteria:**
- [ ] Production launch announced
- [ ] Launch monitored (no critical issues)
- [ ] Functionality verified (all core workflows working)
- [ ] Support provided (responsive to issues)

**Evidence Required:**
- Launch announcement (email, Slack, etc.)
- Monitoring dashboard screenshot
- Functionality test results
- Support ticket log (if any)

**Gate Status:** **NOT STARTED** (launch not yet performed)

---

#### Task 5.5: Post-Launch Monitoring

**Actions:**
- Daily monitoring (first week)
- Weekly reviews (first month)
- Monthly maintenance
- Quarterly disaster recovery drill

**Acceptance Criteria:**
- [ ] Daily monitoring established
- [ ] Weekly reviews scheduled
- [ ] Monthly maintenance procedures documented
- [ ] Quarterly DR drill scheduled

**Evidence Required:**
- Monitoring dashboard screenshot
- Review schedule
- Maintenance procedures document
- DR drill schedule

**Gate Status:** **NOT STARTED** (post-launch monitoring not yet established)

**GO/NO-GO Decision:**
- **GO** if all acceptance criteria met and no critical issues
- **NO-GO** if any critical issues found or system unstable

---

## Final GO/NO-GO Decision

### GO Criteria

All of the following must be true:
1. ✅ All phases completed successfully
2. ✅ All security gates passed
3. ✅ All performance gates passed
4. ✅ All infrastructure gates passed
5. ✅ Owner decisions made (D-001, D-002, D-004, D-009)
6. ✅ All acceptance criteria met
7. ✅ All evidence provided
8. ✅ All procedures documented and tested
9. ✅ No critical vulnerabilities
10. ✅ System stable under load

### NO-GO Criteria

Any of the following triggers NO-GO:
1. ❌ Any security gate failed
2. ❌ Any critical vulnerability found
3. ❌ System unstable under load
4. ❌ Owner decisions not made
5. ❌ Critical acceptance criteria not met
6. ❌ Evidence not provided
7. ❌ Procedures not tested
8. ❌ Tenant isolation not verified
9. ❌ Data loss risk identified
10. ❌ Compliance violation identified
11. 🚫 **JWT localStorage not migrated to HttpOnly cookie** (PRODUCTION BLOCKER)
12. 🚫 **Flyway migration mechanism not tested with Docker container** (was: invalid `java -jar` command)

### Decision Authority

- **GO Decision:** Project owner + deployment team lead
- **NO-GO Decision:** Any team member can trigger NO-GO
- **Escalation:** Disagreements escalated to project owner

---

## Summary

**Total Phases:** 6 (Phase 0–5)  
**Total Duration:** 11–17 days (excluding buffer)  
**Total Effort:** 80–120 hours (10–15 full working days)

**Critical Path:**
1. Phase 0: Owner decisions (1–2 days)
2. Phase 1: Infrastructure provisioning (2–3 days)
3. Phase 2: Backend deployment (2–3 days)
4. Phase 3: Frontend deployment (1–2 days)
5. Phase 4: Monitoring and CI/CD (3–4 days)
6. Phase 5: Production launch (2–3 days)

**Risk Mitigation:**
- Each phase has rollback procedure
- Security gates at each phase
- Performance gates at each phase
- Evidence required for each gate
- GO/NO-GO decision at each phase

**Success Metrics:**
- Zero critical vulnerabilities
- 99.5% uptime (first month)
- API response time < 1 second
- Frontend load time < 3 seconds
- Zero data loss
- Zero security incidents

---

**Document Version:** 5.0 (Revision 5 — Final Reconciliation and Correction)  
**Last Updated:** 2026-11-03  
**Next Review:** After owner decisions and technical verification complete  
**Status:** NO-GO — PRODUCTION BLOCKERS UNRESOLVED

**Revision 4 Changes:**
- Task 2.2.1: Aligned with canonical Flyway migration procedure (flock-based locking, environment validation, temporary credentials file)
- Task 4.3: Documented complete artifact transfer chain, marked unverified GitHub Action SHAs as REQUIRES_VERIFICATION
- Task 1.2: Removed public /actuator/health exposure, added separate /health endpoint for monitoring
- Task 4.2: Added pg_restore --list validation for backup integrity
- Task 4.2.1: Restore testing now targets isolated database (not production)
- Task 1.3: Corrected TLS bootstrap sequence (HTTP-first, then HTTPS after certificate)
- All gates: Consistent 5-state system (NOT STARTED / IN PROGRESS / VERIFIED / BLOCKED / NOT APPLICABLE)
- Overall status: NO-GO (not CONDITIONAL GO)

**Revision 5 Changes:**
- CI/CD action SHAs marked NOT VERIFIED (were placeholder values)
- `latest` tag usage flagged as prohibited for production
- TLS bootstrap sequence documented (HTTP-first → certificates → HTTPS)
- Gate status definitions updated to Revision 5
- Updated version to 5.0
