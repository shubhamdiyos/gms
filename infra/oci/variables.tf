variable "region" {
  type        = string
  default     = "ap-mumbai-1"
  description = "OCI region for the whole stack. The tenancy's home region is India West (Mumbai). §9.3 forbids silently switching regions, so a non-default value here has to be a deliberate edit, not a flag on a command line."
}

variable "oci_config_profile" {
  type        = string
  default     = "DEFAULT"
  description = "Profile name inside the operator's OCI config file. Not a secret: it selects a section header. The private key path stays in that file, on the operator's machine, and is never referenced here."
}

variable "compartment_ocid" {
  type        = string
  description = "OCID of the compartment every resource in this stack is created in. Get it from `oci iam compartment list` (preflight.sh does this read-only). Deliberately has no default: leaving it unset fails the plan instead of quietly provisioning into the root tenancy compartment."

  validation {
    condition     = can(regex("^ocid1\\.(compartment|tenancy)\\.", var.compartment_ocid))
    error_message = "compartment_ocid must be a real OCI compartment OCID, starting with ocid1.compartment. or ocid1.tenancy."
  }
}

variable "ssh_public_key_path" {
  type        = string
  description = "Absolute path to an SSH **public** key on the operator's machine. The private half is never read by this plan, never copied into state and never leaves the laptop (§9.3). It is read at plan time with file(), so the key material that ends up in the state file is the public key only."

  validation {
    condition     = can(regex("\\.pub$", var.ssh_public_key_path))
    error_message = "ssh_public_key_path must end in .pub. Pointing it at a private key would write that private key into terraform.tfstate — this guard exists because that is the one way this stack could leak a credential."
  }
}

variable "ssh_source_cidr" {
  type        = string
  description = "The single address allowed to reach TCP/22. §9.2: \"restrict to my current public IP /32\". Find it with `curl -4 https://ifconfig.me` and write it as <ip>/32."

}

variable "vcn_cidr" {
  type        = string
  default     = "10.0.0.0/16"
  description = "CIDR of gms-prod-vcn (§9.2)."

  validation {
    condition     = can(cidrhost(var.vcn_cidr, 0))
    error_message = "vcn_cidr must be a valid IPv4 CIDR block."
  }
}

variable "public_subnet_cidr" {
  type        = string
  default     = "10.0.1.0/24"
  description = "CIDR of gms-prod-public-subnet (§9.2). Must stay inside vcn_cidr."

  validation {
    condition     = can(cidrhost(var.public_subnet_cidr, 0))
    error_message = "public_subnet_cidr must be a valid IPv4 CIDR block."
  }
}

variable "instance_name" {
  type        = string
  default     = "gms-prod-api-01"
  description = "Display name required by §9.1."
}

variable "shape_ocpus" {
  type        = number
  default     = 2
  description = "OCPUs on the flexible ARM shape. §9.1 fixes this at 2. Raising it is not free: Always Free ARM allows 4 OCPUs across all instances, so 2 here is the agreed budget, not a starting point."

  validation {
    condition     = var.shape_ocpus >= 1 && var.shape_ocpus <= 4
    error_message = "shape_ocpus must be between 1 and 4; the Always Free ARM allowance is 4 OCPUs in total."
  }
}

variable "shape_memory_gb" {
  type        = number
  default     = 12
  description = "RAM in GB on the flexible ARM shape (§9.1: 12 GB)."

  validation {
    condition     = var.shape_memory_gb >= 1 && var.shape_memory_gb <= 24
    error_message = "shape_memory_gb must be between 1 and 24; the Always Free ARM allowance is 24 GB in total."
  }
}

variable "boot_volume_size_gb" {
  type        = number
  default     = 100
  description = "Boot volume size. §9.1 says \"approximately 100 GB, subject to Always Free storage allowance\" — the allowance is 200 GB of block volume storage for the whole tenancy, so 100 GB leaves headroom for a second volume and this upper bound encodes that."

  validation {
    condition     = var.boot_volume_size_gb >= 50 && var.boot_volume_size_gb <= 200
    error_message = "boot_volume_size_gb must be between 50 and 200. Above 200 the boot volume alone consumes more than the whole Always Free block storage allowance and starts billing."
  }
}

variable "deploy_user" {
  type        = string
  default     = "gmsdeploy"
  description = "Non-root deployment user created by cloud-init (§9.4: \"configure a non-root application deployment user\"). The application is not run as root; this is the account Docker and the deploy steps belong to."

  validation {
    condition     = var.deploy_user != "root" && can(regex("^[a-z_][a-z0-9_-]{1,30}$", var.deploy_user))
    error_message = "deploy_user must be a valid non-root Linux username."
  }
}
