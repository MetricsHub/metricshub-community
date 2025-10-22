BEGIN {FS="[;]"}
	{split($1,LunIdArray,"[|]") ;
	 split($2,LunPathCountArray,"[|]") ;
	 split($3,LunNaaIDArray,"[|]") ;
	}

END {for (MPIOID in LunIdArray)
        if ( LunIdArray[MPIOID] != "" ) {print ("MSHW;" LunIdArray[MPIOID] ";" LunPathCountArray[MPIOID] ";" "naa." LunNaaIDArray[MPIOID] ";" ) }
        }
