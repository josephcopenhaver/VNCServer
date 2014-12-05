package com.jcope.vnc.server.input.handle;

import static com.jcope.debug.Debug.assert_;

import com.jcope.vnc.server.ClientHandler;
import com.jcope.vnc.server.input.Handle;
import com.jcope.vnc.shared.AccessModes.ACCESS_MODE;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;

public class SelectScreen extends Handle
{
    
    @Override
    public void handle(ClientHandler client, Object[] args)
    {
        assert_(args != null);
        assert_(args.length == 4);
        assert_(args[0] instanceof Integer);
        assert_(args[1] instanceof ACCESS_MODE);
        assert_(args[2] instanceof Long);
        assert_(args[3] == null || args[3] instanceof String);
        
        int deviceID = (Integer) args[0];
        ACCESS_MODE accessMode = (ACCESS_MODE) args[1];
        Long scanPeriodMS = (Long) args[2];
        String passwordHash = (String) args[3];
        
        assert_(accessMode != null);
        assert_(accessMode != ACCESS_MODE.ALL);
        assert_(scanPeriodMS > 0);
        
        boolean clientBound = client.selectGraphicsDevice(deviceID, accessMode, scanPeriodMS, passwordHash);
        
        client.sendEvent(SERVER_EVENT.AUTHORIZATION_UPDATE, clientBound);
    }
    
}
