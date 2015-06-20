package com.jcope.ui.util;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JFrame;

public class Style {
    public static void positionThenShow(JFrame frame) {
        positionThenShow(frame, Boolean.TRUE);
    }

    public static void positionThenShow(JFrame frame, boolean packBeforeShow) {
        Rectangle gcBounds = null;
        Insets insets = null;
        Point point;

        frame.setVisible(Boolean.TRUE);
        point = frame.getLocationOnScreen();
        frame.setVisible(Boolean.FALSE);

        FOUND_BOUNDS: for (GraphicsDevice gDevice : GraphicsEnvironment
                .getLocalGraphicsEnvironment().getScreenDevices()) {
            for (GraphicsConfiguration gConfig : gDevice.getConfigurations()) {
                Rectangle bounds = gConfig.getBounds();

                if (bounds.contains(point)) {
                    gcBounds = bounds;
                    insets = Toolkit.getDefaultToolkit().getScreenInsets(
                            gConfig);
                    break FOUND_BOUNDS;
                }
            }
        }

        Dimension preferredSize = frame.getPreferredSize();

        if (gcBounds != null) {
            // center the frame in the graphics device
            int w = gcBounds.width - insets.left - insets.right, h = gcBounds.height
                    - insets.top - insets.bottom;
            preferredSize.setSize((preferredSize.width > w ? w
                    : preferredSize.width), (preferredSize.height > h ? h
                    : preferredSize.height));
        }

        frame.setSize(preferredSize);
        frame.setLocation(((int) gcBounds.getMinX()) + insets.left
                + (gcBounds.width - preferredSize.width) / 2,
                ((int) gcBounds.getMinY()) + insets.top
                        + (gcBounds.height - preferredSize.height) / 2);

        if (packBeforeShow) {
            frame.pack();
        }

        frame.setVisible(Boolean.TRUE);
    }

    public static void showModalDialogWithStyle(JDialog dialog,
            final AbstractButton confirmBtn, final AbstractButton cancelBtn) {
        dialog.setModalityType(JDialog.ModalityType.APPLICATION_MODAL);
        dialog.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent evt) {
                JDialog dialog = (JDialog) evt.getSource();
                try {
                    Container parent = dialog.getParent();
                    dialog.setLocationRelativeTo(parent);
                } finally {
                    dialog.removeComponentListener(this);
                }
            }
        });
        KeyEventDispatcher confirmOrCancel = (confirmBtn != null || cancelBtn != null) ? new KeyEventDispatcher() {
            public boolean dispatchKeyEvent(KeyEvent evt) {
                boolean keyHandled = false;
                do {
                    if ((evt.getID() != KeyEvent.KEY_PRESSED)
                            || evt.isConsumed()) {
                        break;
                    }

                    int keyCode = evt.getKeyCode();

                    if (KeyEvent.VK_ENTER == keyCode) {
                        if (confirmBtn != null) {
                            evt.consume();
                            ActionEvent virtualEvent = new ActionEvent(
                                    confirmBtn, ActionEvent.ACTION_PERFORMED,
                                    confirmBtn.getActionCommand());
                            for (ActionListener listener : confirmBtn
                                    .getActionListeners()) {
                                listener.actionPerformed(virtualEvent);
                            }
                            keyHandled = true;
                        }
                    } else if (KeyEvent.VK_ESCAPE == keyCode) {
                        if (cancelBtn != null) {
                            evt.consume();
                            ActionEvent virtualEvent = new ActionEvent(
                                    cancelBtn, ActionEvent.ACTION_PERFORMED,
                                    cancelBtn.getActionCommand());
                            for (ActionListener listener : cancelBtn
                                    .getActionListeners()) {
                                listener.actionPerformed(virtualEvent);
                            }
                            keyHandled = true;
                        }
                    }
                } while (false);
                return keyHandled;
            }
        } : null;

        KeyboardFocusManager keyboardFocusManager = (confirmOrCancel == null) ? null
                : KeyboardFocusManager.getCurrentKeyboardFocusManager();

        try {
            if (keyboardFocusManager != null) {
                keyboardFocusManager.addKeyEventDispatcher(confirmOrCancel);
            }
            dialog.setVisible(Boolean.TRUE);
        } finally {
            if (keyboardFocusManager != null) {
                keyboardFocusManager.removeKeyEventDispatcher(confirmOrCancel);
            }
        }
    }
}
