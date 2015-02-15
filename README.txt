VNCServer
=========



PREREQUISITS:
============
0. apache ant already installed and executable from command line (  ant -v  )
1. If on windows then you need perl (for custom tee command)
   or you should drop all references to the tee command in the
   .bat scripts.



BUILDING SOURCE:
===============
0. set the debug opt accordingly in:
    com.jcope.debug.Debug.java
    // set to false to make faster/not printout crap
    public static final boolean DEBUG = Boolean.TRUE;
1. run ant in the workspace dir:
   ant



CONFIGURE SERVER SECURITY PROFILE:
=================================
**************************************
* The default run mode is fully open *
*  you should really set a password  *
**************************************
(windows): service setup
  (other): bash service.sh setup


  
CONFIGURE SERVER I/O:
====================
SERVER_SECURITY_POLICY=<File generated in security setup step>
SERVER_BIND_ADDRESS=<HOST_IP_OR_NAME>
SERVER_PORT=<PORT_#>
SERVER_LISTEN_BACKLOG=0
SUPPORT_CLIPBOARD_SYNCHRONIZATION=0
MIN_MONITOR_SCANNING_PERIOD=T1S
OBEY_SPEED_LIMITS=1



CONFIGURE CLIENT I/O:
====================
DEFAULT_VIEW_MODE=<com.jcope.vnc.client.VIEW_MODE>
DEFAULT_ACCESS_MODE=<com.jcope.vnc.shared.ACCESS_MODE>
REMOTE_ADDRESS=localhost
REMOTE_PORT=1987
REMOTE_DISPLAY_NUM=0
SYNCHRONIZE_CLIPBOARD=0
MONITOR_SCANNING_PERIOD=T1S



RUN VNC SERVER:
==============
*************************************
* note there is a "restart" command *
*************************************
(windows): service start
  (other): bash service.sh start



STOP VNC SERVER:
===============
(windows): service stop
  (other): bash service.sh stop



RUN VNC CLIENT:
==============
*******************************************
* Note configurables in client.properties *
*******************************************
(windows): client
  (other): bash client.sh



OTHER BUILD TARGETS:
===================
1. clean - deletes all build objects
2. compile - compiles the source code
   -Dmode=client        [compiles only client code]
   -Dmode=server        [compiles only server code]
   -Dnative_support=1   [compiles source with native support enabled]
3. cleangit - Runs clean and attempts to
   restore the git repository to a state
   that is easily commitable from the root



ABOUT -Dnative_support:
======================================
	NOT WORKING YET:
		Compile the software with
		support for natively taking over
		keystrokes/overriding global handles
		using:
		https://code.google.com/p/jnativehook/
		Last known compatible version: 1.1.4
		Drop JNativeHook.jar into "lib" dir
