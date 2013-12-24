package com.jcope.vnc.client;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

import com.jcope.vnc.shared.StateMachine.CLIENT_EVENT;

public class MainFrame extends JFrame
{
	// Generated: serialVersionUID
	private static final long serialVersionUID = -6735839955506471961L;
	
	private final StateMachine client;

	public MainFrame(final StateMachine stateMachine)
	{
		client = stateMachine;
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		
		// setup layout components
		
		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);
		JMenu actionMenu = new JMenu("Actions");
		menuBar.add(actionMenu);
		JMenuItem refreshScreen = new JMenuItem("Refresh");
		actionMenu.add(refreshScreen);
		
		// setup control mechanisms
		
		refreshScreen.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK)); 
		refreshScreen.addActionListener(new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                client.sendEvent(CLIENT_EVENT.GET_SCREEN_SEGMENT, Integer.valueOf(-1));
            }
        });
		
		// TODO: finish
		
	}
	
	@Override
	public void dispose()
	{
		super.dispose();
		client.kill();
	}
	
}
