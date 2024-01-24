keywords: password, encrypt, master, key, security
description: ${solutionName} lets you encrypt the passwords stored in its YAML configuration files using a simple CLI.

# Password Encryption

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

## Encrypting Passwords

To encrypt a password, run the `metricshub-encrypt` command in an interactive terminal:

```shell-session
/$ cd /opt/metricshub/bin
/opt/metricshub/bin$ ./metricshub-encrypt
Enter the password to encrypt: <type the password>
GkwzG6bx8cUhoeQW+/1ERI+2LOyB
```

> User must have Administrator privileges.

## Using Encrypted Passwords

Once a password has been encrypted, it can be used in a `metricshub.yaml` configuration file instead of a readable password:

```yaml
resourceGroups:
    <resourceGroupKey>:
        resources:
          my-server:
            attributes:
              host.name: myhost-01
              host.type: storage
            protocols:
              http:
                https: true
                port: 443
                username: myusername
                password: GkwzG6bx8cUhoeQW+/1ERI+2LOyB
```

## The *Master Password*

On first use, the `metricshub-encrypt` command will create the **security/metricshub-keystore.p12** file to store a unique and random *master password*. This *master password* is used to encrypt passwords with `metricshub-encrypt`, and decrypt them from **config/metricshub.yaml**.

The **metricshub-keystore.p12** file must not be modified, as this would prevent decryption from working. Any password encrypted with a given **metricshub-keystore.p12** *master password* must be decrypted with the exact same **metricshub-keystore.p12** file.

The **metricshub-keystore.p12** file can be shared across several hosts so that a password encrypted on one system can be decrypted on another. Simply copy the **metricshub-keystore.p12** file to the **security** directory. This will make **${solutionName}** able to decrypt the passwords.

> **Note**: On Windows, the **security** directory is located under the **ProgramData\MetricsHub** directory (`C:\ProgramData\MetricsHub\security`) and accessible to *Administrators* only. On Linux, the **security** directory is located under the installation directory (`/opt/metricshub/security`).
