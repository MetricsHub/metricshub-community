($1 == "PPA" && $2 == "Number") {
	deviceID = $4
	model = ""
	bandwidth = ""
	status = ""
	inboundError = 0
	outboundError = 0
	inboundOctet = 0
	outboundOctet = 0
	inboundUnicastPackets = 0
	inbountNonUnicastPackets = 0
	outboundUnicastPackets = 0
	outboundNonUnicastPackets = 0
	errorCount = 0
	macAddress = ""
}

($1 == "Description") {
	if ($3 ~ "^lan[0-9]+")
	{
		model = $4 " " $5 " " $6 " " $7
	}
	else
	{
		model = $3 " " $4 " " $5 " " $6
	}
}

($1 == "Station" && $2 == "Address" && $4 ~ /^0x[0-9a-fA-F]+$/) {
	macAddress = substr($4, 3, length($4) - 2)
}

($1 == "Speed") {
	bandwidth = $3 / 1000000
}

($1 == "Operation" && $2 == "Status") {
	status = $5
}

($1 == "Inbound" && $2 == "Errors") {
	inboundError = $4
}

($1 == "Inbound" && $2 == "Octets") {
	inboundOctet = $4
}

($1 == "Inbound" && $2 == "Unicast" && $3 == "Packets") {
	inboundUnicastPackets = $5
}

($1 == "Inbound" && $2 == "Non-Unicast" && $3 == "Packets") {
	inboundNonUnicastPackets = $5
}

($1 == "Outbound" && $2 == "Octets") {
	outboundOctet = $4
}

($1 == "Outbound" && $2 == "Unicast" && $3 == "Packets") {
	outboundUnicastPackets = $5
}

($1 == "Outbound" && $2 == "Non-Unicast" && $3 == "Packets") {
	outboundNonUnicastPackets = $5
}

($1 == "Outbound" && $2 == "Errors") {
	outboundError = $4

	inboundPackets = inboundUnicastPackets + inboundNonUnicastPackets
	outboundPackets = outboundUnicastPackets + outboundNonUnicastPackets
	printf("MSHW;%s;%s;%.0f;%s;%.0f;%.0f;%s;%.0f;%.0f;%.0f\n", deviceID, macAddress, bandwidth, status, errorCount, inboundPackets, model, outboundPackets, inboundOctet, outboundOctet)
}
