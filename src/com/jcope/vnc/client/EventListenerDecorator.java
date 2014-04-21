package com.jcope.vnc.client;

import static com.jcope.debug.Debug.DEBUG;
import static com.jcope.debug.Debug.assert_;

import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Semaphore;

import javax.swing.JFrame;

import com.jcope.debug.LLog;
import com.jcope.ui.ImagePanel;
import com.jcope.vnc.shared.InputEvent;
import com.jcope.vnc.shared.InputEventInfo.INPUT_TYPE;

public class EventListenerDecorator
{
    public static StateMachine stateMachine = null;
    
    private static FrameDecorator nativeDecorator = null;
    private static volatile ImagePanel currentPanel = null;
    private static volatile JFrame currentParent = null;
    private static final Semaphore accessSema = new Semaphore(1, true);
    private static Point point = new Point();
    private static boolean traversalKeysEnabled = true;
    
    static
    {
        try
        {
            Class<?> cl = Class.forName("com.jcope.vnc.client.NativeDecorator");
            Constructor<?> c = cl.getDeclaredConstructor((Class<?>[])null);
            nativeDecorator = (FrameDecorator) c.newInstance();
        }
        catch (InstantiationException e)
        {
            LLog.e(e);
        }
        catch (IllegalAccessException e)
        {
            LLog.e(e);
        }
        catch (IllegalArgumentException e)
        {
            LLog.e(e);
        }
        catch (InvocationTargetException e)
        {
            LLog.e(e);
        }
        catch (NoSuchMethodException e)
        {
            LLog.e(e);
        }
        catch (SecurityException e)
        {
            LLog.e(e);
        }
        catch (ClassNotFoundException e)
        {
            // Do Nothing
            if (DEBUG) {LLog.w(e);}
        }
    }
    
    public static void decorate(JFrame parent, ImagePanel panel)
    {
        assert_(parent != null);
        try
        {
            accessSema.acquire();
        }
        catch (InterruptedException e)
        {
            LLog.e(e);
        }
        try
        {
            ImagePanel oldPanel = currentPanel;
            currentPanel = panel;
            if (oldPanel != null)
            {
                undecorate(currentParent, oldPanel);
            }
            if (panel != null)
            {
                currentParent = parent;
                _decorate(parent, panel);
            }
            else
            {
                currentParent = null;
            }
        }
        finally {
            accessSema.release();
        }
    }
    
    private static KeyListener keyListener = new KeyListener() {
        
        private boolean isModifier(KeyEvent e)
        {
            boolean rval = false;
            
            switch (e.getKeyCode())
            {
                case KeyEvent.VK_SHIFT:
                case KeyEvent.VK_CONTROL:
                case KeyEvent.VK_META:
                case KeyEvent.VK_ALT:
                    rval = true;
                    break;
                default:
                    break;
            }
            
            return rval;
        }
        
        @Override
        public void keyTyped(KeyEvent e)
        {
            // Do Nothing
        }
        
        @Override
        public void keyReleased(KeyEvent e)
        {
            if (!isModifier(e))
            {
                InputEvent event = new InputEvent(INPUT_TYPE.KEY_UP, e);
                stateMachine.addInput(event);
            }
        }
        
        @Override
        public void keyPressed(KeyEvent e)
        {
            if (!isModifier(e))
            {
                InputEvent event = new InputEvent(INPUT_TYPE.KEY_DOWN, e);
                stateMachine.addInput(event);
            }
        }
        
    };
    
    private static MouseMotionListener mouseMotionListener = new MouseMotionListener() {
        
        @Override
        public void mouseMoved(MouseEvent e)
        {
            if (readPoint(e))
            {
                InputEvent event = new InputEvent(INPUT_TYPE.MOUSE_MOVE, e, point.x, point.y);
                stateMachine.addInput(event);
            }
        }
        
        @Override
        public void mouseDragged(MouseEvent e)
        {
            if (readPoint(e))
            {
                InputEvent event = new InputEvent(INPUT_TYPE.MOUSE_MOVE, e, point.x, point.y);
                stateMachine.addInput(event);
            }
        }
        
    };
    
    private static MouseListener mouseListener = new MouseListener() {

        @Override
        public void mouseClicked(MouseEvent e)
        {
            // Do Nothing
        }

        @Override
        public void mouseEntered(MouseEvent e)
        {
            // Do Nothing
        }

        @Override
        public void mouseExited(MouseEvent e)
        {
            // Do Nothing
        }

        @Override
        public void mousePressed(MouseEvent e)
        {
            if (readPoint(e))
            {
                InputEvent event = new InputEvent(INPUT_TYPE.MOUSE_DOWN, e, point.x, point.y);
                stateMachine.addInput(event);
            }
        }

        @Override
        public void mouseReleased(MouseEvent e)
        {
            if (readPoint(e))
            {
                InputEvent event = new InputEvent(INPUT_TYPE.MOUSE_UP, e, point.x, point.y);
                stateMachine.addInput(event);
            }
        }
        
    };
    
    private static MouseWheelListener mouseWheelListener = new MouseWheelListener() {

        @Override
        public void mouseWheelMoved(MouseWheelEvent e)
        {
            InputEvent event = new InputEvent(INPUT_TYPE.WHEEL_SCROLL, e);
            if (event.mwheel() != 0.0d)
            {
            	stateMachine.addInput(event);
            }
        }
        
    };
    
    private static boolean readPoint(MouseEvent e)
    {
        Point t = e.getPoint();
        
        point.x = t.x;
        point.y = t.y;
        
        return currentPanel.worldToScale(point);
    }
    
    private static void _decorate(JFrame parent, ImagePanel panel)
    {
        if (nativeDecorator != null) {nativeDecorator.decorate(parent);}
        //
        traversalKeysEnabled = parent.getFocusTraversalKeysEnabled();
        parent.setFocusTraversalKeysEnabled(false);
        //
        parent.addKeyListener(keyListener);
        panel.addMouseMotionListener(mouseMotionListener);
        panel.addMouseListener(mouseListener);
        panel.addMouseWheelListener(mouseWheelListener);
    }
    
    private static void undecorate(JFrame parent, ImagePanel panel)
    {
        if (nativeDecorator != null) {nativeDecorator.undecorate();}
        //
        parent.removeKeyListener(keyListener);
        panel.removeMouseMotionListener(mouseMotionListener);
        panel.removeMouseListener(mouseListener);
        panel.removeMouseWheelListener(mouseWheelListener);
        //
        parent.setFocusTraversalKeysEnabled(traversalKeysEnabled);
    }
}
