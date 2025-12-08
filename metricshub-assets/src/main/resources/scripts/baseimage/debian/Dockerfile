# This Dockerfile image build is intended to provide
# a base image for building MetricsHub for linux Debian OS

# Should be build as
# ghcr.io/metricshub/metricshub-community:debian-@debianTagName@-jpackage-@jdkBuildVersion@

FROM debian:@debianTagName@

# Pre-requisites to install Temurin
RUN apt update && apt install -y apt-transport-https gpg wget tar gzip unzip &&\
    wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor | tee /etc/apt/trusted.gpg.d/adoptium.gpg > /dev/null &&\
    echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | tee /etc/apt/sources.list.d/adoptium.list

# Packages required for JPackage build, including JDK
RUN apt update &&\
    if [ "$(dpkg --print-architecture)" = "amd64" ]; then apt install -y gcc-multilib; fi &&\
    apt install -y fakeroot temurin-@jdkBuildVersion@-jdk &&\
    ln -s /usr/lib/jvm/temurin-@jdkBuildVersion@-jdk-$(dpkg --print-architecture) /usr/lib/jvm/default-java
ENV JAVA_HOME=/usr/lib/jvm/default-java

WORKDIR /tmp/jpackage
