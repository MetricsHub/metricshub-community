BEGIN { FS = ";" }
{ location = $5
	shelfID = $1
	temperatureCount = split($2, temperatureIDArray, ",")
	split($3, alarmThresholdArray, ",")
	split($4, warningThresholdArray, ",")
	
	for (i=1 ; i<=temperatureCount ; i++)
	{
		cIndex = index(warningThresholdArray[i], "C")
		if (cIndex > 0)
		{
			warningThreshold = substr(warningThresholdArray[i], 1, cIndex - 1)
		}
		else
		{
			warningThreshold = ""
		}
		
		cIndex = index(alarmThresholdArray[i], "C")
		if (cIndex > 0)
		{
			alarmThreshold = substr(alarmThresholdArray[i], 1, cIndex - 1)
		}
		else
		{
			alarmThreshold = ""
		}
		
		print "MSHW;" shelfID "-" temperatureIDArray[i] ";" shelfID ";" warningThreshold ";" alarmThreshold ";;" location ";"
	}
}