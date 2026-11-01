#!/usr/bin/env bash
#
# preflight.sh — read-only OCI inspection for the gms-prod stack.
#
# Every OCI call below is a GET or a LIST. Nothing here creates, changes or
# deletes anything, which is what makes it safe to run before the tenancy is
# understood. It answers the four questions §9.1 and the owner's own
# instructions require answered before the first `tofu apply`:
#
#   1. Which tenancy, which home region, which compartments actually exist?
#   2. Does ap-mumbai-1 appear in this tenancy's region subscriptions at all?
#   3. What are the compute limits this stack consumes (ARM OCPUs, block storage)?
#   4. Are the resources this stack would create ALREADY present, so the apply
#      becomes an import-or-rename instead of a duplicate?
#
# Usage:
#   ./preflight.sh
#   ./preflight.sh --compartment ocid1.compartment...   # add the duplicate check
#   TARGET_REGION=us-phoenix-1 ./preflight.sh
#
# Requires the oci CLI, configured by `oci setup config` — interactive, and the
# only step in this programme that cannot be automated.

set -uo pipefail

TARGET_REGION="${TARGET_REGION:-ap-mumbai-1}"
COMPARTMENT=""

while [ $# -gt 0 ]; do
  case "$1" in
    --compartment) COMPARTMENT="${2:-}"; shift 2 ;;
    --region) TARGET_REGION="${2:-}"; shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

hr() { printf '\n\033[1m── %s ──\033[0m\n' "$1"; }

OCI="oci"
command -v oci >/dev/null 2>&1 || OCI="$HOME/.venvs/oci-cli/bin/oci"
if [ ! -x "$OCI" ] && [ ! -e "$OCI" ] && ! command -v oci >/dev/null 2>&1; then
  echo "oci CLI not found on PATH or at ~/.venvs/oci-cli/bin/oci." >&2
  exit 127
fi

CONFIG_FILE="${OCI_CONFIG_FILE:-$HOME/.oci/config}"

hr "Tooling"
echo "  oci  : $("$OCI" --version 2>&1 | head -1)"
if command -v tofu >/dev/null 2>&1; then
  echo "  tofu : $(tofu version 2>/dev/null | head -1)"
else
  echo "  tofu : not on PATH"
fi

hr "Authentication"
if [ ! -f "$CONFIG_FILE" ]; then
  echo "  NOT CONFIGURED — $CONFIG_FILE does not exist."
  echo
  echo "  This is the one step that cannot be scripted. \`oci setup config\`"
  echo "  accepts no options (verified against this install: its --help lists only"
  echo "  -h/-?), so a human has to run it in a terminal and type the OCIDs and the"
  echo "  key passphrase. README.md section 'First time' is the exact sequence."
  exit 3
fi
if ! "$OCI" iam region list --output table >/dev/null 2>&1; then
  echo "  Config file exists but an authenticated call failed. Debug with:"
  echo "    $OCI iam region list --debug"
  echo "  Most common causes: the public key was never uploaded under Console →"
  echo "  Profile → API Keys, or the passphrase is being asked for in a shell that"
  echo "  cannot answer."
  exit 4
fi
echo "  OK — $CONFIG_FILE authenticates."

ROOT=$(awk -F= '/^tenancy[[:space:]]*=/{print $2; exit}' "$CONFIG_FILE")
PROFILE_REGION=$(awk -F= '/^region[[:space:]]*=/{print $2; exit}' "$CONFIG_FILE")
echo "  tenancy OCID in config : ${ROOT:-<absent>}"
echo "  region in config       : ${PROFILE_REGION:-<absent>}   (stack targets $TARGET_REGION)"

hr "Regions enabled for this account"
"$OCI" iam region list --output table 2>/dev/null | sed 's/^/  /'

hr "Region subscriptions"
"$OCI" iam region-subscription list --output table 2>/dev/null | sed 's/^/  /'
if ! "$OCI" iam region-subscription list --output json 2>/dev/null \
    | python3 -c 'import json,sys; d=json.load(sys.stdin); sys.exit(0 if any(r.get("region")=="'"$TARGET_REGION"'" for r in d) else 1)'; then
  echo "  ⚠ $TARGET_REGION is not in the subscription list. An apply would fail with"
  echo "    an unsupported-region error, not a capacity error. §9.3: do not switch"
  echo "    regions silently — surface this instead of pointing the stack elsewhere."
fi

hr "Home region vs target region"
"$OCI" iam tenancy get --tenancy-id "$ROOT" --output json 2>/dev/null \
  | python3 -c 'import json,sys; d=json.load(sys.stdin); print("  name        :", d.get("name")); print("  home region :", d.get("homeRegionKey")); print("  description :", d.get("description"))' 2>/dev/null
if [ -n "${PROFILE_REGION:-}" ] && [ "$PROFILE_REGION" != "$TARGET_REGION" ]; then
  echo "  note: the CLI profile defaults to $PROFILE_REGION. tofu passes"
  echo "        region = $TARGET_REGION explicitly, so the two do not have to match —"
  echo "        but a mismatch is worth reading once rather than living with."
fi

hr "Availability domains in $TARGET_REGION"
"$OCI" iam availability-domain list --region "$TARGET_REGION" --output table 2>/dev/null | sed 's/^/  /'

hr "Compartments (compartment_ocid comes from here)"
"$OCI" iam compartment list --compartment-id "$ROOT" --compartment-id-in-subtree true --output table 2>/dev/null \
  | sed 's/^/  /' || true
echo "  If the table above is empty the root compartment is the only option."
echo "  Say which one you used; do not let it default silently, because a"
echo "  production tenancy usually has more than one and the IAM policy has to"
echo "  match the one the stack lands in."

hr "Compute limits this stack consumes"
"$OCI" limits limit-value list --service-name compute --compartment-id "$ROOT" --all --output table 2>/dev/null \
  | sed 's/^/  /' || echo "  (limit-value list failed — some tenancies restrict this read; use the Console's Limits page and record what it says)"

hr "Resource availability for the ARM shape"
"$OCI" limits resource-availability get \
  --service-name compute \
  --limit-name arm-core-count \
  --compartment-id "$ROOT" \
  --region "$TARGET_REGION" \
  --output table 2>/dev/null | sed 's/^/  /' \
  || echo "  'arm-core-count' did not resolve. That is NOT evidence of zero capacity:"
echo "    limit names vary by subscription tier. Discover the real name with"
echo "      oci limits limit-definition list --service-name compute"
echo "    before concluding anything about Always Free ARM eligibility."

hr "Duplicate check"
if [ -z "$COMPARTMENT" ]; then
  echo "  skipped — pass --compartment <ocid> to check whether gms-prod-vcn,"
  echo "  gms-prod-public-subnet, gms-prod-igw or gms-prod-api-01 already exist."
else
  for pair in vcn:gms-prod-vcn subnet:gms-prod-public-subnet internet-gateway:gms-prod-igw instance:gms-prod-api-01; do
    res="${pair%%:*}"; want="${pair##*:}"
    found=$("$OCI" core "$res" list --compartment-id "$COMPARTMENT" --output json 2>/dev/null \
      | python3 -c 'import json,sys
try:
    rows = json.load(sys.stdin)
except Exception:
    rows = []
for r in rows:
    if r.get("displayName") == sys.argv[1]:
        print(r["id"])' "$want")
    if [ -n "$found" ]; then
      printf '  %-24s EXISTS %s\n' "$want" "$found"
    else
      printf '  %-24s absent\n' "$want"
    fi
  done
  echo "  Anything reading EXISTS must be `tofu import`ed or renamed before apply."
  echo "  Creating a second copy is the failure the owner explicitly ruled out."
fi

hr "What this script cannot answer"
cat <<'EOF'
  Public IPv4 cost. The OCI API exposes limits and capacity, not prices, and
  §9.1 requires verifying the charge instead of assuming "free". Read the
  Console's Always Free page and the estimated-monthly-cost widget for this
  exact shape + boot volume + IP, and write the number into
  docs/infrastructure/INFRASTRUCTURE_AUDIT.md before the first apply.

  Whether the tenancy is on a free or pay-as-you-go subscription, because that
  changes what "ARM allowance" means. `oci iam subscription list` needs a policy
  most users cannot read; if it errors, the Console is the answer.

  Nothing printed here proves a resource exists yet. It describes the tenancy,
  and it is the pre-condition for the first plan — not a substitute for it.
EOF
