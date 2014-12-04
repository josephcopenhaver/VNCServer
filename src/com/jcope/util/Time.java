package com.jcope.util;

import static com.jcope.debug.Debug.assert_;

import java.util.GregorianCalendar;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;

import com.jcope.debug.LLog;

public class Time {
    
    public static long parseISO8601Duration(String durationStr, GregorianCalendar startTime) throws DatatypeConfigurationException
    {
        Duration duration;
        long rval;
        
        durationStr = String.format("P%s", durationStr);
        duration = DatatypeFactory.newInstance().newDuration(durationStr);
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
		} catch (DatatypeConfigurationException e) {
			LLog.e(e);
			return 0;// unreachable, makes compiler happy
		}
    }
}
