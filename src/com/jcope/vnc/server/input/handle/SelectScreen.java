package com.jcope.vnc.server.input.handle;

import static com.jcope.debug.Debug.assert_;

import com.jcope.vnc.server.ClientHandler;
import com.jcope.vnc.shared.AccessModes.ACCESS_MODE;
import com.jcope.vnc.server.input.Handle;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;

public class SelectScreen extends Handle
{
    
    @Override
    public void handle(ClientHandler client, Object[] args)
    {
        assert_(args != null);
        assert_(args.length == 3);
        assert_(args[0] instanceof Integer);
        assert_(args[1] instanceof String);
        assert_(args[2] == null || args[2] instanceof String);
        
        int deviceID = (int) args[0];
        String accessModeStr = (String) args[1];
        String passwordHash = (String) args[2];
        ACCESS_MODE accessMode = ACCESS_MODE.get(accessModeStr);
        
        assert_(accessMode != null);
        
        boolean clientBound = client.selectGraphicsDevice(deviceID, accessMode, passwordHash);
        
        client.sendEvent(SERVER_EVENT.AUTHORIZATION_UPDATE, clientBound);
    }
    
}
