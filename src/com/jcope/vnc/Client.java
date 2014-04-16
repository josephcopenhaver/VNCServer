package com.jcope.vnc;

import static com.jcope.debug.Debug.DEBUG;
import static com.jcope.debug.Debug.assert_;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Properties;

import com.jcope.util.EnumPropertyInterface;
import com.jcope.vnc.client.StateMachine;

/**
 * 
 * @author Joseph Copenhaver
 *
 * 
 * This class shall be the main execution point of a client instance.
 * 
 */

public class Client
{
    public static enum CLIENT_PROPERTIES implements EnumPropertyInterface
    {
        REMOTE_ADDRESS(""),
        REMOTE_PORT(1987),
        REMOTE_DISPLAY_NUM(null)
        
        ;
        
        Object value;
        
        CLIENT_PROPERTIES(final Object defaultValue)
        {
            this.value = defaultValue;
        }
        
        public void assertType(Object obj)
        {
            assert_(obj != null);
            switch (this)
            {
                case REMOTE_ADDRESS:
                    assert_(obj instanceof String);
                    break;
                case REMOTE_PORT:
                    assert_(obj instanceof Integer);
                    break;
                case REMOTE_DISPLAY_NUM:
                    assert_(obj == null || obj instanceof Integer);
                    break;
            }
        }
        
        public Object getValue()
        {
            return value;
        }
        
        public void setValue(Object value)
        {
            switch (this)
            {
                case REMOTE_ADDRESS:
                    break;
                case REMOTE_PORT:
                case REMOTE_DISPLAY_NUM:
                    if (value instanceof String)
                    {
                        value = Integer.parseInt((String) value);
                    }
                    break;
            }
            assertType(value);
            this.value = value;
        }
        
        public void load(Properties prop)
        {
            Object value = prop.get(name());
            if (value != null)
            {
                setValue(value);
            }
        }
        
        public static void loadConfig(String properyFile) throws FileNotFoundException, IOException
        {
            if (properyFile == null)
            {
                System.out.println("No property file loaded.");
                return;
            }
            Properties prop = new Properties();
            FileInputStream in = new FileInputStream(properyFile);
            try
            {
                prop.load(in);
                for (CLIENT_PROPERTIES iprop : CLIENT_PROPERTIES.values())
                {
                    iprop.load(prop);
                }
            }
            finally {
                in.close();
            }
        }
    }
    
	public static void main(String[] args) throws UnknownHostException, IOException, InterruptedException
	{
		boolean forceStop = !DEBUG;
		String remoteAddress;
		int remotePort;
		Integer remoteDisplayNum;
		String password;
		
		CLIENT_PROPERTIES.loadConfig(args.length > 0 ? args[0] : null);
		remoteAddress = (String) CLIENT_PROPERTIES.REMOTE_ADDRESS.getValue();
		remotePort = (Integer) CLIENT_PROPERTIES.REMOTE_PORT.getValue();
		remoteDisplayNum = (Integer) CLIENT_PROPERTIES.REMOTE_DISPLAY_NUM.getValue();
		password = null;
		
		try
		{
			StateMachine myClient = new StateMachine(remoteAddress, remotePort, remoteDisplayNum, password);
			System.out.println("Client is running!");
			myClient.run();
			forceStop = Boolean.TRUE;
		}
		finally {
			System.out.println("Client closed");
			if (forceStop)
			{
				System.exit(0);
			}
		}
	}
}
