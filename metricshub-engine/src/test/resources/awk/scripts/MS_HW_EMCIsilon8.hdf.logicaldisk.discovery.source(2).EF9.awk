BEGIN {FS="\"";}
{
	print $1 $3;
}
