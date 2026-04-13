BEGIN {
	DeviceID = ""
}
/^[a-z0-9]+: flags=/ {

	if (DeviceID != "")
	{
		print "MSHW;" DeviceID ";" macAddress ";" ipAddress
		DeviceID = ""
		macAddress = ""
		ipAddress = ""
	}

	ltIndex = index($0, "<")
	if (ltIndex != 0)
	{
		gtIndex = index($0, ">")
		if (gtIndex > ltIndex)
		{
			flags = substr($0, ltIndex + 1, gtIndex - ltIndex - 1)
			
			if (index(flags, "LOOPBACK") == 0 && index(flags, "OFFLINE") == 0 && index(flags, "UP") > 0)
			{
				DeviceID = substr($1, 1, length($1) - 1)
			}
		}
	}
}
/^[ \t]+inet [0-9]/ {
	ipAddress = $2
}
/^[ \t]+ether [0-9A-Za-z]/ {
	macAddress = $2
}
END {
	if (DeviceID != "")
	{
		print "MSHW;" DeviceID ";" macAddress ";" ipAddress
	}
}