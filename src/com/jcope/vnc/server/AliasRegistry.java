package com.jcope.vnc.server;

import java.util.HashMap;
import java.util.concurrent.Semaphore;
import static com.jcope.debug.Debug.assert_;

import com.jcope.debug.LLog;

/**
 * 
 * @author Joseph Copenhaver
 *
 * binds alias and ClientHandles to eachother
 * 
 */

public class AliasRegistry
{
    
    private static final AliasRegistry[] selfRef = new AliasRegistry[]{null};
    private static final Semaphore instanceSema = new Semaphore(1, true);
    private static volatile boolean hasInstance = false;
    
    private HashMap<ClientHandler, String> aliasPerClient;
    private HashMap<String, ClientHandler> clientPerAlias;
    
    private AliasRegistry()
    {
        aliasPerClient = new HashMap<ClientHandler, String>();
        clientPerAlias = new HashMap<String, ClientHandler>();
        hasInstance = true;
    }
    
    public static boolean hasInstance()
    {
        return hasInstance;
    }
    
    public static AliasRegistry getInstance()
    {
        AliasRegistry rval = selfRef[0];
        
        if (rval == null)
        {
            try
            {
                instanceSema.acquire();
            }
            catch (InterruptedException e)
            {
                LLog.e(e);
            }
            try
            {
                synchronized(selfRef)
                {
                    rval = selfRef[0];
                    if (rval == null)
                    {
                        rval = new AliasRegistry();
                        selfRef[0] = rval;
                    }
                }
            }
            finally {
                instanceSema.release();
            }
        }
        
        return rval;
    }
    
    private Semaphore stageLock = new Semaphore(1, true);
    private Object[] stagedArgs = null;
    public void withLock(Runnable r, Object... args)
    {
        try
        {
            stageLock.acquire();
        }
        catch (InterruptedException e)
        {
            LLog.e(e);
        }
        try
        {
            stagedArgs = args;
            synchronized(aliasPerClient){synchronized(clientPerAlias)
            {
                r.run();
            }}
        }
        finally {
            stagedArgs = null;
            stageLock.release();
        }
    }
    
    private boolean actionWorked;
    private Runnable actionBind = new Runnable()
    {

        @Override
        public void run()
        {
            actionWorked = false;
            assert_(stagedArgs != null);
            assert_(stagedArgs.length == 2);
            assert_(stagedArgs[0] instanceof ClientHandler);
            assert_(stagedArgs[1] instanceof String);
            
            ClientHandler client = (ClientHandler) stagedArgs[0];
            String alias = (String) stagedArgs[1];
            if (clientPerAlias.get(alias) == null)
            {
                actionUnbind.run();
                clientPerAlias.put(alias, client);
                aliasPerClient.put(client, alias);
                actionWorked = true;
            }
        }
        
    };
    
    private Runnable actionUnbind = new Runnable()
    {

        @Override
        public void run()
        {
            assert_(stagedArgs != null);
            assert_(stagedArgs.length > 0);
            assert_(stagedArgs[0] instanceof ClientHandler);
            
            ClientHandler client = (ClientHandler) stagedArgs[0];
            String name = aliasPerClient.remove(client);
            if (name != null)
            {
                clientPerAlias.remove(name);
            }
        }
        
    };
    
    public boolean bind(ClientHandler client, String name)
    {
        withLock(actionBind, client, name);
        return actionWorked;
    }
    
    public void unbind(ClientHandler client)
    {
        withLock(actionUnbind, client);
    }
}
