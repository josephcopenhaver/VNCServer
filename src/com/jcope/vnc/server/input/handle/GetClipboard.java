package com.jcope.vnc.server.input.handle;

import static com.jcope.debug.Debug.assert_;

import com.jcope.util.ClipboardInterface;
import com.jcope.vnc.server.ClientHandler;
import com.jcope.vnc.server.input.Handle;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;

public class GetClipboard extends Handle
{
    
    @Override
    public void handle(ClientHandler client, Object[] args)
    {
        assert_(null == args);
        
        Object[] clipboardContents = ClipboardInterface.get();
        
        if (null != clipboardContents)
        {
            client.sendEvent(SERVER_EVENT.SET_CLIPBOARD, clipboardContents);
        }
    }
    
}
