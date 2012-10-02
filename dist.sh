#!/bin/bash -x

VERSION=`cat project/Build.scala | grep -G 'val\s\s*appVersion' | sed 's/[^"]*"\(.*\)".*/\1/'`
NAME=pipeline2-webui-$VERSION

play clean;
mkdir -p dist/$NAME/lib
mkdir -p dist/$NAME/uploads
cp -r dist-resources/dp2webui-cleandb dist/$NAME/dp2webui
cp dist-resources/application-prod.conf dist/$NAME
echo '#!/usr/bin/env sh' > dist/$NAME/start
echo '' >> dist/$NAME/start
echo 'exec java -Dconfig.file=`dirname $0`/application-prod.conf $@ -cp "`dirname $0`/lib/*" play.core.server.NettyServer `dirname $0`/..' >> dist/$NAME/start
chmod +x dist/$NAME/start
play compile stage;
cp -r target/staged/* dist/$NAME/lib
cd dist && zip -r $NAME.zip $NAME && cd ..
