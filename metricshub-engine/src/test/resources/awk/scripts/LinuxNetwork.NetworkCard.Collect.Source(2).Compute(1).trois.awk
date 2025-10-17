BEGIN {
	linkSpeed = "";
	duplexMode = "";
	linkStatus = "";
	deviceID = "";
}
/^Settings for / {
	deviceID = $3;
	gsub(":", "", deviceID);
}
/^[ \t]+Speed: / {
	linkSpeed = $2;
	gsub("Mb/s", "", linkSpeed);
}
/^[ \t]+Duplex: / {
	if ($2 == "Full")
	{
		duplexMode = "Full";
	}
	else
	{
		duplexMode = "Half";
	}
}
/^[ \t]+Link detected: / {
	if ($3 == "yes")
	{
		linkStatus = "OK";
	}
	else if ($3 == "no")
	{
		linkStatus = "WARN";
	}
	else
	{
		linkStatus = "";
	}
}
END {
	if (deviceID != "")
	{
		print "MSHW;" deviceID ";" linkStatus ";" linkSpeed ";" duplexMode ";"
	}
}