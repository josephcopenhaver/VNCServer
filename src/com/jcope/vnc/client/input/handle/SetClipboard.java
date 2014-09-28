package com.jcope.vnc.client.input.handle;

import static com.jcope.debug.Debug.assert_;

import java.io.IOException;

import com.jcope.debug.LLog;
import com.jcope.util.ClipboardInterface;
import com.jcope.util.ClipboardInterface.ClipboardBusyException;
import com.jcope.util.ClipboardMonitor;
import com.jcope.vnc.client.StateMachine;
import com.jcope.vnc.client.input.Handle;

public class SetClipboard extends Handle
{
    @Override
    public void handle(StateMachine stateMachine, Object[] args)
    {
        assert_(null != args);
        assert_(args.length > 0);
        assert_(args.length % 2 == 0);
        
        if (!stateMachine.isClipboardSyncEnabled())
        {
            System.err.println("\nno clipboard sync for you!\n"); // TODO: remove
            return;
        }
        
        ClipboardMonitor clipboardMonitor = ClipboardMonitor.getInstance();
        
        clipboardMonitor.lockAndPause();
        System.err.println("\nGot CM lock and paused\n"); // TODO: remove
        try
        {
            ClipboardInterface.set(args);
        }
        catch (IOException e)
        {
            LLog.e(e, Boolean.FALSE);
        }
        catch (ClipboardBusyException e)
        {
            LLog.e(e, Boolean.FALSE);
        }
        finally {
            clipboardMonitor.unlockAndUnpause();
        }
        System.err.println("\nCM lock removed and unpaused\n"); // TODO: remove
    }
    
}
