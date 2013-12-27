package com.jcope.vnc.client;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;

import com.jcope.ui.ImagePanel;
import com.jcope.vnc.shared.StateMachine.CLIENT_EVENT;

public class MainFrame extends JFrame
{
	// Generated: serialVersionUID
	private static final long serialVersionUID = -6735839955506471961L;
	
	private final StateMachine client;
	
	private volatile ImagePanel imagePanel = null;
	private String alias = null;

	public MainFrame(final StateMachine stateMachine)
	{
	    
	    // TODO: define mneumonics
	    
	    final MainFrame fthis = this;
	    
		client = stateMachine;
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		
		// setup layout components
		
		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);
		JMenu actionMenu = new JMenu("Actions");
		menuBar.add(actionMenu);
		JMenuItem refreshScreen = new JMenuItem("Refresh");
		actionMenu.add(refreshScreen);
		JMenuItem setAlias = new JMenuItem("Set Alias");
		actionMenu.add(setAlias);
		JMenuItem clearAlias = new JMenuItem("Clear Alias");
		actionMenu.add(clearAlias);
		
		// setup control mechanisms
		
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
		
		// TODO: finish
		
	}
	
	public ImagePanel getImagePanel()
	{
	    return imagePanel;
	}

    public void setImagePanel(ImagePanel imagePanel)
    {
        if (this.imagePanel != null)
        {
            remove(this.imagePanel);
        }
        this.imagePanel = imagePanel;
        add(imagePanel);
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
