# Infracost JetBrains Plugin

[![JetBrains IntelliJ plugins](https://img.shields.io/jetbrains/plugin/v/24761-infracost.svg)](https://plugins.jetbrains.com/plugin/24761-infracost)
[![JetBrains IntelliJ plugins](https://img.shields.io/jetbrains/plugin/d/24761-infracost.svg)](https://plugins.jetbrains.com/plugin/24761-infracost)
[![JetBrains IntelliJ plugins](https://img.shields.io/jetbrains/plugin/r/rating/24761-infracost.svg)](https://plugins.jetbrains.com/plugin/24761-infracost)

![Infracost JetBrains Plugin](.github/images/infracost.png)

## Description

<!-- Plugin description -->
Infracost is an IntelliJ-based plugin that allows you to shift left on your Cloud costs by providing cost estimates for
your Terraform code.

Infracost is a companion to the [Infracost CLI](https://www.infracost.io/docs/integrations/ci-cd) and provides a way to
view cost estimates directly in your IDE.
<!-- Plugin description end -->

## Features

- View cost estimates for your Terraform code directly in your IDE
- Supports all JetBrains IDEs
- Supports all Terraform providers

## Installation

You can install the plugin from the JetBrains Plugin Repository.

1. Open the IDE and go to `Settings` -> `Plugins` -> `Marketplace`
2. Search for `Infracost`
3. Click `Install`
4. Restart the IDE
5. Open a Terraform file and click on the `Infracost` tab at the bottom of the IDE
6. Click on `Refresh` to get the cost estimate
7. Use our ![CI/CD integrations](https://www.infracost.io/docs/integrations/cicd/) to add cost estimates to pull
   requests. This provides your team with a safety net as people can understand cloud costs upfront, and discuss them as
   part of your workflow.
