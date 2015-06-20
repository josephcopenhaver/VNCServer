package com.jcope.vnc.server.screen;

import com.jcope.util.FixedLengthBitSet;

public abstract class ScreenListener {
    abstract public void onScreenChange(FixedLengthBitSet changedSegments);
}
