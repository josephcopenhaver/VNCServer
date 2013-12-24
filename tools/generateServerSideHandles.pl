use strict;

sub camelCase($)
{
    my $s = shift;
    my $l = length($s);
    my $rval = '';
    my $doCap = 0;
    my $isFirst = 1;
    
    for (my $i=0; $i<$l; $i++)
    {
        my $uc = uc(substr($s, $i, 1));
        my $lc = lc($uc);
        if ($uc ne $lc)
        {
            if ($isFirst)
            {
                $isFirst = 0;
                $doCap = 0;
                $rval .= $uc;
            }
            elsif ($doCap)
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
import com.jcope.vnc.server.input.Handle;

public class %s extends Handle
{
    
    @Override
    public void handle(%s %s, Object[] args)
    {
        assert_(true); // TODO: remove me and finish
    }
    
}
';

my $argClassImport = 'com.jcope.vnc.server.ClientHandler';
my $argName = 'client';
my $enumList = 'SELECT_SESSION_TYPE,
		OFFER_SECURITY_TOKEN,
		SELECT_SCREEN,
		GET_SCREEN_SEGMENT,
		OFFER_INPUT,
		REQUEST_ALIAS,
		SEND_CHAT_MSG,
		ENABLE_ALIAS_MONITOR,
		ENABLE_CONNECTION_MONITOR';

my $packageRoot = 'com.jcope.vnc.server';
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
            my $content = sprintf($format, $packageRoot, $argClassImport, $c, $argType, $argType, $argName);
            #print "$content\n";
            print F $content;
            close(F);
        }
    }
}