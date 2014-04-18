package com.jcope.vnc.shared;

import static com.jcope.debug.Debug.assert_;

import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.io.Serializable;
import java.util.Arrays;

import com.jcope.vnc.server.DirectRobot;
import com.jcope.vnc.server.screen.Manager;
import com.jcope.vnc.shared.InputEventInfo.INPUT_TYPE;

public class InputEvent implements Serializable
{
    // Generated: serialVersionUID
    private static final long serialVersionUID = 85047497862213637L;
    
    
    private INPUT_TYPE type;
    private int mult, mod, modex;
    private int[] typeProps = null;
    private Double magnitude = null;
    
    
    public InputEvent(INPUT_TYPE type, Object... args)
    {
        int mult = 1;
        
        switch(type)
        {
            case KEY_PRESSED:
            case MOUSE_PRESSED:
            {
                assert_(false);
                
                break;
            }
            case KEY_DOWN:
            case KEY_UP:
            {
                assert_(args != null);
                assert_(args.length == 1);
                assert_(args[0] instanceof KeyEvent);
                
                KeyEvent e = (KeyEvent) args[0];
                mod = e.getModifiers();
                modex = e.getModifiersEx();
                typeProps = new int[]{e.getKeyCode(), e.getKeyLocation()};
                
                break;
            }
            case MOUSE_DOWN:
            case MOUSE_UP:
            {
                assert_(args != null);
                assert_(args.length == 3);
                assert_(args[0] instanceof MouseEvent);
                assert_(args[1] instanceof Integer);
                assert_(args[2] instanceof Integer);
                
                MouseEvent e = (MouseEvent) args[0];
                int button = e.getButton();
                switch(button)
                {
                    case MouseEvent.BUTTON1:
                        button = java.awt.event.InputEvent.BUTTON1_MASK;
                        break;
                    case MouseEvent.BUTTON2:
                        button = java.awt.event.InputEvent.BUTTON2_MASK;
                        break;
                    case MouseEvent.BUTTON3:
                        button = java.awt.event.InputEvent.BUTTON3_MASK;
                        break;
                    default:
                        assert_(false);
                        break;
                }
                mult = e.getClickCount();
                mod = e.getModifiers();
                modex = e.getModifiersEx();
                typeProps = new int[]{(Integer) args[1], (Integer) args[2], button};
                
                break;
            }
            case MOUSE_MOVE:
            {
                assert_(args != null);
                assert_(args.length == 3);
                assert_(args[0] instanceof MouseEvent);
                assert_(args[1] instanceof Integer);
                assert_(args[2] instanceof Integer);
                
                MouseEvent e = (MouseEvent) args[0];
                mod = e.getModifiers();
                modex = e.getModifiersEx();
                typeProps = new int[]{(Integer) args[1], (Integer) args[2]};
                
                break;
            }
            case WHEEL_SCROLL:
            {
                assert_(args != null);
                assert_(args.length == 1);
                assert_(args[0] instanceof MouseWheelEvent);
                
                MouseWheelEvent e = (MouseWheelEvent) args[0];
                mod = e.getModifiers();
                modex = e.getModifiersEx();
                //magnitude = e.getPreciseWheelRotation(); // TODO: use this function when on Java 1.7
                magnitude = (double) e.getWheelRotation();
                break;
            }
        }
        
        this.type = type;
        this.mult = mult;
    }
    
    private int x()
    {
        int rval = 0;
        
        switch(type)
        {
            case MOUSE_DOWN:
            case MOUSE_MOVE:
            case MOUSE_PRESSED:
            case MOUSE_UP:
                rval = typeProps[0];
                break;
            case KEY_DOWN:
            case KEY_PRESSED:
            case KEY_UP:
            case WHEEL_SCROLL:
                assert_(false);
                break;
        }
        
        return rval;
    }
    
    private int y()
    {
        int rval = 0;
        
        switch(type)
        {
            case MOUSE_DOWN:
            case MOUSE_MOVE:
            case MOUSE_PRESSED:
            case MOUSE_UP:
                rval = typeProps[1];
                break;
            case KEY_DOWN:
            case KEY_PRESSED:
            case KEY_UP:
            case WHEEL_SCROLL:
                assert_(false);
                break;
        }
        
        return rval;
    }
    
    public double mwheel()
    {
        double rval = 0.0d;
        
        switch(type)
        {
            case WHEEL_SCROLL:
                rval = magnitude;
                break;
            case MOUSE_DOWN:
            case MOUSE_MOVE:
            case MOUSE_PRESSED:
            case MOUSE_UP:
            case KEY_DOWN:
            case KEY_PRESSED:
            case KEY_UP:
                assert_(false);
                break;
        }
        
        return rval;
    }
    
    private int keycode()
    {
        int rval = 0;
        
        switch(type)
        {
            case KEY_DOWN:
            case KEY_UP:
            case KEY_PRESSED:
                rval = typeProps[0];
                break;
            case MOUSE_DOWN:
            case MOUSE_MOVE:
            case MOUSE_PRESSED:
            case MOUSE_UP:
            case WHEEL_SCROLL:
                assert_(false);
                break;
        }
        
        return rval;
    }
    
    private int mbutton()
    {
        int rval = 0;
        
        switch(type)
        {
            case MOUSE_DOWN:
            case MOUSE_UP:
            case MOUSE_PRESSED:
                rval = typeProps[2];
                break;
            case KEY_DOWN:
            case KEY_UP:
            case KEY_PRESSED:
            case MOUSE_MOVE:
            case WHEEL_SCROLL:
                assert_(false);
                break;
        }
        
        return rval;
    }
    
    public static void perform(DirectRobot dirbot, InputEvent event)
    {
        int modex = pushMods(dirbot, event.mod, event.modex);
        
        switch(event.type)
        {
            case MOUSE_DOWN:
            case MOUSE_UP:
            case MOUSE_PRESSED:
            case MOUSE_MOVE:
                Manager.getInstance().getOrigin(dirbot, InputEventInfo.ORIGIN);
                break;
            case KEY_DOWN:
            case KEY_PRESSED:
            case KEY_UP:
            case WHEEL_SCROLL:
                break;
        }
        
        try
        {
            final int EOM = event.mult - 1;
            for (int i=0; i<event.mult; i++)
            {
                switch(event.type)
                {
                    case KEY_PRESSED:
                    case KEY_DOWN:
                    {
                        dirbot.keyPress(event.keycode());
                        
                        if (event.type == INPUT_TYPE.KEY_DOWN)
                        {
                            break;
                        }
                    }
                    case KEY_UP:
                    {
                        dirbot.keyRelease(event.keycode());
                        
                        break;
                    }
                    case MOUSE_MOVE:
                    {
                        i = EOM;
                        moveMouse(dirbot, event);
                        
                        break;
                    }
                    case MOUSE_PRESSED:
                    case MOUSE_DOWN:
                    {
                        if (i == 0)
                        {
                            moveMouse(dirbot, event);
                        }
                        dirbot.mousePress(event.mbutton());
                        
                        if (event.type == INPUT_TYPE.MOUSE_DOWN)
                        {
                            break;
                        }
                    }
                    case MOUSE_UP:
                    {
                        if (i == 0 && event.type == INPUT_TYPE.MOUSE_UP)
                        {
                            moveMouse(dirbot, event);
                        }
                        dirbot.mouseRelease(event.mbutton());
                        
                        break;
                    }
                    case WHEEL_SCROLL:
                    {
                        i = EOM;
                        dirbot.mouseWheel((int) Math.ceil(event.mwheel()));
                        
                        break;
                    }
                }
            }
        }
        finally {
            popMods(dirbot, modex);
        }
    }
    
    private static void moveMouse(DirectRobot dirbot, InputEvent event)
    {
        int x, y;
        
        x = event.x();
        y = event.y();
        
        dirbot.mouseMove(x, y, InputEventInfo.ORIGIN[0], InputEventInfo.ORIGIN[1]);
    }
    
    private static int pushMods(DirectRobot dirbot, int mod, int modex)
    {
        int rval = 0;
        Robot robot;
        int mask;
        
        if (modex != 0)
        {
            robot = dirbot.robot;
            
            mask = java.awt.event.InputEvent.CTRL_DOWN_MASK;
            if ((mask & modex) != 0)
            {
                rval |= mask;
                robot.keyPress(KeyEvent.VK_CONTROL);
            }
            
            mask = java.awt.event.InputEvent.SHIFT_DOWN_MASK;
            if ((mask & modex) != 0)
            {
                rval |= mask;
                robot.keyPress(KeyEvent.VK_SHIFT);
            }
            
            mask = java.awt.event.InputEvent.ALT_DOWN_MASK;
            if ((mask & modex) != 0)
            {
                rval |= mask;
                robot.keyPress(KeyEvent.VK_ALT);
            }
            
            mask = java.awt.event.InputEvent.META_DOWN_MASK;
            if ((mask & modex) != 0)
            {
                rval |= mask;
                robot.keyPress(KeyEvent.VK_META);
            }
        }
        
        return rval;
    }
    
    private static void popMods(DirectRobot dirbot, int modex)
    {
        Robot robot;
        int mask;
        
        if (modex != 0)
        {
            robot = dirbot.robot;
            
            mask = java.awt.event.InputEvent.CTRL_DOWN_MASK;
            if ((mask & modex) != 0)
            {
                robot.keyRelease(KeyEvent.VK_CONTROL);
            }
            
            mask = java.awt.event.InputEvent.SHIFT_DOWN_MASK;
            if ((mask & modex) != 0)
            {
                robot.keyRelease(KeyEvent.VK_SHIFT);
            }
            
            mask = java.awt.event.InputEvent.ALT_DOWN_MASK;
            if ((mask & modex) != 0)
            {
                robot.keyRelease(KeyEvent.VK_ALT);
            }
            
            mask = java.awt.event.InputEvent.META_DOWN_MASK;
            if ((mask & modex) != 0)
            {
                robot.keyRelease(KeyEvent.VK_META);
            }
        }
    }
    
    public boolean merge(InputEvent next, boolean isFirstCollapse)
    {
        boolean rval = false;
        
        if (mod == next.mod && modex == next.modex)
        {
            if (type == next.type)
            {
                if (isFirstCollapse)
                {
                    switch (type)
                    {
                        case KEY_PRESSED:
                        case MOUSE_PRESSED:
                            assert_(false);
                            break;
                        case KEY_DOWN:
                        case KEY_UP:
                        case MOUSE_DOWN:
                        case MOUSE_UP:
                            // Hidden collapsing of nonsensical sequences
                            rval = (Arrays.equals(typeProps, next.typeProps));
                            break;
                        case MOUSE_MOVE:
                            System.arraycopy(next.typeProps, 0, typeProps, 0, typeProps.length);
                            mult++;
                            rval = true;
                            break;
                        case WHEEL_SCROLL:
                            if ((magnitude > 0) == (next.magnitude > 0) &&
                                    ((magnitude > 0 && magnitude + next.magnitude > 0) ||
                                            (magnitude < 0 && magnitude + next.magnitude < 0)))
                            {
                                magnitude += next.magnitude;
                                mult++;
                                rval = true;
                            }
                            break;
                    }
                }
                else
                {
                    switch (type)
                    {
                        case KEY_PRESSED:
                        case MOUSE_PRESSED:
                            if (Arrays.equals(typeProps, next.typeProps))
                            {
                                mult++;
                                rval = true;
                            }
                            break;
                        case KEY_DOWN:
                        case KEY_UP:
                        case MOUSE_DOWN:
                        case MOUSE_MOVE:
                        case MOUSE_UP:
                        case WHEEL_SCROLL:
                            break;
                    }
                }
            }
            else
            {
                if ((type == INPUT_TYPE.KEY_DOWN && next.type == INPUT_TYPE.KEY_UP) ||
                        (type == INPUT_TYPE.MOUSE_DOWN && next.type == INPUT_TYPE.MOUSE_UP))
                {
                    if (Arrays.equals(typeProps, next.typeProps))
                    {
                        switch (next.type)
                        {
                            case KEY_UP:
                                type = INPUT_TYPE.KEY_PRESSED;
                                break;
                            case MOUSE_UP:
                                type = INPUT_TYPE.MOUSE_PRESSED;
                                break;
                            case MOUSE_MOVE:
                            case KEY_PRESSED:
                            case MOUSE_PRESSED:
                            case KEY_DOWN:
                            case MOUSE_DOWN:
                            case WHEEL_SCROLL:
                                assert_(false);
                                break;
                        }
                        rval = true;
                    }
                }
                else if (type == INPUT_TYPE.MOUSE_MOVE
                        && (next.type == INPUT_TYPE.MOUSE_DOWN
                            || next.type == INPUT_TYPE.MOUSE_UP)
                        && typeProps[0] == next.typeProps[0]
                        && typeProps[1] == next.typeProps[1])
                {
                    mult = next.mult;
                    type = next.type;
                    typeProps = next.typeProps;
                    next.typeProps = null; // So old parent can be GarbageCollected
                    rval = true;
                }
            }
        }
        
        return rval;
    }
}
