package com.jcope.vnc.server;

import com.jcope.vnc.shared.StateMachine.CONNECTION_STATE;

public class ClientState {
    public volatile boolean isMonitoringAliasChanges;
    public volatile boolean isMonitoringConnectionChanges;
    public CONNECTION_STATE conState;

    public ClientState() {
        isMonitoringAliasChanges = Boolean.FALSE;
        isMonitoringConnectionChanges = Boolean.FALSE;
        conState = CONNECTION_STATE.INIT;
    }
}
