BEGIN {
	hardwarePath = "";
	productID = "";
	vendor = "";
	serialNumber = "";
	size = "";
	controllerPath = "";
}
/^Hardware [Pp]ath:/ {
	hardwarePath = $3;
}
/^Product Id:/ {
	productID = $3;
	vendor = $5;
}
/^Serial [Nn]umber:/ {
	serialNumber = $3;
}
/^Capacity \(M Byte\): +[0-9\.]+/ {
	size = $4;
}
/^ *Verify Errors: +[0-9]+/ {
	
	if (hardwarePath != "" && productID != "" && size > 0)
	{
		n = split(hardwarePath, controllerPathArray, "[\.]");
		controllerPath = controllerPathArray[1];
		for (i=2 ; i<=n ; i++)
		{
			print "MSHW;" hardwarePath ";" controllerPath ";" vendor " " productID ";" size ";" serialNumber ";";
			controllerPath = controllerPath "." controllerPathArray[i];
		}
		hardwarePath = "";
		productID = "";
		vendor = "";
		serialNumber = "";
		size = "";
		controllerPath = "";
	}
}