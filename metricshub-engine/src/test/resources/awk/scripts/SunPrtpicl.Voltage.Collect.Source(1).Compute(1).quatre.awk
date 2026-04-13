BEGIN { DeviceID = ""; LowerThreshold = "" ; UpperThreshold = "" ; Voltage = ""; Exponent = ""; Name = ""; }
($2 == "(voltage-sensor,") { DeviceID = substr($3, 1, length($3) - 1); }
($1 == ":LowWarningThreshold") { LowerThreshold = $2 }
($1 == ":HighWarningThreshold") { UpperThreshold = $2 }
($1 == ":Voltage") { Voltage = $2 }
($1 == ":Exponent") { Exponent = $2 }
($1 == ":name") {
	Name = $2;
	
	if (Voltage == "<ERROR:")
	{
		Voltage = "UNKNOWN";
	}
	if (LowerThreshold == "<ERROR:")
	{
		LowerThreshold = "";
	}
	if (UpperThreshold == "<ERROR:")
	{
		UpperThreshold = "";
	}

	if (Exponent ~ /^-?[0-9]+$/)
	{
		if (Voltage != "") { Voltage = Voltage * 10 ^ Exponent; }
		if (LowerThreshold != "") { LowerThreshold = LowerThreshold * 10 ^ Exponent; }
		if (UpperThreshold != "") { UpperThreshold = UpperThreshold * 10 ^ Exponent; }
	}
	
	if (DeviceID != "") {print "MSHW;" DeviceID ";" Name ";" LowerThreshold ";" UpperThreshold ";" Voltage ";"}

	DeviceID = ""; LowerThreshold = "" ; UpperThreshold = "" ; Voltage = ""; Exponent = ""; Name = ""; 
}