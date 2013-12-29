#!/bin/bash
OPT_RESTART=n
OPT_RUNNING=n
function detect_if_runningf {
    OPT_RUNNING=n
    if [ -f "server.lock" ] && [ -f "server.pid" ]; then
        pid=$(head -1 server.pid)
        cmd_name=$(head -1 /proc/$pid/comm)
        if [ "$cmd_name" == "java" ]; then
            OPT_RUNNING=y
        fi
    fi
}
function startf {
    detect_if_runningf
    if [ "$OPT_RUNNING" == "y" ]; then
        echo Service already running.
    else
        (java -cp bin com.jcope.vnc.Server 2>&1 | tee server.log 2>&1) &
        echo Service started.
    fi
}
function stopf {
    detect_if_runningf
    if [ "$OPT_RUNNING" == "n" ]; then
        if [ "$OPT_RESTART" == "y" ]; then
            startf
        else
            echo Service already terminated.
        fi
    else
        pid=$(head -1 server.pid)
        kill -9 $pid
        rm "server.lock" >/dev/null 2>&1
        rm "server.pid" >/dev/null 2>&1
        echo Service terminated.
        if [ "$OPT_RESTART" == "y" ]; then
            startf
        fi
    fi
}
function restartf {
    OPT_RESTART=y
    stopf
}
function unknownf {
    echo Unknown command \"$1\"
}

if [ "$1" == "start" ]; then
    startf
elif [ "$1" == "stop" ]; then
    stopf
elif [ "$1" == "restart" ]; then
    restartf
else
    unknownf
fi
