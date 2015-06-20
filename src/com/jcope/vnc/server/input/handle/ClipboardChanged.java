package com.jcope.vnc.server.input.handle;

import static com.jcope.debug.Debug.assert_;

import com.jcope.vnc.Server;
import com.jcope.vnc.server.ClientHandler;
import com.jcope.vnc.server.input.Handle;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;

public class ClipboardChanged extends Handle {

    @Override
    public void handle(ClientHandler client, Object[] args) {
        assert_(null == args);

        if (!((Boolean) Server.SERVER_PROPERTIES.SUPPORT_CLIPBOARD_SYNCHRONIZATION
                .getValue())) {
            return;
        }

        client.sendEvent(SERVER_EVENT.GET_CLIPBOARD);
    }

}
