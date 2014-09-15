package com.jcope.vnc.client.dialogs;

import static com.jcope.debug.Debug.assert_;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

import com.jcope.ui.JCOptionPane;
import com.jcope.vnc.Client.CLIENT_PROPERTIES;
import com.jcope.vnc.client.MainFrame;
import com.jcope.vnc.shared.AccessModes.ACCESS_MODE;
import com.jcope.vnc.shared.HashFactory;

public class ConnectionDialog
{
    
    public static final int MAX_DISPLAY_IDX = 0xff;
    
    public static class InvalidConnectionConfigurationException extends Exception
    {
        private static final long serialVersionUID = 3463351942240328327L;
        private String msg = null;
        private void setMessage(String msg)
        {
            this.msg = msg;
        }
        
        @Override
        public String getMessage()
        {
            return msg;
        }
    }
    
    private static InvalidConnectionConfigurationException configurationException = new InvalidConnectionConfigurationException();
    
    private CustomDialog dialog;
    
    private class CustomDialog extends JDialog
    {
        /**
         * Generated serialVersionUID
         */
        private static final long serialVersionUID = 1704920951704662091L;
        
        private JTextField serverName = new JTextField();
        private JTextField serverPort = new JTextField();
        private JList<ACCESS_MODE> singleAccessModeSelectionList = new JList<ACCESS_MODE>(ACCESS_MODE.selectable());
        private JTextField displayNum = new JTextField();
        private JPasswordField password = new JPasswordField();
        private int result;
        private JButton okayButton = new JButton("OK");
        private JButton cancelButton = new JButton("Cancel");
        
        private String passwordHash = null;
        
        ActionListener okayCancel = new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent evt)
            {
                Object src = evt.getSource();
                if (src == okayButton)
                {
                    result = JCOptionPane.OK_OPTION;
                }
                else if (src == cancelButton)
                {
                    result = JCOptionPane.CANCEL_OPTION;
                }
                else
                {
                    assert_(false);
                }
                setVisible(Boolean.FALSE);
            }
            
        };
        
        private JPanel getLabeledComponent(String labelText, Component rhs)
        {
            JPanel rval = new JPanel();
            
            rval.setLayout(new BoxLayout(rval, BoxLayout.X_AXIS));
            
            rval.add(new JLabel(labelText + ":"));
            rval.add(rhs);
            
            return rval;
        }
        
        private CustomDialog(JFrame frame)
        {
            Object tmp = CLIENT_PROPERTIES.REMOTE_ADDRESS.getValue();
            serverName.setText(tmp == null ? "" : ((String)tmp));
            
            tmp = CLIENT_PROPERTIES.REMOTE_PORT.getValue();
            serverPort.setText(tmp == null ? "" : ((Integer)tmp).toString());
            
            singleAccessModeSelectionList.setVisibleRowCount(1);
            singleAccessModeSelectionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            singleAccessModeSelectionList.setSelectedValue(ACCESS_MODE.VIEW_ONLY, Boolean.TRUE);
            
            tmp = CLIENT_PROPERTIES.REMOTE_DISPLAY_NUM.getValue();
            displayNum.setText(tmp == null ? "0" : ((Integer)tmp).toString());
            
            password.setText("");
            
            okayButton.addActionListener(okayCancel);
            cancelButton.addActionListener(okayCancel);
            
            JPanel submitPanel = new JPanel();
            submitPanel.setLayout(new BoxLayout(submitPanel, BoxLayout.X_AXIS));
            
            setModalityType(JDialog.ModalityType.APPLICATION_MODAL);
            Container contentPane = getContentPane();
            
            submitPanel.add(okayButton);
            submitPanel.add(cancelButton);
            
            contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS));
            
            contentPane.add(getLabeledComponent("Server Name", serverName));
            contentPane.add(getLabeledComponent("Server Port", serverPort));
            contentPane.add(getLabeledComponent("Access Mode", singleAccessModeSelectionList));
            contentPane.add(getLabeledComponent("Selected Screen", displayNum));
            contentPane.add(getLabeledComponent("Password ", password));
            contentPane.add(submitPanel);
            
            
            tmp = null;
        }
        
        private void stagePasswordHash()
        {
            if (dialog.result != JCOptionPane.OK_OPTION)
            {
                password.setText("");
                return;
            }
            char[] rawText = password.getPassword();
            
            password.setText("");
            passwordHash = HashFactory.hash(rawText);
            rawText = null;
        }
        
        private String getPasswordHash()
        {
            String rval = passwordHash;
            
            passwordHash = null;
            
            return rval;
        }
        
        @Override
        public void dispose()
        {
            ActionListener okayCancel = this.okayCancel;
            this.okayCancel = null;
            if (okayCancel == null)
            {
                return;
            }
            okayButton.removeActionListener(okayCancel);
            cancelButton.removeActionListener(okayCancel);
        }
    }

    public ConnectionDialog(MainFrame mainFrame)
    {
        dialog = new CustomDialog(mainFrame);
    }
    
    public int showInputDialog() throws InvalidConnectionConfigurationException
    {
        dialog.result = JCOptionPane.CLOSED_OPTION;
        
        dialog.setVisible(Boolean.TRUE);
        dialog.stagePasswordHash();
        
        if (dialog.result == JCOptionPane.OK_OPTION)
        {
            String remoteAddress;
            int remotePort,
                remoteDisplayNum;
            
            
            String tmp = dialog.serverName.getText();
            if (tmp == null || tmp.length() == 0)
            {
                tmp = "localhost";
            }
            remoteAddress = tmp;
            
            tmp = dialog.serverPort.getText();
            if (!tmp.matches("^[1-9]\\d*$"))
            {
                configurationException.setMessage("Invalid port number: not a number in {R+ > 0}");
                tmp = null;
                throw configurationException;
            }
            
            try {
                Integer portNum = Integer.parseInt(tmp);
                if ((portNum == null) || portNum > 0xffff || portNum <= 0)
                {
                    configurationException.setMessage("Invalid port number: value is larger than 65535 (0xffff)");
                    tmp = null;
                    throw configurationException;
                }
                remotePort = portNum;
            }
            catch (NumberFormatException e)
            {
                configurationException.setMessage("Invalid port number: digit string not representable as 32bit signed integer");
                tmp = null;
                throw configurationException;
            }
            
            tmp = dialog.displayNum.getText();
            if (!tmp.matches("^\\d+$"))
            {
                configurationException.setMessage("Invalid port number: not a number in {R+ >= 0}");
                tmp = null;
                throw configurationException;
            }
            
            try {
                Integer displayNum = Integer.parseInt(tmp);
                if (displayNum > MAX_DISPLAY_IDX || displayNum < 0)
                {
                    configurationException.setMessage(String.format("Invalid display number: value is larger than MAX_DISPLAY_IDX (%d)", MAX_DISPLAY_IDX));
                    tmp = null;
                    throw configurationException;
                }
                remoteDisplayNum = displayNum;
            }
            catch (NumberFormatException e)
            {
                configurationException.setMessage("Invalid display number: digit string not representable as 32bit signed integer");
                tmp = null;
                throw configurationException;
            }
            
            tmp = null;
            configurationException.setMessage(null);
            
            CLIENT_PROPERTIES.REMOTE_ADDRESS.setValue(remoteAddress);
            CLIENT_PROPERTIES.REMOTE_PORT.setValue(remotePort);
            CLIENT_PROPERTIES.REMOTE_DISPLAY_NUM.setValue(remoteDisplayNum);
        }
        
        return dialog.result;
    }

    public ACCESS_MODE getAccessMode()
    {
        return dialog.singleAccessModeSelectionList.getSelectedValue();
    }

    public String removePassword()
    {
        return dialog.getPasswordHash();
    }
    
    public void dispose()
    {
        if (dialog != null)
        {
            dialog.dispose();
        }
        dialog = null;
    }
}