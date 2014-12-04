package com.jcope.util;

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
    
    
    public static long mustParseISO8601Duration(String durationStr, GregorianCalendar startTime)
    {
    	try {
			return parseISO8601Duration(durationStr, startTime);
		} catch (DatatypeConfigurationException e) {
			LLog.e(e);
			return 0;// unreachable, makes compiler happy
		}
    }
}
