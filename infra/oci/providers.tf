provider "oci" {
  # Nothing in this repository authenticates to OCI. The provider reads the API
  # signing key from the operator's own config file (~/.oci/config by default,
  # or OCI_CONFIG_FILE), and `config_file_profile` selects which profile to use.
  # Third mandate §9 therefore holds by construction: a checkout of this
  # directory contains no credential of any kind, and `git log` can never have
  # contained one.
  region              = var.region
  config_file_profile = var.oci_config_profile
}
