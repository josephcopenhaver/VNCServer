package com.jcope.vnc.client;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import static com.jcope.debug.Debug.DEBUG;

import com.jcope.ui.ImagePanel;
import com.jcope.vnc.shared.StateMachine.CLIENT_EVENT;

enum VIEW_MODE
{
    NORMAL_SCROLLING,
    FULL_SCREEN,
    FIT,
    STRETCHED_FIT,
    STRETCH
};

// TODO: center the image in the frame, keep scroll bars on inner side of frame

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
		
        JMenuItem normalScrollingScreen = new JMenuItem("Normal");
        viewMenu.add(normalScrollingScreen);
        JMenuItem fullScreen = new JMenuItem("Full Screen");
        viewMenu.add(fullScreen);
        JMenuItem stretchScreen = new JMenuItem("Stretch");
        viewMenu.add(stretchScreen);
        JMenuItem stretchedFitScreen = new JMenuItem("Stretched Fit");
        viewMenu.add(stretchedFitScreen);
        JMenuItem fitScreen = new JMenuItem("Fit");
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
                // TODO: 
            }
        }));
		
		// TODO: define accelerator
		setAlias.addActionListener(new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // TODO: 
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
		
		
		// View Menu
		final ActionListener defaultView;
		ActionListener tmpActionListener = null;
		
		
		// TODO: define accelerator
        normalScrollingScreen.addActionListener((defaultView = (tmpActionListener = new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                if (viewMode == VIEW_MODE.NORMAL_SCROLLING)
                {
                    return;
                }
                viewMode = VIEW_MODE.NORMAL_SCROLLING;
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
                            imagePanel.setScaleFactors(1.0f, 1.0f);
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
                if (viewMode == VIEW_MODE.FULL_SCREEN)
                {
                    return;
                }
                viewMode = VIEW_MODE.FULL_SCREEN;
                // TODO: 
                // Maximize and stretch
                disableScrolling();
                if (imagePanel != null)
                {
                    stateMachine.handleUserAction(new Runnable()
                    {
    
                        @Override
                        public void run()
                        {
                            imagePanel.setScaleFactors(1.0f, 1.0f); // TODO: use full screen values
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
                if (viewMode == VIEW_MODE.STRETCH)
                {
                    return;
                }
                viewMode = VIEW_MODE.STRETCH;
                // stretch image to fill width and height
                disableScrolling();
                if (imagePanel != null)
                {
                    stateMachine.handleUserAction(new Runnable()
                    {
    
                        @Override
                        public void run()
                        {
                            float xScale, yScale;
                            Point p = new Point();
                            imagePanel.getImageSize(p);
                            xScale = ((float)((float)contentPaneSize.width)/((float)p.x));
                            yScale = ((float)((float)contentPaneSize.height)/((float)p.y));
                            imagePanel.setScaleFactors(xScale, yScale);
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
                if (viewMode == VIEW_MODE.STRETCHED_FIT)
                {
                    return;
                }
                viewMode = VIEW_MODE.STRETCHED_FIT;
                // preserve aspect ratio scaling
                // Only guaranteed to fill one axis, but maybe not both
                disableScrolling();
                if (imagePanel != null)
                {
                    stateMachine.handleUserAction(new Runnable()
                    {
    
                        @Override
                        public void run()
                        {
                            imagePanel.setScaleFactors(1.0f, 1.0f); // TODO: scale using aspect ratio cap
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
                if (viewMode == VIEW_MODE.FIT)
                {
                    return;
                }
                viewMode = VIEW_MODE.FIT;
                // preserve aspect ratio scaling
                // Not guaranteed to fill any axis
                // Only guaranteed to scale down if required to make entire
                // screen visible in current viewport
                disableScrolling();
                if (imagePanel != null)
                {
                    stateMachine.handleUserAction(new Runnable()
                    {
    
                        @Override
                        public void run()
                        {
                            imagePanel.setScaleFactors(1.0f, 1.0f); // TODO: scale down preserving aspect ratio if required
                        }
                        
                    });
                }
            }
        }));
        viewModeActions.put(VIEW_MODE.FIT, tmpActionListener);
		
        
        
		// TODO: finish
		
        
        
        // trigger default view
        defaultView.actionPerformed(null);
	}
	
	
	@Override
	public void validate()
	{
	    int lastWidth, lastHeight;
        boolean synchronizeView = false;
	    
        super.validate();
	    
	    lastWidth = contentPaneSize.width;
        lastHeight = contentPaneSize.height;
        getContentPane().getSize(contentPaneSize);
	    
	    switch (viewMode)
	    {
	        case NORMAL_SCROLLING:
	            synchronizeView = false;
	            break;
	            
	        case FIT:
            case STRETCHED_FIT:
            case FULL_SCREEN:
            case STRETCH:
                synchronizeView = true;
                break;
                
	    }
	    
	    if (synchronizeView && (lastWidth != contentPaneSize.width || lastHeight != contentPaneSize.height))
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
        scrollPane.setViewportView(imagePanel);
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run()
            {
                scrollPane.invalidate();
                scrollPane.repaint();
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
