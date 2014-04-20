package com.jcope.vnc.client;

import javax.swing.JFrame;

public interface FrameDecorator
{
    public void decorate(JFrame parent);
    public void undecorate(JFrame parent);
}
