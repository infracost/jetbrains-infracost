# Infracost JetBrains Plugin

[![JetBrains IntelliJ plugins](https://img.shields.io/jetbrains/plugin/v/24761-infracost.svg)](https://plugins.jetbrains.com/plugin/24761-infracost)
[![JetBrains IntelliJ plugins](https://img.shields.io/jetbrains/plugin/d/24761-infracost.svg)](https://plugins.jetbrains.com/plugin/24761-infracost)

Cloud cost estimates for Terraform and CloudFormation right in your editor, powered by the [Infracost Language Server](https://github.com/infracost/lsp).

## Installation

1. Open the IDE and go to `Settings` -> `Plugins` -> `Marketplace`
2. Search for `Infracost`
3. Click `Install`
4. Restart the IDE
5. Open a `.tf` file — cost estimates appear as code vision hints above resources
6. Click a code vision hint to open the Infracost tool window with cost breakdowns, FinOps policy violations, and tag issues

## Settings

Go to `Settings` -> `Tools` -> `Infracost` to configure:

- **Server path** — path to the `infracost-ls` binary. Leave empty to use the bundled binary or find it on your PATH.
- **Cache TTL** — how long (in seconds) to cache run parameters between API calls. Defaults to 300.

## How it works

This plugin is a lightweight LSP client that connects to the `infracost-ls` language server. The server handles all Terraform/CloudFormation parsing, cost estimation, and caching. The plugin surfaces cost data via JetBrains Code Vision and a detail panel in the tool window.

