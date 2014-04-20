@echo off
SETLOCAL
title VNC Client
call cmd /c "java -cp bin;lib com.jcope.vnc.Client client.properties 2>&1 | tee client.log 2>&1"