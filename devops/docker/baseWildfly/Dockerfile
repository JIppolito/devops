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
#ADD your-awesome-app.war /opt/jboss/wildfly/standalone/deployments/
RUN /opt/jboss/wildfly/bin/add-user.sh admin Admin#70365 --silent

# Set the default command to run on boot
# This will boot WildFly in the standalone mode and bind to all interface
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0", "-bmanagement", "0.0.0.0"]






