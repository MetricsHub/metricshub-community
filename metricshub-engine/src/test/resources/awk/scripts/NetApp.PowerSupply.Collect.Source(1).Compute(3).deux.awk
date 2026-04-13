BEGIN { FS = ";" }
{ 
	shelfID = $1
	psCount = split($2, psIDArray, ",")
	failedPsList = "," $3 ","
	
	for (i=1 ; i<=psCount ; i++)
	{
		if (index(failedPsList, "," psIDArray[i] ",") > 0)
		{
			print "MSHW;" shelfID "-" psIDArray[i] ";ALARM;Failed;" 
		}
		else
		{
			print "MSHW;" shelfID "-" psIDArray[i] ";OK;;" location ";"
		}
	}
}