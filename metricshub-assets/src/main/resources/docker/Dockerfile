# Use must build the following images before this one:
# - metricshub-assets/target/assets-packaging/Dockerfile.rhel
# - metricshub-assets/target/assets-packaging/Dockerfile.debian
# - metricshub-assets/target/assets-packaging/Dockerfile.build
ARG BUILD_IMAGE=ghcr.io/metricshub/metricshub-community:packages-latest
FROM ${BUILD_IMAGE} AS packages

FROM debian:@debianTagName@-slim

LABEL description="@project.parent.name@" \
      org.opencontainers.image.title="@project.parent.name@" \
      org.opencontainers.image.description="@project.parent.name@" \
      org.opencontainers.image.url="https://metricshub.org/" \
      org.opencontainers.image.documentation="https://metricshub.org/" \
      org.opencontainers.image.source="https://github.com/metricshub/metricshub-community" \
      org.opencontainers.image.vendor="@project.organization.name@" \
      org.opencontainers.image.licenses="AGPL-3.0" \
      org.opencontainers.image.version="@project.version@"

EXPOSE 13133/tcp \
       24375/tcp \
       31888/tcp

COPY --from=packages /opt/metricshub/ /opt/metricshub/
COPY --chmod=755 docker-entrypoint.sh /opt/metricshub/bin/docker-entrypoint.sh
COPY defaults/ /opt/metricshub/defaults/

VOLUME /opt/metricshub/lib/config \
       /opt/metricshub/lib/logs \
       /opt/metricshub/lib/security

RUN apt-get update &&\
    apt-get install -y curl &&\
    rm -rf /var/lib/apt/lists/* &&\
    mkdir -p /opt/metricshub/lib/logs &&\
    ln -s /opt/metricshub/lib/config /opt/metricshub/config &&\
    ln -s /opt/metricshub/lib/security /opt/metricshub/security &&\
    ln -s /opt/metricshub/lib/logs /opt/metricshub/logs &&\
    ln -s /opt/metricshub/lib/LICENSE /opt/metricshub/LICENSE &&\
    ln -s /opt/metricshub/lib/connectors /opt/metricshub/connectors &&\
    ln -s /opt/metricshub/lib/extensions /opt/metricshub/extensions &&\
    useradd -d /opt/metricshub -s /bin/bash -U metricshub &&\
    chown -R metricshub:metricshub /opt/metricshub &&\
    ls -al /opt/metricshub/**

WORKDIR /opt/metricshub
USER metricshub

ENV PATH="/opt/metricshub/bin:${PATH}"

HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 CMD curl -s http://localhost:13133 | sed -n 's/.*"status":"\([^"]*\)".*/\1/p' | grep -q 'Server available'

ENTRYPOINT ["/opt/metricshub/bin/docker-entrypoint.sh"]
CMD ["/opt/metricshub/bin/metricshub-service"]
