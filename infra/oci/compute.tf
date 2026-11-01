resource "oci_core_instance" "api01" {
  compartment_id = var.compartment_ocid

  # ap-mumbai-1 is a single-availability-domain region, so [0] is not a
  # capacity guess — there is no second AD to choose. preflight.sh checks ARM
  # OCPU availability in this AD before the first apply; §9.1 requires that
  # verification and it cannot be done from HCL without hardcoding limit names
  # that are tenancy-specific.
  availability_domain = data.oci_identity_availability_domains.ads.availability_domains[0].name

  display_name = var.instance_name
  shape        = local.shape

  shape_config {
    ocpus         = var.shape_ocpus
    memory_in_gbs = var.shape_memory_gb
  }

  source_details {
    source_type             = "image"
    source_id               = local.boot_image_id
    boot_volume_size_in_gbs = tostring(var.boot_volume_size_gb)
  }

  create_vnic_details {
    subnet_id        = oci_core_subnet.public.id
    display_name     = "${var.instance_name}-vnic"
    hostname_label   = "gms-api-01"
    assign_public_ip = true
  }

  # "Establish basic host monitoring" (§9.4). Left explicit rather than default:
  # the OCI Cloud Agent reports CPU, memory and disk into the tenancy's own
  # free metrics, which is what Infra-M5's alerting will read.
  agent_config {
    is_monitoring_disabled = false
    is_management_disabled = false
  }

  metadata = {
    # Public key only. §9.3: "Never upload, transmit, or expose my private SSH
    # key" — file() reads the .pub path, and variables.tf rejects any path that
    # does not end in .pub so a private key cannot reach state by accident.
    ssh_authorized_keys = file(var.ssh_public_key_path)
    user_data = base64encode(templatefile("${path.module}/cloud-init.yaml.tftpl", {
      deploy_user = var.deploy_user
    }))
  }

  freeform_tags = merge(local.common_tags, { role = "api" })

  # §9.4's "do not run the application as root", plus the standing rule against
  # destructive operations without a backup and authorisation. Replacing or
  # deleting this instance is a deliberate two-step edit, not a typo in a plan.
  lifecycle {
    prevent_destroy = true
    ignore_changes  = [metadata]
  }

  depends_on = [oci_core_subnet.public]
}
