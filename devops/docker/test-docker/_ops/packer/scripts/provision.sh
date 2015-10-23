#! /bin/bash
set -e
SPLUNK_DIR=/opt/splunkforwarder

# Install the splunk forwarder
wget http://download.splunk.com/releases/6.1.3/universalforwarder/linux/splunkforwarder-6.1.3-220630-linux-2.6-amd64.deb -O splunkforwarder.deb
sudo dpkg -i splunkforwarder.deb
rm splunkforwarder.deb

# Copy in templates
ls /tmp/templates
sudo chown -R ubuntu $SPLUNK_DIR/etc/system/local
mv /tmp/templates/inputs.conf $SPLUNK_DIR/etc/system/local/
mv /tmp/templates/outputs.conf $SPLUNK_DIR/etc/system/local/

# Change admin password
sudo $SPLUNK_DIR/bin/splunk edit user $SPLUNK_USER \
  -password '$SPLUNK_PASS' \
  -role admin \
  -auth admin:changeme \
  --accept-license --answer-yes
echo "true\n" > /tmp/splunk_auth
sudo mv /tmp/splunk_auth $SPLUNK_DIR/etc/.setup_`echo $SPLUNK_USER`_password

# Enable boot-start
sudo $SPLUNK_DIR/bin/splunk enable boot-start --accept-license --answer-yes
