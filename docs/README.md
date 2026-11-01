# GMS documentation index

One place to say which document is authoritative for what, and which ones are kept only because deleting history
would lose it. Written on 2026-11-01 under the third MASTER ENGINEERING MANDATE §4, which asks for an index and a
reconciliation matrix and forbids duplicate documentation — so this file is the **only** place that classifies the
docs, and every other file stays responsible for its own subject.

**Citation key.** Three mandates have contributed to these documents and their section numbers do not line up, so a
bare `§` is ambiguous. The convention: **an unqualified `mandate §n` means the mandate that register was written
under** — for `INFRASTRUCTURE_AUDIT.md` and `PRODUCTION_READINESS_MATRIX.md` that is the second one (865 lines,
infrastructure/DevOps/CI-CD, the author of every `INF-*` id); for `REPOSITORY_AUDIT.md`, `SECURITY_AUDIT.md` and
`BASELINE_STATUS.md` it is the first (1,763 lines, the security programme, author of `SEC-*` and the M1 record).
Anything cited from the current execution mandate (1,551 lines) is written **`third mandate §n`**, because its
numbering collides with both predecessors — `Infra mandate §6.1 stage 1` ("Workflow validation") is
`third mandate §12`, and `first mandate §25`'s prohibition list is now `third mandate §2`.

66 Markdown files were scanned (`docs/**`, `gms_backend/memory-bank/**`, root and `gms_frontend/README.md`,
`.github/pull_request_template.md`). Two internal links were broken; both are fixed and listed in §3.

## 1. Canonical — the source of truth

Read these first. If any other document contradicts one of them, the other document is wrong.

| Document | Owns | Finding ids | Status |
|---|---|---|---|
| [audit/REPOSITORY_AUDIT.md](audit/REPOSITORY_AUDIT.md) | The application's audit: what the code is, what it does, what is broken; the M1 remediation record | `SEC-*`, `BKL-*`, `OPS-*`, `TEN-*`, `FE-*`, `ACD-*`, `AP-*` | Current |
| [audit/SECURITY_AUDIT.md](audit/SECURITY_AUDIT.md) | Exposure model and severity-ranked security findings | `SEC-*` by severity | Current, with §1 amended for the EC2 retirement |
| [audit/TECHNICAL_DEBT.md](audit/TECHNICAL_DEBT.md) | Debt register and sequencing for the application | cross-references by id | Current |
| [audit/BASELINE_STATUS.md](audit/BASELINE_STATUS.md) | The measured baseline, the verification matrix, and the ordered owner action list | `B*`, `F*`, `R*`, `I*` evidence tags | Current |
| [infrastructure/INFRASTRUCTURE_AUDIT.md](infrastructure/INFRASTRUCTURE_AUDIT.md) | **The central platform findings register** — containers, CI/CD, runtime, network, cost, and the milestone plan | `INF-01…23` | Current |
| [infrastructure/PRODUCTION_READINESS_MATRIX.md](infrastructure/PRODUCTION_READINESS_MATRIX.md) | The scorecard: ten areas, five statuses, and what would change the verdict | area → finding ids | Current |
| [infrastructure/INFRASTRUCTURE_DECISIONS.md](infrastructure/INFRASTRUCTURE_DECISIONS.md) | Architecture decisions and the alternatives rejected | `ADR-001…017` | Current, including the third-mandate amendments |
| [../README.md](../README.md) | Setup, commands, and the honest state of testing | links out | Current |

**Rule:** a new finding goes into the register that owns its namespace, not into a new file. New namespaces are
`INF-*` (platform) and `ADR-*` (decisions); everything else already exists.

## 2. Supporting, historical and obsolete

| Document | Classification | Why it is kept | What to distrust |
|---|---|---|---|
| [GMS-Frontend-Development-Guide.md](GMS-Frontend-Development-Guide.md) | **Supporting** | Describes the intended product shape (roles, school-bounded tenancy) and the frontend build approach the mandate §11 redesign must work within | Its tenant-isolation claim ("all data operations are school-bounded") is an **intent**, not the current behaviour — see `TEN-01…07` |
| [GMS-API-Integration-Guide.md](GMS-API-Integration-Guide.md) | **Supporting** | Endpoint contracts and auth flow used by the frontend today | Endpoint-level behaviour is governed by `SEC-*` findings, not by this guide |
| [GMS-Frontend-Component-Architecture.md](GMS-Frontend-Component-Architecture.md) | **Supporting** | Component hierarchy map for the §11 UI audit | Counts and paths drift; verify against `src/` before relying on them |
| [GMS-State-Management-Guide.md](GMS-State-Management-Guide.md) | **Historical** | Written as a spec — it says the store "**will be** organized" — so it records the design intent behind `store/api/apiSlice.js` | Anything phrased as future work; read the code for what exists |
| [GMS-Project-Progress.md](GMS-Project-Progress.md) | **Historical** | A progress report from an earlier phase of the project | Completion claims. Use [PRODUCTION_READINESS_MATRIX.md](infrastructure/PRODUCTION_READINESS_MATRIX.md) and `BASELINE_STATUS.md` for status |
| [WindsurfGMS-Postman-Testing-Guide.md](WindsurfGMS-Postman-Testing-Guide.md) | **Supporting**, already corrected | The only manual API test path (45 requests vs ~230 endpoint mappings); carries its own status note | Nothing — it now states plainly that tenant isolation is **not** enforced |
| `gms_backend/memory-bank/` (50 files) | **Historical** | Prior AI-assisted development context: project brief, system patterns, task summaries | All of it. One page was corrected in security-M1 because it told operators to set a variable that no longer binds (`SEC-15`). Excluded from CI by `ci.yml` paths-ignore |
| [../gms_frontend/README.md](../gms_frontend/README.md) | **Obsolete as project docs**, keep as scaffold notice | It is the unmodified Vite template README | Any impression that it describes GMS |
| `.github/pull_request_template.md` | **Supporting** | PR conventions | — |

### Retired systems

| System | State | Where the removal is recorded |
|---|---|---|
| AWS EC2 host `65.0.109.47` | **Permanently closed by the owner.** Confirmed dead from this machine: DNS resolves, TCP connect to `:8080` times out after 8 s | `INFRASTRUCTURE_AUDIT.md` §10; workflow and Vercel rewrite deleted on `infra/retire-ec2-deployment-path` |
| `.github/workflows/deploy-backend.yml` | **Deleted 2026-11-01** | `INFRASTRUCTURE_AUDIT.md` §4 amendment, `ADR-017` |
| Friend-owned Neon database | **Retired, unrecoverable by design** — no access, no migration, no credential reuse | `ADR-016` |
| Oracle Cloud Always Free ARM64 + Neon (owner-owned) + Caddy | **Target, not provisioned** | `ADR-015`, `ADR-016`, `ADR-003` |

## 3. Link validation

`docs/**` and the memory-bank were scanned for relative Markdown links: 66 files, **2 broken**, both fixed in the
branch that added this index.

| Broken link | Cause | Fix |
|---|---|---|
| `README.md` → `LICENSE` | The README claimed "licensed under the MIT License" and linked a file that has never existed | §License rewritten to state that **no license has been chosen**. Picking one is an owner decision with legal effect, not a documentation cleanup |
| `docs/audit/SECURITY_AUDIT.md` → `gms_frontend/vercel.json` | The file was deleted when EC2 was retired, breaking the link | Sentence converted to past tense with the retirement recorded and cross-linked to §10 of the infrastructure audit |

Re-run the scan with:

```bash
python3 - <<'PY'
import re,os,glob
files=[f for f in glob.glob('docs/**/*.md',recursive=True)+glob.glob('*.md')
       +glob.glob('gms_backend/memory-bank/*.md')+['gms_frontend/README.md']]
bad=[]
for f in files:
    d=os.path.dirname(f)
    for m in re.finditer(r'\[[^\]]*\]\(([^)#]+)(#[^)]*)?\)',open(f,errors='ignore').read()):
        t=m.group(1).strip()
        if t.startswith(('http','mailto:','data:')) or not t: continue
        if not os.path.exists(os.path.normpath(os.path.join(d,t))): bad.append(f"{f} -> {t}")
print(len(bad),"broken"); [print(" ",b) for b in bad]
PY
```

## 4. Not yet written

The third mandate's §4 lists these documents as required, and its §19 Phase 7 says "Document deployment and
recovery runbooks". They do not exist yet, and they will be written by the milestone that makes them true, not
before — a runbook for a pipeline that does not exist would be fiction:

`DEPLOYMENT_RUNBOOK.md` · `ROLLBACK_RUNBOOK.md` · `BACKUP_AND_RESTORE.md` · `INCIDENT_RESPONSE.md` ·
`OPERATIONS_GUIDE.md` · the product requirements and feature matrix · the API reference (OpenAPI, generated from
the code rather than typed by hand) · the testing strategy and evidence log · the database and migration guide.
