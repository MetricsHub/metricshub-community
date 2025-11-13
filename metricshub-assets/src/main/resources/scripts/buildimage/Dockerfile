# This Dockerfile image build is intended to package
# MetricsHub for linux Debian and RHEL OSes

# This image is a multi-stage build to create all the components
# required for the package:
#  - OTEL distribution
#  - Custom JRE
#  - Binaries from JAR using JPackage
#  - Installable package using JPackage
ARG DEBIAN_JPACKAGE_IMAGE=ghcr.io/metricshub/metricshub-community:debian-@debianTagName@-jpackage-@jdkBuildVersion@
ARG RHEL_JPACKAGE_IMAGE=ghcr.io/metricshub/metricshub-community:rhel-@fedoraTagName@-jpackage-@jdkBuildVersion@

# Reuse Debian JPackage image to avoid reinstall of common dependencies
FROM ${DEBIAN_JPACKAGE_IMAGE} AS getsources

COPY assets-debian.zip /tmp/
COPY assets-rhel.zip /tmp/
WORKDIR /tmp/debian
RUN unzip /tmp/assets-debian.zip
WORKDIR /tmp/rhel
RUN unzip /tmp/assets-rhel.zip

#********************
# Build JRE for Linux
#********************

FROM eclipse-temurin:@metricshub-jre.version@-jdk AS getjre

COPY --from=getsources /tmp/debian/jre-modules.txt /tmp/modules.txt

RUN jlink \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --add-modules $(tr '\n' ',' < /tmp/modules.txt | sed 's/,$//') \
    --output /opt/jre

#********************
# Run Jpackage for Debian
#********************

FROM ${DEBIAN_JPACKAGE_IMAGE} AS debianbuilder

COPY --from=getjre /opt/jre /opt/jre
COPY --from=getsources /tmp/debian/jpackage /tmp/jpackage

RUN bash package.sh /opt/jre /tmp/metricshub &&\
    rm -f /tmp/metricshub/metricshub/lib/*.png &&\
    mkdir -p /tmp/packages &&\
    mv /tmp/metricshub/*.deb /tmp/packages &&\
    cd /tmp/metricshub/ && \
    tar cvzf /tmp/packages/metricshub-community-linux.tar.gz metricshub/ &&\
    ls -al /tmp/packages

#********************
# Run Jpackage for RHEL
#********************

FROM ${RHEL_JPACKAGE_IMAGE} AS rhelbuilder

COPY --from=getjre /opt/jre /opt/jre
COPY --from=getsources /tmp/rhel/jpackage /tmp/jpackage

RUN bash package.sh /opt/jre /tmp/packages &&\
    ls -al /tmp/packages

#********************
# Merge built packages
#********************

# Should be tagged as ghcr.io/metricshub/metricshub-enterprise:packages-latest
FROM debian:@debianTagName@

COPY --from=debianbuilder /tmp/packages/*.deb /tmp/packages/
COPY --from=debianbuilder /tmp/packages/metricshub-community-linux.tar.gz /tmp/packages/
COPY --from=debianbuilder /tmp/metricshub/metricshub /opt/metricshub
COPY --from=rhelbuilder /tmp/packages/*.rpm /tmp/packages/
