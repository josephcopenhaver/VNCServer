package com.jcope.vnc;

import static com.jcope.debug.Debug.DEBUG;
import static com.jcope.debug.Debug.assert_;
import static com.jcope.util.Time.mustParseISO8601DurationRP;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.GregorianCalendar;
import java.util.Properties;

import com.jcope.util.TypeSafeEnumPropertyPattern;
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
	
	private static final GregorianCalendar startTime = new GregorianCalendar();
	
    public static enum CLIENT_PROPERTIES implements TypeSafeEnumPropertyPattern
    {
        REMOTE_ADDRESS(""),
        REMOTE_PORT(1987),
        REMOTE_DISPLAY_NUM(null),
        SYNCHRONIZE_CLIPBOARD(Boolean.FALSE),
        MONITOR_SCANNING_PERIOD(Long.valueOf(mustParseISO8601DurationRP("T1S", startTime)))
        
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
                case SYNCHRONIZE_CLIPBOARD:
                    assert_(obj instanceof Boolean);
                    break;
                case MONITOR_SCANNING_PERIOD:
                	assert_(obj instanceof Long);
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
                case SYNCHRONIZE_CLIPBOARD:
                    if (value instanceof String)
                    {
                        value = Integer.parseInt((String) value);
                    }
                    if (value instanceof Integer)
                    {
                        value = Boolean.valueOf(0 != ((Integer)value));
                    }
                    break;
                case MONITOR_SCANNING_PERIOD:
                	value = Long.valueOf(mustParseISO8601DurationRP((String) value, startTime));
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
		
		CLIENT_PROPERTIES.loadConfig(args.length > 0 ? args[0] : null);
		
		try
		{
			StateMachine myClient = new StateMachine();
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
