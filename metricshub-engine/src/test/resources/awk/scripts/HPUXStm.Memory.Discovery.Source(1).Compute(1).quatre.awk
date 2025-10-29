BEGIN {
	prefixLetter = "";
	cabCell = "";
	matrixFormat = 0;
}
/^ *Memory [Bb]oard [Ii]nventory *$/,/^ *Memory [Ee]rror [Ll]og [Ss]ummary *$/ {
	if ($0 ~ /Total/)
	{
		next;
	}
	
	if ($0 ~ "^ *CAB/CELL:")
	{
		cabCell = $2;
		matrixFormat = 1;
		split("A B C D E F G H", slotLabel);
	}
	
	if ($0 ~ /Note: The first letter in all DIMM slots start with a/)
	{
		split($0, prefixArray, "\"");
		prefixLetter = prefixArray[2];
	}
	
	if ($0 ~ /Configured and Slot label for each DIMM/)
	{
		getline
		getline
		split($0, slotLabel);
		matrixFormat = 1;
	}

	if (matrixFormat)
	{
		if ($0 ~ /^ *[0-9A-Za-z]+ +[0-9]+ +[0-9]+/)
		{
			if (tolower($1) == "ext")
			{
				bank = $1 " " $2 " DIMM ";
				startAt = 3;
			}
			else
			{
				bank = $1;
				startAt = 2;
			}
			
			if (cabCell != "")
			{
				bank = cabCell " " bank;
			}
			
			if (prefixLetter != "")
			{
				bank = bank "/" prefixLetter;
			}
			
			for (i=startAt ; i<=NF ; i++)
			{
				if ($i == 64 || $i == 128 || $i == 256 || $i == 512 || $i == 1024 || $i == 2048 || $i == 4096)
				{
					print "MSHW;" bank slotLabel[(i - startAt + 1)] ";" $i ";"
				}
			}
		}
	}
	else
	{
		lastMBColumn = 0;
		for (i=2 ; i<=NF ; i++)
		{
			if ($i == 64 || $i == 128 || $i == 256 || $i == 512 || $i == 1024 || $i == 2048 || $i == 4096)
			{
				result = "MSHW;"
				for (j=lastMBColumn + 1 ; j<i-1 ; j++)
				{
					result = result $j " "
				}
				result = result $(i-1) ";" $i ";"
				print result
				lastMBColumn = i;
			}
		}
	}
		
}
END {
	print "MSHW;PDT;;Page Deallocation Table"
}