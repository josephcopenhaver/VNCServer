package com.jcope.util;

import java.util.HashMap;
import java.util.concurrent.Semaphore;

import com.jcope.debug.LLog;

public class TaskDispatcher<T> extends Thread
{

	class Dispatchable
	{
		public T k;
		public Semaphore s;
		public Node n;
		public Runnable r;
		public Runnable onDestroy;
		
		public void dispose()
		{
			k = null;
			
			s = null;
			n = null;
			r = null;
			onDestroy = null;
		}
	}
	
	class Node
	{
		public volatile Node n;
		public volatile Node p;
		public volatile Dispatchable d;
		
		public void dispose()
		{
			Dispatchable cd = d;
			
			p = null;
			n = null;
			d = null;
			
			if (cd != null)
			{
				cd.dispose();
			}
		}
	}

	class LinkedList
	{
		private volatile Node a = null;
		private volatile Node z = null;
		private volatile int size = 0;
		private Semaphore s = new Semaphore(1, true);
		
		public Node add(Dispatchable d) throws InterruptedException
		{
			s.acquire();
			try
			{
				if (size + 1 <= 0)
				{
					throw new RuntimeException();
				}
				
				Node b = new Node();
				b.d = d;
				b.n = null;
				b.p = z;
				if (z == null)
				{
					a = b;
				}
				else
				{
					z.n = b;
				}
				size++;
				z = b;
				
				return b;
			}
			finally
			{
				s.release();
			}
		}
		
		public Node addFirst(Dispatchable d) throws InterruptedException
		{
			s.acquire();
			try
			{
				if (size + 1 <= 0)
				{
					throw new RuntimeException();
				}
				
				Node b = new Node();
				b.d = d;
				b.p = null;
				b.n = a;
				if (a == null)
				{
					z = b;
				}
				else
				{
					a.p = b;
				}
				size++;
				a = b;
				
				return b;
			}
			finally
			{
				s.release();
			}
		}
		
		public Dispatchable peek() throws InterruptedException
		{
			s.acquire();
			try
			{
				return (a == null) ? null : a.d;
			}
			finally
			{
				s.release();
			}
		}
		
		private Dispatchable _remove()
		{
			Dispatchable rval = null;
			
			if (a != null)
			{
				Node b = a;
				
				if (a.n == null)
				{
					a = null;
					z = null;
				}
				else
				{
					a = a.n;
					a.p = null;
				}
				
				rval = b.d;
				b.d = null;
				b.n = null;
				
				size--;
			}
			
			return rval;
		}
		
		public Dispatchable remove() throws InterruptedException
		{
			s.acquire();
			try
			{
				return _remove();
			}
			finally
			{
				s.release();
			}
		}
		
		public void remove(Node sn) throws InterruptedException
		{
			s.acquire();
			try
			{
				if (size - 1 < 0)
				{
					throw new RuntimeException();
				}
				
				if (sn == a)
				{
					if (a.n == null)
					{
						a = null;
						z = null;
					}
					else
					{
						a = a.n;
					}
				}
				else if (sn == z)
				{
					z = z.p;
				}
				else
				{
					sn.p.n = sn.n;
					sn.n.p = sn.p;
				}
				
				size--;
				sn.dispose();
			}
			finally
			{
				s.release();
			}
		}
		
		public int size() throws InterruptedException
		{
			s.acquire();
			try
			{
				return size;
			}
			finally
			{
				s.release();
			}
		}
		
		/**
		 * Will release all locks by default unless call clear(false)
		 */
		public void clear() throws InterruptedException
		{
			clear(null);
		}
		
		public void clear(Boolean releaseLocks) throws InterruptedException
		{
		    if (releaseLocks == null)
		    {
		        releaseLocks = Boolean.TRUE;
		    }
			s.acquire();
			try
			{
				Dispatchable d;
				Semaphore ns;
				Runnable onDestroy;
				
				while ((d = _remove()) != null)
				{
					ns = d.s;
					onDestroy = d.onDestroy;
					d.dispose();
					try
					{
					    if (onDestroy != null)
					    {
					        onDestroy.run();
					    }
					}
					finally {
    					if (releaseLocks && ns != null)
    					{
    						ns.release();
    					}
					}
				}
			}
			finally
			{
				s.release();
			}
		}
	}
	
	private Semaphore pauseAccess = new Semaphore(1, true);
	private Semaphore listLock = new Semaphore(1, true);
	private Semaphore curTaskLock = new Semaphore(1,true);
	private Semaphore sleepLock = new Semaphore(0,true);
	private Semaphore pauseLock = new Semaphore(0,true);
	private volatile LinkedList queue = new LinkedList();
	private volatile LinkedList inQueue = new LinkedList();
	private HashMap<T,Dispatchable> mapSet = new HashMap<T, Dispatchable>();
	private HashMap<T,Dispatchable> inMapSet = new HashMap<T, Dispatchable>();
	private HashMap<T,Boolean> immediateSet = new HashMap<T, Boolean>();
	private HashMap<T,Boolean> mutableSet = new HashMap<T, Boolean>();
	private volatile Dispatchable curTask = null;
	private volatile boolean disposed = false;
	private volatile boolean paused = false;
	private Dispatchable dummyTask = new Dispatchable();
	
	public TaskDispatcher()
	{
        init();
    }
    
	public TaskDispatcher(String name)
	{
        super(name);
        init();
    }
	
	private void init()
	{
	    start();
	}
    
    public void pause()
	{
		paused = true;
	}
    
    /**
     * may throw runtime exception wrapper of InterruptedException
     */
    public void unpause()
    {
        try
        {
            _unpause();
        }
        catch (InterruptedException e)
        {
            LLog.e(e);
        }
    }
    
    private void nts_unpause()
    {
        paused = false;
        pauseLock.release();
    }
    
    private void _unpause() throws InterruptedException
    {
        pauseAccess.acquire();
        try
        {
            nts_unpause();
        }
        finally {
            pauseAccess.release();
        }
    }
	
	public boolean queueContains(T k)
	{
	    try
        {
            listLock.acquire();
        }
        catch (InterruptedException e)
        {
            dispose(e);
        }
	    try
	    {
	        boolean rval = (mapSet.get(k) != null || inMapSet.get(k) != null);
	        
	        return rval;
	    }
	    finally {
	        listLock.release();
	    }
	}
	
	public Boolean isMutable(T k)
	{
		Boolean b = mutableSet.get(k);
		
		if (b == null)
		{
			b = Boolean.TRUE;
		}
		
		return b;
	}
	
	public Boolean isImmediate(T k)
	{
		Boolean b = immediateSet.get(k);
		
		if (b == null)
		{
			b = Boolean.FALSE;
		}
		
		return b;
	}
	
	// Uncomment if on java v1.7
	//@SafeVarargs
	public final void setImmediate(boolean b, T... set)
	{
		if (!b)
		{
			for(T k : set)
			{
				immediateSet.remove(k);
			}
		}
		else
		{
			for(T k : set)
			{
				immediateSet.put(k, b);
			}
		}
	}
	
	// Uncomment if on java v1.7
    //@SafeVarargs
	public final void setMutable(boolean b, T... set)
	{
		if (b)
		{
			for(T k : set)
			{
				mutableSet.remove(k);
			}
		}
		else
		{
			for(T k : set)
			{
				mutableSet.put(k, b);
			}
		}
	}
	
	private void _consumeInQueue()
	{
		Dispatchable d;
		
		inMapSet.clear();
		
		try
		{
			while ((d = inQueue.remove()) != null)
			{
				_dispatch(d);
			}
		}
		catch (InterruptedException e)
		{
			dispose(e);
		}
	}
	
	private boolean consumeInQueue()
	{
		boolean rval;
		try
		{
			rval = (inQueue.size() > 0);
		}
		catch (InterruptedException e)
		{
			dispose(e);
			return false;
		}
		
		if (rval)
		{
			try
			{
				listLock.acquire();
				try
				{
					_consumeInQueue();
				}
				finally
				{
					listLock.release();
				}
			}
			catch (InterruptedException e)
			{
				dispose(e);
			}
		}
		
		return rval;
	}
	
	public void run()
	{
		boolean isNullTask = true;
		boolean needsRelease;
		do
		{
		    needsRelease = true;
		    try
            {
                pauseAccess.acquire();
            }
            catch (InterruptedException e)
            {
                dispose(e);
                break;
            }
		    try
		    {
    			if (paused)
    			{
    				pauseLock.drainPermits();
    				try
    				{
    				    needsRelease = false;
    				    pauseAccess.release();
    					pauseLock.acquire();
    				}
    				catch (InterruptedException e)
    				{
    					paused = false;
    					dispose(e);
    				}
    			}
		    }
		    finally {
		        if (needsRelease)
		        {
		            pauseAccess.release();
		        }
		    }
			if (disposed)
			{
				break;
			}
			consumeInQueue();
			try
			{
				if (disposed)
				{
					break;
				}
				try
				{
					curTaskLock.acquire();
				}
				catch (InterruptedException e)
				{
					dispose(e);
					break;
				}
				needsRelease = true;
				try
				{
					curTask = queue.remove();
					if (curTask == null)
					{
					    curTaskLock.release();
					    needsRelease = false;
					    if (consumeInQueue())
					    {
					        try
	                        {
	                            curTaskLock.acquire();
	                        }
	                        catch (InterruptedException e)
	                        {
	                            dispose(e);
	                        }
	                        needsRelease = true;
					        curTask = queue.remove();
					    }
					}
					isNullTask = (curTask == null || curTask == dummyTask);
				}
				finally {
				    if (needsRelease)
				    {
				        curTaskLock.release();
				    }
				}
				if (isNullTask)
				{
					sleepLock.acquire();
					if (disposed)
	                {
	                    break;
	                }
				}
				else
				{
					mapSet.remove(curTask.k);
					try
					{
						Semaphore s = curTask.s;
						try
						{
							Runnable r = curTask.r;
							Runnable d = curTask.onDestroy;
							curTask.s = null;
							curTask.r = null;
							curTask.onDestroy = null;
							try
							{
							    r.run();
							}
							finally {
							    if (d != null)
							    {
							        d.run();
							    }
							}
						}
						finally {
							if (s != null)
							{
								s.release();
							}
						}
					}
					catch (Exception e)
					{
						LLog.e(e, false);
					}
					catch (Throwable t)
					{
						LLog.e(t, false);
					}
				}
			}
			catch (InterruptedException e)
			{
				dispose(e);
			}
		}
		while(true);
	}
	
	private void updateNode(Dispatchable d, Runnable r, Runnable onDestroy, Semaphore s)
	{
		Runnable od = d.onDestroy;
		Semaphore os = d.s;
		d.s = s;
		d.r = r;
		d.onDestroy = onDestroy;
		if (od != null)
		{
    		try
    		{
    		    od.run();
    		}
    		catch (Exception e)
            {
                LLog.e(e, false);
            }
            catch (Throwable t)
            {
                LLog.e(t, false);
            }
		}
		if (os != null)
		{
			os.release();
		}
	}
	
	private void _dispatch(LinkedList q, HashMap<T,Dispatchable> m, T k, Runnable r, Runnable onDestroy, Semaphore s)
	{
		Dispatchable d = m.get(k);
		if (d != null)
		{
			if (isMutable(k))
			{
				updateNode(d, r, onDestroy, s);
			}
			else
			{
			    try
			    {
    			    if (d.onDestroy != null)
    			    {
    			        d.onDestroy.run();
    			    }
			    }
			    finally {
    				try
    				{
    					q.remove(d.n);
    				}
    				catch (InterruptedException e)
    				{
    					dispose(e);
    					return;
    				}
    				updateNode(d, r, onDestroy, s);
    				try
    				{
    					d.n = q.add(d);
    				}
    				catch (InterruptedException e)
    				{
    					dispose(e);
    					d.n = null;
    					return;
    				}
			    }
			}
		}
		else
		{
			d = new Dispatchable();
			d.k = k;
			d.s = s;
			d.r = r;
			d.onDestroy = onDestroy;
			try
			{
				d.n = isImmediate(k) ? q.addFirst(d) : q.add(d);
			}
			catch (InterruptedException e)
			{
				dispose(e);
				d.n = null;
				return;
			}
			m.put(k, d);
			if (q == inQueue)
			{
				try
				{
					curTaskLock.acquire();
				}
				catch (InterruptedException e)
				{
					dispose(e);
					return;
				}
				try
				{
					if (curTask == null)
					{
						// wake up the dispatcher in 1-1 manner
						// dispatcher will auto consume the inqueue
						curTask = dummyTask;
						sleepLock.release();
					}
				}
				finally
				{
					curTaskLock.release();
				}
			}
		}
	}
	
	private void _dispatch(T k, Runnable r, Runnable onDestroy, Semaphore s)
	{
		_dispatch(inQueue, inMapSet, k, r, onDestroy, s);
	}
	
	private void _dispatch(Dispatchable d)
	{
		d.n = null;
		_dispatch(queue, mapSet, d.k, d.r, d.onDestroy, d.s);
	}
	
	/**
	 * Not very useful unless attempting to do something unique
	 * in a dispatched task when the dispatcher could be about to go idle
	 * (E.G. if each task is guaranteed to write to a socket and empty, then flush)
	 * 
	 * @return
	 */
	public boolean isEmpty()
	{
	    return (queue.size == 0 && inQueue.size == 0);
	}
	
	public void dispatch(T k, Runnable r, Runnable onDestroy, Semaphore semaphore)
	{
		try
		{
			listLock.acquire();
			try
			{
			    if (disposed)
			    {
			        Semaphore t_semaphore = semaphore;
			        semaphore = null;
			        try
			        {
    			        if (onDestroy != null)
    			        {
    			            Runnable t_onDestroy = onDestroy;
    			            onDestroy = null;
    			            t_onDestroy.run();
    			        }
			        }
			        finally {
			            if (t_semaphore != null)
                        {
			                t_semaphore.release();
                        }
			        }
			    }
			    else
			    {
			        _dispatch(k, r, onDestroy, semaphore);
			    }
			}
			finally {
				listLock.release();
			}
		}
		catch(InterruptedException e)
		{
			if (onDestroy != null)
			{
			    onDestroy.run();
			}
			LLog.e(e);
		}
	}
	
	public void cancel(T k) {
		cancel(null, k);
	}
	
	private void cancel(Boolean releaseLocks, T k)
	{
		if (releaseLocks == null)
	    {
	        releaseLocks = Boolean.TRUE;
	    }
		Dispatchable d = null;
		Runnable onDestroy = null;
		Semaphore s = null;
		boolean disposedDetected = false;
		
		try {
			listLock.acquire();
		} catch (InterruptedException e) {
			dispose(e);
			return;
		}
		try
		{
			if (disposed) {
				disposedDetected = true;
				return;
			}
			d = inMapSet.remove(k);
			if (d != null) {
				onDestroy = d.onDestroy;
				s = d.s;
				try {
					inQueue.remove(d.n);
				} catch (InterruptedException e) {
					dispose(e);
					return;
				}
			}
		}
		finally {
			listLock.release();
			if (disposedDetected) {
				return;
			}
			try {
				if (d != null) {
					try {
						try {
							if (onDestroy != null) {
								onDestroy.run();
							}
						}
						finally {
							if (releaseLocks && s != null) {
								s.release();
							}
						}
					}
					finally {
						d.dispose();
					}
				}
			}
			finally {
				if (disposed) {
					return;
				}
				d = null;
				try
				{
					curTaskLock.acquire();
				}
				catch (InterruptedException e)
				{
					dispose(e);
					return;
				}
				try
				{
					if (disposed) {
						return;
					}
					d = mapSet.remove(k);
					if (d == null) {
						return;
					}
					onDestroy = d.onDestroy;
					s = d.s;
					try {
						queue.remove(d.n);
					} catch (InterruptedException e) {
						dispose(e);
						return;
					}
				}
				finally
				{
					curTaskLock.release();
					if (d == null) {
						return;
					}
					try {
						try {
							if (onDestroy != null) {
								onDestroy.run();
							}
						}
						finally {
							if (releaseLocks && s != null) {
								s.release();
							}
						}
					}
					finally {
						d.dispose();
					}
				}
			}
		}
	}
	
	public void dispatch(T k, Runnable r, Runnable onDestroy)
	{
	    dispatch(k, r, onDestroy, null);
	}
	
	public void dispatch(T k, Runnable r)
	{
	    dispatch(k, r, null);
	}
	
	private void dispose(Exception e)
	{
	    dispose(null, e);
	}
	
	private void dispose(Boolean releaseLocks, Exception e)
	{
		boolean warnAsync = false;
		if (e != null)
		{
			LLog.e(e, false);
		}
		try
		{
			synchronized (this)
			{
				if (disposed)
				{
					warnAsync = true;
					return;
				}
				disposed = true;
			}
		}
		finally {
			if (warnAsync)
			{
				LLog.w("already disposed");
			}
		}
		try
		{
		    clear(releaseLocks);
		}
		finally {
		    try
		    {
        		if (paused)
        		{
        		    nts_unpause();
        		}
		    }
		    finally {
		        sleepLock.release();
		    }
		}
	}
	
	public void dispose(Boolean releaseLocks)
	{
	    dispose(releaseLocks, null);
	}
	
	public void dispose()
	{
		dispose(null, null);
	}
	
	public void clear()
	{
	    clear(null);
	}
	
	public void clear(Boolean releaseLocks)
	{
		boolean doUnpause = !paused;
		pause();
		try
		{
    		try
    		{
    			listLock.acquire();
    		}
    		catch (InterruptedException e)
    		{
    			dispose(e);
    			return;
    		}
    		try
    		{
    		    inMapSet.clear();
    		    inQueue.clear(releaseLocks);
    		}
    		catch (InterruptedException e)
    		{
    			dispose(e);
    			return;
    		}
    		finally
    		{
    			listLock.release();
    		}
		}
		finally {
    		try
    		{
    			curTaskLock.acquire();
    		}
    		catch (InterruptedException e)
    		{
    			dispose(e);
    			return;
    		}
    		try
    		{
    			mapSet.clear();
    			queue.clear(releaseLocks);
    		}
    		catch (InterruptedException e)
    		{
    			dispose(e);
    			return;
    		}
    		finally
    		{
    			curTaskLock.release();
    		}
		}
		
		if (doUnpause)
		{
			try
            {
                _unpause();
            }
            catch (InterruptedException e)
            {
                paused = false;
                try
                {
                    dispose(e);
                }
                finally {
                    nts_unpause();
                }
            }
		}
	}

}