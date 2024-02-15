keywords: security, password, encrypt, key, certificate, tls, authentication
description: Security mechanisms to encrypt passwords and secure the configuration files and its internal network communications.

# Security

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

**${solutionName}** provides a set of security mechanisms to secure:

* The **MetricsHub Agent**'s passwords stored in the `config/metricshub.yaml` file
* The communications instantiated between the **MetricsHub Agent** and the **OpenTelemetry Collector**. <span class="badge">Enterprise</span>

## Encryption

Use the `metricshub-encrypt` command to encrypt the passwords specified in the `config/metricshub.yaml` file. See [Passwords Encryption](passwords.md#Passwords_Encryption) for more details.

## OpenTelemetry Collector Security <span class="badge">Enterprise</span>

### Receiver security

To prevent malicious attacks, the `gRPC` listener is by default only opened on `localhost`:

```yaml
  otlp:
    protocols:
      grpc:
        endpoint: localhost:4317
```

### Transport security

**${solutionName}** secures the communications instantiated from the **MetricsHub Agent**'s internal `OTLP Exporter` to the **OpenTelemetry Collector**'s internal `OTLP gRPC Receiver` through TLS.

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

To customize the default TLS settings, see [Customizing TLS Certificates](settings.md#Customizing_TLS_Certificates).

### Requests authentication

Once TLS is established, the `OTLP gRPC Receiver` uses the `basicauth` authenticator to authenticate any incoming request:

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

To customize the default authentication secret, see [Customizing OTLP Authentication Password](settings.md#Customizing_OTLP_Authentication_Password).
