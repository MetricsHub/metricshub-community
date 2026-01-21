keywords: Web Interface, MetricsHub Community, MetricsHub Enterprise
description: How to use the Web Interface.

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

# Operating the Web Interface

A **Web Interface** is bundled with **MetricsHub Community** and **MetricsHub Enterprise** to facilitate configuration, resource and metric visualization, as well as analysis and troubleshooting using the virtual assistant **M8B**.

This interface is accessible at `https://<machine-where-metricshub-is-running>:31888/` provided that:

- **MetricsHub** is properly installed
- You have created a dedicated user as explained below.

## Managing users

The `user` CLI tool allows you to create, list, and delete users that can access the Web Interface. It is accessible from the **MetricsHub** installation directory:

- **Windows:** `C:\ProgramData\MetricsHub`
- **Linux:** `/opt/metricshub/lib/bin`

Users are stored securely within the keystore **metricshub-keystore.p12** on the local system.

> On **Windows**, **metricshub-keystore.p12** is located under `C:\ProgramData\MetricsHub\security`, which is typically accessible **only to Administrator users**.

> On **Linux**, **metricshub-keystore.p12** is located under `/opt/metricshub/security`, and access depends on the file system permissions.

### Creating users

To create a user, run the following command:

```shell-session
./user create <username> --password <password> --role <role>
```

Where:

- `<username>` and `<password>` must be replaced with the desired credentials
- `<role>` must be set to:
  - `ro` to only visualize collected metrics and existing configurations.
  - `rw` to be able to configure **MetricsHub** directly from the Web Interface

Example:

```shell-session
./user create myuser --password mysecretpassword --role rw
```

### Listing users

To list all users, run the following command:

```shell-session
./user list
```

### Deleting users

To delete a user, run the following command:

```shell-session
./user delete <username>
```

## Accessing the Web Interface

In your web browser, enter `https://<machine-where-metricshub-is-running>:31888/` and sign in using the credentials you previously created.

## Configuring resources monitoring

To configure resources monitoring from the Web Interface:

1.  Connect to `https://<machine-where-metricshub-is-running>:31888/` using your read-write credentials
2.  Either click **Import** to load an existing configuration file, or create a new configuration from scratch
3.  Edit the configuration in the right-hand panel, as explained in [Monitoring Configuration
    ](./configuration/configure-monitoring.md). The Web Interface will guide through the configuration, highlighting possible indentation issues or configuration mismatches.

        > **IMPORTANT:** Configuration changes are not automatically backed up. It is strongly recommended to create a backup before making significant changes. Click **Backup** whenever needed.

## Exploring collected metrics

To visualize the monitored resources and collected metrics, connect to the Web Interface and click the **Explorer** tab.

![MetricsHub Web UI - Visualizing monitored hosts and collected metrics](./images/metricshub-ui-explorer.png)

From there, you can:

- search for a specific resource or metric using the search engine
- display a resource's details and:
  - trigger a collect
  - pause or resume collect
  - visualize its attributes, collected metrics, and connectors used.

## Interacting with M8B

**M8B** is a virtual assistant that helps with routine operations and supports system administrators in analysis and troubleshooting tasks. To be able to interact with **M8B**, you need to specify your OpenAI key in the yaml configuration file (`/opt/metricshub/config/metricshub.yaml` for Linux or `C:\ProgramData\MetricsHub\config\metricshub.yaml` for Windows) as follows:

```yaml
web:
  ai.openai.api-key: "<Your-API-Key>"
```

## Updating the SSL Certificate

By default, **MetricsHub** uses a **self-signed SSL certificate** for its Web server.
As a result, your browser will display security warnings because it cannot verify the server identity.

To remove these warnings, configure **MetricsHub** to use a **trusted SSL certificate** matching the server hostname (and domain).

1. Obtain a certificate and its private key

   Request or generate a certificate from a Certificate Authority (CA) (internal or external) and retrieve:

   - The **certificate** (commonly `.crt`, `.cer`, or `.pem`)
   - The **private key** (commonly `.key`)

2. Create a `PKCS#12` keystore (`.p12`)

   **MetricsHub** expects a **PKCS#12 keystore** containing the certificate and its private key.

   Example exporting a certificate and its private key to a `PKCS#12` keystore using **OpenSSL**:

   ```bash
   openssl pkcs12 -export \
     -inkey yourdomain.key \
     -in yourdomain.crt \
     -out keystore.p12 \
     -name "Certificate" \
     -password pass:p4ssw0rd
   ```

3. Store the generated `keystore.p12` on the host where **MetricsHub** is running, typically in the `security` directory:

   - **Linux:** `/opt/metricshub/security`
   - **Windows:** `C:\ProgramData\MetricsHub\security`

4. Configure **MetricsHub** to use the created keystore

   Edit the `metricshub.yaml` configuration file:

   - **Linux:** `/opt/metricshub/config/metricshub.yaml`
   - **Windows:** `C:\ProgramData\MetricsHub\config\metricshub.yaml`

   Then set:

   ```yaml
   web:
     tls.enabled: "true"
     tls.keystore.path: /opt/metricshub/security/keystore.p12 # "C:\\ProgramData\\MetricsHub\\security\\keystore.p12" on Windows
     tls.keystore.password: p4ssw0rd
   ```

   Where `tls.keystore.path` is the **full path to the keystore file**.

5. Restart **MetricsHub** to apply the new certificate
6. Then open the **MetricsHub** Web Interface and verify the certificate:

   - Your Web browser should no longer show warnings (if the certificate is trusted)
   - The certificate details should match the one you installed

   Refresh the **MetricsHub** Web Interface if needed.
