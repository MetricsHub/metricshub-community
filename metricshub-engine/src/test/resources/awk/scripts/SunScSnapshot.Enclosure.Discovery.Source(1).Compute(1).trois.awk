BEGIN { FS=","; }
/^SUNW,/ {
	print "MSHW;" $2
	exit
}