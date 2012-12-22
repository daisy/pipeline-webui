@ECHO OFF

title Pipeline 2 Web UI
cd /d "%~dp0"

echo Starting browser...
start startui\start.html

echo Starting Web UI...
java -Dconfig.file="application.conf" %* -cp "lib\*;" play.core.server.NettyServer .
