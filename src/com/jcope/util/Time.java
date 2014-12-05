package com.jcope.util;

import static com.jcope.debug.Debug.assert_;

import java.util.GregorianCalendar;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;

import com.jcope.debug.LLog;

public class Time {
	
	public static class InvalidFormatException extends Exception {

		/**
		 * Default: serialVersionUID
		 */
		private static final long serialVersionUID = 1L;
		
	}
	
	public static String toISO8601DurationStr(long duration_ms)
	{
		String rval;
		
		java.time.Duration duration = java.time.Duration.ZERO.plusMillis(duration_ms);
		rval = duration.toString();
		rval = rval.replaceFirst("P", "");
		
		return rval;
	}
    
    public static long parseISO8601Duration(String durationStr, GregorianCalendar startTime) throws InvalidFormatException
    {
        Duration duration;
        long rval;
        
        durationStr = String.format("P%s", durationStr);
        try
        {
        	duration = DatatypeFactory.newInstance().newDuration(durationStr);
        }
        catch (IllegalArgumentException e)
        {
        	throw new InvalidFormatException();
        } catch (DatatypeConfigurationException e) {
			// Nothing I can do about these kinds of errors
        	// They indicate the support library config is lacking
        	LLog.e(e);
        	return 0;// unreachable, makes compiler happy
		}
        rval = duration.getTimeInMillis(startTime);
        
        return rval;
    }
    
    /**
     * Return in the domain of R+
     * 
     * @param durationStr
     * @param startTime
     * @return
     */
    public static long mustParseISO8601DurationRP(String durationStr, GregorianCalendar startTime)
    {
    	try {
			long rval = parseISO8601Duration(durationStr, startTime);
			assert_(rval > 0);
			return rval;
		} catch (InvalidFormatException e) {
			LLog.e(e);
			return 0;// unreachable, makes compiler happy
		}
    }
}
