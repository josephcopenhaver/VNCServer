#!/bin/bash
OPT_RESTART=n
OPT_RUNNING=n
OPT_RESUME_AFTER_SETUP=n
detect_if_running() {
    OPT_RUNNING=n
    if [ -f "server.lock" ] && [ -f "server.pid" ]; then
        pid=$(head -1 server.pid)
        OSTYPE=$(uname | tr '[:upper:]' '[:lower:]')
        if [[ "$OSTYPE" == "darwin"* ]] || [[ "$OSTYPE" == "freebsd"* ]]; then
        	cmd_name=$(ps -p "$pid" -o comm)
        	if [[ "$cmd_name" == *"/java" ]]; then
        		cmd_name=java
        	fi
        else
        	cmd_name=$(head -1 "/proc/$pid/comm")
        fi
        if [ "$cmd_name" == "java" ]; then
            OPT_RUNNING=y
        fi
    fi
}
start() {
    detect_if_running
    if [ "$OPT_RUNNING" == "y" ]; then
        echo Service already running.
    else
        (java -server -cp bin com.jcope.vnc.Server server.properties 2>&1 | tee server.log 2>&1) &
        echo Service started.
    fi
}
stop() {
    detect_if_running
    if [ "$OPT_RUNNING" == "n" ]; then
        if [ "$OPT_RESTART" == "y" ]; then
            start
        else
            echo Service already terminated.
        fi
    else
        pid=$(head -1 server.pid)
        kill -9 "$pid"
        rm "server.lock" >/dev/null 2>&1
        rm "server.pid" >/dev/null 2>&1
        echo Service terminated.
        if [ "$OPT_RESTART" == "y" ]; then
            start
        fi
    fi
}
restart() {
    OPT_RESTART=y
    stop
}
unknown() {
    echo Unknown command "\"$1\""
}
setup() {
	detect_if_running
	OPT_RESUME_AFTER_SETUP="$OPT_RUNNING"
	if [ "$OPT_RESUME_AFTER_SETUP" == "y" ]; then
		stop
	fi
	(java -client -cp bin com.jcope.vnc.ServerSetup server.properties 2>&1 | tee setup.log 2>&1)
	if [ "$OPT_RESUME_AFTER_SETUP" == "y" ]; then
		start
	fi
}

if [ "$1" == "start" ]; then
    start
elif [ "$1" == "stop" ]; then
    stop
elif [ "$1" == "restart" ]; then
    restart
elif [ "$1" == "setup" ]; then
    setup
else
    unknown
fi
