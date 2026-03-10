# Infracost JetBrains Plugin

[![JetBrains IntelliJ plugins](https://img.shields.io/jetbrains/plugin/v/24761-infracost.svg)](https://plugins.jetbrains.com/plugin/24761-infracost)
[![JetBrains IntelliJ plugins](https://img.shields.io/jetbrains/plugin/d/24761-infracost.svg)](https://plugins.jetbrains.com/plugin/24761-infracost)

Cloud cost estimates for Terraform right in your editor, powered by the [Infracost Language Server](https://github.com/infracost/lsp).

## Prerequisites

- [infracost-ls](https://github.com/infracost/lsp) must be available on your PATH

## Installation

1. Open the IDE and go to `Settings` -> `Plugins` -> `Marketplace`
2. Search for `Infracost`
3. Click `Install`
4. Restart the IDE
5. Open a `.tf` file — cost estimates appear as code vision hints above resources

## How it works

This plugin is a lightweight LSP client that connects to the `infracost-ls` language server. The server handles all Terraform parsing, cost estimation, and caching. The plugin surfaces cost data via JetBrains Code Vision.

## FAQs

### How can I supply input variables?

Add a [config file](https://www.infracost.io/docs/features/config_file/) at the root of your workspace. Config files let you define variable files for each project. Infracost also auto-detects `terraform.tfvars` and `*.auto.tfvars` files.

### How can I configure the currency?

Run `infracost configure set currency EUR` or update `~/.config/infracost/configuration.yml`:

```yaml
version: "0.1"
currency: EUR
```

Infracost supports all ISO 4217 currency codes. See [this FAQ](https://www.infracost.io/docs/faq/#can-i-show-costs-in-a-different-currency) for details.
