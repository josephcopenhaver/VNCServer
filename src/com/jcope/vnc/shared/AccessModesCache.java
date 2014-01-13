package com.jcope.vnc.shared;

import java.util.HashMap;

import com.jcope.vnc.shared.AccessModes.ACCESS_MODE;

public class AccessModesCache
{
    protected static ACCESS_MODE[] sortedList = null;
    protected static ACCESS_MODE[] selectableList = null;
    protected static HashMap<String, ACCESS_MODE> modesByName = null;
}
