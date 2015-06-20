package com.jcope.vnc;

import static com.jcope.debug.Debug.assert_;
import static com.jcope.ui.util.Style.positionThenShow;
import static com.jcope.vnc.shared.ScreenSelector.getScreenDevices;
import static com.jcope.vnc.shared.Tokens.ALL_TOKEN;

import java.awt.GraphicsDevice;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

import javax.swing.JFrame;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.xml.sax.SAXException;

import com.jcope.ui.JCOptionPane;
import com.jcope.ui.PasswordInputDialog;
import com.jcope.vnc.Server.SERVER_PROPERTIES;
import com.jcope.vnc.server.SecurityPolicy;
import com.jcope.vnc.shared.AccessModes.ACCESS_MODE;
import com.jcope.vnc.shared.HashFactory;
import com.jcope.vnc.shared.ScreenSelector;

/**
 * 
 * @author Joseph Copenhaver
 *
 *         This class shall be the main execution point of a server setup
 *         instance.
 * 
 */

public class ServerSetup {

    public static void main(String[] args) throws ParserConfigurationException,
            SAXException, IOException, TransformerException {
        Server.SERVER_PROPERTIES.loadConfig(args.length > 0 ? args[0] : null);
        String securityPolicyPath = (String) SERVER_PROPERTIES.SERVER_SECURITY_POLICY
                .getValue();

        // using a JFrame so that window managers on all platforms show the
        // application as running
        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        positionThenShow(frame);

        SecurityPolicy securityPolicy = new SecurityPolicy();
        File file = new File(securityPolicyPath);

        if (file.isDirectory()) {
            throw new RuntimeException(String.format(
                    "\"%s\" is a directory and not a file",
                    file.getAbsolutePath()));
        }

        if (file.exists()) {
            securityPolicy.readPolicy(file);
        }

        boolean changed = false;
        GraphicsDevice[] devices = getScreenDevices();
        HashMap<String, GraphicsDevice> deviceMap = new HashMap<String, GraphicsDevice>(
                devices.length);

        for (GraphicsDevice device : devices) {
            deviceMap.put(device.getIDstring(), device);
        }

        int resultInt = JCOptionPane.showConfirmDialog(frame,
                "Reset the entire policy to default \"fully locked\" mode?");

        if (JCOptionPane.OK_OPTION == resultInt) {
            securityPolicy.clear();
            changed = true;
        }
        boolean firstRun = true;

        while (true) {
            String deviceID;

            if (firstRun) {
                firstRun = false;
            } else if (JCOptionPane.OK_OPTION == JCOptionPane
                    .showConfirmDialog(frame, "Stop modifying policy settings?")) {
                break;
            }

            if (JCOptionPane.OK_OPTION == JCOptionPane.showConfirmDialog(frame,
                    "Set a policy for all devices?")) {
                deviceID = ALL_TOKEN;
            } else {
                GraphicsDevice device = ScreenSelector.selectScreen(frame,
                        "Set a policy for the default device?", -1);
                if (null == device) {
                    break;
                }
                deviceID = device.getIDstring();
            }

            String actionDisableDevice = "Disable Device";
            String actionAddOpenning = "Add Openning";
            String actionRemoveOpenning = "Remove Openning";

            String[] availableActions = null;
            if (securityPolicy.getNumModes(deviceID) > 0) {
                availableActions = new String[] { actionDisableDevice,
                        actionAddOpenning, actionRemoveOpenning };
            }

            String action;
            if (null == availableActions) {
                action = actionAddOpenning;
            } else {
                action = (String) JCOptionPane.showInputDialog(frame,
                        String.format("Device: %s\nSelect action", deviceID),
                        "Perform Action", JCOptionPane.PLAIN_MESSAGE, null,
                        availableActions, null);
            }

            if (null != action) {
                if (action.equals(actionDisableDevice)) {
                    securityPolicy.blacklist(deviceID);
                    changed = true;
                } else if (action.equals(actionAddOpenning)) {
                    if (addOrEditOpenning(frame, securityPolicy, deviceID)) {
                        changed = true;
                    }
                } else if (action.equals(actionRemoveOpenning)) {
                    if (removeOpening(frame, securityPolicy, deviceID)) {
                        changed = true;
                    }
                } else {
                    assert_(false);
                }
            }
        }

        String filePath = file.getAbsolutePath();

        if (changed) {
            if (JCOptionPane.OK_OPTION == JCOptionPane.showConfirmDialog(frame,
                    String.format("%s\nSave changes?", filePath))) {
                securityPolicy.writePolicy(file);
            }
        } else {
            JCOptionPane.showMessageDialog(frame, String.format(
                    "%s\nNothing saved, no changes have been made.", filePath),
                    "Warning", JCOptionPane.WARNING_MESSAGE);
        }

        System.exit(0);
    }

    private static boolean removeOpening(JFrame parent,
            SecurityPolicy securityPolicy, String deviceID) {
        int numModes = 0;
        boolean rval = false;

        do {
            do {
                ACCESS_MODE[] enabledModes = securityPolicy.getModes(deviceID);
                numModes = enabledModes.length;
                ACCESS_MODE accessMode = (ACCESS_MODE) JCOptionPane
                        .showInputDialog(
                                parent,
                                String.format(
                                        "Device: \"%s\"\nSelect an access openning to remove",
                                        deviceID), "Remove Access Openning",
                                JCOptionPane.PLAIN_MESSAGE, null, enabledModes,
                                null);

                if (null == accessMode) {
                    break;
                }

                securityPolicy.blacklist(deviceID, accessMode);
                numModes--;
                rval = true;

            } while (false);
        } while (numModes > 0
                && JCOptionPane.OK_OPTION == JCOptionPane
                        .showConfirmDialog(
                                parent,
                                String.format(
                                        "Device: \"%s\"\nRemove another access openning?",
                                        deviceID)));

        return rval;
    }

    private static boolean addOrEditOpenning(JFrame parent,
            SecurityPolicy securityPolicy, String deviceID) {
        boolean rval = false;

        ACCESS_MODE[] availableModes = ACCESS_MODE.sorted();

        do {
            do {
                ACCESS_MODE accessMode = (ACCESS_MODE) JCOptionPane
                        .showInputDialog(
                                parent,
                                String.format(
                                        "Device: \"%s\"\nSelect an access openning to add/edit",
                                        deviceID), "Add/Edit Access Point",
                                JCOptionPane.PLAIN_MESSAGE, null,
                                availableModes, null);

                if (null == accessMode) {
                    break;
                }

                int resultInt = JCOptionPane
                        .showConfirmDialog(
                                parent,
                                String.format(
                                        "Device: \"%s\"\nMode: \"%s\"\nSecure with a password?",
                                        deviceID, accessMode.name()));

                if (JCOptionPane.NO_OPTION == resultInt) {
                    securityPolicy.whitelist(deviceID, accessMode);
                    rval = true;
                    break;
                } else if (JCOptionPane.CANCEL_OPTION == resultInt) {
                    break;
                }

                String defaultPasswordHash = securityPolicy.getPassHash(
                        deviceID, accessMode);
                if (null == defaultPasswordHash) {
                    defaultPasswordHash = "";
                }
                char[] rawPassword = PasswordInputDialog
                        .show(parent,
                                "Password",
                                String.format(
                                        "Device: \"%s\"\nMode: \"%s\"\nConfigure password:",
                                        deviceID, accessMode.name()), true,
                                null);

                if (null == rawPassword || rawPassword.length == 0) {
                    break;
                }

                String passwordHash = defaultPasswordHash;

                if (!Arrays.equals(defaultPasswordHash.toCharArray(),
                        rawPassword) && !defaultPasswordHash.equals(ALL_TOKEN)) {
                    passwordHash = HashFactory.hash(rawPassword);
                }
                rawPassword = null;

                securityPolicy.whitelist(deviceID, accessMode, passwordHash);
                rval = true;

            } while (false);
        } while (JCOptionPane.OK_OPTION == JCOptionPane.showConfirmDialog(
                parent, String.format(
                        "Device: \"%s\"\nAdd/edit another access openning?",
                        deviceID)));

        return rval;
    }
}
