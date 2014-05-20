package com.jcope.vnc.client.input.handle;

import static com.jcope.debug.Debug.assert_;

import java.io.IOException;

import com.jcope.debug.LLog;
import com.jcope.util.ClipboardInterface;
import com.jcope.util.ClipboardMonitor;
import com.jcope.vnc.client.StateMachine;
import com.jcope.vnc.client.input.Handle;

public class SetClipboard extends Handle
{
    private ClipboardMonitor clipboardMonitor = null;
    
    @Override
    public void handle(StateMachine stateMachine, Object[] args)
    {
        assert_(null != args);
        assert_(args.length > 0);
        assert_(args.length % 2 == 0);
        
        if (!stateMachine.isClipboardSyncEnabled())
        {
            return;
        }
        
        if (null == clipboardMonitor)
        {
            clipboardMonitor = ClipboardMonitor.getInstance();
        }
        
        clipboardMonitor.setEnabled(Boolean.FALSE);
        try
        {
            ClipboardInterface.set(args);
        }
        catch (IOException e)
        {
            LLog.e(e, Boolean.FALSE);
        }
        finally {
            clipboardMonitor.setEnabled(Boolean.TRUE);
        }
    }
    
}
