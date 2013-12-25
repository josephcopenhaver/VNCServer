use strict;

sub camelCase($)
{
    my $s = shift;
    my $l = length($s);
    my $rval = '';
    my $doCap = 1;
    
    for (my $i=0; $i<$l; $i++)
    {
        my $uc = uc(substr($s, $i, 1));
        my $lc = lc($uc);
        if ($uc ne $lc)
        {
            if ($doCap)
            {
                $doCap = 0;
                $rval .= $uc;
            }
            else
            {
                $rval .= $lc;
            }
        }
        elsif ($lc eq '_')
        {
            $doCap = 1;
        }
    }
    
    return $rval;
}

my $format = 'package %s.input.handle;

import static com.jcope.debug.Debug.assert_;

import %s;
import com.jcope.vnc.client.input.Handle;

public class %s extends Handle
{
    
    @Override
    public void handle(%s %s, Object[] args)
    {
        assert_(true); // TODO: remove me and finish
    }
    
}
';

my $argClassImport = 'com.jcope.vnc.client.StateMachine';
my $argName = 'stateMachine';
my $enumList = 'AUTHORIZATION_UPDATE, // Response to client event OFFER_SECURITY_TOKEN
	    NUM_SCREENS_CHANGED,
		CURSOR_GONE,
		CURSOR_MOVE,
		SCREEN_SEGMENT_SIZE_UPDATE,
		SCREEN_SEGMENT_UPDATE, // Response to client event GET_SCREEN_SEGMENT
		SCREEN_SEGMENT_CHANGED,
		SCREEN_RESIZED,
		SCREEN_GONE,
		CHAT_MSG_TO_ALL, // includes from and text message
		CHAT_MSG_TO_USER, // for debug purposes, should assert only the target alias
		ALIAS_REGISTERED,
		ALIAS_UNREGISTERED,
		ALIAS_DISCONNECTED,
		ALIAS_CHANGED,
		CONNECTION_ESTABLISHED, // server socket was bound
		FAILED_AUTHORIZATION, // a user failed to log in
		CONNECTION_CLOSED;';

my $packageRoot = 'com.jcope.vnc.client';
my $srcDir = '../src';

chdir($srcDir) || die;

my $argType = $argClassImport;
$argType =~ s/^.*?([^\.]+)$/\1/;
my $dstDir = $packageRoot . ".input.handle";
$dstDir =~ s/\./\//g;

foreach (split(/\r?\n/, $enumList))
{
    if (/[a-zA-Z_]+/)
    {
        my $e = $&;
        my $c = camelCase($e);
        my $dstPath = sprintf("%s/%s.java", $dstDir, $c);
        if (! -f $dstPath)
        {
            #print "$e\n";
            print "$dstPath\n";
            open(F, ">$dstPath") || die;
            my $content = sprintf($format, $packageRoot, $argClassImport, $c, $argType, $argName);
            #print "$content\n";
            print F $content;
            close(F);
        }
    }
}