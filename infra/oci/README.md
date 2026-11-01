# `infra/oci` — the Always Free production stack

OpenTofu definitions for third mandate §9: the VCN, internet gateway, public
subnet, security list and one `VM.Standard.A1.Flex` instance in `ap-mumbai-1`.
This file is the only place that describes how to run this stack; the findings
about *why* the platform looks the way it does stay in
[`docs/infrastructure/INFRASTRUCTURE_AUDIT.md`](../../docs/infrastructure/INFRASTRUCTURE_AUDIT.md),
and nothing here restates them.

**Status: nothing in this directory has been applied.** No OCI resource exists
yet, no credentials are configured on the machine that wrote it, and §9.1's
eligibility/capacity/public-IP-cost verification has not been performed. Until
that verification is recorded, "Always Free" is a requirement in a mandate, not
a measured property of an account.

## Tooling of record

| Tool | Version | How installed | Why |
|---|---|---|---|
| `oci` CLI | 3.95.1 | `~/.venvs/oci-cli` (virtualenv) | official; `brew` is unusable on this macOS build (`unknown or unsupported macOS version: "27.0.1"`) |
| OpenTofu | v1.13.2 | `~/.local/bin/tofu`, from `tofu_1.13.2_darwin_arm64.zip`, SHA-256 `cb46112c…93af61` verified against the published `tofu_1.13.2_SHA256SUMS` | MPL-2.0. Terraform switched to BUSL in Aug 2023; OpenTofu is the fork under a free licence, and its registry serves the same `oracle/oci` provider (resolved to **9.11.0** here, recorded in the committed `.terraform.lock.hcl`) |

## First time — the one step that needs a human

`oci setup config` accepts **no** command-line options (checked against this
install: `--help` lists only `-h`). It is interactive by design, so these five
lines are the only part of the OCI path that cannot be executed here. Type them
in a terminal, never paste anything into a chat window.

```bash
mkdir -p ~/.oci
~/.venvs/oci-cli/bin/oci setup config
```

Answer the prompts with values you read out of the Console yourself:

| Prompt | Where it comes from |
|---|---|
| user OCID | Console → Profile → `(your name)` → **View API Keys** → your `ocid1.user…` (not a secret, but do not post it anywhere) |
| tenancy OCID | Console → Profile → Tenancy → `ocid1.tenancy…` |
| region | `ap-mumbai-1` |
| API signing key | let the CLI **generate** it when asked — it writes `~/.oci/oci_api_key_public.pem` and the private half, and only the **public** file is ever uploaded |
| passphrase | yours, typed once in the terminal. The private key is meaningless without it and it should never exist in a chat log, a PR, or a shell history line |

Then upload the **public** key: Console → Profile → **API Keys** → Add API Key →
paste the contents of `~/.oci/oci_api_key_public.pem`. Confirm the config works
with the cheapest read-only call there is:

```bash
~/.venvs/oci-cli/bin/oci iam region list --output table
```

If that returns a table, everything after this point is scriptable.

## Then, in order

```bash
cd infra/oci
cp terraform.tfvars.example terraform.tfvars     # gitignored; edit the three required values
tofu init
./preflight.sh --compartment "$(grep compartment_ocid terraform.tfvars | cut -d'"' -f2)"
tofu plan                     # read it. This is the "inspect before applying" step.
tofu apply                    # only after the plan is understood and the cost is verified
```

`preflight.sh` is read-only — every call is a GET or a LIST. It prints the
tenancy, home region, region subscriptions, availability domains, compartment
tree, compute limits and ARM capacity, then checks whether `gms-prod-vcn`,
`gms-prod-public-subnet`, `gms-prod-igw` or `gms-prod-api-01` **already exist**
so the answer is `tofu import` or a rename rather than a second copy.

## What is guarded in HCL, not in prose

| Guard | Where | Fails as |
|---|---|---|
| The shape cannot be changed to a paid one | `local.shape` in [data.tf](data.tf) — not a variable | no failure; there is no argument that would select another shape |
| `ssh_source_cidr` must be exactly one IPv4 `/32` | `validation` in [variables.tf](variables.tf) | plan error. `0.0.0.0/0` and any wider prefix are refused, because "do not leave broad SSH access open" (§9.2) is the rule this encodes |
| `ssh_public_key_path` must end in `.pub` | `validation` in [variables.tf](variables.tf) | plan error. Passing a private key path would write that private key into `terraform.tfstate` |
| `compartment_ocid` has no default | [variables.tf](variables.tf) | plan error rather than a silent install into the root compartment |
| Boot volume 50–200 GB, OCPUs 1–4, RAM 1–24 GB | `validation` blocks | plan error. The upper bounds are the Always Free allowance, so exceeding them is the moment the stack starts billing |
| No ingress exists for 5432, 8080 or 3000 | [network.tf](network.tf) | not a check — the ingress list is closed (22, 80, 443, ICMP type 3 code 4 from inside the VCN) and those ports were never added |
| The instance cannot be destroyed by a plan | `lifecycle.prevent_destroy` in [compute.tf](compute.tf) | plan error; removing it is a deliberate edit |
| Ubuntu 24.04 ARM64 must actually exist in the region | `check` block in [data.tf](data.tf) | a readable plan-time failure instead of an index-out-of-range |

Cloud-init ([cloud-init.yaml.tftpl](cloud-init.yaml.tftpl)) carries §9.4:
package upgrade, `docker.io` + `docker-compose-v2` from the Ubuntu archive (no
third-party apt repo, no `curl | sh`), `ufw`, unattended upgrades, journald and
container-log rotation, a non-root `gmsdeploy` user, and a Docker daemon with no
remote listener. Password SSH is disabled **only if an authorized key is
actually present** for that user — §9.4 says "where safe and verified", and an
unconditional drop-in is a lockout.

## CI runs this stack before it reaches `master`

Only markdown in `infra/**` is in `ci.yml`'s `paths-ignore` — the `infra/**/*.md`
line that PR #22 added after PR #20 measured the hole: `*.md` matches one
directory level, so a documentation-only edit that touched
`infra/oci/README.md` started all three CI jobs on the pull request *and* again
on the push to `master`, 140 runner-seconds for prose. No `.tf`, `.tftpl` or
`.sh` path here is ignored, so any real change to this stack still starts the
**Infrastructure definitions** job. It installs OpenTofu from the official
release archive and refuses to unpack it unless the digest matches the
publisher's `SHA256SUMS`, then runs `tofu fmt -check`, `tofu init
-backend=false`, `tofu validate`, the seven-guard suite and the forbidden-port
check. No third-party action, no credentials, no cloud call.

Three things learned the hard way while writing that job, because all three would
otherwise be re-learned by the next person:

- **`tofu validate` does not evaluate `validation` blocks.** All seven invalid
  values return `Success! The configuration is valid` from `validate`; only
  `tofu plan` raises them. The suite therefore runs plans, and reads the
  validation error *next to* the expected `open ~/.oci/config: no such file or
  directory` error.
- **The suite must supply every required variable on every invocation.**
  `ssh_source_cidr` has no default, so a check about `deploy_user` aborts on
  "No value for required variable" before validation runs — which reads as
  "the guard is missing" while the guard is intact. It happened on this job's
  first local run: 2 of 7 passed, 5 failed, all seven guards fine.
- And the matcher must capture output before testing it. `tofu plan | grep -q`
  under `pipefail` makes grep exit on the first match, tofu die of SIGPIPE, and
  the whole pipeline report failure — so every guard reads as broken precisely
  when every guard is working.

If a `validation` block is deleted, this job fails — and that sentence was tested
rather than assumed. PR #19 (closed unmerged, scratch branch deleted) removed the
`/32` guard from `ssh_source_cidr` and nothing else; run 41289417174 came back
green through `fmt`, `init` and **`validate`**, then red at the guard step on
exactly the two SSH-source assertions, with the other five still `ok`. The rules
in §9 stop being enforceable-by-habit the moment the file is bigger than the
person editing it.

## Deliberately not in this repository

- `terraform.tfstate` — it holds real OCIDs, the assigned public IPv4 and the
  SSH public key inside `metadata`. Ignored by [.gitignore](.gitignore).
- `terraform.tfvars` — the operator's values. The `.example` file is the tracked
  contract.
- Any private key, API key, passphrase or database credential. The provider
  authenticates from `~/.oci/config` via `config_file_profile`; there is no
  attribute in this directory that could hold a secret, so §9.3 and the mandate
  rule against exposing private keys hold by construction rather than by care.
- Remote state. Local state is the honest default until there is a second
  machine or a second person; moving it is a migration with a plan, not a
  one-line backend block added on hope.

## Verification that must be recorded before `apply`

§9.1 forbids assuming anything is free because a tutorial said so. Four numbers
have to come from the account, not from this file, and belong in
`docs/infrastructure/INFRASTRUCTURE_AUDIT.md` once read:

1. Always Free **ARM OCPU** allowance remaining (the shape wants 2 of 4).
2. Always Free **block volume** bytes remaining (the boot volume wants 100 GB
   of 200 GB).
3. **Public IPv4** charge for an auto-provisioned address on a running Always
   Free instance, and the separate fact that an *unattached reserved* IPv4 bills.
4. The Console's **estimated monthly charge** for this exact configuration.

If any of those four shows a charge, the rule is to stop and report it — not to
proceed and check the bill later.
