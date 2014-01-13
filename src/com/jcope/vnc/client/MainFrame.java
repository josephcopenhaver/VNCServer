package com.jcope.vnc.client;

import static com.jcope.debug.Debug.DEBUG;
import static com.jcope.util.Scale.factorsThatShrinkToFitWithin;
import static com.jcope.util.Scale.factorsThatStretchToFit;

import java.awt.Dimension;
import java.awt.DisplayMode;
import java.awt.GraphicsDevice;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import com.jcope.debug.LLog;
import com.jcope.ui.ImagePanel;
import com.jcope.util.DimensionF;
import com.jcope.vnc.shared.AccessModes.ACCESS_MODE;
import com.jcope.vnc.shared.ScreenSelector;
import com.jcope.vnc.shared.StateMachine.CLIENT_EVENT;

enum VIEW_MODE
{
    NORMAL_SCROLLING,
    FULL_SCREEN,
    FIT,
    STRETCHED_FIT,
    STRETCH
};

public class MainFrame extends JFrame
{
	// Generated: serialVersionUID
	private static final long serialVersionUID = -6735839955506471961L;
	
	private final StateMachine client;
	private final JScrollPane scrollPane = new JScrollPane();
	
	private volatile ImagePanel imagePanel = null;
	private volatile String alias = null;
	private volatile VIEW_MODE viewMode = null;
	
	private Dimension contentPaneSize = new Dimension();
	private HashMap<VIEW_MODE,ActionListener> viewModeActions = new HashMap<VIEW_MODE,ActionListener>(VIEW_MODE.values().length);
	
	private Dimension imagePanelSize = new Dimension();
	private DimensionF scaleFactors = new DimensionF(1.0f, 1.0f);
	
	private Point locationBeforeFullScreen = new Point();
    private Dimension sizeBeforeFullScreen = new Dimension();
    private volatile boolean isChangingFullScreenStatus = false;
	private Semaphore isChangingFullScreenLock = new Semaphore(1, true);
	private boolean isFullScreen = false;
	private DisplayMode displayMode = null;
	private GraphicsDevice currentFullScreenDevice = null;

	public MainFrame(final StateMachine stateMachine)
	{
	    
	    add(scrollPane);
	    
	    // TODO: define mneumonics
	    
	    final MainFrame fthis = this;
	    
		client = stateMachine;
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		
		// setup layout components
		
		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);
		
		
		
		if (DEBUG)
		{
		    JMenu debugMenu = new JMenu("Debug");
		    menuBar.add(debugMenu);
		    JMenuItem actionInvalidateScreen = new JMenuItem("Invalidate Screen");
		    debugMenu.add(actionInvalidateScreen);
		    actionInvalidateScreen.addActionListener(new ActionListener()
		    {

                @Override
                public void actionPerformed(ActionEvent arg0)
                {
                    invalidate();
                    repaint();
                }
		    });
		}
		
		
		
		JMenu actionMenu = new JMenu("Actions");
        menuBar.add(actionMenu);
        JMenu viewMenu = new JMenu("View");
        menuBar.add(viewMenu);
        
        JMenuItem refreshScreen = new JMenuItem("Refresh");
		actionMenu.add(refreshScreen);
		JMenuItem setAlias = new JMenuItem("Set Alias");
		actionMenu.add(setAlias);
        JMenuItem clearAlias = new JMenuItem("Clear Alias");
        actionMenu.add(clearAlias);
        JMenuItem disconnect = new JMenuItem("Disconnect");
        actionMenu.add(disconnect);
		
        JMenuItem normalScrollingScreen = new JMenuItem("Normal");
        viewMenu.add(normalScrollingScreen);
        JMenuItem fullScreen = new JMenuItem("Full Screen");
        viewMenu.add(fullScreen);
        JMenuItem stretchScreen = new JMenuItem("Stretch");
        viewMenu.add(stretchScreen);
        JMenuItem stretchedFitScreen = new JMenuItem("Stretch to fit");
        viewMenu.add(stretchedFitScreen);
        JMenuItem fitScreen = new JMenuItem("Shrink to fit");
        viewMenu.add(fitScreen);
		
		
        
        // setup control mechanisms
        
        
        
        // Actions Menu
		
        
		refreshScreen.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK)); 
		refreshScreen.addActionListener(new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                client.sendEvent(CLIENT_EVENT.GET_SCREEN_SEGMENT, Integer.valueOf(-1));
            }
        });
		
		final ActionListener clearAction;
		// TODO: define accelerator
        clearAlias.addActionListener((clearAction = new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                alias = null;
                stateMachine.sendEvent(CLIENT_EVENT.REQUEST_ALIAS, "");
                // TODO: disable chat input pane
            }
        }));
		
		// TODO: define accelerator
		setAlias.addActionListener(new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // TODO: enable chat input pane
                String result = JOptionPane.showInputDialog(fthis, "What would you like your alias to be?", alias);
                if (result != null)
                {
                    if (result.equals(""))
                    {
                        clearAction.actionPerformed(null);
                    }
                    else if (!result.equals(alias))
                    {
                        alias = result;
                        stateMachine.sendEvent(CLIENT_EVENT.REQUEST_ALIAS, alias);
                    }
                }
            }
        });
		
		// TODO: define accelerator
		disconnect.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent arg0)
            {
                client.disconnect();
            }
		    
		});
		
		
		
		// View Menu
		final ActionListener defaultView;
		ActionListener tmpActionListener = null;
		
		
		
		// TODO: define accelerator
        normalScrollingScreen.addActionListener((defaultView = (tmpActionListener = new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                if (!setViewMode(VIEW_MODE.NORMAL_SCROLLING))
                {
                    return;
                }
                
                // No stretching/scale factors
                // enable scrolling as needed
                enableScrolling();
                if (imagePanel != null)
                {
                    stateMachine.handleUserAction(new Runnable()
                    {
    
                        @Override
                        public void run()
                        {
                            int lSpace,tSpace;
                            
                            scaleFactors.width = 1.0f;
                            scaleFactors.height = 1.0f;
                            
                            imagePanel.getImageSize(imagePanelSize);
                            
                            lSpace = (contentPaneSize.width - imagePanelSize.width)/2;
                            tSpace = (contentPaneSize.height - imagePanelSize.height)/2;
                            if (lSpace < 0)
                            {
                                lSpace = 0;
                            }
                            if (tSpace < 0)
                            {
                                tSpace = 0;
                            }
                            
                            imagePanel.setScaleFactors(lSpace, tSpace, scaleFactors);
                        }
                        
                });
                }
            }
        })));
        viewModeActions.put(VIEW_MODE.NORMAL_SCROLLING, tmpActionListener);
        
        // TODO: define accelerator 
		fullScreen.addActionListener((tmpActionListener = new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                if (!setViewMode(VIEW_MODE.FULL_SCREEN))
                {
                    return;
                }
                
                setFullScreen(true);
                if (imagePanel != null)
                {
                    SwingUtilities.invokeLater(new Runnable() {
                        
                        @Override
                        public void run()
                        {
                            stateMachine.handleUserAction(new Runnable()
                            {
            
                                @Override
                                public void run()
                                {
                                    int lSpace,tSpace;
                                    
                                    scaleFactors.width = 1.0f;
                                    scaleFactors.height = 1.0f;
                                    
                                    imagePanel.getImageSize(imagePanelSize);
                                    
                                    lSpace = (contentPaneSize.width - imagePanelSize.width)/2;
                                    tSpace = (contentPaneSize.height - imagePanelSize.height)/2;
                                    if (lSpace < 0)
                                    {
                                        lSpace = 0;
                                    }
                                    if (tSpace < 0)
                                    {
                                        tSpace = 0;
                                    }
                                    
                                    imagePanel.setScaleFactors(lSpace, tSpace, scaleFactors);
                                }
                                
                            });
                        }
                    });
                }
            }
        }));
		viewModeActions.put(VIEW_MODE.FULL_SCREEN, tmpActionListener);
		
		// TODO: define accelerator
        stretchScreen.addActionListener((tmpActionListener = new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                if (!setViewMode(VIEW_MODE.STRETCH))
                {
                    return;
                }
                
                // stretch image to fill width and height
                if (imagePanel != null)
                {
                    stateMachine.handleUserAction(new Runnable()
                    {
    
                        @Override
                        public void run()
                        {
                            imagePanel.getImageSize(imagePanelSize);
                            scaleFactors.width = ((float)((float)contentPaneSize.width)/((float)imagePanelSize.width));
                            scaleFactors.height = ((float)((float)contentPaneSize.height)/((float)imagePanelSize.height));
                            imagePanel.setScaleFactors(0, 0, scaleFactors);
                        }
                        
                    });
                }
            }
        }));
        viewModeActions.put(VIEW_MODE.STRETCH, tmpActionListener);
        
        // TODO: define accelerator
        stretchedFitScreen.addActionListener((tmpActionListener = new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                if (!setViewMode(VIEW_MODE.STRETCHED_FIT))
                {
                    return;
                }
                
                // preserve aspect ratio scaling
                // Only guaranteed to fill one axis, but maybe not both
                if (imagePanel != null)
                {
                    stateMachine.handleUserAction(new Runnable()
                    {
    
                        @Override
                        public void run()
                        {
                            int lSpace,tSpace;
                            
                            imagePanel.getImageSize(imagePanelSize);
                            factorsThatStretchToFit(imagePanelSize.width, imagePanelSize.height, contentPaneSize.width, contentPaneSize.height, scaleFactors);
                            
                            lSpace = (int) Math.floor((((float)contentPaneSize.width) - ((float)imagePanelSize.width)*scaleFactors.width)/2.0f);
                            tSpace = (int) Math.floor((((float)contentPaneSize.height) - ((float)imagePanelSize.height)*scaleFactors.height)/2.0f);
                            
                            imagePanel.setScaleFactors(lSpace, tSpace, scaleFactors);
                        }
                        
                    });
                }
            }
        }));
        viewModeActions.put(VIEW_MODE.STRETCHED_FIT, tmpActionListener);
        
        // TODO: define accelerator
        fitScreen.addActionListener((tmpActionListener = new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                if (!setViewMode(VIEW_MODE.FIT))
                {
                    return;
                }
                
                // preserve aspect ratio scaling
                // Not guaranteed to fill any axis
                // Only guaranteed to scale down if required to make entire
                // screen visible in current viewport
                
                // To actually see this work, view a small screen on a larger screen
                if (imagePanel != null)
                {
                    stateMachine.handleUserAction(new Runnable()
                    {
    
                        @Override
                        public void run()
                        {
                            int lSpace,tSpace;
                            
                            imagePanel.getImageSize(imagePanelSize);
                            factorsThatShrinkToFitWithin(imagePanelSize.width, imagePanelSize.height, contentPaneSize.width, contentPaneSize.height, scaleFactors);
                            
                            lSpace = (int) Math.floor((((float)contentPaneSize.width) - ((float)imagePanelSize.width)*scaleFactors.width)/2.0f);
                            tSpace = (int) Math.floor((((float)contentPaneSize.height) - ((float)imagePanelSize.height)*scaleFactors.height)/2.0f);
                            
                            imagePanel.setScaleFactors(lSpace, tSpace, scaleFactors);
                        }
                        
                    });
                }
            }
        }));
        viewModeActions.put(VIEW_MODE.FIT, tmpActionListener);
		
        
        
        // trigger default view
        defaultView.actionPerformed(null);
        
        EventListenerDecorator.stateMachine = stateMachine;
	}
	
	private boolean setViewMode(VIEW_MODE newMode)
	{
	    boolean rval = false;
	    
	    if (newMode != viewMode)
	    {
    	    if (viewMode != null)
    	    {
    	        // actions to perform when leaving a mode
    	        switch (viewMode)
    	        {
                    case FIT:
                    case STRETCH:
                    case STRETCHED_FIT:
                        break;
                    case FULL_SCREEN:
                        setFullScreen(false);
                        break;
                    case NORMAL_SCROLLING:
                        disableScrolling();
                        break;
    	        }
    	    }
    	    
    	    rval = true;
    	    viewMode = newMode;
	    }
	    
	    return rval;
	}
	
	private void setFullScreen(boolean enabled)
	{
	    if (isFullScreen == enabled)
	    {
	        if (enabled)
	        {
	            Rectangle r = currentFullScreenDevice.getDefaultConfiguration().getBounds();
	            setLocation(r.x, r.y);
	            setSize(r.width, r.height);
	        }
	        return;
	    }
	    try
        {
            isChangingFullScreenLock.acquire();
        }
        catch (InterruptedException e)
        {
            LLog.e(e);
        }
	    try
	    {
    	    if (isChangingFullScreenStatus)
            {
                return;
            }
    	    isChangingFullScreenStatus = true;
	    }
	    finally {
	        isChangingFullScreenLock.release();
	    }
	    try
	    {
    	    isFullScreen = enabled;
    	    
    	    GraphicsDevice device;
    	    boolean handled = false;
    	    
    	    if (enabled)
    	    {
    	        device = ScreenSelector.selectScreen(this, 0);
    	    }
    	    else
    	    {
    	        device = currentFullScreenDevice;
    	    }
    	    
    	    if (device.isFullScreenSupported())
    	    {
    	        handled = true;
    	        
    	        if (enabled)
    	        {
    	            int width, height;
    	            Rectangle bounds = device.getDefaultConfiguration().getBounds();
    	            Dimension d = new Dimension();
    	            
    	            currentFullScreenDevice = device;
    	            
    	            imagePanel.getImageSize(d);
    	            width = Math.min(d.width, bounds.width);
    	            height = Math.min(d.height, bounds.height);
    	            if (device.isDisplayChangeSupported())
    	            {
        	            displayMode = device.getDisplayMode();
        	            DisplayMode newDisplayMode = new DisplayMode(width, height, 32, 60);
        	            device.setDisplayMode(newDisplayMode);
    	            }
    	            setSize(width, height);
    	            device.setFullScreenWindow(this);
    	        }
    	        else
    	        {
    	            if (displayMode != null)
    	            {
    	                device.setDisplayMode(displayMode);
    	            }
    	            device.setFullScreenWindow(null);
    	            displayMode = null;
    	        }
    	    }
    	    else
    	    {
    	        currentFullScreenDevice = null;
    	    }
    	    
    	    if (enabled)
            {
                setVisible(false);
                super.dispose();
                setUndecorated(true);
                if (!handled)
                {
                    setExtendedState(getExtendedState() | JFrame.MAXIMIZED_BOTH);
                }
                setVisible(true);
            }
            else
            {
                setVisible(false);
                super.dispose();
                setUndecorated(false);
                if (!handled)
                {
                    getRootPane().setWindowDecorationStyle(JRootPane.FRAME);
                    setExtendedState(getExtendedState() ^ JFrame.MAXIMIZED_BOTH);
                }
                setVisible(true);
            }
    	    
    	    if (!enabled)
    	    {
    	        setLocation(locationBeforeFullScreen);
    	        setSize(sizeBeforeFullScreen);
    	    }
	    }
	    finally {
	        isChangingFullScreenStatus = false;
	    }
	}
	
	
	@Override
	public void validate()
	{
	    int lastWidth, lastHeight;
	    
        super.validate();
        
        if (viewMode != VIEW_MODE.FULL_SCREEN)
        {
            getLocation(locationBeforeFullScreen);
            sizeBeforeFullScreen.width = getWidth();
            sizeBeforeFullScreen.height = getHeight();
        }
	    
	    lastWidth = contentPaneSize.width;
        lastHeight = contentPaneSize.height;
        getContentPane().getSize(contentPaneSize);
        
        // Was able to get height to be negative...
        if (contentPaneSize.width <= 0)
        {
            contentPaneSize.width = 1;
        }
        if (contentPaneSize.height <= 0)
        {
            contentPaneSize.height = 1;
        }
        
	    if (lastWidth != contentPaneSize.width || lastHeight != contentPaneSize.height)
	    {
	        ActionListener action = viewModeActions.get(viewMode);
	        if (action != null)
	        {
	            viewMode = null;
	            action.actionPerformed(null);
	        }
	    }
	}
    
    private void enableScrolling()
    {
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    }
    
    private void disableScrolling()
    {
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
    }
	
	public ImagePanel getImagePanel()
	{
	    return imagePanel;
	}

    public void setImagePanel(ImagePanel imagePanel)
    {
        if (this.imagePanel != null)
        {
            scrollPane.remove(this.imagePanel);
        }
        this.imagePanel = imagePanel;
        ACCESS_MODE accessMode = client.getAccessMode();
        ImagePanel panelToDecorate = null;
        if (accessMode != null)
        {
            switch(accessMode)
            {
                case FULL_CONTROL:
                    panelToDecorate = imagePanel;
                    break;
                case ALL:
                case VIEW_AND_CHAT:
                case VIEW_ONLY:
                    break;
            }
        }
        EventListenerDecorator.decorate(panelToDecorate);
        scrollPane.setViewportView(imagePanel);
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run()
            {
                ActionListener action = viewModeActions.get(viewMode);
                if (action != null)
                {
                    action.actionPerformed(null);
                }
                else
                {
                    scrollPane.invalidate();
                    scrollPane.repaint();
                }
            }
            
        });
    }
    
    @Override
	public void dispose()
	{
	    super.dispose();
		client.kill();
	}

    public String getAlias()
    {
        return alias;
    }
    
    public void setAlias(String alias)
    {
        this.alias = alias;
    }
	
}
