BEGIN { FS = ";" }
{ 
	shelfID = $1
	temperatureCount = split($2, temperatureIDArray, ",")
	split($3, temperatureArray, ",")
	
	for (i=1 ; i<=temperatureCount ; i++)
	{
		cIndex = index(temperatureArray[i], "C")
		if (cIndex > 0)
		{
			temperature = substr(temperatureArray[i], 1, cIndex - 1)
		}
		else
		{
			temperature = ""
		}
		
		print "MSHW;" shelfID "-" temperatureIDArray[i] ";" temperature ";;"
	}
}