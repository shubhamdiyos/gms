output "vcn_id" {
  value       = oci_core_vcn.gms.id
  description = "gms-prod-vcn"
}

output "public_subnet_id" {
  value       = oci_core_subnet.public.id
  description = "gms-prod-public-subnet"
}

output "internet_gateway_id" {
  value       = oci_core_internet_gateway.gms.id
  description = "gms-prod-igw"
}

output "instance_id" {
  value       = oci_core_instance.api01.id
  description = "gms-prod-api-01"
}

output "boot_image" {
  value       = "${local.boot_image_name} (${local.boot_image_id})"
  description = "The exact Ubuntu 24.04 ARM64 image selected, so a rebuild is attributable to an image OCID rather than to \"the latest\"."
}

output "public_ipv4" {
  value       = oci_core_instance.api01.public_ip
  description = <<-EOT
    Automatically provisioned IPv4 for the instance.

    READ BEFORE USING THIS: §9.1 requires verifying public IP cost rather than
    assuming it, and this plan cannot verify it — the provider exposes no price.
    Confirm in the Console (or with `oci limits resource-availability get`) that
    this address is billed as part of the Always Free grant, and re-check it if
    the instance is ever stopped and released, because an *unattached reserved*
    IPv4 does bill. Never rely on this output to prove the stack is free.
  EOT
}

output "ssh_command" {
  value       = "ssh -i <your-private-key> ${var.deploy_user}@${oci_core_instance.api01.public_ip}"
  description = "Connection string for the hardened deploy user. The private key stays on the operator's machine and is never stored in this repository or in state."
}

output "applied_rules_summary" {
  value = {
    ssh_ingress_source = var.ssh_source_cidr
    public_ingress     = ["tcp/22 from ${var.ssh_source_cidr}", "tcp/80 from 0.0.0.0/0", "tcp/443 from 0.0.0.0/0"]
    never_published    = ["tcp/5432", "tcp/8080", "tcp/3000"]
    shape              = local.shape
  }
  description = "Echoed at apply time so §9.2's port rules are visible in the run output rather than only in the source."
}
