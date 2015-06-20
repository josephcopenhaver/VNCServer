package com.jcope.vnc.shared;

import static com.jcope.debug.Debug.assert_;

import java.awt.DisplayMode;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Rectangle;

public class ScreenInfo {
    public static Rectangle getScreenBounds(GraphicsDevice device) {
        return _getScreenBounds(device, null, false);
    }

    public static Rectangle getVirtualScreenBounds(GraphicsDevice device) {
        return _getScreenBounds(device, null, true);
    }

    public static Rectangle getScreenBoundsVerbose(GraphicsDevice device,
            int[] initialScale) {
        return _getScreenBounds(device, initialScale, false);
    }

    private static Rectangle _getScreenBounds(GraphicsDevice device,
            int[] initialScale, boolean getVirtual) {
        GraphicsConfiguration defaultConf = device.getDefaultConfiguration();
        Rectangle rval = defaultConf.getBounds();
        int dispModeW, dispModeH;

        if (getVirtual) {
            dispModeW = 0;
            dispModeH = 0;
        } else {
            DisplayMode dispMode = device.getDisplayMode();
            dispModeW = dispMode.getWidth();
            dispModeH = dispMode.getHeight();
        }

        // x and y describe relative origin
        // width and height are true width and height of the graphics device

        for (GraphicsConfiguration gc : device.getConfigurations()) {
            if (gc.equals(defaultConf)) {
                continue;
            }

            Rectangle.union(gc.getBounds(), rval, rval);
        }

        // workaround for bug where for some reason
        // the bounds do not match the displaymode bounds

        if (getVirtual) {
            assert_(initialScale == null);
        } else {
            if (initialScale != null) {
                initialScale[0] = rval.width;
                initialScale[1] = rval.height;
            }

            assert_(dispModeW >= rval.width);
            assert_(dispModeH >= rval.height);

            rval.width = dispModeW;
            rval.height = dispModeH;
        }

        return rval;
    }
}
