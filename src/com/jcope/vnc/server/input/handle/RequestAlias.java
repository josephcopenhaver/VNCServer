package com.jcope.vnc.server.input.handle;

import static com.jcope.debug.Debug.assert_;

import com.jcope.vnc.server.AliasRegistry;
import com.jcope.vnc.server.ClientHandler;
import com.jcope.vnc.server.input.Handle;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;

public class RequestAlias extends Handle {

    @Override
    public void handle(ClientHandler client, Object[] args) {
        assert_(args != null);
        assert_(args.length == 1);
        assert_(args[0] instanceof String);

        String alias = (String) args[0];

        if (alias.equals("")) {
            AliasRegistry.getInstance().unbind(client);
            return;
        }

        boolean worked = AliasRegistry.getInstance().bind(client, alias);

        client.sendEvent(SERVER_EVENT.CLIENT_ALIAS_UPDATE, alias, worked);
    }

}
