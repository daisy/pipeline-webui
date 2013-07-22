@ECHO OFF
SETLOCAL ENABLEDELAYEDEXPANSION

title Pipeline 2 Web UI
cd /d "%~dp0"

set DP2DATA=%APPDATA%\DAISY Pipeline 2
set DP2DATA_SLASH=%DP2DATA:\=/%

IF EXIST "!DP2DATA!\webui" GOTO CONFIGURATION_DONE

echo Creating application data directory...
mkdir "%DP2DATA%\webui"

echo Creating directory for log files...
mkdir "%DP2DATA%\log"

echo Copying default database...
mkdir "%DP2DATA%\webui\dp2webui"
xcopy /E /Y dp2webui "%DP2DATA%\webui\dp2webui" >NUL

echo Copying loading page...
mkdir "%DP2DATA%\webui\startui"
xcopy /E /Y startui "%DP2DATA%\webui\startui" >NUL

:CONFIGURATION_DONE

set DP2WEBUILOGFILE="%DP2DATA%\webui\startui\log.txt"
echo %date% %time% > %DP2WEBUILOGFILE%
ver >> %DP2WEBUILOGFILE%
echo %processor_architecture% >> %DP2WEBUILOGFILE%

rem add variables to the DP2 object in the startui page
copy /Y startui\start.html "%DP2DATA%\webui\startui" >> %DP2WEBUILOGFILE%
echo ^<script type=^"text/javascript^"^>set^('slash','\\'^);^</script^> >> "%DP2DATA%\webui\startui\start.html"
echo ^<script type=^"text/javascript^"^>set^('DP2DATA','%DP2DATA_SLASH%'^);^</script^> >> "%DP2DATA%\webui\startui\start.html"
IF EXIST "!DP2DATA!\webui\RUNNING_PID" (
echo ^<script type=^"text/javascript^"^>set^('ALREADY_RUNNING','true'^);^</script^> >> "%DP2DATA%\webui\startui\start.html"
) ELSE (
echo ^<script type=^"text/javascript^"^>set^('ALREADY_RUNNING','false'^);^</script^> >> "%DP2DATA%\webui\startui\start.html"
)

echo Starting browser...
start "" "%DP2DATA%\webui\startui\start.html"

IF NOT EXIST "%DP2DATA%\webui\RUNNING_PID" (
echo Starting Web UI...
java  -Dderby.stream.error.file="%DP2DATA%\log\webui-database.log" -Dlogger.file="%~dp0\logger.xml" -Dpidfile.path="%DP2DATA%\webui\RUNNING_PID" -Dconfig.file="%~dp0\application.conf" %* -cp "%~dp0\lib\*;" play.core.server.NettyServer . >> %DP2WEBUILOGFILE% 2>&1
) ELSE (
echo Web UI is already running.
)
