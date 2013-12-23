#TODO: untested
OPT_RESTART=n
function startf {
    rm "server.lock" >/dev/null 2>&1
    if [ -e "server.lock" ]; then
        echo Service already running.
    else
        (java -cp bin com.jcope.vnc.Server 2>&1 | tee server.log 2>&1) &
        echo Service started.
    fi
}
function stopf {
    rm "server.lock" >/dev/null 2>&1
    if [ ! -e "server.lock" ]; then
        if [ "$OPT_RESTART" == "y" ]; then
            startf()
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
            startf()
        fi
    fi
}
function restartf {
    OPT_RESTART=y
    stopf()
}
function unknownf {
    echo Unknown command \"$1\"
}

if [ "$1" == "start" ]; then
    startf()
elif [ "$1" == "stop" ]; then
    stopf()
elif [ "$1" == "restart" ]; then
    restartf()
else
    unknownf()
fi
