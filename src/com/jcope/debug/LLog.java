package com.jcope.debug;

import static com.jcope.debug.Debug.DEBUG;

import java.lang.ref.WeakReference;

import com.jcope.vnc.shared.StateMachine.CLIENT_EVENT;
import com.jcope.vnc.shared.StateMachine.SERVER_EVENT;

public class LLog
{
    private static class RuntimeException_ extends RuntimeException
    {
        /**
         * Generated serialVersionUID
         */
        private static final long serialVersionUID = 3637551056474769981L;
        
        private Throwable e;

        public RuntimeException_(Throwable e)
        {
            super(e);
            this.e = e;
        }
        
        private Throwable getExceptionRoot()
        {
            return e;
        }
    }
    //private static final Semaphore lastErrSema = new Semaphore(1, true);
    private static volatile WeakReference<Object> lastErr = null;
    
	public static void e(final Throwable e)
	{
		e(e, Boolean.TRUE);
	}
	
	public static void e(final Throwable e, final boolean rethrow)
	{
		e(e, rethrow, Boolean.FALSE);
	}
	
	public static void e(final Throwable e, final boolean rethrow, final boolean hardStop)
	{
	    if (!DEBUG){if(!hardStop && !rethrow){return;}}
	    try
	    {
	        boolean isNotLastError = isNotLastErr(e);
	        if (hardStop || isNotLastError)
	        {
	            e.printStackTrace(System.err);
	        }
	        if (!rethrow)
	        {
	            if (hardStop)
	            {
	                e.printStackTrace(System.out);
	            }
	            else if (isNotLastError)
	            {
	                System.out.println("* HANDLING ERROR...");
	                e.printStackTrace(System.out);
	            }
	            else
	            {
	                System.out.println("* LAST ERROR HANDLED *");
	            }
	        }
    	    if (hardStop)
    		{
    		    System.out.flush();
    		    System.err.flush();
    			System.exit(127);
    		}
    		else if (rethrow)
    		{
    			if (e instanceof RuntimeException)
    			{
    				throw ((RuntimeException)e);
    			}
    			else
    			{
    				throw new RuntimeException_(e);
    			}
    		}
	    }
	    finally {
	        if (hardStop)
            {
                System.exit(127);
            }
	    }
	}
	
	/**
	 * Currently not thread safe, but then again I am only trying to cut down
	 * on debug messages being spat out at runtime
	 * 
	 * @param e
	 * @return
	 */
	private static boolean isNotLastErr(Object e)
	{
	    WeakReference<Object> lastErrRef = lastErr;
	    Object lastError = (lastErrRef == null) ? null : lastErrRef.get();
	    boolean rval;
	    
	    if (lastError instanceof RuntimeException_)
	    {
	        lastError = ((RuntimeException_) lastError).getExceptionRoot();
	    }
	    
	    if (lastError != null && e instanceof RuntimeException_)
        {
            e = ((RuntimeException_) e).getExceptionRoot();
        }
        
        if (rval = (lastError == null || lastError != e))
	    {
            lastErr = new WeakReference<Object>(e);
	    }
	    
	    return rval;
	}
	
	public static void i(final String info_msg)
	{
	    if (!DEBUG){return;}
		System.out.println(info_msg);
	}
	
	public static void w(final String warn_msg)
    {
        if (!DEBUG){return;}
        System.err.println(warn_msg);
    }
    
	public static void w(final Exception e)
    {
        if (!DEBUG){return;}
        System.err.println(e.getMessage());
        e.printStackTrace(System.err);
    }
    
    public static void logEvent(final String source, final SERVER_EVENT event, final Object[] args)
	{
	    if (!DEBUG){return;}
	    if (event == SERVER_EVENT.SCREEN_SEGMENT_CHANGED
	            || event == SERVER_EVENT.CURSOR_MOVE
                || event == SERVER_EVENT.CURSOR_GONE
                || (event == SERVER_EVENT.SCREEN_SEGMENT_UPDATE && ((Integer)args[0]) != -1)
                || event == SERVER_EVENT.READ_INPUT_EVENTS
                || event == SERVER_EVENT.END_OF_FRAME)
	    {
	        return;
	    }
		_logEvent(source, event, args);
	}

	public static void logEvent(final String source, final CLIENT_EVENT event, final Object[] args)
	{
	    if (!DEBUG){return;}
	    if ((event == CLIENT_EVENT.GET_SCREEN_SEGMENT && !(args[0] instanceof Integer))
	            || event == CLIENT_EVENT.OFFER_INPUT
	            || event == CLIENT_EVENT.ACKNOWLEDGE_NON_SERIAL_EVENT)
	    {
	        return;
	    }
		_logEvent(source, event, args);
	}

	private static void _logEvent(final String source, final Object event, final Object[] args)
	{
		String eventName;
		if (event instanceof SERVER_EVENT)
		{
			eventName = ((SERVER_EVENT)event).name();
		}
		else
		{
			eventName = ((CLIENT_EVENT)event).name();
		}
		System.out.print(String.format("%s sent event: %s", source, eventName));
		if (args == null)
		{
			System.out.println(" - null");
		}
		else
		{
			System.out.print(" - [");
			boolean isFirst = true;
			for (Object obj : args)
			{
				if (isFirst)
				{
					isFirst = false;
				}
				else
				{
					System.out.print(", ");
				}
				System.out.print(obj == null ? "null" : obj.toString());
			}
			System.out.println("]");
		}
	}
}
