package com.jcope.util;

import java.util.HashMap;
import java.util.concurrent.Semaphore;

public class TaskDispatcher<T> extends Thread
{

	class Dispatchable
	{
		public T k;
		public Semaphore s;
		public Node n;
		public Runnable r;
		
		public void dispose()
		{
			k = null;
			
			s = null;
			n = null;
			r = null;
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
			clear(true);
		}
		
		public void clear(boolean releaseLocks) throws InterruptedException
		{
			s.acquire();
			try
			{
				Dispatchable d;
				Semaphore ns;
				
				while ((d = _remove()) != null)
				{
					//System.out.println("Dropping task!");
					ns = d.s;
					d.dispose();
					if (releaseLocks && ns != null)
					{
						ns.release();
					}
				}
			}
			finally
			{
				s.release();
			}
		}
	}
	
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
		//System.out.println("Signaling pause");
		paused = true;
	}
	
	public void unpause()
	{
		//System.out.println("Signaling unpause");
		paused = false;
		pauseLock.release();
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
				//System.out.println("Adding to active queue");
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
			if (paused)
			{
				//System.out.println("Handling pause signal");
				pauseLock.drainPermits();
				try
				{
					pauseLock.acquire();
				}
				catch (InterruptedException e)
				{
					paused = false;
					dispose(e);
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
					//System.out.println("Going to sleep");
					sleepLock.acquire();
					if (disposed)
	                {
	                    break;
	                }
				}
				else
				{
					//System.out.println("Running...");
					mapSet.remove(curTask.k);
					try
					{
						Semaphore s = curTask.s;
						try
						{
							Runnable r = curTask.r;
							curTask.s = null;
							curTask.r = null;
							r.run();
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
						e.printStackTrace();
					}
					catch (Throwable t)
					{
						t.printStackTrace();
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
	
	private void updateNode(Dispatchable d, Runnable r, Semaphore s)
	{
		Semaphore os = d.s;
		d.s = s;
		d.r = r;
		if (os != null)
		{
			os.release();
		}
	}
	
	private void _dispatch(LinkedList q, HashMap<T,Dispatchable> m, T k, Runnable r, Semaphore s)
	{
		Dispatchable d = m.get(k);
		if (d != null)
		{
			if (isMutable(k))
			{
				//System.out.println("Is mutable");
				updateNode(d, r, s);
			}
			else
			{
				try
				{
					q.remove(d.n);
				}
				catch (InterruptedException e)
				{
					dispose(e);
					return;
				}
				updateNode(d, r, s);
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
		else
		{
			d = new Dispatchable();
			d.k = k;
			d.s = s;
			d.r = r;
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
	
	private void _dispatch(T k, Runnable r, Semaphore s)
	{
		_dispatch(inQueue, inMapSet, k, r, s);
	}
	
	private void _dispatch(Dispatchable d)
	{
		d.n = null;
		_dispatch(queue, mapSet, d.k, d.r, d.s);
	}
	
	public void dispatch(T k, Runnable r)
	{
		try
		{
			listLock.acquire();
			try
			{
				//System.out.println("Adding to input queue");
				_dispatch(k, r, null);
			}
			finally {
				listLock.release();
			}
		}
		catch(InterruptedException e)
		{
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	private void dispose(Exception e)
	{
		if (e != null)
		{
			e.printStackTrace();
		}
		disposed = true;
		if (paused)
		{
			unpause();
		}
		sleepLock.release();
	}
	
	public void dispose()
	{
		dispose(null);
	}
	
	public void clear()
	{
		boolean doUnpause = !paused;
		pause();
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
			inQueue.clear();
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
			queue.clear();
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
		
		if (doUnpause)
		{
			unpause();
		}
	}
	
	/*
	public static void main(String[] args)
	{
		TaskDispatcher<Integer> disp = new TaskDispatcher<Integer>();
		disp.pause();
		//final Semaphore s = new Semaphore(0,true);
		disp.dispatch(1, new Runnable(){public void run(){
			//try {
			//s.acquire();
			//} catch(InterruptedException e){e.printStackTrace();}
			System.out.println("rawr");
		}});
		disp.dispatch(2, new Runnable(){public void run(){
			System.out.println("rawr2");
		}});
		disp.dispatch(3, new Runnable(){public void run(){
			System.out.println("rawr3");
		}});
		disp.dispatch(3, new Runnable(){public void run(){
			System.out.println("rawr3-2");
		}});
		//s.release();
		disp.dispatch(4, new Runnable(){public void run(){
			System.out.println("rawr4");
		}});
		//disp.clear();
		disp.unpause();
	}
	*/

}
