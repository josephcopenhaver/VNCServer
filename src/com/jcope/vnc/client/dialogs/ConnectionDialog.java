package com.jcope.vnc.client.dialogs;

import static com.jcope.debug.Debug.assert_;
import static com.jcope.ui.util.Style.showModalDialogWithStyle;
import static com.jcope.util.Time.parseISO8601Duration;
import static com.jcope.util.Time.toISO8601DurationStr;

import java.awt.Component;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;

import com.jcope.ui.JCOptionPane;
import com.jcope.util.Time.InvalidFormatException;
import com.jcope.vnc.Client;
import com.jcope.vnc.Client.CLIENT_PROPERTIES;
import com.jcope.vnc.client.MainFrame;
import com.jcope.vnc.shared.AccessModes.ACCESS_MODE;
import com.jcope.vnc.shared.HashFactory;

public class ConnectionDialog {

    public static final int MAX_DISPLAY_IDX = 0xff;
    public static final int GAP_SIZE_PIXELS = 10;
    public static final int BORDER_SIZE_PIXELS = 10;

    public static class InvalidConnectionConfigurationException extends
            Exception {
        private static final long serialVersionUID = 3463351942240328327L;
        private String msg = null;

        private void setMessage(String msg) {
            this.msg = msg;
        }

        @Override
        public String getMessage() {
            return msg;
        }
    }

    private static InvalidConnectionConfigurationException configurationException = new InvalidConnectionConfigurationException();

    private CustomDialog dialog;

    private class CustomDialog extends JDialog {
        /**
         * Generated serialVersionUID
         */
        private static final long serialVersionUID = 1704920951704662091L;

        private JTextField serverName = new JTextField();
        private JTextField serverPort = new JTextField();
        private JList<ACCESS_MODE> single_accessModeSelectionList = new JList<ACCESS_MODE>(
                ACCESS_MODE.selectable());
        private JTextField displayNum = new JTextField();
        private JPasswordField password = new JPasswordField();
        private JTextField monitorScanningPeriod = new JTextField();
        private int result;
        private JButton okButton = new JButton("OK");
        private JButton cancelButton = new JButton("Cancel");
        private JCheckBox askToSynchronizeClipboard = new JCheckBox();

        private String passwordHash = null;

        ActionListener ok_or_cancel = new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent evt) {
                Object src = evt.getSource();
                if (src == okButton) {
                    result = JCOptionPane.OK_OPTION;
                } else if (src == cancelButton) {
                    result = JCOptionPane.CANCEL_OPTION;
                } else {
                    assert_(false);
                }
                setVisible(Boolean.FALSE);
            }

        };

        private void addLabeledComponent(String labelText, Component rhs) {
            add(new JLabel(labelText + ":"));
            add(rhs);
        }

        private CustomDialog(JFrame frame) {
            super(frame);

            Container contentPane = getContentPane();
            ((JPanel) contentPane).setBorder(new EmptyBorder(
                    BORDER_SIZE_PIXELS, BORDER_SIZE_PIXELS, BORDER_SIZE_PIXELS,
                    BORDER_SIZE_PIXELS));
            contentPane.setLayout(new GridLayout(0, 2, GAP_SIZE_PIXELS,
                    GAP_SIZE_PIXELS));

            Object tmp = CLIENT_PROPERTIES.REMOTE_ADDRESS.getValue();
            serverName.setText(tmp == null ? "" : ((String) tmp));

            tmp = CLIENT_PROPERTIES.REMOTE_PORT.getValue();
            serverPort.setText(tmp == null ? "" : ((Integer) tmp).toString());

            single_accessModeSelectionList.setLayoutOrientation(JList.VERTICAL);
            single_accessModeSelectionList.setVisibleRowCount(1);
            single_accessModeSelectionList
                    .setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            JScrollPane jsp_single_accessModeSelectionList = new JScrollPane(
                    single_accessModeSelectionList);
            jsp_single_accessModeSelectionList
                    .setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            jsp_single_accessModeSelectionList
                    .setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            single_accessModeSelectionList.setSelectedValue(
                    CLIENT_PROPERTIES.DEFAULT_ACCESS_MODE.getValue(),
                    Boolean.TRUE);

            tmp = CLIENT_PROPERTIES.REMOTE_DISPLAY_NUM.getValue();
            displayNum.setText(tmp == null ? "0" : ((Integer) tmp).toString());

            tmp = CLIENT_PROPERTIES.SYNCHRONIZE_CLIPBOARD.getValue();
            askToSynchronizeClipboard.setSelected(tmp == null ? Boolean.FALSE
                    : ((Boolean) tmp));

            password.setText("");

            tmp = toISO8601DurationStr((Long) CLIENT_PROPERTIES.MONITOR_SCANNING_PERIOD
                    .getValue());
            monitorScanningPeriod.setText((String) tmp);

            okButton.addActionListener(ok_or_cancel);
            cancelButton.addActionListener(ok_or_cancel);

            addLabeledComponent("Password", password);
            addLabeledComponent("Select Screen", displayNum);
            addLabeledComponent("Server Name", serverName);
            addLabeledComponent("Server Port", serverPort);
            addLabeledComponent("Access Mode",
                    jsp_single_accessModeSelectionList);
            addLabeledComponent("Refresh Period", monitorScanningPeriod);
            addLabeledComponent("SYNC Clipboard", askToSynchronizeClipboard);

            add(okButton);
            add(cancelButton);

            tmp = null;
            contentPane = null;
        }

        private void stagePasswordHash() {
            char[] rawText = password.getPassword();
            if (result != JCOptionPane.OK_OPTION) {
                if (rawText != null && rawText.length > 0) {
                    Arrays.fill(rawText, (char) 0);
                    password.setText("");
                }
                return;
            }

            password.setText("");
            passwordHash = (rawText != null && rawText.length > 0) ? HashFactory
                    .hash(rawText) : null;
            rawText = null;
        }

        private String removePasswordHash() {
            String rval = passwordHash;

            passwordHash = null;

            return rval;
        }

        @Override
        public void dispose() {
            ActionListener ok_or_cancel = this.ok_or_cancel;
            this.ok_or_cancel = null;
            if (ok_or_cancel == null) {
                return;
            }
            okButton.removeActionListener(ok_or_cancel);
            cancelButton.removeActionListener(ok_or_cancel);

            // unbind everything

            getContentPane().removeAll();

            // Null everything out!

            serverName = null;
            serverPort = null;
            single_accessModeSelectionList = null;
            displayNum = null;
            password = null;
            okButton = null;
            cancelButton = null;
            askToSynchronizeClipboard = null;
            passwordHash = null;

            super.dispose();
        }
    }

    public ConnectionDialog(MainFrame mainFrame) {
        dialog = new CustomDialog(mainFrame);
    }

    public int showInputDialog() throws InvalidConnectionConfigurationException {
        boolean somethingBadHappened = true;
        dialog.result = JCOptionPane.CLOSED_OPTION;

        dialog.pack();
        dialog.setSize(dialog.getPreferredSize());
        try {
            showModalDialogWithStyle(dialog, dialog.okButton,
                    dialog.cancelButton);
            somethingBadHappened = false;
        } finally {
            if (somethingBadHappened) {
                dialog.result = JCOptionPane.CLOSED_OPTION;
            }
            dialog.stagePasswordHash();
        }

        if (dialog.result == JCOptionPane.OK_OPTION) {
            String remoteAddress;
            int remotePort, remoteDisplayNum;
            long monitorScanningPeriod_ms;
            Boolean synchronizeClipboard;

            String tmp = dialog.serverName.getText();
            if (tmp == null || tmp.length() == 0) {
                tmp = "localhost";
            }
            remoteAddress = tmp;

            tmp = dialog.serverPort.getText();
            if (!tmp.matches("^[1-9]\\d*$")) {
                configurationException
                        .setMessage("Invalid port number: not a number in {R+ > 0}");
                tmp = null;
                throw configurationException;
            }

            try {
                Integer portNum = Integer.parseInt(tmp);
                if ((portNum == null) || portNum > 0xffff || portNum <= 0) {
                    configurationException
                            .setMessage("Invalid port number: value is larger than 65535 (0xffff)");
                    tmp = null;
                    throw configurationException;
                }
                remotePort = portNum;
            } catch (NumberFormatException e) {
                configurationException
                        .setMessage("Invalid port number: digit string not representable as 32bit signed integer");
                tmp = null;
                throw configurationException;
            }

            tmp = dialog.displayNum.getText();
            if (!tmp.matches("^\\d+$")) {
                configurationException
                        .setMessage("Invalid port number: not a number in {R+ >= 0}");
                tmp = null;
                throw configurationException;
            }

            try {
                Integer displayNum = Integer.parseInt(tmp);
                if (displayNum > MAX_DISPLAY_IDX || displayNum < 0) {
                    configurationException
                            .setMessage(String
                                    .format("Invalid display number: value is larger than MAX_DISPLAY_IDX (%d)",
                                            MAX_DISPLAY_IDX));
                    tmp = null;
                    throw configurationException;
                }
                remoteDisplayNum = displayNum;
            } catch (NumberFormatException e) {
                configurationException
                        .setMessage("Invalid display number: digit string not representable as 32bit signed integer");
                tmp = null;
                throw configurationException;
            }

            tmp = dialog.monitorScanningPeriod.getText();
            try {
                monitorScanningPeriod_ms = parseISO8601Duration(tmp,
                        Client.startTime);
            } catch (InvalidFormatException e) {
                configurationException
                        .setMessage("Not a valid refresh Period format (want ISO8601 without 'P' header)");
                tmp = null;
                throw configurationException;
            }
            if (monitorScanningPeriod_ms <= 0) {
                configurationException.setMessage(String.format(
                        "Not a valid refresh Period (%d is <= 0)",
                        monitorScanningPeriod_ms));
                tmp = null;
                throw configurationException;
            }

            synchronizeClipboard = dialog.askToSynchronizeClipboard
                    .isSelected();

            tmp = null;
            configurationException.setMessage(null);

            CLIENT_PROPERTIES.REMOTE_ADDRESS.setValue(remoteAddress);
            CLIENT_PROPERTIES.REMOTE_PORT.setValue(remotePort);
            CLIENT_PROPERTIES.REMOTE_DISPLAY_NUM.setValue(remoteDisplayNum);
            CLIENT_PROPERTIES.MONITOR_SCANNING_PERIOD.setValue(Long
                    .valueOf(monitorScanningPeriod_ms));
            CLIENT_PROPERTIES.SYNCHRONIZE_CLIPBOARD
                    .setValue(synchronizeClipboard);
            CLIENT_PROPERTIES.DEFAULT_ACCESS_MODE.setValue(getAccessMode());
        }

        return dialog.result;
    }

    public ACCESS_MODE getAccessMode() {
        return dialog.single_accessModeSelectionList.getSelectedValue();
    }

    public String removePasswordHash() {
        return dialog.removePasswordHash();
    }

    public void dispose() {
        CustomDialog tmp = dialog;
        dialog = null;
        if (tmp != null) {
            tmp.dispose();
        }
    }
}
