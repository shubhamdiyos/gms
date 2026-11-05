# GMS Stage J — Production Architecture Recommendation

**Date:** 2026-11-03  
**Author:** Principal DevOps Engineer, Cloud Architect  
**Repository:** GMS (test/playwright-e2e branch)  
**Purpose:** Recommend a simple, reliable, cost-effective production architecture

---

## Executive Summary

This document recommends a **production architecture** for GMS that balances **simplicity, reliability, and cost-effectiveness**. The recommendation is based on:

1. **Existing infrastructure audit** (see `GMS_STAGE_J_INFRASTRUCTURE_AUDIT.md`)
2. **Pilot scale requirements** (~2,000 students)
3. **Zero-cost preference** (free-tier hosting where possible)
4. **Modular monolith architecture** (no microservices, no Kubernetes)
5. **Team capacity** (solo developer, limited DevOps time)

**Recommended Architecture:**
- **Frontend:** Static hosting on Vercel (free tier) or Cloudflare Pages (free tier)
- **Backend:** Single VPS (Virtual Private Server) with Docker Compose
- **Database:** Neon PostgreSQL (free tier) or managed PostgreSQL on same VPS
- **SSL/TLS:** Let's Encrypt with automatic renewal (free)
- **CDN:** Cloudflare (free tier) for static assets and DDoS protection
- **Monitoring:** UptimeRobot (free tier) + basic logging

**Estimated Monthly Cost:** $0–$12/month (depending on hosting choice)

---

## 1. Architecture Principles

### 1.1 Design Constraints

1. **Simplicity over sophistication**
   - No Kubernetes (overkill for 2,000 students)
   - No microservices (modular monolith is sufficient)
   - No multi-region deployment (single region is adequate for pilot)

2. **Cost minimization**
   - Prefer free-tier services
   - Avoid paid services without explicit approval
   - Target <$20/month total infrastructure cost

3. **Reliability over performance**
   - 99.5% uptime target (not 99.99%)
   - Single points of failure acceptable for pilot
   - Manual recovery procedures acceptable

4. **Maintainability**
   - Infrastructure as Code (IaC) where possible
   - Automated deployments from CI/CD
   - Clear documentation for solo operator

### 1.2 Architecture Decisions

**Decision 1: Single VPS vs. Managed Services**

| Option | Pros | Cons | Recommendation |
|--------|------|------|----------------|
| Single VPS | Simple, full control, predictable cost | Manual maintenance, single point of failure | **YES** for pilot |
| Managed Kubernetes | Scalable, HA, automated | Complex, expensive, overkill | NO |
| Serverless (Lambda/Functions) | Auto-scaling, pay-per-use | Cold starts, vendor lock-in, complex | NO |
| PaaS (Heroku/Render) | Easy deployment, managed | Expensive at scale, limited control | NO |

**Rationale:** A single VPS with Docker Compose is the simplest path to production for a solo developer. The pilot scale (2,000 students) does not justify the complexity or cost of managed services.

**Decision 2: Database Hosting**

| Option | Pros | Cons | Recommendation |
|--------|------|------|----------------|
| Neon PostgreSQL (free tier) | Managed, automated backups, serverless | 0.5 GB storage limit, cold starts | **YES** for pilot |
| Self-managed on VPS | Full control, no limits | Manual backups, maintenance overhead | Alternative |
| AWS RDS / DigitalOcean Managed | Reliable, automated | $15+/month, overkill | NO |

**Rationale:** Neon's free tier provides 0.5 GB storage, automated backups, and serverless scaling. For 2,000 students with moderate data volume, this is sufficient for the pilot phase. If storage limits are reached, migrate to self-managed PostgreSQL on the same VPS.

**Decision 3: Frontend Hosting**

| Option | Pros | Cons | Recommendation |
|--------|------|------|----------------|
| Vercel (free tier) | Fast CDN, automatic HTTPS, preview deployments | 100 GB bandwidth limit | **YES** |
| Cloudflare Pages (free tier) | Unlimited bandwidth, fast CDN | Fewer integrations | Alternative |
| Same VPS as backend | Simple, single server | No CDN, slower global access | NO |

**Rationale:** Vercel's free tier provides excellent performance, automatic HTTPS, and preview deployments for PRs. The 100 GB/month bandwidth limit is sufficient for 2,000 students.

**Decision 4: SSL/TLS Termination**

| Option | Pros | Cons | Recommendation |
|--------|------|------|----------------|
| Vercel (frontend) | Automatic, free, managed | Only for frontend | **YES** for frontend |
| Let's Encrypt on VPS | Free, automated, full control | Requires renewal setup | **YES** for backend API |
| Cloudflare proxy | Free, DDoS protection, CDN | Another layer of complexity | Alternative |

**Rationale:** Use Vercel's automatic HTTPS for the frontend. For the backend API on the VPS, use Let's Encrypt with Certbot for automatic certificate renewal.

---

## 2. Recommended Architecture

### 2.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Internet                              │
└────────────────┬────────────────────────────────────────────┘
                 │
                 │ HTTPS (443)
                 │
        ┌────────▼────────┐
        │   Cloudflare    │  (Optional: DDoS protection, CDN)
        │   (Free Tier)   │
        └────────┬────────┘
                 │
        ┌────────┴────────┐
        │                 │
        │                 │
┌───────▼──────┐  ┌──────▼───────┐
│   Vercel     │  │  VPS (Single)│
│  (Frontend)  │  │              │
│              │  │  ┌────────┐  │
│  React SPA   │  │  │ Nginx  │  │
│  Static Host │  │  │ (80/   │  │
│              │  │  │  443)  │  │
└──────────────┘  │  └────┬───┘  │
                  │       │      │
                  │       ▼      │
                  │  ┌────────┐  │
                  │  │Backend │  │
                  │  │Container│ │
                  │  │(8080)  │  │
                  │  └────┬───┘  │
                  │       │      │
                  │       ▼      │
                  │  ┌────────┐  │
                  │  │Neon PG │  │
                  │  │(Remote)│  │
                  │  └────────┘  │
                  └──────────────┘
```

### 2.2 Component Breakdown

#### Frontend: Vercel (Free Tier)

**Service:** Vercel Hobby Plan  
**Cost:** $0/month  
**Features:**
- Automatic HTTPS (Let's Encrypt)
- Global CDN (fast worldwide)
- Preview deployments for PRs
- 100 GB/month bandwidth
- Automatic builds from Git

**Configuration:**
```json
// vercel.json
{
  "buildCommand": "npm run build",
  "outputDirectory": "dist",
  "installCommand": "npm ci",
  "framework": "vite",
  "rewrites": [
    { "source": "/api/:path*", "destination": "https://api.yourdomain.com/api/:path*" }
  ]
}
```

**Deployment:**
- Connect GitHub repository to Vercel
- Automatic deployments on push to `master`
- Preview deployments for pull requests
- Custom domain configuration

**Why Vercel?**
- Zero cost for pilot scale
- Excellent performance (global CDN)
- Automatic HTTPS (no certificate management)
- Preview deployments improve code review workflow

#### Backend: Single VPS with Docker Compose

**Service:** DigitalOcean Droplet / Hetzner Cloud / Linode  
**Cost:** $6–$12/month (1 GB RAM, 1 vCPU, 25 GB SSD)  
**Specifications:**
- 1 vCPU (2 vCPU recommended for production)
- 1 GB RAM (2 GB recommended)
- 25 GB SSD storage
- 1 TB/month bandwidth
- Ubuntu 22.04 LTS

**Deployment Model:**
```yaml
# docker-compose.prod.yml
version: '3.8'

services:
  backend:
    image: ghcr.io/yourorg/gms-backend:${GIT_SHA}
    container_name: gms-backend
    restart: unless-stopped
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DB_URL=jdbc:postgresql://neon-db:5432/gms
      - DB_USERNAME=${DB_USERNAME}
      - DB_PASSWORD=${DB_PASSWORD}
      - SECURITY_JWT_SECRET=${JWT_SECRET}
      - CORS_ALLOWED_ORIGINS=https://yourdomain.com
      - APP_SUPERADMIN_ENABLED=false
    ports:
      - "127.0.0.1:8080:8080"
    networks:
      - gms-network
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  nginx:
    image: nginx:alpine
    container_name: gms-nginx
    restart: unless-stopped
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
      - ./ssl:/etc/nginx/ssl:ro
    ports:
      - "80:80"
      - "443:443"
    depends_on:
      - backend
    networks:
      - gms-network

networks:
  gms-network:
    driver: bridge
```

**Why Docker Compose on VPS?**
- Simple to manage (single server)
- Full control over configuration
- Predictable cost ($6–$12/month)
- Easy to backup and restore
- No vendor lock-in

#### Database: Neon PostgreSQL (Free Tier)

**Service:** Neon Serverless PostgreSQL  
**Cost:** $0/month (free tier)  
**Features:**
- 0.5 GB storage
- Automated daily backups
- Point-in-time recovery (7 days)
- Serverless scaling (auto-pause when idle)
- Connection pooling (built-in PgBouncer)

**Configuration:**
```
DB_URL=jdbc:postgresql://ep-cool-name-123456.us-east-2.aws.neon.tech/gms?sslmode=require
DB_USERNAME=neon_user
DB_PASSWORD=${NEON_PASSWORD}
```

**Why Neon?**
- Free tier sufficient for pilot (2,000 students)
- Automated backups (no manual pg_dump scripts)
- Serverless scaling (cost-effective for variable load)
- PostgreSQL 15 compatible (matches development environment)
- No connection management needed (built-in pooling)

**Limitations:**
- 0.5 GB storage limit (monitor usage)
- Cold starts after 5 minutes of inactivity (acceptable for pilot)
- No read replicas (single point of failure, acceptable for pilot)

**Migration Path:**
If storage limits are reached:
1. Upgrade to Neon paid plan ($19/month for 10 GB)
2. Migrate to self-managed PostgreSQL on VPS
3. Use AWS RDS or DigitalOcean Managed Database ($15+/month)

#### SSL/TLS: Let's Encrypt with Certbot

**Service:** Let's Encrypt (Free)  
**Cost:** $0/month  
**Tool:** Certbot (automatic renewal)

**Configuration:**
```bash
# Install Certbot
sudo apt update
sudo apt install certbot python3-certbot-nginx

# Obtain certificate
sudo certbot --nginx -d api.yourdomain.com

# Automatic renewal (cron job)
sudo certbot renew --dry-run
```

**Nginx SSL Configuration:**
```nginx
server {
    listen 443 ssl http2;
    server_name api.yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/api.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.yourdomain.com/privkey.pem;

    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;

    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}

server {
    listen 80;
    server_name api.yourdomain.com;
    return 301 https://$server_name$request_uri;
}
```

**Why Let's Encrypt?**
- Free SSL certificates
- Automatic renewal (no manual intervention)
- Widely trusted (all browsers)
- Industry standard

#### Monitoring: UptimeRobot + Basic Logging

**Service 1:** UptimeRobot (Free Tier)  
**Cost:** $0/month  
**Features:**
- 50 monitors (free)
- 5-minute check interval
- Email alerts
- Public status page

**Configuration:**
- Monitor `https://api.yourdomain.com/actuator/health/liveness`
- Monitor `https://yourdomain.com` (frontend)
- Alert on 3 consecutive failures

**Service 2:** Basic Logging (VPS-local)  
**Cost:** $0/month (included with VPS)  
**Tool:** journald + log files

**Configuration:**
```bash
# View backend logs
docker logs gms-backend --follow

# View nginx logs
docker logs gms-nginx --follow

# Log rotation (already configured by Docker)
```

**Why UptimeRobot?**
- Free tier sufficient for pilot
- Simple setup (no infrastructure to manage)
- Reliable alerting (email/SMS)
- Public status page for transparency

**Future Enhancement:**
If more sophisticated monitoring is needed:
1. Add Prometheus + Grafana (self-hosted, free)
2. Use Datadog (paid, comprehensive)
3. Use New Relic (free tier available)

### 2.3 Network Architecture

#### Traffic Flow

```
User Browser
    ↓
HTTPS (443)
    ↓
Vercel CDN (Frontend)
    ↓
/api/* requests
    ↓
VPS Nginx (Reverse Proxy)
    ↓
Backend Container (8080)
    ↓
Neon PostgreSQL (5432)
```

#### Firewall Configuration

**VPS Firewall (ufw):**
```bash
# Allow SSH
sudo ufw allow 22/tcp

# Allow HTTP (redirect to HTTPS)
sudo ufw allow 80/tcp

# Allow HTTPS
sudo ufw allow 443/tcp

# Enable firewall
sudo ufw enable
```

**Backend Container:**
- Exposed only on `127.0.0.1:8080` (not accessible from internet)
- Nginx proxies requests to backend

**Database:**
- Neon PostgreSQL (remote, managed)
- No direct database access from internet
- Connection via SSL only

### 2.4 Security Architecture

#### Authentication and Authorization

```
User → Vercel Frontend → /api/v1/auth/login
    ↓
Backend validates credentials
    ↓
JWT token issued (signed with SECURITY_JWT_SECRET)
    ↓
Frontend stores token in localStorage
    ↓
Subsequent requests include Authorization: Bearer <token>
    ↓
Backend validates JWT on every request
```

#### CORS Configuration

**Backend (application-prod.properties):**
```properties
cors.allowed-origins=https://yourdomain.com
```

**Frontend (Vercel):**
- Same-origin requests to `/api/*` proxied to backend
- No cross-origin issues

#### Secrets Management

**Environment Variables:**
```bash
# .env file on VPS (not committed to Git)
DB_USERNAME=neon_user
DB_PASSWORD=super_secret_password
JWT_SECRET=openssl_rand_base64_48_output
SUPERADMIN_PASSWORD=initial_admin_password
```

**Secret Storage:**
- VPS: `.env` file with restricted permissions (`chmod 600 .env`)
- Vercel: Environment variables in dashboard
- Neon: Connection string in dashboard

**Secret Rotation:**
- JWT secret: Rotate quarterly (invalidates all tokens, users must re-login)
- Database password: Rotate annually (update `.env` and restart backend)
- Superadmin password: Change immediately after first login

---

## 3. Hosting Options Comparison

### 3.1 Frontend Hosting

| Provider | Free Tier Limits | Cost (Overage) | Pros | Cons | Recommendation |
|----------|------------------|----------------|------|------|----------------|
| **Vercel** | 100 GB bandwidth, 100 GB-hours serverless | $20/100 GB bandwidth | Fast CDN, preview deployments, automatic HTTPS | Bandwidth limit | **YES** |
| **Cloudflare Pages** | Unlimited bandwidth, 500 builds/month | Free | Unlimited bandwidth, fast CDN, DDoS protection | Fewer integrations | Alternative |
| **Netlify** | 100 GB bandwidth, 300 build minutes | $19/100 GB bandwidth | Similar to Vercel | Slightly slower CDN | NO |
| **GitHub Pages** | 100 GB bandwidth | Free | Free, integrated with GitHub | No preview deployments, slower | NO |

**Decision:** Vercel (primary) or Cloudflare Pages (alternative)

### 3.2 Backend Hosting

| Provider | Free Tier | Cost (Pilot Scale) | Pros | Cons | Recommendation |
|----------|-----------|-------------------|------|------|----------------|
| **DigitalOcean Droplet** | None | $6/month (1 GB RAM) | Simple, predictable cost, full control | Manual maintenance | **YES** |
| **Hetzner Cloud** | None | €4.50/month (2 GB RAM) | Excellent price/performance, EU-based | Less documentation | Alternative |
| **Linode** | None | $5/month (1 GB RAM) | Good performance, Akamai CDN | Similar to DigitalOcean | Alternative |
| **Render** | 750 hours/month | $7/month (spinned down) | Managed, auto-deploy | Sleeps after 15 min | NO |
| **Railway** | $5 credit/month | $5–$20/month | Easy deployment | Expensive at scale | NO |
| **Fly.io** | 3 shared CPUs, 256 MB RAM | $0–$5/month | Global distribution, Docker-native | Complex pricing | NO |

**Decision:** DigitalOcean Droplet (primary) or Hetzner Cloud (alternative)

### 3.3 Database Hosting

| Provider | Free Tier Limits | Cost (Overage) | Pros | Cons | Recommendation |
|----------|------------------|----------------|------|------|----------------|
| **Neon PostgreSQL** | 0.5 GB storage, auto-pause | $0.50/GB/month | Serverless, automated backups, connection pooling | Cold starts, storage limit | **YES** |
| **Supabase** | 500 MB storage, 2 projects | $25/month (Pro) | PostgreSQL + auth + storage | Overkill for pilot | NO |
| **ElephantSQL** | 20 MB storage | $5/month (2 GB) | Simple, reliable | Very small free tier | NO |
| **Self-managed on VPS** | Unlimited | $0 (included) | Full control, no limits | Manual backups, maintenance | Alternative |
| **AWS RDS** | 12 months free (t2.micro) | $15+/month | Reliable, automated | Expensive, complex | NO |

**Decision:** Neon PostgreSQL (primary) or Self-managed on VPS (alternative)

### 3.4 Total Cost Analysis

#### Option A: Vercel + DigitalOcean + Neon (Recommended)

| Component | Cost | Notes |
|-----------|------|-------|
| Vercel (Frontend) | $0/month | Free tier sufficient |
| DigitalOcean Droplet (Backend) | $6/month | 1 GB RAM, 1 vCPU |
| Neon PostgreSQL (Database) | $0/month | Free tier (0.5 GB) |
| Let's Encrypt (SSL) | $0/month | Free certificates |
| UptimeRobot (Monitoring) | $0/month | Free tier (50 monitors) |
| **Total** | **$6/month** | **Well within budget** |

#### Option B: Cloudflare Pages + Hetzner + Neon (Alternative)

| Component | Cost | Notes |
|-----------|------|-------|
| Cloudflare Pages (Frontend) | $0/month | Free tier (unlimited bandwidth) |
| Hetzner Cloud (Backend) | €4.50/month | 2 GB RAM, 2 vCPU |
| Neon PostgreSQL (Database) | $0/month | Free tier (0.5 GB) |
| Let's Encrypt (SSL) | $0/month | Free certificates |
| UptimeRobot (Monitoring) | $0/month | Free tier (50 monitors) |
| **Total** | **€4.50/month (~$5)** | **Even cheaper** |

#### Option C: Vercel + DigitalOcean + Self-managed PostgreSQL (Fallback)

| Component | Cost | Notes |
|-----------|------|-------|
| Vercel (Frontend) | $0/month | Free tier sufficient |
| DigitalOcean Droplet (Backend + DB) | $12/month | 2 GB RAM, 1 vCPU |
| Self-managed PostgreSQL | $0/month | On same droplet |
| Let's Encrypt (SSL) | $0/month | Free certificates |
| UptimeRobot (Monitoring) | $0/month | Free tier (50 monitors) |
| **Total** | **$12/month** | **If Neon limits are reached** |

---

## 4. Deployment Architecture

### 4.1 CI/CD Pipeline

```
┌──────────────┐
│  Developer   │
│  pushes code │
└──────┬───────┘
       │
       ▼
┌──────────────────────────────┐
│  GitHub Actions (CI)         │
│  - Run backend tests         │
│  - Run frontend tests        │
│  - Build Docker images       │
│  - Push to GitHub Container  │
│    Registry (GHCR)           │
└──────┬───────────────────────┘
       │
       ├──────────────────┬──────────────────┐
       │                  │                  │
       ▼                  ▼                  ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│  Vercel      │  │  VPS         │  │  Neon        │
│  (Frontend)  │  │  (Backend)   │  │  (Database)  │
│              │  │              │  │              │
│  Automatic   │  │  Pull image  │  │  Run Flyway  │
│  deploy on   │  │  from GHCR   │  │  migrations  │
│  push to     │  │  Restart     │  │              │
│  master      │  │  container   │  │              │
└──────────────┘  └──────────────┘  └──────────────┘
```

### 4.2 Deployment Workflow

#### Frontend Deployment (Vercel)

1. Developer pushes to `master`
2. Vercel automatically builds and deploys
3. Preview deployment created for PR
4. Production deployment updated on merge

**No manual intervention required.**

#### Backend Deployment (VPS)

1. GitHub Actions builds Docker image
2. Image pushed to GHCR with Git SHA tag
3. Deployment script on VPS:
   ```bash
   #!/bin/bash
   # deploy.sh
   
   export GIT_SHA=$(git rev-parse --short HEAD)
   export IMAGE=ghcr.io/yourorg/gms-backend:$GIT_SHA
   
   docker pull $IMAGE
   docker-compose -f docker-compose.prod.yml up -d --no-deps backend
   docker image prune -f
   ```
4. Backend container restarted with new image
5. Flyway migrations run automatically on startup

**Automation:** Can be triggered via webhook or SSH command.

#### Database Migration (Neon)

1. Flyway migrations run on backend startup
2. Migrations are idempotent (safe to re-run)
3. No manual intervention required

**Rollback:** Not supported by Flyway. Manual intervention required for rollback.

### 4.3 Rollback Strategy

#### Frontend Rollback (Vercel)

1. Vercel keeps last 10 deployments
2. Rollback via Vercel dashboard:
   - Go to Deployments
   - Find previous deployment
   - Click "Promote to Production"

**Time to rollback:** < 1 minute

#### Backend Rollback (VPS)

1. Identify previous Git SHA:
   ```bash
   git log --oneline -10
   ```
2. Deploy previous version:
   ```bash
   export GIT_SHA=<previous-sha>
   export IMAGE=ghcr.io/yourorg/gms-backend:$GIT_SHA
   docker pull $IMAGE
   docker-compose -f docker-compose.prod.yml up -d --no-deps backend
   ```

**Time to rollback:** < 5 minutes

#### Database Rollback

**Not supported automatically.** Flyway migrations are forward-only.

**Manual rollback procedure:**
1. Restore from Neon backup (point-in-time recovery)
2. Or restore from `pg_dump` backup

**Time to rollback:** 15–30 minutes (depending on backup size)

---

## 5. Backup and Disaster Recovery

### 5.1 Backup Strategy

#### Database Backups (Neon)

**Automated Backups:**
- Daily snapshots (retained for 7 days)
- Point-in-time recovery (any second within 7 days)
- Stored in Neon's infrastructure (encrypted at rest)

**Manual Backups (Recommended):**
```bash
# Daily backup script (cron job on VPS)
#!/bin/bash
# backup.sh

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR=/backups
NEON_URL=postgresql://user:pass@ep-cool-name.neon.tech/gms

pg_dump $NEON_URL | gzip > $BACKUP_DIR/gms_backup_$TIMESTAMP.sql.gz

# Retain last 30 days
find $BACKUP_DIR -name "gms_backup_*.sql.gz" -mtime +30 -delete
```

**Off-site Storage:**
- Upload backups to Backblaze B2 ($0.005/GB/month)
- Or AWS S3 ($0.023/GB/month)
- Or Google Cloud Storage ($0.020/GB/month)

**Estimated cost:** $0.01–$0.05/month for 1 GB of backups

#### Application Backups

**Configuration Files:**
- Store in Git (already version-controlled)
- Backup `.env` file separately (encrypted)

**Docker Volumes:**
- No persistent volumes on VPS (stateless backend)
- All state in Neon PostgreSQL

### 5.2 Disaster Recovery Plan

#### Scenario 1: VPS Failure

**Impact:** Backend unavailable, frontend still accessible (Vercel)

**Recovery:**
1. Provision new VPS (DigitalOcean, Hetzner, or Linode)
2. Install Docker and Docker Compose
3. Clone repository
4. Restore `.env` file from backup
5. Run `docker-compose up -d`
6. Update DNS to point to new VPS IP

**RTO (Recovery Time Objective):** 1–2 hours  
**RPO (Recovery Point Objective):** 0 (no data loss, database is on Neon)

#### Scenario 2: Neon Database Failure

**Impact:** Backend unavailable (database connection fails)

**Recovery:**
1. Check Neon status page (https://status.neon.tech)
2. If outage, wait for Neon to restore (usually < 1 hour)
3. If data corruption, restore from point-in-time backup:
   - Go to Neon dashboard
   - Select "Branches"
   - Create new branch from restore point
   - Update connection string in `.env`
   - Restart backend container

**RTO:** 1–4 hours (depending on failure type)  
**RPO:** < 5 minutes (point-in-time recovery)

#### Scenario 3: Vercel Frontend Failure

**Impact:** Frontend unavailable, backend still accessible

**Recovery:**
1. Check Vercel status page (https://www.vercel-status.com)
2. If outage, wait for Vercel to restore (usually < 1 hour)
3. If deployment issue, rollback to previous deployment via Vercel dashboard

**RTO:** < 1 hour  
**RPO:** 0 (no data loss, static assets)

#### Scenario 4: Complete Infrastructure Failure

**Impact:** All services unavailable

**Recovery:**
1. Provision new VPS
2. Restore from backups (database, configuration)
3. Re-deploy frontend to Vercel
4. Update DNS records
5. Verify all services operational

**RTO:** 4–8 hours  
**RPO:** < 24 hours (depending on backup frequency)

### 5.3 Testing Disaster Recovery

**Quarterly DR Drill:**
1. Restore Neon backup to new branch
2. Deploy to staging environment
3. Verify data integrity
4. Test application functionality
5. Document lessons learned

**Annual Full DR Test:**
1. Simulate complete infrastructure failure
2. Restore from backups to new environment
3. Verify all services operational
4. Measure actual RTO and RPO
5. Update DR plan based on findings

---

## 6. Scaling Considerations

### 6.1 Current Capacity (Pilot Scale)

**Estimated Load:**
- 2,000 students
- ~500 concurrent users (25% active at any time)
- ~50 requests/second (average)
- ~200 requests/second (peak)

**Resource Requirements:**
- Backend: 1 vCPU, 1 GB RAM (sufficient)
- Database: 0.5 GB storage (sufficient for 1–2 years)
- Bandwidth: 10 GB/month (well within Vercel's 100 GB limit)

### 6.2 Scaling Triggers

**When to scale:**
- Backend CPU > 80% sustained (scale vertically: 2 vCPU)
- Backend RAM > 80% sustained (scale vertically: 2 GB RAM)
- Database storage > 400 MB (scale Neon to paid plan or self-managed)
- Response time > 2 seconds (scale horizontally: multiple backend instances)
- Concurrent users > 1,000 (scale horizontally: load balancer + multiple VPS)

### 6.3 Scaling Options

#### Vertical Scaling (Easiest)

**Backend:**
- Increase VPS resources (2 vCPU, 4 GB RAM)
- Cost: $12–$24/month

**Database:**
- Upgrade Neon to paid plan (10 GB storage)
- Cost: $19/month

#### Horizontal Scaling (More Complex)

**Backend:**
- Add load balancer (DigitalOcean Load Balancer, $12/month)
- Run multiple backend instances (2+ VPS)
- Use session-less architecture (already implemented with JWT)
- Cost: $24+/month

**Database:**
- Add read replicas (Neon paid plan, $19+/month)
- Or migrate to managed PostgreSQL (AWS RDS, $15+/month)
- Cost: $19–$35/month

**Recommendation:** Start with vertical scaling. Horizontal scaling is not needed until 10,000+ students.

---

## 7. Implementation Plan

### 7.1 Phase 1: Infrastructure Setup (1–2 days)

**Tasks:**
1. Create Neon PostgreSQL account and database
2. Provision DigitalOcean Droplet (or Hetzner Cloud server)
3. Install Docker and Docker Compose on VPS
4. Configure firewall (ufw) on VPS
5. Set up Let's Encrypt SSL certificate
6. Configure Nginx reverse proxy
7. Create `.env` file with production secrets
8. Test backend container deployment

**Deliverables:**
- Backend accessible at `https://api.yourdomain.com`
- SSL certificate valid
- Health check passing

### 7.2 Phase 2: Frontend Deployment (1 day)

**Tasks:**
1. Create Vercel account
2. Connect GitHub repository to Vercel
3. Configure environment variables in Vercel
4. Set up custom domain
5. Configure API proxy (`/api/*` → backend)
6. Test frontend deployment
7. Verify HTTPS and CORS

**Deliverables:**
- Frontend accessible at `https://yourdomain.com`
- API calls proxied to backend
- All features working

### 7.3 Phase 3: Monitoring and Backups (1 day)

**Tasks:**
1. Set up UptimeRobot monitors (frontend, backend API)
2. Configure email alerts
3. Create backup script for database
4. Set up cron job for daily backups
5. Configure off-site backup storage (Backblaze B2 or S3)
6. Test restore procedure
7. Document disaster recovery plan

**Deliverables:**
- Monitoring active
- Backups running daily
- Restore procedure tested

### 7.4 Phase 4: CI/CD Integration (1–2 days)

**Tasks:**
1. Configure GitHub Actions to build Docker images
2. Push images to GitHub Container Registry (GHCR)
3. Create deployment script on VPS
4. Set up webhook or SSH deployment trigger
5. Test automated deployment
6. Document rollback procedure
7. Test rollback

**Deliverables:**
- Automated deployment from CI
- Rollback procedure documented and tested

### 7.5 Phase 5: Production Launch (1 day)

**Tasks:**
1. Final security review
2. Performance testing (load testing)
3. DNS cutover (point domain to production)
4. Monitor logs for errors
5. Verify all features working
6. Announce production launch

**Deliverables:**
- Production system live
- All users able to access
- Monitoring confirmed

**Total Time:** 5–7 days

---

## 8. Risk Assessment

### 8.1 Technical Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| VPS failure | Medium | High | Daily backups, DR plan, 1–2 hour RTO |
| Database corruption | Low | Critical | Neon automated backups, point-in-time recovery |
| SSL certificate expiry | Low | Medium | Automatic renewal with Certbot |
| Deployment failure | Medium | Medium | Rollback procedure, < 5 minute recovery |
| Performance degradation | Low | Medium | Vertical scaling, monitoring alerts |

### 8.2 Operational Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Solo operator unavailable | Medium | High | Document procedures, train backup operator |
| Cost overrun | Low | Low | Free-tier services, $6–$12/month budget |
| Vendor lock-in | Low | Medium | Use open-source tools, portable configuration |
| Security breach | Low | Critical | Firewall, SSL, secrets management, regular updates |

### 8.3 Business Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Data loss | Very Low | Critical | Daily backups, off-site storage, tested restore |
| Downtime | Low | High | Monitoring, DR plan, < 2 hour RTO |
| Scalability limits | Low | Medium | Vertical scaling path, horizontal scaling plan |

---

## 9. Cost Summary

### 9.1 Monthly Costs

| Component | Provider | Cost | Notes |
|-----------|----------|------|-------|
| Frontend hosting | Vercel | $0 | Free tier |
| Backend hosting | DigitalOcean | $6 | 1 GB RAM, 1 vCPU |
| Database | Neon | $0 | Free tier (0.5 GB) |
| SSL certificate | Let's Encrypt | $0 | Free |
| Monitoring | UptimeRobot | $0 | Free tier |
| Backups | Backblaze B2 | $0.01 | ~1 GB storage |
| **Total** | | **$6.01/month** | **Well within budget** |

### 9.2 Annual Costs

| Component | Annual Cost |
|-----------|-------------|
| Infrastructure | $72.12 |
| Domain name | $10–$15 |
| **Total** | **$82–$87/year** |

### 9.3 Scaling Costs (Future)

| Scenario | Additional Cost |
|----------|-----------------|
| 2x VPS resources | +$6/month |
| Neon paid plan (10 GB) | +$19/month |
| Load balancer + 2 VPS | +$18/month |
| Managed PostgreSQL | +$15/month |
| **Total at 10,000 students** | **~$50–$60/month** |

---

## 10. Recommendations

### 10.1 Immediate Actions

1. **Create Neon account** and set up free-tier PostgreSQL database
2. **Provision DigitalOcean Droplet** ($6/month, 1 GB RAM)
3. **Deploy backend** using Docker Compose
4. **Configure SSL** with Let's Encrypt
5. **Deploy frontend** to Vercel (free tier)
6. **Set up monitoring** with UptimeRobot

### 10.2 Short-Term Improvements (First Month)

1. **Automate backups** with daily cron job
2. **Document deployment procedures** for disaster recovery
3. **Test restore procedure** quarterly
4. **Set up CI/CD** for automated deployments
5. **Configure log aggregation** (optional, self-hosted)

### 10.3 Long-Term Enhancements (First Quarter)

1. **Add performance monitoring** (Prometheus + Grafana)
2. **Implement APM** (Application Performance Monitoring)
3. **Set up staging environment** (mirror of production)
4. **Create automated testing** in production (synthetic transactions)
5. **Plan horizontal scaling** for 10,000+ students

---

## 11. Conclusion

The recommended architecture provides a **simple, reliable, and cost-effective** production deployment for GMS:

**Strengths:**
- ✅ Total cost: $6/month (well within budget)
- ✅ Simple to manage (single VPS + managed services)
- ✅ Automated deployments (CI/CD integration)
- ✅ Good performance (global CDN for frontend)
- ✅ Reliable backups (Neon automated + manual)
- ✅ Easy to scale (vertical scaling path)

**Trade-offs:**
- ⚠️ Single point of failure (VPS)
- ⚠️ Manual intervention required for disasters
- ⚠️ Limited to 2,000–5,000 students before scaling needed

**Recommendation:** **GO** for production deployment with this architecture.

The architecture is appropriate for the pilot scale (2,000 students) and can be incrementally improved as the user base grows. The total cost of $6/month is well within the zero-cost preference, and the complexity is manageable for a solo developer.

---

**Document Version:** 1.0  
**Last Updated:** 2026-11-03  
**Next Review:** After 3 months of production operation
