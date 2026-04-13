BEGIN { DeviceID = ""; Status = ""; Name = ""; }
($2 == "(temperature-indicator,") { DeviceID = substr($3, 1, length($3) - 1); }
($1 == ":Condition") { Status = $2; }
($1 == ":name") {
	Name = $2;

	if (Status == "<ERROR:")
	{
		Status = "UNKNOWN";
	}

	if (DeviceID != "") {print "MSHW;" DeviceID ";" Name ";;;" Status}
	
	DeviceID = ""; Status = ""; Name = ""; 
}