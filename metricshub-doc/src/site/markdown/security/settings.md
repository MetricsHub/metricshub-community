keywords: security, password, encrypt, key, certificate, tls, authentication
description: How to configure ${solutionName} security settings.

# Security Settings


<!-- MACRO{toc|fromDepth=1|toDepth=3|id=toc} -->

## Customizing TLS Certificates

You can use your own certificate to secure the communications between the **MetricsHub Agent** and the **OpenTelemetry Collector** by replacing the default TLS certificate of the `OTLP gRPC Receiver`.

### Prerequisites

- The certificate file must be in PEM format and can contain one or more certificate chains. The first certificate compatible with the client's requirements will be automatically selected.
- The private key must be non-encrypted and in PEM format.
- When the **OpenTelemetry Collector** and the **MetricsHub Agent** are hosted on the same machine, the certificate must include the `subjectAltName` extension with `DNS:localhost` and `IP:127.0.0.1` to accommodate internal communications via localhost. This requirement arises because the **MetricsHub Agent**'s OTLP Exporter verifies the hostname. On the other hand, if the **OpenTelemetry Collector** operates from a remote server, the certificate's `subjectAltName` extension needs to be adjusted to reflect the remote server's appropriate `DNS` and `IP` address.

### Procedure

1. Generate your new private key and certificate files (for example: `my-otel.key` and `my-otel.crt`).
2. Copy the generated certificate and private key files into the `security` directory located under the installation directory.
3. In the `otel/otel-config.yaml` file, update the `tls:cert_file` and `tls:key_file` attributes of the `OTLP gRPC Receiver`:

    ```yaml
    receivers:
      otlp:
        protocols:
          grpc:
            endpoint: localhost:4317
            tls:
              cert_file: ../security/my-otel.crt  # Your new certificate file.
              key_file: ../security/my-otel.key   # Your new private key file.
            auth:
              authenticator: basicauth
    ```

4. In the `config/metricshub.yaml` file, set your new certificate (`security/my-otel.crt`) as `otel.exporter.otlp.metrics.certificate` and `otel.exporter.otlp.logs.certificate`  in the `OTLP Exporter` configuration section:

    ```yaml
    otel:
      otel.exporter.otlp.metrics.certificate: /opt/metricshub/security/my-otel.crt # Your new OTLP gRPC Receiver certificate.
      otel.exporter.otlp.logs.certificate: /opt/metricshub/security/my-otel.crt # Your new OTLP gRPC Receiver certificate.

    resourceGroups: # ...
    ```

5. Restart **${solutionName}**. See [Installation](../install.md) for more details.

#### Generating a Self-Signed Certificate with OpenSSL (Example)

OpenSSL is a command line tool to generate X.509 certificates. It can be used to generate Self-Signed Certificates.

> The example below explains how to generate a server certificate using the OpenSSL utility on a Linux machine. Your organization may define its own security policy to handle certificates and private keys. Before proceeding further, make sure that this procedure is right for your organization.

1. Create a private key for the Certificate Authority (CA):

   ```batch
   $ openssl genrsa 2048 > ca.key
   ```

2. Generate the X.509 certificate for the CA:

   ```batch
   $ openssl req -new -x509 -nodes -days 365000 \
      -key ca.key \
      -out ca.crt
   ```

3. Generate the private key and certificate request:

   ```batch
   $ openssl req -newkey rsa:2048 -nodes -days 365000 \
      -keyout my-otel.key \
      -out my-otel.req
   ```

4. Generate the X.509 certificate for the `OTLP gRPC Receiver`:

   ```batch
   $ openssl x509 -req -days 365000 -set_serial 01 \
     -in my-otel.req \
     -out my-otel.crt \
     -CA ca.crt \
     -CAkey ca.key \
     -extfile cert.conf -extensions req_ext
   ```

   Where the `cert.conf` file defines the extension to add to your certificate:

   ```
   [ req ]

   req_extensions = req_ext

   [ req_ext ]

   subjectAltName = DNS:localhost,IP:127.0.0.1
   ```

5. Your certificate (`my-otel.crt`) and private key (`my-otel.key`) are now generated in PEM format. You can verify your certificate as follows:

   ```batch
    $ openssl verify -CAfile ca.crt \
       ca.crt \
       my-otel.crt
   ```

## Customizing OTLP Authentication Password

You can use your own password to have the `OTLP gRPC Receiver` authenticate any incoming request.

### Prerequisites

Access to the `htpasswd` tool:

- On a Linux system, you can install the `httpd-tools` package.
- On a Windows system, the `htpasswd` utility is embedded in one of the packages listed in the [*Downloading Apache for Windows*](https://httpd.apache.org/docs/2.4/platform/windows.html#down) page.

### Procedure

1. Create a new `.htpasswd-otel` file using your username and password:

   ```shell-session
   $ htpasswd -cbB .htpasswd-otel myUsername myPassword
   Adding password for user myUsername
   ```

2. Copy the `.htpasswd-otel` file into the `security` directory located under the installation directory.

3. In the `otel/otel-config.yaml` file, update the `file` attribute of the `basicauth` extension:

   ```yaml
   extensions:

     # ...

     basicauth:
       htpasswd:
         file: ../security/.htpasswd-otel  # Your new htpasswd file
   ```

4. In the `otel/otel-config.yaml` file:

   - make sure the `basicauth` is declared as a service extension :

    ```yaml
    service:

      # ...

      extensions: [health_check, basicauth] # basicauth is added to the extensions list
      pipelines:
      
      # ...
    ```

   - make sure the `basicauth` extension is declared as `OTLP gRPC Receiver` *authenticator*:

   ```yaml
   receivers:
     otlp:
       protocols:
         grpc:
           # ...
           auth:
             authenticator: basicauth
    ```

5. Generate a `base64` string using the same credentials provided to generate the `.htpasswd-otel` file. Join your username and password with a colon `myUsername:myPassword`, and then encode the resulting string in `base64`.

   ```shell-session
   $ echo -n 'myUsername:myPassword' | base64
   bXlVc2VybmFtZTpteVBhc3N3b3Jk
   ```

6. In the `config/metricshub.yaml` file, set a new `Authorization` header for the `otel.exporter.otlp.metrics.headers` and `otel.exporter.otlp.logs.headers` parameters under the `otel` sections:

   ```yaml
   otel:
     otel.exporter.otlp.metrics.headers: Authorization=Basic bXlVc2VybmFtZTpteVBhc3N3b3Jk # Basic <base64-credentials>
     otel.exporter.otlp.logs.headers: Authorization=Basic bXlVc2VybmFtZTpteVBhc3N3b3Jk # Basic <base64-credentials>
   ```

   The `Authorization` header must be provided as `Basic <base64-credentials>`, where `<base64-credentials>` is the `base64` value you have generated in the previous step.

7. Restart **${solutionName}**.

## Disabling TLS (Not recommended)

When you disable TLS on **${solutionName}**, the communications between the **MetricsHub Agent** and the **OpenTelemetry Collector** are not encrypted anymore.

1. In the `otel/otel-config.yaml` file, remove or comment out the `tls` section from the `OTLP gRPC Receiver` configuration:

    ```yaml
    receivers:
      otlp:
        protocols:
          grpc:
            endpoint: localhost:4317
            #tls:                                 # No TLS
            #  cert_file: ../security/my-otel.crt
            #  key_file: ../security/my-otel.key
            auth:
              authenticator: basicauth
    ```

2. In the `config/metricshub.yaml` file, update the `OTLP Exporter` endpoint to enable `HTTP`:

    ```yaml
    otel:
      otel.exporter.otlp.metrics.endpoint: http://localhost:4317 # gRPC OTLP Receiver metrics endpoint
      otel.exporter.otlp.logs.endpoint: http://localhost:4317 # gRPC OTLP Receiver logs endpoint

    resourceGroups: # ...
    ```

3. Remove or comment out the `otel.exporter.otlp.metrics.certificate` and `otel.exporter.otlp.logs.certificate` attributes of the `OTLP Exporter` in the `config/metricshub.yaml` file:

    ```yaml
    otel:
      otel.exporter.otlp.metrics.endpoint: http://localhost:4317 # gRPC OTLP Receiver metrics endpoint
      otel.exporter.otlp.logs.endpoint: http://localhost:4317 # gRPC OTLP Receiver logs endpoint
      # otel.exporter.otlp.metrics.certificate: security/otel.crt
      # otel.exporter.otlp.logs.certificate: security/otel.crt

    resourceGroups: # ...
    ```

4. Restart **${solutionName}**.

## Disabling Authentication (Not Recommended)

If you disable the authentication on **${solutionName}**, incoming requests will no longer be authenticated by the **OpenTelemetry Collector**'s `OTLP gRPC Receiver` and might expose you to malicious attacks.

1. In the `otel/otel-config.yaml` file, remove or comment out the `auth` section from the `OTLP gRPC Receiver` configuration:

    ```yaml
    receivers:
      otlp:
        protocols:
          grpc:
            endpoint: localhost:4317
            tls:
              cert_file: ../security/my-otel.crt
              key_file: ../security/my-otel.key
            # auth:
              # authenticator: basicauth   # No authentication
    ```

2. In the `otel/otel-config.yaml` file, remove the `basicauth` extension from the service extensions list:

   ```yaml
   service:

     # ...

     extensions: [health_check] # basicauth is not added to the extensions list
     pipelines:
     
     # ...
   ```

3. In the `config/metricshub.yaml` file, remove or comment out the `Authorization` header configuration from the `OTLP Exporter` configuration:

    ```yaml
    otel:
      otel.exporter.otlp.metrics.certificate: /opt/metricshub/security/otel.crt
      otel.exporter.otlp.logs.certificate: /opt/metricshub/security/otel.crt
      # otel.exporter.otlp.metrics.headers: Authorization=Basic bXlVc2VybmFtZTpteVBhc3N3b3Jk # Basic <base64-credentials>
      # otel.exporter.otlp.logs.headers: Authorization=Basic bXlVc2VybmFtZTpteVBhc3N3b3Jk # Basic <base64-credentials>
    resourceGroups: # ...
    ```

4. Restart **${solutionName}**.
