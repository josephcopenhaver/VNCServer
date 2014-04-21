package com.jcope.vnc.client;

import java.lang.reflect.Field;

import javax.swing.JFrame;

import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeHookException;
import org.jnativehook.NativeInputEvent;
import org.jnativehook.keyboard.NativeKeyEvent;
import org.jnativehook.keyboard.NativeKeyListener;

import com.jcope.debug.LLog;
import com.jcope.vnc.shared.InputEventInfo.INPUT_TYPE;

public class NativeDecorator implements FrameDecorator
{
    private static boolean forwardMeta = Boolean.TRUE;
    
    private JFrame frame;
    private final static GlobalScreen globalScreen;
    
    static
    {
        try
        {
            GlobalScreen.registerNativeHook();
        }
        catch (NativeHookException e)
        {
            LLog.e(e);
        }
        globalScreen = GlobalScreen.getInstance();
    }
    
    public NativeDecorator()
    {
        frame = null;
    }
    
    private void handleNativeKey(NativeKeyEvent evt, INPUT_TYPE type)
    {
        boolean forwardKey = Boolean.FALSE;
        do
        {
            if (!frame.isActive() || !frame.isFocused())
            {
                // Only forwarding stuff when focused
                break;
            }
            
            if (evt.getKeyCode() != NativeKeyEvent.VK_META)
            {
                // TODO: if is a key on a platform that would not normally forward
                // the key to the target frame
                break;
            }
            else if (!forwardMeta)
            {
                break;
            }
            
            try
            {
                Field f = NativeInputEvent.class.getDeclaredField("propagate");
                f.setAccessible(true);
                f.setBoolean(evt, false);
            }
            catch (NoSuchFieldException e)
            {
                LLog.e(e);
            }
            catch (SecurityException e)
            {
                LLog.e(e);
            }
            catch (IllegalArgumentException e)
            {
                LLog.e(e);
            }
            catch (IllegalAccessException e)
            {
                LLog.e(e);
            }
            
            if (forwardKey)
            {
                // TODO: queue up the key for outbound I/O
            }
        } while (Boolean.FALSE);
    }
    
    private NativeKeyListener listener = new NativeKeyListener() {
        
        @Override
        public void nativeKeyTyped(NativeKeyEvent evt)
        {
            handleNativeKey(evt, INPUT_TYPE.KEY_PRESSED);
        }
        
        @Override
        public void nativeKeyReleased(NativeKeyEvent evt)
        {
            handleNativeKey(evt, INPUT_TYPE.KEY_UP);
        }
        
        @Override
        public void nativeKeyPressed(NativeKeyEvent evt)
        {
            handleNativeKey(evt, INPUT_TYPE.KEY_DOWN);
        }
    };
    
    public void undecorate()
    {
        try
        {
            globalScreen.removeNativeKeyListener(listener);
        }
        finally {
            frame = null;
        }
    }
    
    public void decorate(JFrame parent)
    {
        frame = parent;
        globalScreen.addNativeKeyListener(listener);
    }
}
