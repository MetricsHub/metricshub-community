keywords: security, opentelemetry, otel, collector, tls, authentication, grpc, certificate
description: How to customize TLS certificates and authentication for the OpenTelemetry Collector.

# OpenTelemetry Collector Security

<!-- MACRO{toc|fromDepth=1|toDepth=3|id=toc} -->

MetricsHub Enterprise includes an embedded OpenTelemetry Collector. This page describes how communications between the MetricsHub Agent and the Collector are secured, and how to customize these security settings.

## Receiver Security

To prevent unauthorized access, the `gRPC` listener is by default only opened on `localhost`:

```yaml
otlp:
  protocols:
    grpc:
      endpoint: localhost:4317
```

This means only processes running on the same machine can send data to the Collector.

## Transport Security

MetricsHub secures the communications between the **MetricsHub Agent**'s internal `OTLP Exporter` and the **OpenTelemetry Collector**'s internal `OTLP gRPC Receiver` using TLS encryption.

The `OTLP gRPC Receiver` is configured as follows in the `otel/otel-config.yaml` file:

```yaml
otlp:
  protocols:
    grpc:
      endpoint: localhost:4317
      tls:
        cert_file: ../security/otel.crt
        key_file: ../security/otel.key
```

## Request Authentication

Once TLS is established, the `OTLP gRPC Receiver` uses the `basicauth` authenticator to verify any incoming request:

```yaml
otlp:
  protocols:
    grpc:
      endpoint: localhost:4317
      tls:
        cert_file: ../security/otel.crt
        key_file: ../security/otel.key
      auth:
        authenticator: basicauth
```

## Customizing TLS Certificates

You can use your own certificate to secure the communications between the **MetricsHub Agent** and the **OpenTelemetry Collector** by replacing the default TLS certificate of the `OTLP gRPC Receiver`.

### Prerequisites

- The certificate file must be in PEM format and can contain one or more certificate chains. The first certificate compatible with the client's requirements will be automatically selected.
- The private key must be non-encrypted and in PEM format.
- The certificate must include the `subjectAltName` extension indicating `DNS:localhost,IP:127.0.0.1` because internal communications are on `localhost` only and the **MetricsHub Agent**'s `OTLP Exporter` performs hostname verification.

### Procedure

1. Generate your new private key and certificate files (for example: `my-otel.key` and `my-otel.crt`).

2. Copy the generated certificate and private key files into the `security` directory.

3. In the `otel/otel-config.yaml` file, update the `tls:cert_file` and `tls:key_file` attributes of the `OTLP gRPC Receiver`:

   ```yaml
   receivers:
     otlp:
       protocols:
         grpc:
           endpoint: localhost:4317
           tls:
             cert_file: ../security/my-otel.crt # Your new certificate file
             key_file: ../security/my-otel.key # Your new private key file
           auth:
             authenticator: basicauth
   ```

4. In the `config/metricshub.yaml` file, set your new certificate as `certificate` in the `OTLP Exporter` configuration section:

   **On Linux:**

   ```yaml
   otel:
     otel.exporter.otlp.metrics.certificate: /opt/metricshub/lib/security/my-otel.crt
   ```

   **On Windows:**

   ```yaml
   otel:
     otel.exporter.otlp.metrics.certificate: C:/ProgramData/MetricsHub/security/my-otel.crt
   ```

5. Restart MetricsHub.

### Generating a Self-Signed Certificate with OpenSSL

OpenSSL is a command-line tool to generate X.509 certificates. You can use it to generate self-signed certificates.

> **Note:** The example below explains how to generate a server certificate using the OpenSSL utility on a Linux machine. Your organization may define its own security policy to handle certificates and private keys. Before proceeding, make sure this procedure aligns with your organization's requirements.

1. Create a private key for the Certificate Authority (CA):

   ```shell-session
   openssl genrsa 2048 > ca.key
   ```

2. Generate the X.509 certificate for the CA:

   ```shell-session
   openssl req -new -x509 -nodes -days 365000 \
      -key ca.key \
      -out ca.crt
   ```

3. Generate the private key and certificate request:

   ```shell-session
   openssl req -newkey rsa:2048 -nodes -days 365000 \
      -keyout my-otel.key \
      -out my-otel.req
   ```

4. Create a `cert.conf` file that defines the required extension:

   ```
   [ req ]

   req_extensions = req_ext

   [ req_ext ]

   subjectAltName = DNS:localhost,IP:127.0.0.1
   ```

5. Generate the X.509 certificate for the `OTLP gRPC Receiver`:

   ```shell-session
   openssl x509 -req -days 365000 -set_serial 01 \
     -in my-otel.req \
     -out my-otel.crt \
     -CA ca.crt \
     -CAkey ca.key \
     -extfile cert.conf -extensions req_ext
   ```

6. Your certificate (`my-otel.crt`) and private key (`my-otel.key`) are now generated in PEM format. Verify your certificate:

   ```shell-session
   openssl verify -CAfile ca.crt ca.crt my-otel.crt
   ```

## Customizing OTLP Authentication Password

You can use your own password to have the `OTLP gRPC Receiver` authenticate incoming requests.

### Prerequisites

Access to the `htpasswd` tool:

- **On Linux:** Install the `httpd-tools` package (or `apache2-utils` on Debian/Ubuntu)
- **On Windows:** The `htpasswd` utility is available in [Apache for Windows](https://httpd.apache.org/docs/2.4/platform/windows.html#down) packages

### Procedure

1. Create a new `.htpasswd-otel` file using your username and password:

   ```shell-session
   htpasswd -cbB .htpasswd-otel myUsername myPassword
   ```

2. Copy the `.htpasswd-otel` file into the `security` directory.

3. In the `otel/otel-config.yaml` file, update the `file` attribute of the `basicauth` extension:

   ```yaml
   extensions:
     # ...

     basicauth:
       htpasswd:
         file: ../security/.htpasswd-otel # Your new htpasswd file
   ```

4. In the `otel/otel-config.yaml` file, make sure `basicauth` is declared as a service extension and as the `OTLP gRPC Receiver` authenticator:

   ```yaml
   service:
     # ...
     extensions: [health_check, basicauth]
     pipelines:
       # ...

   receivers:
     otlp:
       protocols:
         grpc:
           # ...
           auth:
             authenticator: basicauth
   ```

5. Generate a `base64` string using the same credentials. Join your username and password with a colon and encode the result:

   ```shell-session
   echo -n 'myUsername:myPassword' | base64
   ```

   Output: `bXlVc2VybmFtZTpteVBhc3N3b3Jk`

6. In the `config/metricshub.yaml` file, add the `Authorization` header to the OTLP exporter configuration:

   ```yaml
   otel:
     otel.exporter.otlp.metrics.headers: Authorization=Basic bXlVc2VybmFtZTpteVBhc3N3b3Jk
   ```

   The header value must be `Basic <base64-credentials>`, where `<base64-credentials>` is the value you generated in the previous step.

7. Restart MetricsHub.

## Disabling TLS (Not Recommended)

> **Warning:** Disabling TLS means communications between the MetricsHub Agent and the OpenTelemetry Collector are no longer encrypted. Only do this in isolated test environments.

1. In the `otel/otel-config.yaml` file, remove or comment out the `tls` section:

   ```yaml
   receivers:
     otlp:
       protocols:
         grpc:
           endpoint: localhost:4317
           # tls:
           #   cert_file: ../security/otel.crt
           #   key_file: ../security/otel.key
           auth:
             authenticator: basicauth
   ```

2. In the `config/metricshub.yaml` file, update the OTLP exporter endpoint to use HTTP:

   ```yaml
   otel:
     otel.exporter.otlp.metrics.endpoint: http://localhost:4317
   ```

3. Remove or comment out the `certificate` attribute in `config/metricshub.yaml`:

   ```yaml
   otel:
     otel.exporter.otlp.metrics.endpoint: http://localhost:4317
     # otel.exporter.otlp.metrics.certificate: security/otel.crt
   ```

4. Restart MetricsHub.

## Disabling Authentication (Not Recommended)

> **Warning:** Disabling authentication means incoming requests will no longer be verified by the OpenTelemetry Collector, which may expose you to unauthorized access.

1. In the `otel/otel-config.yaml` file, remove or comment out the `auth` section:

   ```yaml
   receivers:
     otlp:
       protocols:
         grpc:
           endpoint: localhost:4317
           tls:
             cert_file: ../security/otel.crt
             key_file: ../security/otel.key
           # auth:
           #   authenticator: basicauth
   ```

2. In the `otel/otel-config.yaml` file, remove `basicauth` from the service extensions list:

   ```yaml
   service:
     # ...
     extensions: [health_check] # basicauth removed
     pipelines:
       # ...
   ```

3. In the `config/metricshub.yaml` file, remove the `Authorization` header:

   ```yaml
   otel:
     # otel.exporter.otlp.metrics.headers: Authorization=Basic ...
   ```

4. Restart MetricsHub.
