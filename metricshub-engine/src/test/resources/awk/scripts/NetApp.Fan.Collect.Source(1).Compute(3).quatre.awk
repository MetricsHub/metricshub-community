BEGIN { FS = ";" }
{ 
	shelfID = $1
	fanCount = split($2, fanIDArray, ",")
	failedFanList = "," $3 ","
	
	for (i=1 ; i<=fanCount ; i++)
	{
		if (index(failedFanList, "," fanIDArray[i] ",") > 0)
		{
			print "MSHW;" shelfID "-" fanIDArray[i] ";ALARM;Failed;" 
		}
		else
		{
			print "MSHW;" shelfID "-" fanIDArray[i] ";OK;;" 
		}
	}
}