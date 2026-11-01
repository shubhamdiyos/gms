# GMS documentation index

One place to say which document is authoritative for what, and which ones are kept only because deleting history
would lose it. Written on 2026-11-01 under the third MASTER ENGINEERING MANDATE §4, which asks for an index and a
reconciliation matrix and forbids duplicate documentation — so this file is the **only** place that classifies the
docs, and every other file stays responsible for its own subject.

**Citation key.** Four mandates have contributed to these documents and the first three have colliding section
numbers, so a bare `§` is ambiguous. The convention: **an unqualified `mandate §n` means the mandate that register
was written under** — for `INFRASTRUCTURE_AUDIT.md`, `INFRASTRUCTURE_DECISIONS.md` and
`PRODUCTION_READINESS_MATRIX.md` that is the second one (864 lines, `# 1. YOUR ROLE` … `# 19. FINAL PRINCIPLE`, the
author of every `INF-*` id); for `REPOSITORY_AUDIT.md`, `SECURITY_AUDIT.md` and `BASELINE_STATUS.md` it is the first
(1,762 lines, `# 1. PRODUCT VISION` … `# 27. REQUIRED FINAL REPORT`, author of `SEC-*` and the M1 record). Anything
cited from the third is written **`third mandate §n`** (1,551 lines). The fourth — the **"GMS — Engineering Execution
Mandate"**, 2026-11-01, the author of [ENGINEERING_ROADMAP.md](ENGINEERING_ROADMAP.md) — is cited by **name and part**
(`execution mandate Priority 2`, `execution mandate Stage 6`, `execution mandate: Definition of Done`) because it is
not numbered in sections; it has five priorities, a list of execution rules, a seven-row stage table and one DoD
sentence. Never write a bare `§n` for it: there is no §n to resolve to.

The convention is not a guess about the existing text — every unqualified `mandate §n` in the three infrastructure
registers was checked against the actual headings of the 864-line mandate: §2 non-negotiable constraints (zero-cost),
§4.5 compose architecture, §5 environments, §7 deployment, §9 persistence and the "never store production data only
in an ephemeral container" line, §11 hardening, §12 disaster recovery and operations, §13 infrastructure testing,
§17 current project findings — all match what the registers cite them for. Five citations did **not** belong to that
mandate and are now qualified: three `§25` references that are the first mandate's *Critical Operational
Restrictions*, `§8.1` for the Neon account (third mandate), and the workflow-validation stage — `§6.1 stage 1` of the
Infra mandate, which is `third mandate §12` ("Workflow self-validation"). Collisions worth knowing about: backup and
DR is Infra §12 but third-mandate §15; CI/CD is Infra §6 but third-mandate §12; cost policy is Infra §2 but
third-mandate §20.

Markdown files are counted by the scan in §3, which is the command that produces the number rather than a claim
about it. Re-run it after adding any document: it currently covers `docs/**`, `infra/**`, root `*.md`,
`gms_backend/memory-bank/*.md` and `gms_frontend/README.md` — **70 files, 0 broken links** (67 before Stage 1 added
[ENGINEERING_ROADMAP.md](ENGINEERING_ROADMAP.md), [PRODUCT_REQUIREMENTS.md](PRODUCT_REQUIREMENTS.md) and
[ARCHITECTURE.md](ARCHITECTURE.md)). The two broken links this index was written to fix are listed in §3.

## 1. Canonical — the source of truth

Read these first. If any other document contradicts one of them, the other document is wrong.

| Document | Owns | Finding ids | Status |
|---|---|---|---|
| [ENGINEERING_ROADMAP.md](ENGINEERING_ROADMAP.md) | **The one sequence**: stages 1–7, per-stage acceptance criteria, the six decomposed clauses of the definition of done, the standing constraints, and the mapping that absorbs the three older milestone series | names every other file's ids; owns none | Current, **and nothing past Stage 1 has started** |
| [PRODUCT_REQUIREMENTS.md](PRODUCT_REQUIREMENTS.md) | Personas, roles, journeys, business requirements, the **frozen MVP scope** and its non-goals, the feature matrix, non-functional targets, open owner decisions | `BR-*` (new), `P-*`, `J-*`, `D-*`, `NFR-*` local to it | Current, Stage-1 deliverable |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Module map, data ownership, dependency direction, the tenancy mechanism, API contract standards, and the index/constraint design sized for ≥50,000 records per school — **§5.1 states that design as the V2–V6 migration DDL, and §5.2 records the run that corrected it** | `D-*`/`I-*` rules local to it; cites `TEN-*`, `DB-*`, `SEC-*` | Current, Stage-1 deliverable. The DDL **has** executed once, against a schema derived from the entity files (`§5.2`); it has never run against a real baseline or populated tables, which is Stage 2's job |
| [audit/REPOSITORY_AUDIT.md](audit/REPOSITORY_AUDIT.md) | The application's audit: what the code is, what it does, what is broken; the M1 remediation record | `SEC-*`, `BKL-*`, `OPS-*`, `TEN-*`, `FE-*`, `ACD-*`, `AP-*`; cites `DB-*` (defined in `TECHNICAL_DEBT §1`) | Current — `DB-05` re-measured and `DB-07` added by Stage 1 |
| [audit/SECURITY_AUDIT.md](audit/SECURITY_AUDIT.md) | Exposure model and severity-ranked security findings | `SEC-*` by severity | Current, with §1 amended for the EC2 retirement |
| [audit/TECHNICAL_DEBT.md](audit/TECHNICAL_DEBT.md) | Debt register and sequencing for the application | cross-references by id | Current |
| [audit/BASELINE_STATUS.md](audit/BASELINE_STATUS.md) | The measured baseline, the verification matrix, and the ordered owner action list | `B*`, `F*`, `R*`, `I*` evidence tags | Current |
| [infrastructure/INFRASTRUCTURE_AUDIT.md](infrastructure/INFRASTRUCTURE_AUDIT.md) | **The central platform findings register** — containers, CI/CD, runtime, network, cost, and the milestone plan | `INF-01…23` | Current |
| [infrastructure/PRODUCTION_READINESS_MATRIX.md](infrastructure/PRODUCTION_READINESS_MATRIX.md) | The scorecard: ten areas, five statuses, and what would change the verdict | area → finding ids | Current |
| [infrastructure/INFRASTRUCTURE_DECISIONS.md](infrastructure/INFRASTRUCTURE_DECISIONS.md) | Architecture decisions and the alternatives rejected | `ADR-001…023` | Current, including the third-mandate and execution-mandate amendments |
| [../infra/oci/README.md](../infra/oci/README.md) | **How to run the OCI stack** — the first-time authentication step, the order of commands, what the HCL guards enforce, and the four cost numbers that must be read from the account before `apply` | implements `ADR-015`, `ADR-018` | Current, **and nothing in it has been applied** — by plan, not by accident: `ADR-019` puts provisioning at Stage 6 |
| [../README.md](../README.md) | Setup, commands, and the honest state of testing | links out | Current |

**Rule:** a new finding goes into the register that owns its namespace, not into a new file. `INF-*` (platform) and
`ADR-*` (decisions) are the namespaces the registers accept from anywhere; `SEC-17` therefore went into
`REPOSITORY_AUDIT.md`, not into the PRD that found it. **New since Stage 1:** `BR-*` (business requirements) belongs to
[PRODUCT_REQUIREMENTS.md](PRODUCT_REQUIREMENTS.md), and the local id families inside those two documents — `P-*`
personas, `J-*` journeys, `D-*`/`NFR-*` in the PRD, `D-*`/`I-*` ownership and index rules in
[ARCHITECTURE.md](ARCHITECTURE.md) — are **not** findings and are not to be copied into the registers. An id that
appears in two places is a defect in this index, not a convenience.

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
| Oracle Cloud Always Free ARM64 + Neon (owner-owned) + Caddy | **Target, deliberately not provisioned.** The infrastructure *definitions* exist and validate in [`infra/oci/`](../infra/oci/README.md); the account has never been read and no resource has been created. As of 2026-11-01 this is a **plan, not a gap**: `ADR-019` moves provisioning to roadmap Stage 6, and Stages 1–5 need zero cloud resources (Compose + PostgreSQL locally, Testcontainers in CI) | `ADR-019`, then `ADR-015`, `ADR-016`, `ADR-003`, `ADR-018` |

## 3. Link validation

`docs/**`, `infra/**`, root and the memory-bank were scanned for relative Markdown links. **2** were broken when
this index was written, both fixed in the branch that added it; the same scan now reports **0** across 70 files.

The Stage-1 branch checked two further things that the command below does not: **in-page and cross-page anchors**
(every `#slug`, resolved with GitHub's own slug rule — lowercase, punctuation dropped except hyphens, spaces to
hyphens) and **table shape** (every row of a table block carrying the same pipe count, with `\|` escapes stripped
first). Result: 0 bad anchors and 0 ragged tables over 977 table rows. That check was run by a script written in the
session and **not committed to the repository**, which is the same weakness `ADR-018` closed for the OpenTofu guards
— a claim re-checkable only by its author. Promoting a Markdown structural check into `ci.yml` is therefore a Stage-5
candidate, listed here so the gap has a home instead of a memory.

| Broken link | Cause | Fix |
|---|---|---|
| `README.md` → `LICENSE` | The README claimed "licensed under the MIT License" and linked a file that has never existed | §License rewritten to state that **no license has been chosen**. Picking one is an owner decision with legal effect, not a documentation cleanup |
| `docs/audit/SECURITY_AUDIT.md` → `gms_frontend/vercel.json` | The file was deleted when EC2 was retired, breaking the link | Sentence converted to past tense with the retirement recorded and cross-linked to §10 of the infrastructure audit |

Re-run the scan with:

```bash
python3 - <<'PY'
import re,os,glob
files=[f for f in glob.glob('docs/**/*.md',recursive=True)+glob.glob('*.md')
       +glob.glob('infra/**/*.md',recursive=True)
       +glob.glob('gms_backend/memory-bank/*.md')+['gms_frontend/README.md']]
files=[f for f in dict.fromkeys(files) if os.path.exists(f)]
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
`OPERATIONS_GUIDE.md` · the API reference (OpenAPI, generated from the code rather than typed by hand) ·
the testing strategy and evidence log · the operational database and migration guide.

**Two entries were removed from this list on 2026-11-01**, because Stage 1 wrote them: *the product requirements and
feature matrix* is now [PRODUCT_REQUIREMENTS.md](PRODUCT_REQUIREMENTS.md) §7–§8, and the *database* half is designed
in [ARCHITECTURE.md](ARCHITECTURE.md) §4–§5 and §9 — which is a design, not the operations guide that can only be
written against a database that exists. The *architecture* and *ADRs* and *roadmap* halves of debt package 13
(`TECHNICAL_DEBT.md §2`) are therefore also discharged. Nothing else on this list is written, and Stage 2 does not
begin until a reviewer has read the three new documents.

**What Stage 1 did not do, stated so the gap is visible:** it changed no application code, ran no test that had not
run before, and proved no defect. Every quantitative claim in the three documents is a `grep`/`find`/`ls` over
`master` at `bcbb71c` on 2026-11-01, and `ARCHITECTURE.md §1` lists the command shape per row precisely so the next
reader can re-run rather than trust. The design in `§5` and the mechanism in `§4` are **proposed until Stage 2 and
Stage 3 execute them** — `implemented` ≠ `tested` ≠ `verified` applies to a document as much as to a patch.
