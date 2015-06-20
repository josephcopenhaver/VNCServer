package com.jcope.util;

import static com.jcope.debug.Debug.assert_;

import java.util.GregorianCalendar;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;

import com.jcope.debug.LLog;

public class Time {

    private static long ms_per_second = 1000;
    private static long ms_per_minute = 60 * ms_per_second;
    private static long ms_per_hour = 60 * ms_per_minute;
    private static long ms_per_day = 24 * ms_per_hour;

    public static class InvalidFormatException extends Exception {

        /**
         * Default: serialVersionUID
         */
        private static final long serialVersionUID = 1L;

    }

    public static String toISO8601DurationStr(long duration_ms) {
        if (duration_ms <= 0) {
            return "T0S";
        }
        String rval;
        StringBuffer sb = new StringBuffer();
        long factor;

        do {
            factor = duration_ms / ms_per_day;
            if (factor > 0) {
                sb.append("" + factor);
                sb.append("D");
                duration_ms %= ms_per_day;
            }

            if (duration_ms == 0)
                break;

            sb.append("T");

            factor = duration_ms / ms_per_hour;
            if (factor > 0) {
                sb.append("" + factor);
                sb.append("H");
                duration_ms %= ms_per_hour;
            }

            if (duration_ms == 0)
                break;

            factor = duration_ms / ms_per_minute;
            if (factor > 0) {
                sb.append("" + factor);
                sb.append("M");
                duration_ms %= ms_per_minute;
            }

            if (duration_ms == 0)
                break;

            factor = duration_ms / ms_per_second;
            if (factor > 0) {
                sb.append("" + factor);
                duration_ms %= ms_per_second;
            }
            if (duration_ms > 0) {
                sb.append(".");
                sb.append("" + duration_ms);
            }
            sb.append("S");
        } while (false);

        rval = sb.toString();
        return rval;
    }

    public static long parseISO8601Duration(String durationStr,
            GregorianCalendar startTime) throws InvalidFormatException {
        Duration duration;
        long rval;

        durationStr = String.format("P%s", durationStr);
        try {
            duration = DatatypeFactory.newInstance().newDuration(durationStr);
        } catch (IllegalArgumentException e) {
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
    public static long mustParseISO8601DurationRP(String durationStr,
            GregorianCalendar startTime) {
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
