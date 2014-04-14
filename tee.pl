use FileHandle;
use Getopt::Long;
use Getopt::Std;
use strict;

my %opts = ();
my $lAppend = undef;
GetOptions ('append!' => \$lAppend);
getopts('a', \%opts);
my $append = $opts{'a'};

if (!defined($append))
{
	$append = 0;
}

if (defined($lAppend))
{
	$append = $lAppend;
}



if ($#ARGV != 0)
{
	die "\nRedirects stdin to a file and stdout.\n\n\tUsage: perl tee.pl [-a|--append] <file_out>"
}

my $file = $ARGV[0];

my $fh = FileHandle->new($file, ($append ? '>>' : '>')) || die;
binmode($fh);


my $b;
while (read(STDIN, $b, 1))
{
	$fh->print($b);
	print STDOUT $b;
	if ($b eq "\n")
	{
		$fh->flush();
		STDOUT->flush();
	}
}