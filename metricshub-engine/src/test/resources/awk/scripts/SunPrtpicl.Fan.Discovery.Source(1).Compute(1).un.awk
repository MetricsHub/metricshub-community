BEGIN {
	DeviceID = ""; 
	WarningThreshold = "" ; 
	Speed = "" ; 
	Status = "" ; 
	SpeedPercent = "" ; 
	Switch = "" ;
	Description = "";
}
($2 == "(rpm-sensor," || $2 == "(fan,") { DeviceID = substr($3, 1, length($3) - 1); }
($1 == ":SpeedUnit") { SpeedUnit = $2 }
($1 == ":BaseUnits") { SpeedUnit = $2 }
($1 == ":Description") { Description = $2; for (i=3 ; i<=NF ; i++) { Description = Description " " $i; } }
($1 == ":LowWarningThreshold") { WarningThreshold = $2 }
($1 == ":Speed") { Speed = $2 }
($1 == ":Fan-speed") { Speed = $2 }
($1 == ":Fan-switch") { Switch = $2 }
($1 == ":State") { Status = $2 }
($1 == ":name") {

	if (Description == "")
	{
		Description = $2
	}
	
	if (Speed == "<ERROR:")
	{
		Speed = "UNKNOWN";
	}
	
	if (Status == "<ERROR:")
	{
		Status = "UNKNOWN";
	}
	
	if (Speed != "")
	{
		if (Speed ~ /^[0-9]+$/)
		{
			SpeedHex = "";
		}
		else if (substr(Speed, 1, 2) != "0x")
		{
			Speed = "UNKNOWN";
			SpeedHex = "";
		}
		else {
			    SpeedHex = Speed ;
			    Speed = "" ; 
			   }
	}
	
	if (WarningThreshold != "")
	{
		if (substr(WarningThreshold, 1, 2) != "0x")
		{
			WarningThresholdHex = "";
		}
		else
	  {
	 	  WarningThresholdHex = WarningThreshold ;
	 	  WarningThreshold = "" ; 
	  }
	}
	
	if (SpeedUnit == "%")
	{
		SpeedPercent = Speed;
		SpeedPercentHex = SpeedHex;
		Speed = "";
		SpeedHex = "" ; 
		WarningThreshold = "";
		WarningThresholdHex = "";
	}
	
	if (Status == "" && SpeedUnit != "%" && SpeedUnit != "rpm")
	{
		Status = SpeedUnit;
	}
	
	if (Switch == "OFF")
	{
		WarningThreshold = "";
		Speed = "";
	}

	if ((SpeedHex != "") || (SpeedPercentHex != "") || (Speed != "") || (Status != "") || (SpeedPercent != ""))
	{
		print "MSHW;" DeviceID ";" Description ";" WarningThreshold ";" Speed ";" SpeedPercent ";" Status ";" SpeedHex ";" SpeedPercentHex ";" WarningThresholdHex ";"
	}

	SpeedUnit = "";
	Speed = "";
	SpeedHex = "" ; 
	SpeedPercent = "";
	SpeedPercentHex = "";
	WarningThreshold = "";
	WarningThresholdHex = "";
	Status = "";
	Switch = "";
	Description = "";
}