@echo off
SETLOCAL
title VNC Client
call cmd /c "java -client -cp bin;lib/JNativeHook.jar com.jcope.vnc.Client client.properties 2>&1 | tee client.log 2>&1"