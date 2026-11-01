# §9.2, verbatim: VCN gms-prod-vcn 10.0.0.0/16, internet gateway gms-prod-igw,
# public subnet gms-prod-public-subnet 10.0.1.0/24, 0.0.0.0/0 via the IGW.
# No NAT gateway and no service gateway: §9.2 says avoid them unless an
# explicitly justified free configuration is verified, and none has been.

resource "oci_core_vcn" "gms" {
  compartment_id = var.compartment_ocid
  display_name   = "gms-prod-vcn"
  dns_label      = "gmsprod"
  cidr_block     = var.vcn_cidr

  freeform_tags = local.common_tags
}

resource "oci_core_internet_gateway" "gms" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.gms.id
  display_name   = "gms-prod-igw"
  enabled        = true

  freeform_tags = local.common_tags
}

resource "oci_core_route_table" "public" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.gms.id
  display_name   = "gms-prod-public-rt"

  route_rules {
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
    network_entity_id = oci_core_internet_gateway.gms.id
    description       = "Default route through the internet gateway (§9.2)"
  }

  freeform_tags = local.common_tags
}

resource "oci_core_security_list" "public" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.gms.id
  display_name   = "gms-prod-public-sl"

  # Ingress is a closed list, not an open one with exceptions. Ports 5432, 8080
  # and 3000 are absent because §9.2 forbids them, and "absent" is the only
  # rule form that cannot be forgotten later — there is no comment here telling
  # a future reader to remove them, because there is nothing to remove.
  #
  # protocol 6 = TCP, 1 = ICMP.

  ingress_security_rules {
    protocol    = "6"
    source      = var.ssh_source_cidr
    description = "SSH from one address only (§9.2)"

    tcp_options {
      min = 22
      max = 22
    }
  }

  ingress_security_rules {
    protocol    = "6"
    source      = "0.0.0.0/0"
    description = "HTTP, public by design (§9.2). Caddy terminates this and redirects to 443."

    tcp_options {
      min = 80
      max = 80
    }
  }

  ingress_security_rules {
    protocol    = "6"
    source      = "0.0.0.0/0"
    description = "HTTPS, public by design (§9.2)"

    tcp_options {
      min = 443
      max = 443
    }
  }

  ingress_security_rules {
    protocol    = "1"
    source      = var.vcn_cidr
    description = "ICMP type 3 code 4 within the VCN: fragmentation-needed, so PMTUD works instead of black-holing large responses."

    icmp_options {
      type = 3
      code = 4
    }
  }

  egress_security_rules {
    protocol    = "all"
    destination = "0.0.0.0/0"
    description = "Outbound is open: apt, Docker Hub, the Neon endpoint and ACME all need it, and this subnet holds only the one instance."
  }

  freeform_tags = local.common_tags
}

resource "oci_core_subnet" "public" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.gms.id

  # Regional subnet: no availability_domain, so the instance can be placed in
  # any AD that has ARM capacity rather than the one this plan happened to read
  # first. That matters because A1.Flex capacity is per-AD and §9.1 says to
  # verify capacity before creating.
  cidr_block                 = var.public_subnet_cidr
  display_name               = "gms-prod-public-subnet"
  dns_label                  = "pub"
  route_table_id             = oci_core_route_table.public.id
  security_list_ids          = [oci_core_security_list.public.id]
  prohibit_public_ip_on_vnic = false
  prohibit_internet_ingress  = false

  freeform_tags = local.common_tags
}
