BEGIN {
	cpuID = 0;
}
/physical processor has [0-9]+.* virtual processors \([0-9\- ,]+\)/ {
    gsub(",", " ");
    split($0, temporaryList, "[\(\)]");
    coreCount = split(temporaryList[2], coreArray, " ");
    for (i=1; i<=coreCount; i++)
    {
    	if (index(coreArray[i], "-") > 0)
    	{
			split(coreArray[i], rangeArray, "-");
			for (coreID=rangeArray[1]; coreID<=rangeArray[2]; coreID++)
			{
				printf("MSHW;%d;%d\n", coreID, cpuID);
			}
    	}
    	else
    	{
            printf("MSHW;%d;%d\n", coreArray[i], cpuID);
        }
    }

	cpuID++;
}
