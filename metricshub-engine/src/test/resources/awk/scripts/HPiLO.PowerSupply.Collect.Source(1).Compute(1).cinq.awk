/^[0-9]+ .* Type [0-9] +\| / {
	psID = $1
	typeIndex = index($0, "Type")
	psType = substr($0, typeIndex, 6)
	psStatus = substr($0, 4, typeIndex - 4)
	while (substr(psStatus, length(psStatus), 1) == " ") { psStatus = substr(psStatus, 1, length(psStatus) - 1); }
	if (psStatus != "Not Installed")
	{
		print "MSHW;" psID ";" psType ";" psStatus ";"
	}
}
/^[0-9]+ .* [0-9]+ Watt +\| / {
	psID = $1
	typeIndex = index($0, "Watt") - 5
	psType = substr($0, typeIndex, 9)
	psStatus = substr($0, 4, typeIndex - 4)
	while (substr(psStatus, length(psStatus), 1) == " ") { psStatus = substr(psStatus, 1, length(psStatus) - 1); }
	if (psStatus != "Not Installed")
	{
		print "MSHW;" psID ";" psType ";" psStatus ";"
	}
}
/^Power Supply [0-9]/ {
	psID = $3
	psType = ""
	psStatus = $4
	for (i=5 ; i<=NF ; i++)
	{
		psStatus = psStatus " " $i
	}
	if (psStatus != "Not Installed")
	{
		print "MSHW;" psID ";" psType ";" psStatus ";"
	}
}