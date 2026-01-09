keywords: Web Interface, MetricsHub Community, MetricsHub Enterprise
description: How to use the Web Interface.

# Operating the Web Interface

A **Web Interface** is bundled with **MetricsHub Community** and **MetricsHub Enterprise** to facilitate configuration, resource and metric visualization, as well as analysis and troubleshooting using the virtual assistant **M8B**.

This interface is accessible at `https://<machine-where-metricshub-is-running>:31888/` provided that:

* **MetricsHub** is properly installed
* You have created a dedicated user as explained below.

## Creating a user to access the Web Interface

From the **MetricsHub** installation directory (for example, `C:/Program Files/MetricsHub`), run the following command:

```batch
user create [--password[=<password>]] --role=<role> USERNAME
```

Where:

* `[=<password>]` and `USERNAME` must be replaced with the desired credentials
* `<role>` must be set to:
  * `ro` to only visualize collected metrics and existing configurations.
  * `rw` to be able to configure **MetricsHub** directly from the Web Interface

## Accessing the Web Interface

In your web browser, enter `https://<machine-where-metricshub-is-running>:31888/` and sign in using the credentials you previously created.

## Configuring resources onitoring

To configure resources monitoring from the Web Interface:

1. Connect to  `https://<machine-where-metricshub-is-running>:31888/` using your read-write credentials
2. Either click **Import** to load an existing configuration file, or create a new configuration from scratch
3. Perform a backup
4. Edit the configuration in the right-hand panel. The Web Interface will guide through the configuration, highlighting possible indentation issues or configuration mismatches.

    > **IMPORTANT:** Configuration changes are not automatically backed up. It is strongly recommended to create a backup before making significant changes. Click **Backup** whenever needed.

## Exploring the collected metrics

To visualize the monitored resources and collected metrics, connect to the Web Interface and click the **Explorer** tab.

![MetricsHub Web UI - Visualizing monitored hosts and collected metrics](./images/metricshub-ui-explorer.png)

From there, you can:

* search for a specific resource or metric using the search engine
* display a resource's details and:
  * trigger a collect
  * pause or resume collect
  * visualize its attributes, collected metrics, and connectors used.

## Interacting with M8B

**M8B** is a virtual assistant that helps with routine operations and supports system administrators in analysis and troubleshooting tasks. To be able to interact with **M8B**, you need to specify your OpenAI key in the yaml configuration file as follows:

```yaml
web: # 
  ai.openai.api-key: "<Your-API-Key>"
  ai.openai.model: "gpt-5.2"  # Optional, defaults to gpt-4o-mini

```