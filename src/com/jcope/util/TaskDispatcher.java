package com.jcope.util.TaskDispatcher;

import java.util.concurrent.Semaphore;
import java.util.HashMap;

public class TaskDispatcher<T> extends Thread {

	class Dispatchable {
		public T k;
		public Semaphore s;
		public Node n;
		public Runnable r;
	}
	
	class Node {
		public volatile Node n;
		public volatile Node p;
		public volatile Dispatchable d;
	}

	class LinkedList {
		private volatile Node a = null;
		private volatile Node z = null;
		private volatile int size = 0;
		
		public Node add(Dispatchable d)
		{
			if (size + 1 <= 0)
			{
				throw new RuntimeException();
			}
			
			Node r;
			
			if (a == null)
			{
				a = new Node();
				a.n = null;
				a.p = null;
				a.d = d;
				r = a;
			}
			else
			{
				Node b = new Node();
				b.d = d;
				b.n = null;
				b.p = z;
				z.n = b;
				r = b;
			}
			
			size++;
			z = r;
			
			return r;
		}
		
		public Node addFirst(Dispatchable d)
		{
			if (size + 1 <= 0)
			{
				throw new RuntimeException();
			}
			
			Node r;
			
			if (a == null)
			{
				r = add(d);
			}
			else
			{
				Node b = new Node();
				b.d = d;
				b.p = null;
				b.n = a;
				a.p = b;
				r = b;
				size++;
				a = r;
			}
			
			return r;
		}
		
		public Dispatchable peek()
		{
			return (a == null) ? null : a.d;
		}
		
		public Dispatchable remove()
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
				
				size--;
			}
			
			return rval;
		}
		
		public void remove(Node s)
		{
			if (size - 1 < 0)
			{
				throw new RuntimeException();
			}
			
			if (s == a)
			{
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
			}
			else if (s == z)
			{
				if (z.p == null)
				{
					a = null;
					z = null;
				}
				else
				{
					z = z.p;
					z.n = null;
				}
			}
			else
			{
				s.p.n = s.n;
				s.n.p = s.p;
			}
			
			size--;
		}
		
		public int size()
		{
			return size;
		}
	}
	
	private Semaphore listLock = new Semaphore(1, true);
	private Semaphore sleepLock = new Semaphore(0,true);
	private volatile LinkedList queue = new LinkedList();
	private volatile LinkedList inQueue = new LinkedList();
	private HashMap<T,Dispatchable> mapSet = new HashMap<T, Dispatchable>();
	private HashMap<T,Dispatchable> inMapSet = new HashMap<T, Dispatchable>();
	private HashMap<T,Boolean> immediateSet = new HashMap<T, Boolean>();
	private HashMap<T,Boolean> mutableSet = new HashMap<T, Boolean>();
	private volatile Dispatchable curTask = null;
	private volatile boolean disposed = false;
	private Dispatchable dummyTask = new Dispatchable();
	
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
	
	public void setImmediate(boolean b, T... set)
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
	
	public void setMutable(boolean b, T... set)
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
	
	public TaskDispatcher() {
		start();
	}
	
	private void _consumeInQueue()
	{
		Dispatchable d;
		
		inMapSet.clear();
		
		while ((d = inQueue.remove()) != null)
		{
			//System.out.println("Adding to active queue");
			_dispatch(d);
		}
	}
	
	private boolean consumeInQueue(boolean lockSet)
	{
		boolean rval = (inQueue.size() > 0);
		
		if (rval)
		{
			if (lockSet)
			{
				try {
					listLock.acquire();
					try {
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
			else
			{
				_consumeInQueue();
			}
		}
		
		return rval;
	}
	
	public void run()
	{
		do
		{
			if (disposed)
			{
				break;
			}
			consumeInQueue(true);
			try {
				listLock.acquire();
				if (disposed)
				{
					listLock.release();
					break;
				}
				curTask = queue.remove();
				if (curTask == null && consumeInQueue(false))
				{
					curTask = queue.remove();
				}
				if (curTask == null)
				{
					listLock.release();
					//System.out.println("Going to sleep");
					sleepLock.acquire();
				}
				else
				{
					//System.out.println("Running...");
					inMapSet.put(curTask.k, null);
					listLock.release();
					try {
						Semaphore s = curTask.s;
						try {
							curTask.s = null;
							Runnable r = curTask.r;
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
				q.remove(d.n);
				updateNode(d, r, s);
				d.n = q.add(d);
			}
		}
		else
		{
			d = new Dispatchable();
			d.k = k;
			d.s = s;
			d.r = r;
			d.n = isImmediate(k) ? q.addFirst(d) : q.add(d);
			m.put(k, d);
			if (curTask == null && q == inQueue)
			{
				// wake up the dispatcher in 1-1 manner
				// dispatcher will auto consume the inqueue
				curTask = dummyTask;
				sleepLock.release();
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
		try {
			listLock.acquire();
			try {
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
	}
	
	public void dispose()
	{
		dispose(null);
	}
	
	/*
	public static void main(String[] args)
	{
		TaskDispatcher<Integer> disp = new TaskDispatcher<Integer>();
		final Semaphore s = new Semaphore(0,true);
		disp.dispatch(1, new Runnable(){public void run(){
			try {
			s.acquire();
			} catch(InterruptedException e){e.printStackTrace();}
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
		s.release();
		disp.dispatch(4, new Runnable(){public void run(){
			System.out.println("rawr4");
		}});
	}
	*/

}