BEGIN {
	deviceID = "";
	linkStatus = "";
	linkDuplex = "";
	linkSpeed = "";
	receivedPackets = "";
	transmittedPackets = "";
	inputErrors = 0;
	outputErrors = 0;
	collisions = 0;
	receivedBytes = "";
	transmittedBytes = "";
}
($1 == "DeviceID") { deviceID = $2; }
(($1 == "link_up" || $1 == "link_status") && linkStatus != "OK") { if ($2 == 1) { linkStatus = "OK"; } else { linkStatus = "WARN"; } }
($1 == "ifspeed" || $1 == "ifSpeed" && linkSpeed == "") { linkSpeed = $2; }
($1 == "link_speed") { linkSpeed = $2; }
($1 == "duplex") { if ($2 == "full") { linkDuplex = "FULL"; } else { linkDuplex = "HALF"; } }
($1 == "link_mode") { if ($2 == 1) { linkDuplex = "FULL"; } else { linkDuplex = "HALF"; } }
($1 == "link_duplex") { if ($2 == 2) { linkDuplex = "FULL"; } else { linkDuplex = "HALF"; } }
($1 == "ipackets64") { receivedPackets = $2; }
($1 == "ipackets" && receivedPackets == "") { receivedPackets = $2; }
($1 == "opackets64") { transmittedPackets = $2; }
($1 == "opackets" && transmittedPackets == "") { transmittedPackets = $2; }
($1 == "ierrors") { inputErrors = $2; }
($1 == "oerrors") { outputErrors = $2; }
($1 == "collisions") { collisions = $2; }
($1 == "rbytes64") { receivedBytes = $2; }
($1 == "rbytes" && receivedBytes == "") { receivedBytes = $2; }
($1 == "obytes64") { transmittedBytes = $2; }
($1 == "obytes" && transmittedBytes == "") { transmittedBytes = $2; }


# Solaris 10 Aggregate Section
#
($1 == deviceID) && ($2 ~ /[0-9]+/) && ($3 ~ /[0-9]+/) && ($4 ~ /[0-9]+/) && ($5 ~ /[0-9]+/) && ($6 ~ /[0-9]+/) && ($7 ~ /[0-9]+/) {
 	print deviceID
	receivedPackets = $2
	receivedBytes = $3
	inputErrors = $4
	transmittedPackets = $5
	transmittedBytes = $6
	outputErrors = $7
	}
($1 == deviceID) && ($2 ~ /link=/) && ($3 ~ /speed=/) && ($4 ~ /duplex=/) {
	if ($2 ~ /up/) {linkStatus = "OK"} else { linkStatus = "WARN"; }
  linkSpeed = $3 ; gsub ("speed=","",linkSpeed)
  if ($4 ~ /full/) { linkDuplex = "FULL"; } else { linkDuplex = "HALF"; }
}

END {
	if (linkSpeed == 0) 				{ linkSpeed = 10; }
	else if (linkSpeed == 1)			{ linkSpeed = 100; }
	else if (linkSpeed == 10000000)		{ linkSpeed = 10; }
	else if (linkSpeed == 100000000)	{ linkSpeed = 100; }
	else if (linkSpeed == 1000000000)	{ linkSpeed = 1000; }
	else if (linkSpeed == "10000000000")	{ linkSpeed = 10000; }

	errors = inputErrors + outputErrors + collisions;

	print "MSHW;" deviceID ";" linkStatus ";" linkSpeed ";" linkDuplex ";" receivedPackets ";" transmittedPackets ";" errors ";" receivedBytes ";" transmittedBytes ";"
}
