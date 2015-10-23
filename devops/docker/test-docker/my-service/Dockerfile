# Docker commands:
##################
FROM ubuntu:14.04
MAINTAINER Jeff Ippolito <jeff.ippolito@gmail.com>

# Install packages from apt-get
RUN apt-get update && apt-get install -y \
    alien \
    wget \
    libaio1 \
    unzip \
    python \
    phantomjs \
    git

# Install Node.js
RUN \
  cd /tmp && \
  wget http://nodejs.org/dist/node-latest.tar.gz && \
  tar xvzf node-latest.tar.gz && \
  rm -f node-latest.tar.gz && \
  cd node-v* && \
  ./configure && \
  CXX="g++ -Wno-unused-local-typedefs" make && \
  CXX="g++ -Wno-unused-local-typedefs" make install && \
  cd /tmp && \
  rm -rf /tmp/node-v* && \
  npm install -g npm && \
  printf '\n# Node.js\nexport PATH="node_modules/.bin:$PATH"' >> /root/.bashrc

# ---------------- #
#   Installation   #
# ---------------- #

# Install Java8
###############
FROM java:8

# Install Jboss8
################
FROM jboss/wildfly
USER jboss
ADD standalone.xml /opt/wildfly/standalone/configuration/standalone.xml
ADD demandmgt-urls.properties /opt/myapp/resource/demandmgt-urls.properties
ADD demandmanagement-webapp-1.9.6-master.war /opt/jboss/wildfly/standalone/deployments/
RUN /opt/jboss/wildfly/bin/add-user.sh admin Admin#70365 --silent

# Set the default command to run on boot
# This will boot WildFly in the standalone mode and bind to all interface

# TODO: I need to override the Spring Profile below via docker-compose??
# /bin/sh /usr/local/jboss/default/bin/standalone.sh -b 0.0.0.0 -bmanagement 0.0.0.0 -# Ddemandmgt.URLsPropertyFile=file:///opt/myapp/resource/demandmgt-urls.properties -Dspring.profiles.active=dev
  
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0", "-bmanagement", "0.0.0.0", "-Ddemandmgt.URLsPropertyFile", "file:///opt/myapp/resource/demandmgt-urls.properties", "-Dspring.profiles.active", "dev"]






