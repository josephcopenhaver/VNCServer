package com.jcope.util;

import java.util.GregorianCalendar;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;

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
}
