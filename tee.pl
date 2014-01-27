use FileHandle;
use Getopt::Long;
use Getopt::Std;
use IO::Socket::INET;
use IO::Select;
use feature 'state';
use threads;
use threads::shared;
use IO::Handle;

STDOUT->autoflush(1);


my %SELECT_INFO = ();
initIOSelect(2);


my %opts = ();
getopts('a', \%opts);
my $append = $opts{'a'};
my $lAppend = undef;

if (!defined($append))
{
	$append = 0;
}

GetOptions ('append!' => \$lAppend);
if (defined($lAppend))
{
	$append = $lAppend;
}



if ($#ARGV != 0)
{
	die "Usage: perl tee.pl [-a|--append] <file_out>"
}

my $file = $ARGV[0];

my $fh = FileHandle->new($file, ($append ? '>>' : '>')) || die;
binmode($fh);
my $stdIn = newSelIn(STDIN) || die;
my $stdErr = newSelIn(STDERR) || die;
my $io = IO::Select->new();
$io->add($stdIn);
$io->add($stdErr);
my @canRead = ();
my ($curFH, $nin, $nout);

if (!selEOF($stdIn))
{
	while (1)
	{
		while (@canRead = $io->can_read(.2))
		{
			foreach (@canRead)
			{
				$curFH = $_;
				$_ = undef;
				$nin = numCanRead($curFH);
				while ($nin > 0)
				{
					$nout = read($curFH, $_, $nin);
					$fh->print($_);
					print $_;
					$nin = numCanRead($curFH);
				}
			}
			if (selEOF($stdIn))
			{
				last;
			}
		}
		if (selEOF($stdIn))
		{
			last;
		}
	}
}
close(STDOUT);
close(STDERR);
# End of Script



sub numCanRead($)
{
	state $FIONREAD = 0x4004667f;
	our $numbytes = pack('L', 0);
	
	ioctl($_[0], $FIONREAD, unpack('I', pack('P', $numbytes)));
	my $rval = unpack('I', $numbytes);
	
	return $rval;
}

sub initIOSelect($)
{
	!$SELECT_INFO{'initialized'} || die;
	$SELECT_INFO{'initialized'} = 1;
	
	if ($^O ne 'MSWin32')
	{
		return;
	}
	$SELECT_INFO{'isWindows'} = 1;
	
	my $serverSocket = IO::Socket::INET->new(
		LocalHost => '127.0.0.1',
		LocalPort => '0',
		Proto => 'tcp',
		Listen => $_[0],
		Reuse => 1
	) or die "ERROR in ServerSocket Creation : $!\n";
	my $canAccept = 1;
	share($canAccept);
	
	my $listenPort = (sockaddr_in(getsockname($serverSocket)))[0];
	
	my %receivingSockets = ();
	share(%receivingSockets);
	$SELECT_INFO{'receivingSockets'} = \%receivingSockets;
	
	my %threadsForSocket = ();
	$SELECT_INFO{'threadsForSocket'} = \%threadsForSocket;
	
	my %notEOFs = ();
	$SELECT_INFO{'notEOFs'} = \%notEOFs;
	
	$SELECT_INFO{'serverHandle'} = sub
	{
		my $fh = $_[0];
		my $signal = undef;
		share($signal);
		lock($signal);
		my $thr = threads->new(sub {
			my $socketOut = undef;
			my $socketFileNo = undef;
			{
				lock(%receivingSockets);
				eval{{
					defined($fh) || die;
					cond_broadcast($signal);
					$socketOut = IO::Socket::INET->new(
						PeerHost => '127.0.0.1',
						PeerPort => $listenPort,
						Proto => 'tcp',
					) or die "ERROR in Socket Creation : $!\n";
					$socketFileNo = fileno($socketOut);
					binmode($socketOut);
					$receivingSockets{$socketFileNo} = 1;
				}};
				if ($@)
				{
					$canAccept = 0;
					die $@;
				}
				cond_wait(%receivingSockets);
			}
			my $b;
			eval{{
				while (read($fh, $b, 1))
				{
					$socketOut->send($b);
				}
			}};
			delete $receivingSockets{$socketFileNo};
			die $@ if $@;
			
			$socketOut->flush;
			read($socketOut, $b, 1);
		}) || die;
		cond_wait($signal);
		$canAccept || die;
		my $socketIn = $serverSocket->accept() || die;
		binmode($socketIn);
		my $nonblocking = 1;
		cond_broadcast(%receivingSockets);
		$threadsForSocket{$socketIn} = $thr;
		$notEOFs{$socketIn} = 1;
		
		return $socketIn;
	};
}

sub newSelIn($)
{
	state $isWindows = $SELECT_INFO{'isWindows'};
	
	if (!$isWindows)
	{
		return $_[0];
	}
	
	state $handle = $SELECT_INFO{'serverHandle'};
	
	return $handle->(@_);
}

sub selEOF($)
{
	state $isWindows = $SELECT_INFO{'isWindows'};
	
	if (!$isWindows)
	{
		return;
	}
	
	state $notEOFs = $SELECT_INFO{'notEOFs'};
	state $readSet = IO::Select->new();
	state $threadsForSocket = $SELECT_INFO{'threadsForSocket'};
	state $receivingSockets = $SELECT_INFO{'receivingSockets'};
	
	my $fh = $_[0];
	my $rval = 0;
	if (!$notEOFs->{$fh})
	{
		$rval = 1;
	}
	else
	{
		my $selNotReceiving = !($receivingSockets->{fileno($fh)});
		my $canReadNothing = 0;
		if ($selNotReceiving)
		{
			my @read = ();
			$canReadNothing = (numCanRead($fh) == 0);
			if ($canReadNothing)
			{
				$readSet->add($fh);
				eval{{
					(@read) = IO::Select->select($readSet, undef, undef, .2);
					@read = @{$read[0]};
					$canReadNothing = ($#read == -1);
				}};
				my $eCode = $@;
				eval{{
					$readSet->remove($fh);
				}};
				$eCode = $ecode || $@;
				die $eCode if $eCode;
			}
		}
		if ($selNotReceiving && $canReadNothing)
		{
			my $thr = $threadsForSocket->{$fh};
			delete $notEOFs->{$fh};
			delete $threadsForSocket->{$fh};
			$fh->send('x');
			$fh->close();
			$thr->join();
			$rval = 1;
		}
	}
	
	return $rval;
}
