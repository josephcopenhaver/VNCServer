package com.jcope.vnc.client;

import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.awt.event.WindowListener;
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
    private volatile boolean windowActive = false;
    private volatile boolean windowFocused = false;
    
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
            if (!windowActive || !windowFocused)
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
    
    private NativeKeyListener nKeyListener = new NativeKeyListener() {
        
        @Override
        public void nativeKeyTyped(NativeKeyEvent evt)
        {
            // Do Nothing
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
    
    private WindowListener windowListener = new WindowListener() {
        
        @Override
        public void windowOpened(WindowEvent e)
        {
            // Do Nothing
        }
        
        @Override
        public void windowIconified(WindowEvent e)
        {
            // Do Nothing
        }
        
        @Override
        public void windowDeiconified(WindowEvent e)
        {
            // Do Nothing
        }
        
        @Override
        public void windowDeactivated(WindowEvent e)
        {
            windowActive = Boolean.FALSE;
        }
        
        @Override
        public void windowClosing(WindowEvent e)
        {
            // Do Nothing
        }
        
        @Override
        public void windowClosed(WindowEvent e)
        {
            // Do Nothing
        }
        
        @Override
        public void windowActivated(WindowEvent e)
        {
            windowActive = Boolean.TRUE;
        }
    };
    
    private WindowFocusListener windowFocusListener = new WindowFocusListener() {
        
        @Override
        public void windowLostFocus(WindowEvent e)
        {
            windowFocused = Boolean.FALSE;
        }
        
        @Override
        public void windowGainedFocus(WindowEvent e)
        {
            windowFocused = Boolean.TRUE;
        }
    };
    
    public void undecorate()
    {
        try
        {
            frame.removeWindowListener(windowListener);
            frame.removeWindowFocusListener(windowFocusListener);
            globalScreen.removeNativeKeyListener(nKeyListener);
        }
        finally {
            frame = null;
        }
    }
    
    public void decorate(JFrame parent)
    {
        frame = parent;
        frame.addWindowListener(windowListener);
        frame.addWindowFocusListener(windowFocusListener);
        windowActive = frame.isActive();
        windowFocused = frame.isFocused();
        globalScreen.addNativeKeyListener(nKeyListener);
    }
}
