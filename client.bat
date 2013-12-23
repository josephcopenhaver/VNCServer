@echo off
title VNC Client
call cmd /c "java -cp bin com.jcope.vnc.Client 2>&1 | tee client.log 2>&1"