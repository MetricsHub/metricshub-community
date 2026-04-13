BEGIN {
	FS = ";"
}
($2 ~ /^[a-zA-Z0-9]+$/) {
	DeviceID = $2
	driver = DeviceID
	while (substr(driver, length(driver), 1) ~ /[0-9]/)
	{
		driver = substr(driver, 1, length(driver) - 1)
	}
	
	if (driver != "lo" && driver != "lpfc" && driver != "jnet" && driver != "dman" && driver != "aggr" && driver != "clprivnet" && driver != "sppp")
	{
		print "MSHW;" DeviceID ";" driver ";" $3 ";" $4 ";" $5
	}
}