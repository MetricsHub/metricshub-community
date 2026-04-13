{
	if ($0 ~ /Current Drive Temperature: *[0-9]* C$/)
	{
		print "MSHW;" $4
		exit;
	}
	
	if ($1 == "194" && $10 ~ /[0-9]+/)
	{
	  print "MSHW;" $10
	  exit;
	}
}