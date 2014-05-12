package com.jcope.ui;

import java.awt.Component;

import javax.swing.Icon;
import javax.swing.JOptionPane;

// TODO: use this class to abstract around the issue where the dialog is not centered on the screen or the frame (when minimized)

public class JCOptionPane extends JOptionPane
{

    /**
     * Generated
     */
    
    private static final long serialVersionUID = 5141953148465311896L;
    
    
    
    // showConfirmDialog
    
    
    
    public static int showConfirmDialog(Component parentComponent, Object message, String title, int optionType, int messageType, Icon icon)
    {
        return JOptionPane.showConfirmDialog(parentComponent, message, title, optionType, messageType, icon);
    }
    
    public static int showConfirmDialog(Component parentComponent, Object message, String title, int optionType, int messageType)
    {
        return JOptionPane.showConfirmDialog(parentComponent, message, title, optionType, messageType);
    }
    
    public static int showConfirmDialog(Component parentComponent, Object message, String title, int optionType)
    {
        return JOptionPane.showConfirmDialog(parentComponent, message, title, optionType);
    }
    
    public static int showConfirmDialog(Component parentComponent, Object message)
    {
        return JOptionPane.showConfirmDialog(parentComponent, message);
    }
    
    
    
    
    // showInputDialog
    
    
    
    public static Object showInputDialog(Component parentComponent, Object message, String title, int messageType, Icon icon, Object[] selectionValues, Object initialSelectionValue)
    {
        return JOptionPane.showInputDialog(parentComponent, message, title, messageType, icon, selectionValues, initialSelectionValue);
    }
    
    public static String showInputDialog(Component parentComponent, Object message, String title, int messageType)
    {
        return JOptionPane.showInputDialog(parentComponent, message, title, messageType);
    }
    
    public static String showInputDialog(Component parentComponent, Object message, Object initialSelectionValue)
    {
        return JOptionPane.showInputDialog(parentComponent, message, initialSelectionValue);
    }
    
    public static String showInputDialog(Component parentComponent, Object initialSelectionValue)
    {
        return JOptionPane.showInputDialog(parentComponent, initialSelectionValue);
    }
    
    public static String showInputDialog(Component parentComponent)
    {
        return JOptionPane.showInputDialog(parentComponent);
    }
    
    
    
    
    // showOptionDialog
    
    
    
    public static int showOptionDialog(Component parentComponent, Object message, String title, int optionType, int messageType, Icon icon, Object[] options, Object initialValue)
    {
        return JOptionPane.showOptionDialog(parentComponent, message, title, optionType, messageType, icon, options, initialValue);
    }
    
    
    
    
    // showMessageDialog
    
    
    
    public static void showMessageDialog(Component parentComponent, Object message, String title, int messageType, Icon icon)
    {
        JOptionPane.showMessageDialog(parentComponent, message, title, messageType, icon);
    }
    
    public static void showMessageDialog(Component parentComponent, Object message, String title, int messageType)
    {
        JOptionPane.showMessageDialog(parentComponent, message, title, messageType);
    }
    
    public static void showMessageDialog(Component parentComponent, Object message)
    {
        JOptionPane.showMessageDialog(parentComponent, message);
    }
    
}
