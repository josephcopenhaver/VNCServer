package com.jcope.vnc.shared;

public class InputEventInfo
{
    public static final int MAX_QUEUE_SIZE = 50;
    public static final int[] ORIGIN = new int[]{0,0};
    
    public static enum INPUT_TYPE
    {
        KEY_DOWN,      // prevent subsequent same events
        KEY_UP,        // prevent subsequent same events
        KEY_PRESSED,   // replace previous key_down, i.f.f.
        MOUSE_DOWN,    // prevent subsequent same events
        MOUSE_UP,      // prevent subsequent same events
        MOUSE_PRESSED, // replace previous mouse_down, i.f.f.
        MOUSE_MOVE,    // collapse by replacing old with newest
        WHEEL_SCROLL   // collapse on same directional summation of magnitude
    };
}
