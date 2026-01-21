keywords: data, troubleshooting, collection, resource
description: How to resolve issues where MetricsHub fails to collect data for a specific resource.

# No Data for a Specific Resource

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

**MetricsHub** extracts metrics from resources based on the [protocols and credentials](../configuration/configure-monitoring.md#protocols-and-credentials) defined in the MetricsHub configuration file(s). If a connectivity issue occurs between **MetricsHub** and the configured resource, data collection will fail.

To troubleshoot this issue, test the connection to the resource using the CLI associated with the protocol specified in your MetricsHub configuration file. Use the credentials configured in the file. If the CLI returns:

- **An error**: Verify that the credentials and protocol settings in the configuration file are correct
- **A successful output**: Confirm that the query retrieves the expected data.

For detailed guidance, refer to the protocol-specific [CLI](cli/index.md) documentation.
