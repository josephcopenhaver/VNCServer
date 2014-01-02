@echo off
set OPT_RESTART=n
if "%1"=="restart" goto restart
if "%1"=="stop" goto stop
if "%1"=="start" goto start
goto unknown_command


:restart
set OPT_RESTART=y
goto stop


:stop
del server.lock>NUL 2>&1
IF NOT EXIST server.lock IF "%OPT_RESTART%"=="y" (goto start) ELSE (goto already_stopped)
set /p pid=<server.pid
TASKKILL /PID %pid% /F>NUL 2>&1
del server.lock>NUL 2>&1
del server.pid>NUL 2>&1
echo.
echo Service terminated.
rem wait 2 seconds (3 pings)
if "%OPT_RESTART%"=="y" ping 127.0.0.1 -n 3 > nul
if "%OPT_RESTART%"=="y" goto start
goto end


:start
del server.lock>NUL 2>&1
IF EXIST server.lock goto already_running
start "VNC Server" cmd /c "java -cp bin com.jcope.vnc.Server 2>&1 | tee server.log 2>&1"
echo.
echo Service started.
goto end


:already_running
echo.
echo Service already running.
goto end


:already_stopped
echo.
echo Service already terminated.
goto end


:unknown_command
echo.
echo Unknown command "%1"
goto end


:end