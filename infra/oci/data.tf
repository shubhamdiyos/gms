locals {
  # §9.1 names one shape and it is the Always Free one. Making it a local
  # instead of a variable is the difference between a default and a guarantee:
  # there is no tfvars value that can select a paid shape, and "do not select
  # paid shapes" (§9.1) cannot be violated by an argument.
  shape = "VM.Standard.A1.Flex"

  common_tags = {
    "managed-by" = "opentofu"
    "stack"      = "gms-prod-oci"
    "mandate"    = "third-mandate-9"
  }

  # Newest first: `sort_by = "timecreated"` plus the provider's default
  # descending order. Filtered to AVAILABLE because a deprovisioning or
  # deprecated image is still returned by the list.
  boot_images = [
    for img in data.oci_core_images.ubuntu_arm.images : img
    if img.state == "AVAILABLE" && try(img.launch_options[0].boot_volume_type, "PARAVIRTUALIZED") == "PARAVIRTUALIZED"
  ]

  boot_image_id   = local.boot_images[0].id
  boot_image_name = local.boot_images[0].display_name
}

data "oci_identity_availability_domains" "ads" {
  compartment_id = var.compartment_ocid
}

data "oci_core_images" "ubuntu_arm" {
  compartment_id           = var.compartment_ocid
  operating_system         = "Canonical Ubuntu"
  operating_system_version = "24.04"
  shape                    = local.shape
  sort_by                  = "TIMECREATED"
  sort_order               = "DESC"
}

# Fails with a readable message instead of an index-out-of-range on a tenancy
# where Ubuntu 24.04 ARM64 has not been mirrored into the region yet.
check "boot_image_available" {
  assert {
    condition     = length(local.boot_images) > 0
    error_message = "No AVAILABLE Ubuntu 24.04 ARM64 image for ${local.shape} in ${var.region}. §9.1 requires exactly this OS and architecture; do not substitute an x86 image — re-run preflight.sh and check the region's image list."
  }
}
