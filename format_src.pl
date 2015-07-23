use Cwd 'abs_path';
use File::Basename;
use File::Spec;
use File::Find;
use File::Temp qw/ tempfile /;
use File::Copy qw/ copy /;
use strict;

sub path_join {
    return File::Spec->catfile(@_);
}

my $search_dir = path_join(dirname(abs_path($0)), 'src');

use constant {
    STATE_CODE => 0,
    STATE_STRING => 1,
    STATE_COMMENT => 2,
    STATE_BLOCK_COMMENT => 3,
};

sub inline_scrub_code {
    my $s = $_[0];
    $s =~ s/\t/    /g;
    $s =~ s/\Aif\(/if (/;
    $s =~ s/(?<=[^a-zA-Z0-9_])if\(/if (/g;
    return $s;
}

sub inline_scrub_comment {
    my $s = $_[0];
    $s =~ s/\t/    /g;
    return $s;
}

sub inline_scrub_string {
    return $_[0];
}

sub inline_scrub_b_comment {
    my $s = $_[0];
    $s =~ s/\t/    /g;
    return $s;
}

sub scrub_b_comment {
    return $_[0];
}

sub append2 {
    my ($a, $b, $c) = @_;
    $a->[1] = sprintf('%s%s%s', $a->[1], $b, $c);
}

sub wanted {
    $_ = $File::Find::name;
    next unless (/\.java\z/ && -f $_);
    print $_ . "\n";
    open(F, "<$_") || die($_);
    my ($srcfile, $state, $stringType, $fh, $fname) = ($_, STATE_CODE, undef, undef, undef);
    my @buffer = ();
    eval {{
        ($fh, $fname) = tempfile(UNLINK => 1);
        eval {{
            while ($_ = <F>) {
                while (!/\A[\r\n]\z/) {
                    if (STATE_CODE == $state) {
                        if (/(?:\/\/|\/\*|'|")/) {
                            if ($`) {
                                push(@buffer, [STATE_CODE, inline_scrub_code($`)]);
                            }
                            if ($& eq '"' || $& eq "'") {
                                $stringType = $&;
                                push(@buffer, [STATE_STRING, $&]);
                                $state = STATE_STRING;
                                $_ = $';
                            } elsif ($& eq '//') {
                                push(@buffer, [STATE_COMMENT, $& . inline_scrub_comment($')]);
                                undef($_);
                            } else {
                                # must be block comment
                                push(@buffer, [STATE_BLOCK_COMMENT, $&]);
                                $state = STATE_BLOCK_COMMENT;
                                $_ = $';
                            }
                        }
                        else {
                            push(@buffer, [STATE_CODE, inline_scrub_code($_)]);
                            undef($_);
                        }
                    } elsif (STATE_STRING == $state) {
                        if (/\A(.+?(?:(?<!\\)(?:\\\\)*))\Q$stringType\E/ || /\A((?:\\\\)*)\Q$stringType\E/) {
                            append2($buffer[$#buffer], inline_scrub_string($1), $stringType);
                            $state = STATE_CODE;
                            $_ = $';
                        } else {
                            die(); # should not be reachable
                            $buffer[$#buffer]->[1] .= inline_scrub_string($_);
                        }
                    } elsif (STATE_BLOCK_COMMENT == $state) {
                        if (/\A(.*?)\*\//) {
                            append2($buffer[$#buffer], inline_scrub_b_comment($1), '*/');
                            $state = STATE_CODE;
                            $_ = $';
                        } else {
                            $buffer[$#buffer]->[1] .= inline_scrub_b_comment($_);
                            undef($_);
                        }
                    }
                    last if !defined($_);
                }
                if ($state == STATE_CODE) {
                    my $endState = $_;
                    if (scalar(@buffer)) {
                        foreach (@buffer) {
                            if ($_->[0] == STATE_BLOCK_COMMENT) {
                                print $fh scrub_b_comment($_->[1]);
                            } else {
                                print $fh $_->[1];
                            }
                        }
                        @buffer = ();
                    }
                    if (defined($endState)) {
                        print $fh $endState; # forward empty lines
                    }
                } elsif (defined($_)) {
                    # forward empty lines in a non-code context
                    $buffer[$#buffer]->[1] .= $_;
                }
            }
            die unless ($state == STATE_CODE);
            $fh->flush() || die;
        }};
        if ($@) {
            $fh->close() || die($@);
            (unlink($fname) || die($@)) if (-f $fname);
            die($@);
        };
    }};
    if ($@) {
        close(F);
        die($@);
    };
    eval {{
        close(F) || die("closing $srcfile");
        copy($fname, $srcfile) || die;
    }};
    if ($@) {
        $fh->close() || die($@);
        (unlink($fname) || die($@)) if (-f $fname);
        die($@);
    };
    $fh->close() || die;
    (unlink($fname) || die) if (-f $fname);
}

print "$search_dir\n";
find(\&wanted, $search_dir);