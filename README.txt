VNCServer
=========



PREREQUISITS:
============
***************************************************
* Note jruby is not supported, there are critical *
* issues with their popen support.                *
***************************************************
0. apache ant already installed and executable from command line (  ant -v  )
1. buildr installed (  http://buildr.apache.org/  ) and executable from command line (  buildr --version  )
2. If on windows then you need perl (for custom tee command)
   or you should drop all references to the tee command in the
   .bat scripts.



BUILDING SOURCE:
===============
0a. set the debug opt accordingly in:
    com.jcope.debug.Debug.java
    // set to false to make faster/not printout crap
    public static final boolean DEBUG = Boolean.TRUE;
0b. If you get issues around downloading dependencies,
    then set your "HTTP_PROXY" environment variable
    before building or double check your internet
    connection. Dependencies are only fetched once.
1. run buildr in the workspace dir:
   buildr



CONFIGURE SERVER:
================
**************************************
* The default run mode is fully open *
*  you should really set a password  *
**************************************
(windows): service setup
  (other): sh service.sh setup



RUN VNC SERVER:
==============
*************************************
* note there is a "restart" command *
*************************************
(windows): service start
  (other): sh service.sh start



STOP VNC SERVER:
===============
(windows): service stop
  (other): sh service.sh stop



RUN VNC CLIENT:
==============
(windows): client
  (other): sh client.sh



OTHER BUILD TARGETS:
===================
*************************************
* Note buildr automatically defines *
* the follow target's behavior, see *
* their documentation for           *
* explanation:                      *
*     clean                         *
*     compile                       *
*     resources                     *
*     build                         *
*************************************
1. cleangit - Runs clean and attempts to
   restore the git repository to a state
   that is easily commitable from the root



OPTIONAL BUILD ARGUMENTS in build.yaml:
======================================
#mode: client # OR server 
		(customize the java build for a specific application target, better for release purposes)
#native_support: true
	NOT WORKING YET:
		Compile the software with
		support for natively taking over
		keystrokes/overriding global handles
		using:
		https://code.google.com/p/jnativehook/
		Last known compatible version: 1.1.4
		Drop JNativeHook.jar into "lib" dir



PROJECT CONFIGURATION FUTURE PLANS:
===============================
1. Integrate task scripts into the project buildfile (service/client)