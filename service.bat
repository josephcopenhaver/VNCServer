@echo off
SETLOCAL
set OPT_RESTART=n
set OPT_SETUP=n
if "%1"=="restart" goto restart
if "%1"=="stop" goto stop
if "%1"=="start" goto start
if "%1"=="setup" goto setup
goto unknown_command


:setup
set OPT_SETUP=y
del server.lock>NUL 2>&1
IF EXIST server.lock set OPT_RESTART=y
IF EXIST server.lock goto stop
:setup1
cmd /c "java -client -cp bin com.jcope.vnc.ServerSetup server.properties 2>&1 | tee setup.log 2>&1"
goto setup2


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
IF "%OPT_SETUP%"=="y" goto setup1
:setup2
rem wait 2 seconds (3 pings)
if "%OPT_RESTART%"=="y" ping 127.0.0.1 -n 3 > nul
if "%OPT_RESTART%"=="y" goto start
goto end


:start
del server.lock>NUL 2>&1
IF EXIST server.lock goto already_running
start "VNC Server" cmd /c "java -server -cp bin com.jcope.vnc.Server server.properties 2>&1 | tee server.log 2>&1"
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