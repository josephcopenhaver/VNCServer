package com.jcope.vnc.server.input.handle;

import static com.jcope.debug.Debug.assert_;

import com.jcope.util.ClipboardInterface;
import com.jcope.util.ClipboardMonitor;
import com.jcope.vnc.server.ClientHandler;
import com.jcope.vnc.server.input.Handle;
import com.jcope.vnc.server.screen.Manager;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;

public class SetClipboard extends Handle
{
    private ClipboardMonitor clipboardMonitor = null;
    
    @Override
    public void handle(ClientHandler client, Object[] args)
    {
        assert_(null != args);
        assert_(1 == args.length);
        assert_(args[0] instanceof Object[]);
        
        if (null == clipboardMonitor)
        {
            clipboardMonitor = ClipboardMonitor.getInstance();
        }
        
        clipboardMonitor.setEnabled(Boolean.FALSE);
        try
        {
            ClipboardInterface.set((Object[]) args[0]);
        }
        finally {
            clipboardMonitor.setEnabled(Boolean.TRUE);
        }
        
        // Notify all OTHER clients of the clipboard contents change
        Manager.getInstance().sendToAllExcept(client, SERVER_EVENT.CLIPBOARD_CHANGED, (Object[]) null);
    }
    
}
