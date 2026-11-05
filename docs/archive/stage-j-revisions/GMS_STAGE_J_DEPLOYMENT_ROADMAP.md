# GMS Stage J — Deployment Roadmap

**Date:** 2026-11-03  
**Author:** Principal DevOps Engineer, Cloud Architect  
**Repository:** GMS (test/playwright-e2e branch)  
**Purpose:** Phased implementation plan for production deployment

---

## Executive Summary

This roadmap provides a **phased implementation plan** to move GMS from its current state (audit-complete, infrastructure gaps identified) to a production-ready deployment. The plan is organized into **5 phases** over **4–6 weeks**, with clear acceptance criteria, dependencies, and risk mitigation strategies.

**Key Milestones:**
- **Phase 1 (Week 1):** Infrastructure provisioning and SSL setup
- **Phase 2 (Week 2):** Backend deployment and database migration
- **Phase 3 (Week 3):** Frontend deployment and integration testing
- **Phase 4 (Week 4):** Monitoring, backups, and CI/CD automation
- **Phase 5 (Week 5–6):** Production launch and post-launch stabilization

**Total Estimated Effort:** 40–60 hours (5–7 full working days)  
**Total Estimated Cost:** $6–$12/month (infrastructure) + $0 (free-tier services)

---

## Phase 0: Pre-Deployment Preparation (Day 0)

**Duration:** 2–4 hours  
**Prerequisites:** None  
**Owner:** Solo developer

### 0.1 Objectives

1. Finalize architecture decisions
2. Create accounts on required services
3. Prepare domain and DNS
4. Set up development environment for production configuration

### 0.2 Tasks

#### Task 0.1: Finalize Architecture Decisions

**Actions:**
- Review `GMS_STAGE_J_PRODUCTION_ARCHITECTURE.md`
- Confirm hosting choices:
  - Frontend: Vercel (free tier)
  - Backend: DigitalOcean Droplet ($6/month) or Hetzner Cloud (€4.50/month)
  - Database: Neon PostgreSQL (free tier)
  - SSL: Let's Encrypt (free)
  - Monitoring: UptimeRobot (free tier)

**Deliverables:**
- [ ] Architecture decision document signed off
- [ ] Hosting providers selected
- [ ] Budget approved ($6–$12/month)

**Dependencies:** None

#### Task 0.2: Create Service Accounts

**Actions:**
- Create Neon PostgreSQL account (https://neon.tech)
- Create DigitalOcean account (https://digitalocean.com) OR Hetzner Cloud account (https://hetzner.com/cloud)
- Create Vercel account (https://vercel.com)
- Create UptimeRobot account (https://uptimerobot.com)
- Create Cloudflare account (optional, for DNS and CDN) (https://cloudflare.com)

**Deliverables:**
- [ ] Neon account created and verified
- [ ] VPS provider account created and verified
- [ ] Vercel account created and verified
- [ ] UptimeRobot account created and verified
- [ ] (Optional) Cloudflare account created and verified

**Dependencies:** Task 0.1 complete

#### Task 0.3: Prepare Domain and DNS

**Actions:**
- Purchase domain name (if not already owned) (~$10–$15/year)
- Configure DNS records:
  - `yourdomain.com` → Vercel (CNAME)
  - `api.yourdomain.com` → VPS IP address (A record)
  - `www.yourdomain.com` → Vercel (CNAME)

**Deliverables:**
- [ ] Domain purchased
- [ ] DNS records configured
- [ ] DNS propagation verified (use `dig` or `nslookup`)

**Dependencies:** Task 0.2 complete

#### Task 0.4: Set Up Local Production Configuration

**Actions:**
- Create production `.env` file template:
  ```bash
  # .env.production (template)
  SPRING_PROFILES_ACTIVE=prod
  
  # Database (Neon)
  DB_URL=jdbc:postgresql://<neon-host>:5432/gms?sslmode=require
  DB_USERNAME=<neon-user>
  DB_PASSWORD=<neon-password>
  
  # JWT
  SECURITY_JWT_SECRET=<generate-with-openssl-rand-base64-48>
  SECURITY_JWT_VALIDITY_SECONDS=3600
  SECURITY_JWT_ISSUER=gms
  
  # CORS
  CORS_ALLOWED_ORIGINS=https://yourdomain.com,https://www.yourdomain.com
  
  # Superadmin (initial setup)
  APP_SUPERADMIN_ENABLED=true
  APP_SUPERADMIN_USERNAME=admin
  APP_SUPERADMIN_PASSWORD=<strong-password>
  APP_SUPERADMIN_EMAIL=admin@yourdomain.com
  APP_SUPERADMIN_FULL_NAME=Platform Administrator
  
  # Mail (optional, can configure later)
  MAIL_HOST=
  MAIL_PORT=587
  MAIL_USERNAME=
  MAIL_PASSWORD=
  MAIL_FROM=
  
  # Server
  SERVER_PORT=8080
  
  # Logging
  LOG_LEVEL_ROOT=INFO
  LOG_LEVEL_COM_GMS=INFO
  ```

- Generate JWT secret:
  ```bash
  openssl rand -base64 48
  ```

- Create production `docker-compose.prod.yml` (see Phase 2)

**Deliverables:**
- [ ] `.env.production` template created
- [ ] JWT secret generated and stored securely
- [ ] `docker-compose.prod.yml` created
- [ ] Nginx production configuration created (see Phase 2)

**Dependencies:** Task 0.2 complete

### 0.3 Acceptance Criteria

- [ ] All service accounts created and verified
- [ ] Domain purchased and DNS configured
- [ ] Production configuration files created and reviewed
- [ ] JWT secret generated and stored securely (not in Git)

### 0.4 Risks and Mitigations

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Domain already taken | Low | Medium | Choose alternative domain |
| Service account verification delayed | Low | Low | Start account creation early |
| DNS propagation slow | Medium | Low | Wait 24–48 hours, use DNS checker tools |
| Configuration errors | Medium | High | Review configuration with checklist |

### 0.5 Rollback Plan

**Not applicable.** Phase 0 is preparation only, no production changes made.

---

## Phase 1: Infrastructure Provisioning (Days 1–2)

**Duration:** 8–12 hours (1–2 full working days)  
**Prerequisites:** Phase 0 complete  
**Owner:** Solo developer

### 1.1 Objectives

1. Provision VPS and configure firewall
2. Install Docker and Docker Compose
3. Set up SSL certificates (Let's Encrypt)
4. Configure Nginx reverse proxy
5. Create Neon PostgreSQL database

### 1.2 Tasks

#### Task 1.1: Provision VPS

**Actions:**
- Create DigitalOcean Droplet OR Hetzner Cloud server:
  - **OS:** Ubuntu 22.04 LTS
  - **Size:** 1 GB RAM, 1 vCPU (minimum), 2 GB RAM recommended
  - **Region:** Closest to target users
  - **Storage:** 25 GB SSD (minimum)
  
- SSH into VPS:
  ```bash
  ssh root@<vps-ip-address>
  ```

- Update system:
  ```bash
  sudo apt update && sudo apt upgrade -y
  ```

- Create non-root user (optional but recommended):
  ```bash
  adduser deploy
  usermod -aG sudo deploy
  su - deploy
  ```

**Deliverables:**
- [ ] VPS provisioned and accessible via SSH
- [ ] System updated
- [ ] (Optional) Non-root user created

**Dependencies:** Task 0.2 complete

#### Task 1.2: Configure Firewall

**Actions:**
- Install UFW (Uncomplicated Firewall):
  ```bash
  sudo apt install ufw -y
  ```

- Configure firewall rules:
  ```bash
  # Allow SSH (critical: do this first or you'll lock yourself out)
  sudo ufw allow 22/tcp
  
  # Allow HTTP (will redirect to HTTPS)
  sudo ufw allow 80/tcp
  
  # Allow HTTPS
  sudo ufw allow 443/tcp
  
  # Enable firewall
  sudo ufw enable
  
  # Check status
  sudo ufw status
  ```

**Deliverables:**
- [ ] Firewall configured and enabled
- [ ] SSH, HTTP, HTTPS ports open
- [ ] Firewall status verified

**Dependencies:** Task 1.1 complete

#### Task 1.3: Install Docker and Docker Compose

**Actions:**
- Install Docker:
  ```bash
  # Add Docker's official GPG key
  sudo apt-get update
  sudo apt-get install ca-certificates curl gnupg -y
  sudo install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  sudo chmod a+r /etc/apt/keyrings/docker.gpg
  
  # Add Docker repository
  echo \
    "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
    $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
    sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
  
  # Install Docker
  sudo apt-get update
  sudo apt-get install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin -y
  
  # Verify installation
  sudo docker --version
  sudo docker compose version
  
  # Add user to docker group (optional, avoids sudo for docker commands)
  sudo usermod -aG docker $USER
  newgrp docker
  ```

- Test Docker:
  ```bash
  docker run hello-world
  ```

**Deliverables:**
- [ ] Docker installed and verified
- [ ] Docker Compose installed and verified
- [ ] (Optional) User added to docker group

**Dependencies:** Task 1.1 complete

#### Task 1.4: Create Neon PostgreSQL Database

**Actions:**
- Log in to Neon dashboard (https://console.neon.tech)
- Create new project:
  - **Name:** gms-production
  - **Region:** Closest to VPS
  - **PostgreSQL version:** 15 (matches development)
  
- Create database:
  - **Name:** gms
  - **Owner:** neon_user (default)
  
- Get connection string:
  - Copy connection string from Neon dashboard
  - Format: `postgresql://user:password@host.neon.tech/dbname?sslmode=require`
  
- Test connection from VPS:
  ```bash
  # Install PostgreSQL client
  sudo apt install postgresql-client -y
  
  # Test connection
  psql "postgresql://<user>:<password>@<host>.neon.tech/gms?sslmode=require"
  ```

**Deliverables:**
- [ ] Neon project created
- [ ] Database created
- [ ] Connection string obtained
- [ ] Connection tested from VPS

**Dependencies:** Task 0.2 complete

#### Task 1.5: Set Up SSL Certificates (Let's Encrypt)

**Actions:**
- Install Certbot:
  ```bash
  sudo apt install certbot python3-certbot-nginx -y
  ```

- Obtain SSL certificate (after Nginx is configured in Task 1.6):
  ```bash
  sudo certbot --nginx -d api.yourdomain.com
  ```

- Verify automatic renewal:
  ```bash
  sudo certbot renew --dry-run
  ```

- Check cron job (should be automatically created):
  ```bash
  sudo systemctl list-timers | grep certbot
  ```

**Deliverables:**
- [ ] Certbot installed
- [ ] SSL certificate obtained for `api.yourdomain.com`
- [ ] Automatic renewal verified
- [ ] Cron job configured

**Dependencies:** Task 1.6 complete (Nginx must be running)

#### Task 1.6: Configure Nginx Reverse Proxy

**Actions:**
- Install Nginx:
  ```bash
  sudo apt install nginx -y
  ```

- Create Nginx configuration:
  ```bash
  sudo nano /etc/nginx/sites-available/gms-api
  ```

- Add configuration:
  ```nginx
  # Redirect HTTP to HTTPS
  server {
      listen 80;
      server_name api.yourdomain.com;
      return 301 https://$server_name$request_uri;
  }
  
  # HTTPS server
  server {
      listen 443 ssl http2;
      server_name api.yourdomain.com;
  
      # SSL certificates (will be updated by Certbot)
      ssl_certificate /etc/letsencrypt/live/api.yourdomain.com/fullchain.pem;
      ssl_certificate_key /etc/letsencrypt/live/api.yourdomain.com/privkey.pem;
  
      # SSL configuration
      ssl_protocols TLSv1.2 TLSv1.3;
      ssl_ciphers HIGH:!aNULL:!MD5;
      ssl_prefer_server_ciphers on;
      ssl_session_cache shared:SSL:10m;
      ssl_session_timeout 10m;
  
      # Security headers
      add_header X-Frame-Options "SAMEORIGIN" always;
      add_header X-Content-Type-Options "nosniff" always;
      add_header X-XSS-Protection "1; mode=block" always;
      add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
  
      # Proxy to backend container
      location /api/ {
          proxy_pass http://127.0.0.1:8080;
          proxy_set_header Host $host;
          proxy_set_header X-Real-IP $remote_addr;
          proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
          proxy_set_header X-Forwarded-Proto $scheme;
          proxy_set_header Upgrade $http_upgrade;
          proxy_set_header Connection "upgrade";
          proxy_http_version 1.1;
          proxy_cache_bypass $http_upgrade;
          proxy_read_timeout 90s;
          proxy_connect_timeout 90s;
      }
  
      # Health check endpoint (for monitoring)
      location /actuator/health {
          proxy_pass http://127.0.0.1:8080;
          proxy_set_header Host $host;
          access_log off;
      }
  
      # Deny all other requests
      location / {
          return 404;
      }
  }
  ```

- Enable site:
  ```bash
  sudo ln -s /etc/nginx/sites-available/gms-api /etc/nginx/sites-enabled/
  sudo nginx -t
  sudo systemctl restart nginx
  ```

**Deliverables:**
- [ ] Nginx installed
- [ ] Nginx configuration created
- [ ] Site enabled and tested
- [ ] Nginx running and accessible

**Dependencies:** Task 1.2 complete (firewall configured)

### 1.3 Acceptance Criteria

- [ ] VPS provisioned and accessible via SSH
- [ ] Firewall configured (SSH, HTTP, HTTPS open)
- [ ] Docker and Docker Compose installed and verified
- [ ] Neon PostgreSQL database created and accessible
- [ ] SSL certificate obtained for `api.yourdomain.com`
- [ ] Nginx reverse proxy configured and running
- [ ] Health check endpoint accessible: `https://api.yourdomain.com/actuator/health`

### 1.4 Risks and Mitigations

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| VPS provisioning fails | Low | High | Try different region or provider |
| Firewall locks out SSH | Medium | Critical | Use provider's web console to access VPS |
| Docker installation fails | Low | Medium | Use official Docker installation script |
| SSL certificate fails | Low | Medium | Check DNS propagation, wait 24 hours |
| Nginx configuration error | Medium | Medium | Test with `nginx -t` before restarting |

### 1.5 Rollback Plan

**If VPS provisioning fails:**
- Try different region or provider (Hetzner ↔ DigitalOcean)
- Use local development environment for testing

**If SSL certificate fails:**
- Use HTTP temporarily (not recommended for production)
- Wait for DNS propagation (up to 48 hours)
- Try different domain or subdomain

**If Nginx configuration fails:**
- Revert to default Nginx configuration
- Fix configuration errors and test with `nginx -t`

---

## Phase 2: Backend Deployment (Days 3–4)

**Duration:** 8–12 hours (1–2 full working days)  
**Prerequisites:** Phase 1 complete  
**Owner:** Solo developer

### 2.1 Objectives

1. Build production Docker image
2. Push image to container registry
3. Deploy backend container on VPS
4. Run Flyway migrations
5. Verify backend health and functionality

### 2.2 Tasks

#### Task 2.1: Build Production Docker Image

**Actions:**
- Navigate to backend directory:
  ```bash
  cd gms_backend
  ```

- Build Docker image:
  ```bash
  docker build -t gms-backend:latest .
  ```

- Tag image with Git SHA:
  ```bash
  GIT_SHA=$(git rev-parse --short HEAD)
  docker tag gms-backend:latest gms-backend:$GIT_SHA
  ```

- Verify image:
  ```bash
  docker images | grep gms-backend
  ```

**Deliverables:**
- [ ] Docker image built successfully
- [ ] Image tagged with Git SHA
- [ ] Image size reasonable (< 300 MB)

**Dependencies:** None

#### Task 2.2: Set Up Container Registry (GitHub Container Registry)

**Actions:**
- Log in to GitHub Container Registry:
  ```bash
  echo $GITHUB_TOKEN | docker login ghcr.io -u YOUR_GITHUB_USERNAME --password-stdin
  ```

- Tag image for GHCR:
  ```bash
  docker tag gms-backend:latest ghcr.io/YOUR_GITHUB_USERNAME/gms-backend:latest
  docker tag gms-backend:$GIT_SHA ghcr.io/YOUR_GITHUB_USERNAME/gms-backend:$GIT_SHA
  ```

- Push image to GHCR:
  ```bash
  docker push ghcr.io/YOUR_GITHUB_USERNAME/gms-backend:latest
  docker push ghcr.io/YOUR_GITHUB_USERNAME/gms-backend:$GIT_SHA
  ```

- Verify image in GHCR:
  - Go to https://github.com/YOUR_GITHUB_USERNAME?tab=packages
  - Confirm `gms-backend` package exists

**Deliverables:**
- [ ] GitHub Container Registry configured
- [ ] Docker image pushed to GHCR
- [ ] Image accessible in GHCR dashboard

**Dependencies:** Task 2.1 complete

#### Task 2.3: Create Production Docker Compose File

**Actions:**
- Create `docker-compose.prod.yml` on VPS:
  ```bash
  ssh deploy@<vps-ip>
  mkdir -p ~/gms-production
  cd ~/gms-production
  nano docker-compose.prod.yml
  ```

- Add configuration:
  ```yaml
  version: '3.8'
  
  services:
    backend:
      image: ghcr.io/YOUR_GITHUB_USERNAME/gms-backend:${GIT_SHA}
      container_name: gms-backend
      restart: unless-stopped
      environment:
        - SPRING_PROFILES_ACTIVE=prod
        - DB_URL=${DB_URL}
        - DB_USERNAME=${DB_USERNAME}
        - DB_PASSWORD=${DB_PASSWORD}
        - SECURITY_JWT_SECRET=${JWT_SECRET}
        - SECURITY_JWT_VALIDITY_SECONDS=3600
        - SECURITY_JWT_ISSUER=gms
        - CORS_ALLOWED_ORIGINS=${CORS_ALLOWED_ORIGINS}
        - APP_SUPERADMIN_ENABLED=${APP_SUPERADMIN_ENABLED}
        - APP_SUPERADMIN_USERNAME=${APP_SUPERADMIN_USERNAME}
        - APP_SUPERADMIN_PASSWORD=${APP_SUPERADMIN_PASSWORD}
        - APP_SUPERADMIN_EMAIL=${APP_SUPERADMIN_EMAIL}
        - APP_SUPERADMIN_FULL_NAME=${APP_SUPERADMIN_FULL_NAME}
        - SERVER_PORT=8080
        - LOG_LEVEL_ROOT=INFO
        - LOG_LEVEL_COM_GMS=INFO
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
          max-size: "10m"
          max-file: "3"
  
  networks:
    gms-network:
      driver: bridge
  ```

**Deliverables:**
- [ ] `docker-compose.prod.yml` created on VPS
- [ ] Configuration reviewed and validated

**Dependencies:** Task 2.2 complete

#### Task 2.4: Create Production Environment File

**Actions:**
- Create `.env` file on VPS:
  ```bash
  cd ~/gms-production
  nano .env
  ```

- Add environment variables:
  ```bash
  # Git SHA (update with each deployment)
  GIT_SHA=latest
  
  # Database (Neon)
  DB_URL=jdbc:postgresql://<neon-host>:5432/gms?sslmode=require
  DB_USERNAME=<neon-user>
  DB_PASSWORD=<neon-password>
  
  # JWT
  JWT_SECRET=<generate-with-openssl-rand-base64-48>
  
  # CORS
  CORS_ALLOWED_ORIGINS=https://yourdomain.com,https://www.yourdomain.com
  
  # Superadmin (initial setup)
  APP_SUPERADMIN_ENABLED=true
  APP_SUPERADMIN_USERNAME=admin
  APP_SUPERADMIN_PASSWORD=<strong-password>
  APP_SUPERADMIN_EMAIL=admin@yourdomain.com
  APP_SUPERADMIN_FULL_NAME=Platform Administrator
  ```

- Set file permissions:
  ```bash
  chmod 600 .env
  ```

**Deliverables:**
- [ ] `.env` file created on VPS
- [ ] All environment variables configured
- [ ] File permissions set to 600 (owner read/write only)
- [ ] JWT secret generated and stored securely

**Dependencies:** Task 1.4 complete (Neon database created)

#### Task 2.5: Deploy Backend Container

**Actions:**
- Pull Docker image on VPS:
  ```bash
  cd ~/gms-production
  source .env
  docker pull ghcr.io/YOUR_GITHUB_USERNAME/gms-backend:$GIT_SHA
  ```

- Start backend container:
  ```bash
  docker compose -f docker-compose.prod.yml up -d backend
  ```

- Verify container is running:
  ```bash
  docker ps | grep gms-backend
  ```

- Check container logs:
  ```bash
  docker logs gms-backend --follow
  ```

- Wait for Flyway migrations to complete (watch for "Flyway migration successful" messages)

- Verify backend health:
  ```bash
  curl http://localhost:8080/actuator/health
  ```

- Expected response:
  ```json
  {"status":"UP"}
  ```

**Deliverables:**
- [ ] Backend container running
- [ ] Flyway migrations completed successfully
- [ ] Health check returning `{"status":"UP"}`
- [ ] No errors in container logs

**Dependencies:** Tasks 2.3, 2.4 complete

#### Task 2.6: Verify Backend Functionality

**Actions:**
- Test API endpoint via Nginx:
  ```bash
  curl https://api.yourdomain.com/actuator/health
  ```

- Expected response:
  ```json
  {"status":"UP"}
  ```

- Test login endpoint:
  ```bash
  curl -X POST https://api.yourdomain.com/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"<APP_SUPERADMIN_PASSWORD>"}'
  ```

- Expected response:
  ```json
  {"token":"<jwt-token>","username":"admin","role":"SUPERADMIN","schoolId":null,"mustChangePassword":true}
  ```

- Test authenticated endpoint:
  ```bash
  curl https://api.yourdomain.com/api/v1/schools \
    -H "Authorization: Bearer <jwt-token>"
  ```

- Expected response:
  ```json
  []
  ```

**Deliverables:**
- [ ] API accessible via HTTPS
- [ ] Login endpoint working
- [ ] JWT token issued successfully
- [ ] Authenticated endpoints accessible
- [ ] CORS headers present in responses

**Dependencies:** Task 2.5 complete, Task 1.5 complete (SSL configured)

### 2.3 Acceptance Criteria

- [ ] Backend Docker image built and pushed to GHCR
- [ ] Backend container running on VPS
- [ ] Flyway migrations completed successfully
- [ ] Health check endpoint accessible via HTTPS
- [ ] Login endpoint working and issuing JWT tokens
- [ ] Authenticated endpoints accessible
- [ ] CORS configured correctly
- [ ] No errors in container logs

### 2.4 Risks and Mitigations

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Docker image build fails | Low | High | Check Dockerfile, fix errors, rebuild |
| GHCR push fails | Low | Medium | Verify GitHub token, check package permissions |
| Container fails to start | Medium | High | Check logs, fix environment variables, restart |
| Flyway migration fails | Medium | Critical | Check migration files, verify database connection |
| Health check fails | Medium | High | Check backend logs, verify Nginx configuration |
| CORS errors | Medium | Medium | Verify `CORS_ALLOWED_ORIGINS` in `.env` |

### 2.5 Rollback Plan

**If backend deployment fails:**
- Stop container: `docker compose -f docker-compose.prod.yml down`
- Check logs: `docker logs gms-backend`
- Fix issues and redeploy

**If Flyway migration fails:**
- Restore database from Neon backup (point-in-time recovery)
- Fix migration files
- Redeploy backend

**If backend is unstable:**
- Rollback to previous image:
  ```bash
  export GIT_SHA=<previous-sha>
  docker compose -f docker-compose.prod.yml up -d backend
  ```

---

## Phase 3: Frontend Deployment (Days 5–6)

**Duration:** 6–8 hours (1 full working day)  
**Prerequisites:** Phase 2 complete  
**Owner:** Solo developer

### 3.1 Objectives

1. Configure Vercel project
2. Set up environment variables in Vercel
3. Deploy frontend to Vercel
4. Configure custom domain
5. Verify frontend-backend integration

### 3.2 Tasks

#### Task 3.1: Configure Vercel Project

**Actions:**
- Log in to Vercel dashboard (https://vercel.com)
- Click "New Project"
- Import GitHub repository: `YOUR_GITHUB_USERNAME/gms`
- Configure project:
  - **Framework Preset:** Vite
  - **Root Directory:** `gms_frontend`
  - **Build Command:** `npm run build`
  - **Output Directory:** `dist`
  - **Install Command:** `npm ci`

- Click "Deploy"

**Deliverables:**
- [ ] Vercel project created
- [ ] Initial deployment successful
- [ ] Preview URL accessible

**Dependencies:** Task 0.2 complete (Vercel account created)

#### Task 3.2: Configure Environment Variables in Vercel

**Actions:**
- Go to Vercel project settings
- Navigate to "Environment Variables"
- Add variables:
  - `VITE_API_BASE_URL` = `https://api.yourdomain.com/api/v1`
  - `VITE_MOCK_AUTH` = `false`

- Redeploy project (Vercel dashboard → Deployments → Latest → Redeploy)

**Deliverables:**
- [ ] Environment variables configured
- [ ] Project redeployed with new variables

**Dependencies:** Task 3.1 complete

#### Task 3.3: Configure Custom Domain in Vercel

**Actions:**
- Go to Vercel project settings
- Navigate to "Domains"
- Add domain: `yourdomain.com`
- Add domain: `www.yourdomain.com`
- Configure DNS records:
  - Type: `A` or `CNAME` (Vercel will provide)
  - Name: `@` or `www`
  - Value: (provided by Vercel)

- Wait for DNS propagation (up to 48 hours)
- Verify domain in Vercel dashboard

**Deliverables:**
- [ ] Custom domain added to Vercel
- [ ] DNS records configured
- [ ] Domain verified and active
- [ ] HTTPS enabled automatically by Vercel

**Dependencies:** Task 0.3 complete (domain purchased)

#### Task 3.4: Configure API Proxy in Vercel

**Actions:**
- Create `vercel.json` in `gms_frontend` directory:
  ```json
  {
    "rewrites": [
      {
        "source": "/api/:path*",
        "destination": "https://api.yourdomain.com/api/:path*"
      }
    ],
    "headers": [
      {
        "source": "/(.*)",
        "headers": [
          {
            "key": "X-Content-Type-Options",
            "value": "nosniff"
          },
          {
            "key": "X-Frame-Options",
            "value": "SAMEORIGIN"
          },
          {
            "key": "X-XSS-Protection",
            "value": "1; mode=block"
          }
        ]
      }
    ]
  }
  ```

- Commit and push to GitHub:
  ```bash
  git add vercel.json
  git commit -m "Configure API proxy and security headers"
  git push origin master
  ```

- Vercel will automatically redeploy

**Deliverables:**
- [ ] `vercel.json` created
- [ ] API proxy configured
- [ ] Security headers added
- [ ] Project redeployed automatically

**Dependencies:** Task 3.1 complete

#### Task 3.5: Verify Frontend-Backend Integration

**Actions:**
- Access frontend: `https://yourdomain.com`
- Verify page loads without errors
- Open browser developer tools (F12)
- Check Console tab for errors
- Check Network tab for failed requests

- Test login:
  - Navigate to login page
  - Enter superadmin credentials
  - Verify successful login
  - Verify redirect to dashboard

- Test API calls:
  - Navigate to Students page
  - Verify data loads from backend
  - Check Network tab for `/api/v1/students` request
  - Verify response status: 200 OK

- Test CORS:
  - Verify no CORS errors in console
  - Check response headers for `Access-Control-Allow-Origin`

**Deliverables:**
- [ ] Frontend accessible via HTTPS
- [ ] No console errors
- [ ] Login working
- [ ] API calls successful
- [ ] CORS configured correctly

**Dependencies:** Tasks 3.2, 3.3, 3.4 complete, Phase 2 complete

### 3.3 Acceptance Criteria

- [ ] Frontend deployed to Vercel
- [ ] Custom domain configured and verified
- [ ] HTTPS enabled automatically
- [ ] API proxy working correctly
- [ ] Frontend accessible at `https://yourdomain.com`
- [ ] Login functionality working
- [ ] API calls successful (no CORS errors)
- [ ] No console errors in browser

### 3.4 Risks and Mitigations

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Vercel deployment fails | Low | High | Check build logs, fix errors, redeploy |
| Domain verification fails | Medium | Medium | Wait for DNS propagation (up to 48 hours) |
| API proxy not working | Medium | High | Check `vercel.json` configuration, verify backend URL |
| CORS errors | Medium | Medium | Verify backend CORS configuration, check allowed origins |
| Build errors | Medium | High | Check frontend logs, fix errors, rebuild |

### 3.5 Rollback Plan

**If frontend deployment fails:**
- Rollback to previous deployment:
  - Vercel dashboard → Deployments → Previous → Promote to Production

**If domain verification fails:**
- Use Vercel-provided URL temporarily (e.g., `gms.vercel.app`)
- Wait for DNS propagation
- Re-verify domain

**If API proxy fails:**
- Revert `vercel.json` changes
- Redeploy
- Fix configuration and redeploy again

---

## Phase 4: Monitoring, Backups, and CI/CD (Days 7–10)

**Duration:** 12–16 hours (2–3 full working days)  
**Prerequisites:** Phases 1–3 complete  
**Owner:** Solo developer

### 4.1 Objectives

1. Set up monitoring and alerting
2. Configure automated backups
3. Create deployment scripts
4. Set up CI/CD pipeline
5. Document procedures

### 4.2 Tasks

#### Task 4.1: Set Up UptimeRobot Monitoring

**Actions:**
- Log in to UptimeRobot dashboard (https://uptimerobot.com)
- Add new monitor:
  - **Monitor Type:** HTTP(s)
  - **Friendly Name:** GMS Frontend
  - **URL:** `https://yourdomain.com`
  - **Monitoring Interval:** 5 minutes
  
- Add second monitor:
  - **Monitor Type:** HTTP(s)
  - **Friendly Name:** GMS Backend API
  - **URL:** `https://api.yourdomain.com/actuator/health`
  - **Monitoring Interval:** 5 minutes

- Configure alert contacts:
  - Email: your-email@example.com
  - (Optional) SMS, Slack, or other integrations

- Verify monitors are working:
  - Check UptimeRobot dashboard
  - Verify both monitors show "Up" status

**Deliverables:**
- [ ] UptimeRobot account configured
- [ ] Frontend monitor active
- [ ] Backend API monitor active
- [ ] Alert contacts configured
- [ ] Monitors verified working

**Dependencies:** Phases 1–3 complete

#### Task 4.2: Create Database Backup Script

**Actions:**
- Create backup script on VPS:
  ```bash
  ssh deploy@<vps-ip>
  cd ~/gms-production
  nano backup.sh
  ```

- Add script content:
  ```bash
  #!/bin/bash
  
  # GMS Database Backup Script
  # Runs daily via cron job
  
  set -euo pipefail
  
  # Configuration
  BACKUP_DIR="/backups"
  RETENTION_DAYS=30
  TIMESTAMP=$(date +%Y%m%d_%H%M%S)
  BACKUP_FILE="$BACKUP_DIR/gms_backup_$TIMESTAMP.sql.gz"
  
  # Load environment variables
  source /home/deploy/gms-production/.env
  
  # Create backup directory if it doesn't exist
  mkdir -p $BACKUP_DIR
  
  # Perform backup
  echo "Starting backup at $(date)"
  pg_dump "$DB_URL" | gzip > "$BACKUP_FILE"
  echo "Backup completed: $BACKUP_FILE"
  
  # Get backup size
  BACKUP_SIZE=$(du -h "$BACKUP_FILE" | cut -f1)
  echo "Backup size: $BACKUP_SIZE"
  
  # Remove old backups
  echo "Removing backups older than $RETENTION_DAYS days"
  find $BACKUP_DIR -name "gms_backup_*.sql.gz" -mtime +$RETENTION_DAYS -delete
  
  # List remaining backups
  echo "Remaining backups:"
  ls -lh $BACKUP_DIR/gms_backup_*.sql.gz
  
  echo "Backup process completed at $(date)"
  ```

- Make script executable:
  ```bash
  chmod +x backup.sh
  ```

- Test backup script:
  ```bash
  ./backup.sh
  ```

- Verify backup file created:
  ```bash
  ls -lh /backups/
  ```

**Deliverables:**
- [ ] Backup script created
- [ ] Script tested successfully
- [ ] Backup file created in `/backups/`
- [ ] Old backup retention working

**Dependencies:** Task 1.4 complete (Neon database created)

#### Task 4.3: Set Up Automated Backup Cron Job

**Actions:**
- Edit crontab:
  ```bash
  crontab -e
  ```

- Add cron job (daily at 2 AM):
  ```bash
  # GMS database backup (daily at 2 AM)
  0 2 * * * /home/deploy/gms-production/backup.sh >> /var/log/gms-backup.log 2>&1
  ```

- Verify cron job is scheduled:
  ```bash
  crontab -l
  ```

- Wait for next scheduled run (or test manually):
  ```bash
  # Check backup log
  tail -f /var/log/gms-backup.log
  ```

**Deliverables:**
- [ ] Cron job configured
- [ ] Backup runs automatically
- [ ] Log file created

**Dependencies:** Task 4.2 complete

#### Task 4.4: Set Up Off-Site Backup Storage (Optional)

**Actions:**
- Choose storage provider:
  - Backblaze B2 ($0.005/GB/month)
  - AWS S3 ($0.023/GB/month)
  - Google Cloud Storage ($0.020/GB/month)

- Install storage provider CLI (example: Backblaze B2):
  ```bash
  # Install B2 CLI
  sudo apt install python3-pip -y
  pip3 install b2
  ```

- Configure storage credentials:
  ```bash
  b2 authorize-account <account-id> <application-key>
  ```

- Create backup bucket:
  ```bash
  b2 create-bucket gms-backups private
  ```

- Update backup script to upload to off-site storage:
  ```bash
  # Add to backup.sh (after backup creation)
  echo "Uploading backup to off-site storage"
  b2 upload-file gms-backups "$BACKUP_FILE" "gms_backup_$TIMESTAMP.sql.gz"
  echo "Upload completed"
  ```

- Test upload:
  ```bash
  ./backup.sh
  ```

- Verify file in off-site storage:
  ```bash
  b2 ls gms-backups
  ```

**Deliverables:**
- [ ] Off-site storage configured
- [ ] Backup script updated to upload
- [ ] Upload tested successfully
- [ ] Backup file visible in off-site storage

**Dependencies:** Task 4.2 complete

#### Task 4.5: Create Deployment Script

**Actions:**
- Create deployment script on VPS:
  ```bash
  cd ~/gms-production
  nano deploy.sh
  ```

- Add script content:
  ```bash
  #!/bin/bash
  
  # GMS Deployment Script
  # Deploys latest Docker image from GHCR
  
  set -euo pipefail
  
  # Configuration
  GIT_SHA=${1:-latest}
  IMAGE="ghcr.io/YOUR_GITHUB_USERNAME/gms-backend:$GIT_SHA"
  
  echo "Starting deployment at $(date)"
  echo "Deploying image: $IMAGE"
  
  # Load environment variables
  source /home/deploy/gms-production/.env
  
  # Update GIT_SHA in .env
  sed -i "s/^GIT_SHA=.*/GIT_SHA=$GIT_SHA/" /home/deploy/gms-production/.env
  
  # Pull latest image
  echo "Pulling Docker image..."
  docker pull $IMAGE
  
  # Restart backend container
  echo "Restarting backend container..."
  docker compose -f docker-compose.prod.yml up -d --no-deps backend
  
  # Wait for container to be healthy
  echo "Waiting for backend to be healthy..."
  sleep 10
  
  # Check health
  HEALTH=$(curl -s http://localhost:8080/actuator/health | jq -r '.status')
  if [ "$HEALTH" != "UP" ]; then
    echo "ERROR: Backend health check failed"
    docker logs gms-backend --tail 50
    exit 1
  fi
  
  echo "Backend is healthy"
  
  # Clean up old images
  echo "Cleaning up old Docker images..."
  docker image prune -f
  
  echo "Deployment completed successfully at $(date)"
  ```

- Make script executable:
  ```bash
  chmod +x deploy.sh
  ```

- Test deployment script:
  ```bash
  ./deploy.sh latest
  ```

**Deliverables:**
- [ ] Deployment script created
- [ ] Script tested successfully
- [ ] Backend deployed with new image

**Dependencies:** Task 2.2 complete (GHCR configured)

#### Task 4.6: Set Up CI/CD Pipeline (GitHub Actions)

**Actions:**
- Create GitHub Actions workflow:
  ```bash
  cd /path/to/gms/repository
  nano .github/workflows/deploy-production.yml
  ```

- Add workflow content:
  ```yaml
  name: Deploy to Production
  
  on:
    push:
      branches:
        - master
  
  jobs:
    deploy:
      runs-on: ubuntu-latest
      steps:
        - name: Checkout code
          uses: actions/checkout@v4
        
        - name: Set up Docker Buildx
          uses: docker/setup-buildx-action@v3
        
        - name: Log in to GitHub Container Registry
          uses: docker/login-action@v3
          with:
            registry: ghcr.io
            username: ${{ github.actor }}
            password: ${{ secrets.GITHUB_TOKEN }}
        
        - name: Build and push Docker image
          uses: docker/build-push-action@v5
          with:
            context: ./gms_backend
            push: true
            tags: |
              ghcr.io/${{ github.repository_owner }}/gms-backend:latest
              ghcr.io/${{ github.repository_owner }}/gms-backend:${{ github.sha }}
        
        - name: Deploy to VPS
          uses: appleboy/ssh-action@v1.0.0
          with:
            host: ${{ secrets.VPS_HOST }}
            username: ${{ secrets.VPS_USERNAME }}
            key: ${{ secrets.VPS_SSH_KEY }}
            script: |
              cd ~/gms-production
              ./deploy.sh ${{ github.sha }}
  ```

- Add GitHub secrets:
  - Go to repository settings → Secrets and variables → Actions
  - Add secrets:
    - `VPS_HOST`: `<vps-ip-address>`
    - `VPS_USERNAME`: `deploy`
    - `VPS_SSH_KEY`: (contents of `~/.ssh/id_rsa` on your local machine)

- Test workflow:
  - Push to `master` branch
  - Check GitHub Actions tab for workflow run
  - Verify deployment successful

**Deliverables:**
- [ ] GitHub Actions workflow created
- [ ] Secrets configured
- [ ] Workflow tested successfully
- [ ] Automated deployment working

**Dependencies:** Tasks 2.2, 4.5 complete

#### Task 4.7: Document Procedures

**Actions:**
- Create operations runbook:
  ```bash
  nano docs/OPERATIONS_RUNBOOK.md
  ```

- Document procedures:
  - **Deployment:**
    - Manual deployment: `./deploy.sh <git-sha>`
    - Automated deployment: Push to `master` triggers GitHub Actions
  
  - **Rollback:**
    - Identify previous Git SHA: `git log --oneline -10`
    - Rollback deployment: `./deploy.sh <previous-sha>`
  
  - **Backup:**
    - Manual backup: `./backup.sh`
    - Automated backup: Daily at 2 AM via cron
    - Backup location: `/backups/`
    - Off-site storage: (if configured)
  
  - **Restore:**
    - List backups: `ls -lh /backups/`
    - Restore from backup: `gunzip -c /backups/gms_backup_YYYYMMDD_HHMMSS.sql.gz | psql $DB_URL`
  
  - **Monitoring:**
    - UptimeRobot dashboard: https://uptimerobot.com
    - Backend logs: `docker logs gms-backend --follow`
    - Nginx logs: `sudo tail -f /var/log/nginx/error.log`
  
  - **Troubleshooting:**
    - Backend not starting: Check logs with `docker logs gms-backend`
    - Health check failing: Verify database connection, check environment variables
    - CORS errors: Verify `CORS_ALLOWED_ORIGINS` in `.env`
    - SSL certificate expired: Run `sudo certbot renew`

- Create disaster recovery plan:
  ```bash
  nano docs/DISASTER_RECOVERY_PLAN.md
  ```

- Document scenarios:
  - **VPS failure:**
    - Provision new VPS
    - Restore from backups
    - Update DNS records
    - RTO: 1–2 hours
  
  - **Database corruption:**
    - Restore from Neon backup (point-in-time recovery)
    - Or restore from `pg_dump` backup
    - RTO: 15–30 minutes
  
  - **Complete infrastructure failure:**
    - Provision new VPS
    - Restore database from backups
    - Redeploy frontend to Vercel
    - Update DNS records
    - RTO: 4–8 hours

**Deliverables:**
- [ ] Operations runbook created
- [ ] Disaster recovery plan created
- [ ] All procedures documented
- [ ] Troubleshooting guide complete

**Dependencies:** Tasks 4.1–4.6 complete

### 4.3 Acceptance Criteria

- [ ] UptimeRobot monitoring active (frontend and backend)
- [ ] Automated daily backups configured and tested
- [ ] (Optional) Off-site backup storage configured
- [ ] Deployment script created and tested
- [ ] CI/CD pipeline configured and tested
- [ ] Operations runbook documented
- [ ] Disaster recovery plan documented

### 4.4 Risks and Mitigations

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Backup fails | Low | Critical | Test backup restore quarterly, monitor backup logs |
| Deployment script fails | Medium | High | Test deployment in staging, keep manual deployment option |
| CI/CD pipeline fails | Medium | High | Monitor GitHub Actions, fix errors quickly |
| Monitoring alerts missed | Low | Medium | Configure multiple alert channels (email, SMS, Slack) |
| Documentation incomplete | Medium | Medium | Review documentation with checklist, update regularly |

### 4.5 Rollback Plan

**If monitoring setup fails:**
- Use alternative monitoring service (e.g., Pingdom, StatusCake)
- Or set up self-hosted monitoring (e.g., Prometheus + Grafana)

**If backup fails:**
- Use Neon's automated backups (point-in-time recovery)
- Or manually backup with `pg_dump`

**If CI/CD pipeline fails:**
- Deploy manually using `./deploy.sh` script
- Fix pipeline errors and retry

---

## Phase 5: Production Launch (Days 11–14)

**Duration:** 8–12 hours (1–2 full working days)  
**Prerequisites:** Phases 1–4 complete  
**Owner:** Solo developer + stakeholders

### 5.1 Objectives

1. Final security review
2. Performance testing
3. User acceptance testing
4. DNS cutover
5. Production launch
6. Post-launch monitoring

### 5.2 Tasks

#### Task 5.1: Final Security Review

**Actions:**
- Review firewall rules:
  ```bash
  sudo ufw status
  ```
  - Verify only ports 22, 80, 443 are open

- Review SSL configuration:
  ```bash
  curl -I https://api.yourdomain.com
  ```
  - Verify `Strict-Transport-Security` header present
  - Verify SSL certificate valid

- Review environment variables:
  ```bash
  cat ~/gms-production/.env
  ```
  - Verify no hardcoded secrets
  - Verify JWT secret is strong (48+ characters)
  - Verify database password is strong

- Review CORS configuration:
  - Verify `CORS_ALLOWED_ORIGINS` includes only production domains
  - Verify no `*` wildcard

- Review superadmin account:
  - Verify `APP_SUPERADMIN_ENABLED=false` after initial setup
  - Verify superadmin password is changed from default

- Run security scan (optional):
  - Use tools like OWASP ZAP, Nikto, or Qualys SSL Labs

**Deliverables:**
- [ ] Firewall rules reviewed and verified
- [ ] SSL configuration verified
- [ ] Environment variables reviewed
- [ ] CORS configuration verified
- [ ] Superadmin account secured
- [ ] (Optional) Security scan completed

**Dependencies:** Phases 1–4 complete

#### Task 5.2: Performance Testing

**Actions:**
- Load testing (optional but recommended):
  - Use tools like Apache JMeter, k6, or Locust
  - Simulate 100 concurrent users
  - Measure response times
  - Identify bottlenecks

- Response time benchmarks:
  - Login: < 2 seconds
  - Dashboard load: < 3 seconds
  - Student list: < 2 seconds
  - API calls: < 1 second

- Resource utilization:
  - Check VPS CPU usage: `top` or `htop`
  - Check VPS memory usage: `free -h`
  - Check VPS disk usage: `df -h`
  - Check database connections: Neon dashboard

- Optimize performance:
  - Enable Gzip compression (already configured in Nginx)
  - Optimize database queries (check slow query logs)
  - Cache static assets (already configured in Nginx)

**Deliverables:**
- [ ] Load testing completed (optional)
- [ ] Response time benchmarks measured
- [ ] Resource utilization verified
- [ ] Performance optimizations applied

**Dependencies:** Phases 1–4 complete

#### Task 5.3: User Acceptance Testing (UAT)

**Actions:**
- Create test accounts:
  - Superadmin: `admin@yourdomain.com`
  - School admin: `schooladmin@yourdomain.com`
  - Teacher: `teacher@yourdomain.com`
  - Student: `student@yourdomain.com`
  - Parent: `parent@yourdomain.com`

- Test core workflows:
  - Login and password change
  - Student registration
  - Attendance marking
  - Grade entry
  - Fee payment
  - Report generation

- Test role-based access:
  - Verify each role can only access authorized features
  - Verify cross-tenant isolation

- Test error scenarios:
  - Invalid login credentials
  - Unauthorized access attempts
  - Network failures
  - Database connection issues

- Collect feedback:
  - Survey stakeholders
  - Document issues and improvements
  - Prioritize fixes

**Deliverables:**
- [ ] Test accounts created
- [ ] Core workflows tested
- [ ] Role-based access verified
- [ ] Error scenarios tested
- [ ] Feedback collected and documented

**Dependencies:** Phases 1–4 complete

#### Task 5.4: DNS Cutover

**Actions:**
- Update DNS records (if not already done):
  - `yourdomain.com` → Vercel (CNAME)
  - `api.yourdomain.com` → VPS IP (A record)
  - `www.yourdomain.com` → Vercel (CNAME)

- Wait for DNS propagation:
  - Check with `dig yourdomain.com`
  - Check with `nslookup yourdomain.com`
  - Use online tools like https://dnschecker.org

- Verify DNS resolution:
  ```bash
  # From multiple locations
  ping yourdomain.com
  ping api.yourdomain.com
  ```

- Test accessibility:
  - Open `https://yourdomain.com` in browser
  - Open `https://api.yourdomain.com/actuator/health` in browser
  - Verify both are accessible

**Deliverables:**
- [ ] DNS records updated
- [ ] DNS propagation verified
- [ ] Production URLs accessible

**Dependencies:** Task 0.3 complete (domain purchased)

#### Task 5.5: Production Launch

**Actions:**
- Announce production launch:
  - Send email to stakeholders
  - Post announcement on company intranet
  - Schedule launch meeting

- Monitor launch:
  - Watch UptimeRobot dashboard
  - Monitor backend logs: `docker logs gms-backend --follow`
  - Monitor Nginx logs: `sudo tail -f /var/log/nginx/access.log`
  - Check for errors in application logs

- Verify functionality:
  - Test login
  - Test core workflows
  - Verify data integrity
  - Check performance

- Provide support:
  - Be available for questions
  - Monitor support channels
  - Address issues quickly

**Deliverables:**
- [ ] Production launch announced
- [ ] Launch monitored
- [ ] Functionality verified
- [ ] Support provided

**Dependencies:** Tasks 5.1–5.4 complete

#### Task 5.6: Post-Launch Monitoring (Days 14–30)

**Actions:**
- Daily monitoring:
  - Check UptimeRobot dashboard
  - Review backend logs
  - Monitor resource utilization
  - Check backup logs

- Weekly reviews:
  - Analyze performance metrics
  - Review error logs
  - Check database growth
  - Update documentation

- Monthly maintenance:
  - Update system packages: `sudo apt update && sudo apt upgrade`
  - Update Docker images
  - Rotate secrets (JWT secret, database password)
  - Test disaster recovery procedures
  - Review and update documentation

**Deliverables:**
- [ ] Daily monitoring established
- [ ] Weekly reviews scheduled
- [ ] Monthly maintenance procedures documented
- [ ] Post-launch report created

**Dependencies:** Task 5.5 complete

### 5.3 Acceptance Criteria

- [ ] Security review completed
- [ ] Performance testing completed
- [ ] User acceptance testing completed
- [ ] DNS cutover successful
- [ ] Production launch completed
- [ ] Post-launch monitoring established
- [ ] No critical issues in first 30 days

### 5.4 Risks and Mitigations

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Security vulnerability discovered | Low | Critical | Conduct security review, use security scanning tools |
| Performance issues | Medium | High | Load testing, performance monitoring, optimization |
| Data loss | Very Low | Critical | Daily backups, off-site storage, tested restore |
| Downtime during launch | Low | High | Staged rollout, rollback plan, monitoring |
| User adoption issues | Medium | Medium | Training, documentation, support |

### 5.5 Rollback Plan

**If critical security issue discovered:**
- Take system offline immediately
- Notify stakeholders
- Fix security issue
- Redeploy after verification

**If performance is unacceptable:**
- Scale up VPS resources (vertical scaling)
- Optimize database queries
- Add caching layer
- Consider horizontal scaling (multiple VPS)

**If data corruption occurs:**
- Restore from Neon backup (point-in-time recovery)
- Or restore from `pg_dump` backup
- Investigate root cause
- Implement preventive measures

**If system is unstable:**
- Rollback to previous version: `./deploy.sh <previous-sha>`
- Investigate logs
- Fix issues
- Redeploy after testing

---

## 6. Timeline Summary

| Phase | Duration | Days | Key Deliverables |
|-------|----------|------|------------------|
| Phase 0: Preparation | 2–4 hours | Day 0 | Accounts created, domain purchased, configuration prepared |
| Phase 1: Infrastructure | 8–12 hours | Days 1–2 | VPS provisioned, Docker installed, SSL configured, Neon database created |
| Phase 2: Backend | 8–12 hours | Days 3–4 | Backend deployed, migrations run, API accessible |
| Phase 3: Frontend | 6–8 hours | Days 5–6 | Frontend deployed to Vercel, custom domain configured |
| Phase 4: Monitoring & CI/CD | 12–16 hours | Days 7–10 | Monitoring active, backups automated, CI/CD pipeline working |
| Phase 5: Launch | 8–12 hours | Days 11–14 | Security review, UAT, DNS cutover, production launch |
| Post-Launch | Ongoing | Days 14–30 | Monitoring, maintenance, optimization |

**Total Duration:** 4–6 weeks (including buffer for issues and testing)  
**Total Effort:** 44–64 hours (5.5–8 full working days)

---

## 7. Budget Summary

### 7.1 One-Time Costs

| Item | Cost | Notes |
|------|------|-------|
| Domain name | $10–$15/year | Optional (can use subdomain) |
| **Total** | **$10–$15** | **One-time setup** |

### 7.2 Monthly Costs

| Component | Provider | Cost | Notes |
|-----------|----------|------|-------|
| Frontend hosting | Vercel | $0 | Free tier |
| Backend hosting | DigitalOcean | $6 | 1 GB RAM, 1 vCPU |
| Database | Neon | $0 | Free tier (0.5 GB) |
| SSL certificate | Let's Encrypt | $0 | Free |
| Monitoring | UptimeRobot | $0 | Free tier |
| Backups | Backblaze B2 | $0.01 | ~1 GB storage (optional) |
| **Total** | | **$6.01/month** | **Well within budget** |

### 7.3 Annual Costs

| Component | Annual Cost |
|-----------|-------------|
| Infrastructure | $72.12 |
| Domain name | $10–$15 |
| **Total** | **$82–$87/year** |

### 7.4 Scaling Costs (Future)

| Scenario | Additional Cost | Trigger |
|----------|-----------------|---------|
| 2x VPS resources | +$6/month | CPU > 80% or RAM > 80% |
| Neon paid plan (10 GB) | +$19/month | Database > 400 MB |
| Load balancer + 2 VPS | +$18/month | Concurrent users > 1,000 |
| Managed PostgreSQL | +$15/month | Need for read replicas |
| **Total at 10,000 students** | **~$50–$60/month** | **Scale as needed** |

---

## 8. Success Metrics

### 8.1 Technical Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| Uptime | 99.5% | UptimeRobot dashboard |
| Response time (API) | < 1 second | Backend logs, monitoring |
| Response time (frontend) | < 3 seconds | Browser dev tools, monitoring |
| Backup success rate | 100% | Backup logs |
| Deployment success rate | > 95% | GitHub Actions logs |
| Error rate | < 1% | Application logs, monitoring |

### 8.2 Business Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| User adoption | 80% of target users | User registration data |
| User satisfaction | > 4/5 | User surveys |
| Support tickets | < 5/week | Support system |
| Data integrity | 100% | Database audits |
| Security incidents | 0 | Security logs, monitoring |

### 8.3 Operational Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| Deployment frequency | Weekly | GitHub Actions logs |
| Rollback frequency | < 5% of deployments | Deployment logs |
| Mean time to recovery (MTTR) | < 2 hours | Incident logs |
| Backup restore time | < 30 minutes | DR drill results |
| Documentation completeness | 100% | Documentation review |

---

## 9. Conclusion

This deployment roadmap provides a **clear, phased approach** to moving GMS from development to production. The plan is designed to be:

- **Simple:** Single VPS, managed services, minimal complexity
- **Cost-effective:** $6/month total infrastructure cost
- **Reliable:** Automated backups, monitoring, disaster recovery
- **Scalable:** Vertical and horizontal scaling paths defined
- **Maintainable:** Comprehensive documentation, automated deployments

**Key Success Factors:**
1. Follow phases in order (don't skip steps)
2. Test thoroughly at each phase
3. Document everything
4. Monitor closely after launch
5. Be prepared to rollback if needed

**Next Steps:**
1. Review and approve this roadmap
2. Begin Phase 0 (preparation)
3. Execute phases sequentially
4. Launch production system
5. Monitor and optimize

**Final Recommendation:** **GO** for production deployment following this roadmap.

The architecture is appropriate for the pilot scale (2,000 students), the cost is within budget ($6/month), and the complexity is manageable for a solo developer. The phased approach minimizes risk and allows for course correction at each stage.

---

**Document Version:** 1.0  
**Last Updated:** 2026-11-03  
**Next Review:** After Phase 1 completion  
**Roadmap Status:** READY FOR EXECUTION
