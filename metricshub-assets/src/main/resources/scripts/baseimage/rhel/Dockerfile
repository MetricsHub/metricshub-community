# This Dockerfile image build is intended to provide
# a base image for building MetricsHub for linux RHEL OS

# Should be build as
# ghcr.io/metricshub/metricshub-community:rhel-@fedoraTagName@-jpackage-@jdkBuildVersion@

FROM fedora:@fedoraTagName@

RUN set -e; \
    . /etc/os-release; \
    DIST="${DISTRIBUTION_NAME:-$ID}"; \
    cat > /etc/yum.repos.d/adoptium.repo <<EOF
[Adoptium]
name=Adoptium
baseurl=https://packages.adoptium.net/artifactory/rpm/${DIST}/\$releasever/\$basearch
enabled=1
gpgcheck=1
gpgkey=https://packages.adoptium.net/artifactory/api/gpg/key/public
EOF

# Packages required for JPackage build
RUN dnf -y install rpm-build &&\
    dnf -y install temurin-@jdkBuildVersion@-jdk
ENV JAVA_HOME=/usr/lib/jvm/temurin-@jdkBuildVersion@-jdk

WORKDIR /tmp/jpackage
