BEGIN { FS = ";" }
{ location = $3
	shelfID = $1
	fanCount = split($2, fanIDArray, ",")
	
	for (i=1 ; i<=fanCount ; i++)
	{if (fanIDArray[i] ~ /[A-Za-z0-9]/) {
		                               print "MSHW;" shelfID "-" fanIDArray[i] ";Shelf " shelfID ";" location "-" fanIDArray[i] ";"
		                              }
	}
}