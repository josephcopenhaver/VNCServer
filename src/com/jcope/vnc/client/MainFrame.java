package com.jcope.vnc.client;

import static com.jcope.debug.Debug.DEBUG;
import static com.jcope.debug.Debug.assert_;
import static com.jcope.util.Scale.factorsThatShrinkToFitWithin;
import static com.jcope.util.Scale.factorsThatStretchToFit;
import static com.jcope.vnc.shared.ScreenInfo.getVirtualScreenBounds;
import static java.awt.event.InputEvent.CTRL_DOWN_MASK;

import java.awt.Dimension;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import com.jcope.debug.LLog;
import com.jcope.ui.ImagePanel;
import com.jcope.ui.JCOptionPane;
import com.jcope.util.DimensionF;
import com.jcope.vnc.shared.AccessModes.ACCESS_MODE;
import com.jcope.vnc.shared.InputEvent;
import com.jcope.vnc.shared.InputEventInfo.INPUT_TYPE;
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
	
	private static final MainFrame[] selfRef = new MainFrame[]{null};
	
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
    private volatile boolean usingSetFullScreenWindow = false;
	private Semaphore isChangingFullScreenLock = new Semaphore(1, true);
	private boolean isFullScreen = false;
	private GraphicsDevice currentFullScreenDevice = null;
	
	private final Semaphore iconifiedSema;
	
	/**
	 * To be called once and only once
	 * @param self
	 */
	private static final void cacheSelf(MainFrame self)
	{
	    synchronized (selfRef) {
	        MainFrame oldSelf = selfRef[0];
            assert_(oldSelf == null);
            selfRef[0] = self;
        }
	}
	
	public static final MainFrame getCachedInstance()
	{
	    MainFrame rval = selfRef[0];
	    
	    if (rval == null)
	    {
    	    synchronized (selfRef) {
    	        rval = selfRef[0];
    	        assert_(rval != null);
    	    }
	    }
	    
	    return rval;
	}

	public MainFrame(final StateMachine stateMachine)
	{
	    
	    cacheSelf(this);
	    
	    iconifiedSema = stateMachine.getIconifiedSemaphore();
	    
	    addWindowListener(new WindowAdapter() {
            
	        @Override
            public void windowIconified(WindowEvent evt) {
                try
                {
                    iconifiedSema.acquire();
                }
                catch (InterruptedException e)
                {
                    LLog.e(e);
                }
            }
            
            @Override
            public void windowDeiconified(WindowEvent evt) {
                iconifiedSema.release();
            }
        });
	    
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
        
        JMenu sendSpecial = new JMenu("Send Special");
        actionMenu.add(sendSpecial);
        final JMenuItem sendSpecialCAD = new JMenuItem("Control-Alt-Delete");
        sendSpecial.add(sendSpecialCAD);
        
        
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
		
        
        // TODO: define accelerator
        sendSpecialCAD.addActionListener(new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent e)
            {
                StateMachine thisStateMachine = stateMachine;
                KeyEvent keyEvent = new KeyEvent(sendSpecialCAD, 0, 0L, KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_DOWN_MASK, KeyEvent.VK_DELETE, (char) 127);
                InputEvent eventDown = new InputEvent(INPUT_TYPE.KEY_DOWN, keyEvent);
                InputEvent eventUp = new InputEvent(INPUT_TYPE.KEY_UP, keyEvent);
                
                thisStateMachine.nts_acquireInputqueue();
                try
                {
                    thisStateMachine.nts_addInput(eventDown);
                    thisStateMachine.nts_addInput(eventUp);
                }
                finally {
                    thisStateMachine.nts_releaseInputqueue();
                }
            }
        });
        
        refreshScreen.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, CTRL_DOWN_MASK)); 
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
                String result = JCOptionPane.showInputDialog(fthis, "What would you like your alias to be?", alias);
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
		
		// preserve aspect ratio scaling
        // Only guaranteed to fill one axis, but maybe not both
        final Runnable setScreenFitStretchedRunnable = new Runnable() {

            @Override
            public void run()
            {
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
		    
		};
		
		// stretch image to fill width and height
		final Runnable setScreenStretchedRunnable = new Runnable() {

            @Override
            public void run()
            {
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
		    
		};
		
		//TODO: add a way to select between Fit-Stretch and Full-Stretch
		final Runnable setScreenScallingFullRunnable = setScreenFitStretchedRunnable; //setScreenFitStretchedRunnable;
		
		
		
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
                setScreenScallingFullRunnable.run();
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
                
                setScreenStretchedRunnable.run();
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
                
                setScreenFitStretchedRunnable.run();
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
	            Rectangle r = getVirtualScreenBounds(currentFullScreenDevice);
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
    	        if (currentFullScreenDevice == null)
    	        {
    	            device = ScreenSelector.selectScreen(this, null, 0);
    	        }
    	        else
    	        {
    	            device = currentFullScreenDevice;
    	        }
    	    }
    	    else
    	    {
    	        device = currentFullScreenDevice;
    	    }
    	    
    	    if (device.isFullScreenSupported())
    	    {
	            if (enabled)
    	        {
    	            Rectangle bounds = getVirtualScreenBounds(device);
    	            Insets insets= null;
                    
    	            currentFullScreenDevice = device;
    	            setSize(bounds.width, bounds.height);
    	            
    	            if (device.equals(GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()) && (insets = Toolkit.getDefaultToolkit().getScreenInsets(device.getDefaultConfiguration())) != null
    	                    && (insets.left != 0 || insets.top != 0 || insets.right != 0 || insets.bottom != 0))
                    {
    	                handled = true;
    	                usingSetFullScreenWindow = true;
        	            
        	            device.setFullScreenWindow(this);
                    }
    	            else
    	            {
    	                usingSetFullScreenWindow = false;
    	            }
    	        }
    	        else
    	        {
    	            handled = true;
    	            if (usingSetFullScreenWindow)
    	            {
    	                device.setFullScreenWindow(null);
    	            }
    	        }
    	    }
    	    else
    	    {
    	        currentFullScreenDevice = null;
    	    }
    	    
    	    setVisible(false);
    	    try
    	    {
    	        super.dispose();
        	    if (enabled)
                {
                    setUndecorated(true);
                    if (!handled)
                    {
                        setExtendedState(getExtendedState() | JFrame.MAXIMIZED_BOTH);
                    }
                }
                else
                {
                    setUndecorated(false);
                    setExtendedState(getExtendedState() ^ (JFrame.MAXIMIZED_BOTH | JFrame.MAXIMIZED_HORIZ | JFrame.MAXIMIZED_VERT));
                    setLocation(locationBeforeFullScreen);
                    setSize(sizeBeforeFullScreen);
                    currentFullScreenDevice = null;
                }
    	    }
    	    finally {
    	        setVisible(true);
    	    }
    	    
    	    repaint();
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
	
	public void onReconnect()
	{
	    ACCESS_MODE accessMode = client.getAccessMode();
        ImagePanel panelToDecorate = null;
        if (accessMode != null)
        {
            switch(accessMode)
            {
                case FULL_CONTROL:
                    panelToDecorate = imagePanel;
                    break;
                case CHAT_AND_VIEW:
                case VIEW_ONLY:
                    break;
                case ALL:
                    assert_(false);
                    break;
            }
        }
        EventListenerDecorator.decorate(this, panelToDecorate);
	}

    public void setImagePanel(ImagePanel imagePanel)
    {
        if (this.imagePanel != null)
        {
            scrollPane.remove(this.imagePanel);
        }
        this.imagePanel = imagePanel;
        onReconnect();
        scrollPane.setViewportView(imagePanel);
        final VIEW_MODE fViewMode = viewMode;
        final GraphicsDevice fDevice = currentFullScreenDevice;
        setViewMode(null);
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run()
            {
                ActionListener action = viewModeActions.get(fViewMode);
                if (action != null)
                {
                    currentFullScreenDevice = fDevice;
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
