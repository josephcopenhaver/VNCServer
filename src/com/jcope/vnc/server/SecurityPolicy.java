package com.jcope.vnc.server;

import static com.jcope.debug.Debug.assert_;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import com.jcope.util.XMLTools;
import com.jcope.vnc.shared.AccessModes.ACCESS_MODE;


public class SecurityPolicy
{
    private static final Object[][] xmlStruct = new Object[][]
    {
        {"whitelist", null, new Object[][]{
                {"device", new String[]{"guid"}, new Object[][]{
                        {"opening", new String[]{"mode", "authToken"}, null}
                }}
        }}
    };
    public static final String ALL_TOKEN = "*";
    
    private HashMap<String, HashMap<ACCESS_MODE, String>> selectableDevices = new HashMap<String, HashMap<ACCESS_MODE, String>>(1);
    
    public SecurityPolicy()
    {
        
    }
    
    public void readPolicy(File file) throws ParserConfigurationException, SAXException, IOException
    {
        clear();
        
        Document doc = XMLTools.readDoc(file);
        
        Node rootNode = doc.getFirstChild();
        assert_(rootNode.getNextSibling() == null);
        assert_(rootNode.getAttributes().getLength() == XMLTools.attrLen(xmlStruct[0][1]));
        assert_(rootNode.getNodeName().equals(xmlStruct[0][0]));
        
        Object[][] subStruct1 = (Object[][]) xmlStruct[0][2];
        Object[][] subStruct2 = (Object[][]) subStruct1[0][2];
        
        for (Node deviceNode : XMLTools.children(rootNode))
        {
            assert_(deviceNode.getNodeName().equals(subStruct1[0][0]));
            
            String guid = ((Element)deviceNode).getAttribute(((String[])subStruct1[0][1])[ 0 ]);
            
            for (Node openingNode : XMLTools.children(deviceNode))
            {
                assert_(openingNode.getNodeName().equals(subStruct2[0][0]));
                
                String accessModeStr = ((Element)openingNode).getAttribute(((String[])subStruct2[0][1])[ 0 ]);
                String passwordHash  = ((Element)openingNode).getAttribute(((String[])subStruct2[0][1])[ 1 ]);
                ACCESS_MODE accessMode = ACCESS_MODE.get(accessModeStr);
                
                whitelist(guid, accessMode, passwordHash);
            }
        }
    }
    
    public void writePolicy(File file) throws ParserConfigurationException, TransformerException, IOException
    {
        
        if (file.exists() && !file.delete())
        {
            throw new IOException(String.format("Failed to delete file: %s", file.getAbsolutePath()));
        }
        
        Document doc = XMLTools.newDoc();
        
        Element docRoot = doc.createElement((String) xmlStruct[0][0]);
        doc.appendChild(docRoot);
        
        ArrayList<String> guids = new ArrayList<String>(selectableDevices.keySet());
        Collections.sort(guids);
        
        Object[][] subStruct1 = (Object[][]) xmlStruct[0][2];
        Object[][] subStruct2 = (Object[][]) subStruct1[0][2];
        
        for (String guid : guids)
        {
            HashMap<ACCESS_MODE, String> accessModes = selectableDevices.get(guid);
            
            Element whiteListedDevice = doc.createElement((String) subStruct1[0][0]);
            whiteListedDevice.setAttribute(((String[]) subStruct1[0][1])[0], guid);
            
            for (ACCESS_MODE accessMode : ACCESS_MODE.sorted())
            {
                String authToken = accessModes.get(accessMode);
                
                if (authToken == null)
                {
                    continue;
                }
                
                String mode = accessMode.commonName();
                
                Element whiteListEntryPoint = doc.createElement((String) subStruct2[0][0]);
                whiteListEntryPoint.setAttribute(((String[]) subStruct2[0][1])[0], mode);
                whiteListEntryPoint.setAttribute(((String[]) subStruct2[0][1])[1], authToken);
                
                whiteListedDevice.appendChild(whiteListEntryPoint);
            }
            
            docRoot.appendChild(whiteListedDevice);
        }
        
        XMLTools.writeDoc(doc, file);
    }
    
    public void clear()
    {
        selectableDevices.clear();
    }
    
    public void whitelist(GraphicsDevice device, ACCESS_MODE accessMode)
    {
        whitelist(device.getIDstring(), accessMode, ALL_TOKEN);
    }
    
    public void whitelist(String deviceIDString, ACCESS_MODE accessMode)
    {
        whitelist(deviceIDString, accessMode, ALL_TOKEN);
    }
    
    public void whitelist(GraphicsDevice device, ACCESS_MODE accessMode,
            String passwordHash)
    {
        whitelist(device.getIDstring(), accessMode, passwordHash);
    }
    
    public void whitelist(String deviceIDString, ACCESS_MODE accessMode,
            String passwordHash)
    {
        HashMap<ACCESS_MODE, String> availableModes = selectableDevices.get(deviceIDString);
        
        if (availableModes == null)
        {
            availableModes = new HashMap<ACCESS_MODE, String>(1);
            selectableDevices.put(deviceIDString, availableModes);
        }
        else
        {
            blacklist(deviceIDString, accessMode);
            selectableDevices.put(deviceIDString, availableModes);
        }
        
        availableModes.put(accessMode, passwordHash);
        
        if (!deviceIDString.equals(ALL_TOKEN))
        {
            blacklist(ALL_TOKEN);
        }
    }
    
    public void blacklist(GraphicsDevice device, ACCESS_MODE accessMode)
    {
        blacklist(device.getIDstring(), accessMode);
    }
    
    public void blacklist(String deviceIDString, ACCESS_MODE accessMode)
    {
        if (accessMode != ACCESS_MODE.ALL)
        {
            _blacklist(ALL_TOKEN);
            if (deviceIDString.equals(ALL_TOKEN))
            {
                return;
            }
            _blacklist(deviceIDString, ACCESS_MODE.ALL);
        }
        _blacklist(deviceIDString, accessMode);
    }
    
    private void _blacklist(String deviceIDString, ACCESS_MODE accessMode)
    {
        HashMap<ACCESS_MODE, String> availableModes = selectableDevices.get(deviceIDString);
        if (availableModes != null && availableModes.remove(accessMode) != null
                && availableModes.size() == 0)
        {
            blacklist(deviceIDString);
        }
    }
    
    public void blacklist(GraphicsDevice device)
    {
        blacklist(device.getIDstring());
    }
    
    public void blacklist(String deviceIDString)
    {
        if (!deviceIDString.equals(ALL_TOKEN))
        {
            _blacklist(ALL_TOKEN);
        }
        _blacklist(deviceIDString);
    }
    
    private void _blacklist(String deviceIDString)
    {
        selectableDevices.remove(deviceIDString);
    }
    
    public int getNumModes(GraphicsDevice device)
    {
        return getNumModes(device.getIDstring());
    }
    
    public int getNumModes(String deviceIDString)
    {
        int rval;
        
        HashMap<ACCESS_MODE, String> availableModes = selectableDevices.get(deviceIDString);
        
        if (availableModes == null)
        {
            rval = 0;
        }
        else
        {
            rval = availableModes.size();
        }
        
        return rval;
    }
    
    public ACCESS_MODE[] getModes(GraphicsDevice device)
    {
        return getModes(device.getIDstring());
    }
    
    public ACCESS_MODE[] getModes(String deviceIDString)
    {
        ACCESS_MODE[] rval;
        
        HashMap<ACCESS_MODE, String> availableModes = selectableDevices.get(deviceIDString);
        int i=0;
        
        rval = new ACCESS_MODE[availableModes.size()];
        
        for (ACCESS_MODE accessMode : ACCESS_MODE.sorted())
        {
            if (availableModes.get(accessMode) != null)
            {
                rval[i] = accessMode;
                i++;
            }
        }
        
        return rval;
    }

    public String getPassHash(GraphicsDevice device, ACCESS_MODE accessMode)
    {
        return getPassHash(device.getIDstring(), accessMode);
    }

    public String getPassHash(String deviceIDString, ACCESS_MODE accessMode)
    {
        String rval = null;
        
        HashMap<ACCESS_MODE, String> availableModes = selectableDevices.get(deviceIDString);
        
        if (availableModes != null)
        {
            rval = availableModes.get(accessMode);
        }
        
        return rval;
    }
    
    public GraphicsDevice[] getEnabledDevices(ACCESS_MODE opt_accessMode)
    {
        ArrayList<GraphicsDevice> enabledDevices = new ArrayList<GraphicsDevice>();
        HashMap<ACCESS_MODE, String> availableModes;
        
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices())
        {
            if ((availableModes = selectableDevices.get(device.getIDstring())) != null &&
                    (opt_accessMode == null || availableModes.get(ACCESS_MODE.ALL) != null || availableModes.get(opt_accessMode) != null))
            {
                enabledDevices.add(device);
            }
        }
        
        return (GraphicsDevice[]) enabledDevices.toArray();
    }
    
    public boolean checkAuth(GraphicsDevice device, ACCESS_MODE accessMode, String challengeHash)
    {
        return checkAuth(device.getIDstring(), accessMode, challengeHash);
    }
    
    private boolean checkAuth(String deviceIDString, ACCESS_MODE accessMode, String challengeHash)
    {
        boolean rval = false;
        String passwordHash;
        
        HashMap<ACCESS_MODE, String> availableModes = selectableDevices.get(deviceIDString);
        
        if (availableModes == null)
        {
            availableModes = selectableDevices.get(ALL_TOKEN);
        }
        
        if (availableModes != null)
        {
            if ((passwordHash = availableModes.get(ACCESS_MODE.ALL)) != null &&
                    (passwordHash.equals(ALL_TOKEN) || passwordHash.equals(challengeHash)))
            {
                rval = true;
            }
            else if ((passwordHash = availableModes.get(accessMode)) != null &&
                    (passwordHash.equals(ALL_TOKEN) || passwordHash.equals(challengeHash)))
            {
                rval = true;
            }
        }
        
        return rval;
    }
}
