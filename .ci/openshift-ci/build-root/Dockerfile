# Use the centos image as the base image
FROM quay.io/centos/centos:stream8

# Install additional tooling, see https://github.com/openshift/release/blob/master/clusters/app.ci/supplemental-ci-images/ci-tools-build-root.yaml#L338
RUN INSTALL_PKGS="git java-17-openjdk.x86_64 httpd-tools" && \
    dnf install -y $INSTALL_PKGS && \
    rpm -V $INSTALL_PKGS && \
    dnf clean all && \
    touch /os-build-image

# Downloading and installing Maven
ARG MAVEN_VERSION=3.8.8
ARG BASE_URL=https://apache.osuosl.org/maven/maven-3/${MAVEN_VERSION}/binaries

RUN mkdir -p /usr/share/maven /usr/share/maven/ref \
  && echo "Downlaoding maven" \
  && curl -fsSL -o /tmp/apache-maven.tar.gz ${BASE_URL}/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
  \
  && echo "Unziping maven" \
  && tar -xzf /tmp/apache-maven.tar.gz -C /usr/share/maven --strip-components=1 \
  \
  && echo "Cleaning and setting links" \
  && rm -f /tmp/apache-maven.tar.gz \
  && ln -s /usr/share/maven/bin/mvn /usr/bin/mvn

ENV MAVEN_HOME /usr/share/maven
ENV MAVEN_CONFIG "$USER_HOME_DIR/.m2"

# Downloading and installing the OpenShift CLI
ENV OC_PKG=https://mirror.openshift.com/pub/openshift-v4/clients/ocp/latest-4.12/openshift-client-linux.tar.gz \
      OC_HOME=/usr/oc/oc-4.1.2

RUN set -eux; \
      curl -L --output /tmp/oc.tgz "$OC_PKG"; \
      mkdir -p "$OC_HOME"; \
      tar --extract --file /tmp/oc.tgz --directory "$OC_HOME"; \
      ln -s "$OC_HOME"/oc /usr/bin/oc
