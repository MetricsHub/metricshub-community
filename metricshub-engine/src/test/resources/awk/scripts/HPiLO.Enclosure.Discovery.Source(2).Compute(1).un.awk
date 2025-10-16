BEGIN {
	powerUsagePresent = "";
}
/^System Power usage:.*Watts/ {
	powerUsagePresent = 2
}
END {
	print "MSHW;HP_MP_GSP;9000/Integrity;;" powerUsagePresent
}