#!/bin/bash -v

# Compile pipeline-clientlib-java
cd ~/pipeline-clientlib-java/lib && mvn clean install

# Delete all cached DAISY resources from the SBT cache
cd /opt/play-2.1.0/repository/cache/ && rm -rfv org.daisy*

# cd back to the WebUI dir; SBT will retrieve DAISY resources from local Maven repo on next run
cd ~/pipeline-webui
