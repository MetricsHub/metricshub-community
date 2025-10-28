BEGIN {
	errorCount = 0;
	lastErrorDescription = "";
	lastTimestamp = "";
}
($5 == "sysplanar0") {
	errorCount++;
	lastErrorDescription = $6;
	for (i=7 ; i<=NF ; i++)
	{
		lastErrorDescription = lastErrorDescription " " $i;
	}
	lastTimestamp = "20" substr($2, 9, 2) "-" substr($2, 1, 2) "-" substr($2, 3, 2) " " substr($2, 5, 2) ":" substr($2, 7, 2)
}
END {
	if (errorCount == 0)
	{
		print "MSHW;OK;No error on sysplanar0 as of today";
	}
	else if (errorCount == 1)
	{
		print "MSHW;ALARM;" lastErrorDescription " on " lastTimestamp;
	}
	else
	{
		print "MSHW;ALARM;" errorCount " errors in errpt. Last error: " lastErrorDescription " on " lastTimestamp;
	} # end else
}
