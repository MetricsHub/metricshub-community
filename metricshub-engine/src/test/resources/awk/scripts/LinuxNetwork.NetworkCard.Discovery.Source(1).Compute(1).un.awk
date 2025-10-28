BEGIN {
	deviceID = ""
}
/^eth[0-9][0-9]* |^vmnic[0-9][0-9]* / {
	deviceID = $1
	if ($(NF-1) == "HWaddr")
	{
		macAddress = $NF
	}
}
/ +inet addr:[0-9]+/ {
	ipAddress = $2
	gsub("addr:", "", ipAddress)
}
/ UP / {
	if (deviceID != "")
	{
		print "MSHW;" deviceID ";" macAddress ";" ipAddress ";"
	}
	deviceID = "";
	macAddress = "";
	ipAddress = "";
}