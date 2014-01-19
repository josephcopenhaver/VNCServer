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
        assert_(args[1] instanceof ACCESS_MODE);
        assert_(args[2] == null || args[2] instanceof String);
        
        int deviceID = (Integer) args[0];
        ACCESS_MODE accessMode = (ACCESS_MODE) args[1];
        String passwordHash = (String) args[2];
        
        assert_(accessMode != null);
        assert_(accessMode != ACCESS_MODE.ALL);
        
        boolean clientBound = client.selectGraphicsDevice(deviceID, accessMode, passwordHash);
        
        client.sendEvent(SERVER_EVENT.AUTHORIZATION_UPDATE, clientBound);
    }
    
}
