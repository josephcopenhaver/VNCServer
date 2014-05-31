package com.jcope.vnc.server;

import java.awt.Robot;
import java.awt.event.KeyEvent;

import com.jcope.vnc.server.screen.Manager;
import com.jcope.vnc.shared.InputEvent;
import com.jcope.vnc.shared.InputEventInfo;
import com.jcope.vnc.shared.InputEventInfo.INPUT_TYPE;

public class InputEventPlayer
{
    
    public static void replay(DirectRobot dirbot, InputEvent event)
    {
        INPUT_TYPE type = event.getType();
        int mult, mod, modex;
        int[] data = event.getData();
        
        mult = data[0];
        mod = data[1];
        modex = data[2];
        
        modex = pushMods(dirbot, mod, modex);
        
        switch(type)
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
            final int EOM = mult - 1;
            for (int i=0; i<mult; i++)
            {
                switch(type)
                {
                    case KEY_PRESSED:
                    case KEY_DOWN:
                    {
                        dirbot.keyPress(event.keycode());
                        
                        if (type == INPUT_TYPE.KEY_DOWN)
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
                        
                        if (type == INPUT_TYPE.MOUSE_DOWN)
                        {
                            break;
                        }
                    }
                    case MOUSE_UP:
                    {
                        if (i == 0 && type == INPUT_TYPE.MOUSE_UP)
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
}
