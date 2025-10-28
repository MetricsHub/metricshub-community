BEGIN { DeviceID = ""; WarningThreshold = "" ; Temperature = ""; Name = ""; }
($2 == "(temperature-sensor,") { DeviceID = substr($3, 1, length($3) - 1); }
($1 == ":HighWarningThreshold") { WarningThreshold = $2 }
($1 == ":Temperature") { Temperature = $2 }
($1 == ":Exponent") { Exponent = $2 }
($1 == ":name") {
	Name = $2;

	if (Temperature == "<ERROR:")
	{
		Temperature = "UNKNOWN";
	}
	
	if (WarningThreshold == "<ERROR:")
	{
		WarningThreshold = "";
	}
	if (Exponent ~ /^-?[0-9]+$/)
	{
		if (Temperature ~ /^-?[0-9]+$/) { Temperature = Temperature * 10 ^ Exponent; }
		if (LowerThreshold ~ /^-?[0-9]+$/) { WarningThreshold = WarningThreshold * 10 ^ Exponent; }
	}
	
	if (DeviceID != "") {print "MSHW;" DeviceID ";" Name ";" WarningThreshold ";" Temperature ";"}
	
	DeviceID = ""; WarningThreshold = "" ; Temperature = ""; Name = ""; 
}