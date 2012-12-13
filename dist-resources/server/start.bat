@ECHO OFF

echo Starting Web UI...
cd /d "%~dp0"
java -Dconfig.file="%~dp0/application.conf" %* -cp "%~dp0\lib\*;" play.core.server.NettyServer .
