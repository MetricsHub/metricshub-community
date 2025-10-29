BEGIN { FS = ";" }
{ location = $4
	shelfID = $1
	psCount = split($2, psIDArray, ",")
	split($3, psSerialNumberArray, ",")
	for (i=1 ; i<=psCount ; i++)
	{if (psIDArray[i] ~ /[A-Za-z0-9]/) {
		                               print "MSHW;" shelfID "-" psIDArray[i] ";" psSerialNumberArray[i] ";" location "-" psIDArray[i] ";"
		                              }	
	}
}