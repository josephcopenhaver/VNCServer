package com.jcope.vnc.server.input.handle;

import static com.jcope.debug.Debug.assert_;

import com.jcope.vnc.server.ClientHandler;
import com.jcope.vnc.server.input.Handle;

public class SetScreenMonitorPaused extends Handle
{
    
    @Override
    public void handle(ClientHandler client, Object[] args)
    {
        assert_(args != null);
        assert_(args.length == 1);
        assert_(args[0] != null);
        assert_(args[0] instanceof Boolean);
        
        boolean paused = (Boolean) args[0];
        
        client.setPaused(paused);
    }
    
}
