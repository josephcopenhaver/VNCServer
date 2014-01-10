package com.jcope.vnc.shared;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import com.jcope.vnc.server.SecurityPolicy;

public class AccessModes
{
    public static enum ACCESS_MODE
    {
        VIEW_ONLY,
        VIEW_AND_CHAT,
        FULL_CONTROL,
        ALL
        
        ;
        
        private static ACCESS_MODE[] sortedList = null;
        private static HashMap<String, ACCESS_MODE> modesByName = null;
        
        public static ACCESS_MODE[] sorted()
        {
            if (sortedList == null)
            {
                ACCESS_MODE[] values = ACCESS_MODE.values();
                HashMap<String, ACCESS_MODE> nameToEnum = new HashMap<String, ACCESS_MODE>(values.length);
                ArrayList<String> names = new ArrayList<String>(values.length);
                for (ACCESS_MODE value : values)
                {
                    String name = value.commonName();
                    
                    names.add(name);
                    nameToEnum.put(name, value);
                }
                sortedList = new ACCESS_MODE[values.length];
                Collections.sort(names);
                for (int i=0; i<values.length; i++)
                {
                    sortedList[i] = nameToEnum.get(names.get(i));
                }
            }
            
            return sortedList;
        }
        
        public String commonName()
        {
            String rval = equals(ALL) ? SecurityPolicy.ALL_TOKEN : name();
            
            return rval;
        }

        public static ACCESS_MODE get(String accessModeStr)
        {
            ACCESS_MODE rval;
            
            if (modesByName == null)
            {
                ACCESS_MODE[] values = ACCESS_MODE.values();
                modesByName = new HashMap<String, ACCESS_MODE>(values.length);
                for (ACCESS_MODE value : values)
                {
                    String name = value.commonName();
                    modesByName.put(name, value);
                }
            }
            
            rval = modesByName.get(accessModeStr);
            
            return rval;
        }
    };
}
