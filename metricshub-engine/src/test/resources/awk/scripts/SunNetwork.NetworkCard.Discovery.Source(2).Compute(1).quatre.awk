/driver name:/ {
	instance = substr($3, 2, length($3) - 1);
	driver = substr($NF, 1, length($NF) - 1);
	if (driver != "lo" && driver != "lpfc" && driver != "jnet" && driver != "dman" && driver != "aggr" && driver != "clprivnet" && driver != "sppp")
	{
		print "MSHW;" driver instance ";" driver
	}
}
/link:.*speed:.*duplex:/ {
	deviceid = $1;
	driver = deviceid;
	instance = "";
	while (substr(driver, length(driver), 1) ~ /^[0-9]$/ && length(driver) > 1)
	{
		instance = substr(driver, length(driver), 1) instance;
		driver = substr(driver, 1, length(driver) - 1);
	}
	if (driver != "lo" && driver != "lpfc" && driver != "jnet" && driver != "dman" && driver != "aggr" && driver != "clprivnet" && driver != "sppp")
	{
		print "MSHW;" driver instance ";" driver
	}	
}
/: flags=/ {
	deviceid = substr($1, 1, length($1) - 1);
	driver = deviceid;
	instance = "";
	while (substr(driver, length(driver), 1) ~ /^[0-9]$/ && length(driver) > 1)
	{
		instance = substr(driver, length(driver), 1) instance;
		driver = substr(driver, 1, length(driver) - 1);
	}
	if (driver != "lo" && driver != "lpfc" && driver != "jnet" && driver != "dman" && driver != "aggr" && driver != "clprivnet" && driver != "sppp")
	{
		print "MSHW;" driver instance ";" driver
	}
}