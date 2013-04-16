@ECHO OFF

title Pipeline 2 Web UI
cd /d "%~dp0"

echo Starting browser...
start startui\start.html

echo Starting Web UI...
echo %date% %time% > startui\log.txt
ver >> startui\log.txt
echo %processor_architecture% >> startui\log.txt
java -Dconfig.file="application.conf" %* -cp "lib\*;" play.core.server.NettyServer . >> startui\log.txt 2>&1
