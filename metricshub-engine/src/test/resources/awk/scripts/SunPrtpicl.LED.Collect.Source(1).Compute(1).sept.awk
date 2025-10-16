BEGIN { DeviceID = ""; Status = ""; Name = ""; Color = ""; Description = ""; Status = ""; }
($2 == "(led,") { DeviceID = substr($3, 1, length($3) - 1); }
($1 == ":Color" || $1 == ":ModelName") { Color = $2 }
($1 == ":Description") { Description = $2; for (i=3 ; i<=NF ; i++) { Description = Description " " $i; } }
($1 == ":State") { Status = $2; }
($1 == ":name") {
	Name = $2;

	if (Color == "<ERROR:")
	{
		Color = "";
	}
	if (Status == "<ERROR:")
	{
		Status = "UNKNOWN";
	}

	if (Description != "")
	{
		Name = Description;
	}
	if (Status != "" && DeviceID != "")
	{
		print "MSHW;" DeviceID ";" Name ";" Color ";" Status;
	}
	
	DeviceID = ""; Status = ""; Name = ""; Color = ""; Description = ""; Status = ""; 
}