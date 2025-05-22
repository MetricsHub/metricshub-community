keywords: linux, service
description: How to 

# Linux Service Monitoring

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

## Introduction

## Procedure

1. Declare the resource to be monitored (prod-web)​
2. Define resource attributes (host.name, host.type)​
3. Configure the SSH protocol with credentials and timeout​
4. Add an additional connector (httpd) using the LinuxService module​
5. Set the variable serviceNames to specify the service to monitor (httpd)​