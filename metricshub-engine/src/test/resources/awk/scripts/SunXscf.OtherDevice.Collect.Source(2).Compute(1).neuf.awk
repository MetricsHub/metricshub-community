BEGIN {
	XSCFUList = ";";
	IOUList = "";
}
($2 ~ /^IOU#[0-9]+$/) {
	DeviceID = $1;
	status = $4;
	SerialNumber = $8;
	getline;
	FruPartNumber = $3 " " $4 " " $5;
	IOUList = IOUList "§MSHW;I/O Unit;" DeviceID ";Main;;" SerialNumber ";" FruPartNumber ";" status ";Power:"
}
($1 ~ "^IOU#[0-9]+/PCI#[0-9]+$" && $NF != "DownLink") {
	IOUList = IOUList "W";
}
($2 ~ /^XSCFU$/ || $2 ~ /^XSCFU_[A-Z]#[0-9]$/) {
	DeviceID = $1;
	if (index(XSCFUList, ";" DeviceID ";") > 0)
	{
		DeviceID = DeviceID "b";
	}
	else
	{
		XSCFUList = XSCFUList DeviceID ";";
	}
	status = $4;
	if (index(status, ",") > 1) { status = substr(status, 1, index(status, ",") - 1); }
	SerialNumber = $8;
	getline;
	FruPartNumber = $3 " " $4 " " $5;
	print "MSHW;XSCF Unit;" DeviceID ";Main;;" SerialNumber ";" FruPartNumber ";" status ";30"
}
($2 ~ /^OPNL$/ || $2 ~ /^OPNL#[0-9]+$/) {
	DeviceID = $1;
	status = $4;
	SerialNumber = $8;
	getline;
	FruPartNumber = $3 " " $4 " " $5;
	print "MSHW;Operator Panel;" DeviceID ";Main;;" SerialNumber ";" FruPartNumber ";" status ";2"
}
($2 ~ /^CMU#[0-9]+$/) {
	DeviceID = $1;
	status = $4;
	SerialNumber = $8;
	getline;
	FruPartNumber = $3 " " $4 " " $5;
	print "MSHW;CPU/Memory Board Unit;" DeviceID ";Main;;" SerialNumber ";" FruPartNumber ";" status ";80"
}
($2 ~ /^MEMB#[0-9]+$/) {
	DeviceID = $1;
	status = $4;
	SerialNumber = $8;
	getline;
	FruPartNumber = $3 " " $4 " " $5;
	print "MSHW;Memory Board;" DeviceID ";Main;" DeviceID ";" SerialNumber ";" FruPartNumber ";" status ";50"
}
($2 ~ /^MBU_[A-Z0-9]$/) {
	DeviceID = $1;
	status = $4;
	SerialNumber = $8;
	getline;
	FruPartNumber = $3 " " $4 " " $5;
	print "MSHW;Motherboard Unit;" DeviceID ";Main;;" SerialNumber ";" FruPartNumber ";" status ";80"
}
($2 ~ /BP_[A-Z]#[0-9]$/ || $2 ~ /BP#[0-9]+$/) {
	DeviceID = $1;
	status = $4;
	SerialNumber = $8;
	getline;
	FruPartNumber = $3 " " $4 " " $5;
	print "MSHW;Backplane;" DeviceID ";Main;;" SerialNumber ";" FruPartNumber ";" status ";25"
}
($2 ~ /^DDC_[A-Z]#[0-9]+$/) {
	DeviceID = $1;
	status = $4;
	SerialNumber = $8;
	print "MSHW;DC-to-DC Converter;" DeviceID ";Main;" DeviceID ";" SerialNumber ";;" status ";5"
}
($2 ~ /^DDCR$/) {
	DeviceID = $1;
	status = $4;
	SerialNumber = $8;
	print "MSHW;DC-to-DC Converter;" DeviceID ";Main;" DeviceID ";" SerialNumber ";;" status ";5"
}
($NF == "DownLink" && $3 == "Status") {
	DeviceID = $1;
	status = $4;
	getline;
	SerialNumber = $5;
	getline;
	Connection = $3;
	getline;
	FruPartNumber = $3 " " $4 " " $5;
	print "MSHW;DownLink Card;" DeviceID ";Main;to " Connection ";" SerialNumber ";" FruPartNumber ";" status ";35"
}
($2 ~ /^IOB[0-9]+$/ && $3 == "Status") {
	DeviceID = $1;
	ShortName = $2;
	status = $4;
	SerialNumber = $6;
	getline;
	FruPartNumber = $3 " " $4 " " $5
	if (index(DeviceID, "/") > 0)
	{
		parentID = substr(DeviceID, 1, index(DeviceID, "/") - 1);
	}
	else
	{
		parentID = "Main";
	}
	IOBList = IOBList "§MSHW;I/O Board;" DeviceID ";" parentID ";" ShortName ";" SerialNumber ";" FruPartNumber ";" status ";Power:"
}
($1 ~ "^IOX@[A-Z0-9]+/IOB[0-9]+/SLOT[0-9]+$") {
	IOBList = IOBList "w";
}
($2 == "LINK" && $3 == "Status") {
	DeviceID = $1;
	status = $4;
	SerialNumber = $8;
	getline;
	FruPartNumber = $3 " " $4 " " $5;
	split(DeviceID, PathArray, "/");
	parentID = PathArray[1];
	ShortName = PathArray[2];
	print "MSHW;UpLink Card;" DeviceID ";" parentID ";" ShortName ";" SerialNumber ";" FruPartNumber ";" status ";35"
}
($2 ~ /^XBU_[A-Z]#[0-9]+$/) {
	DeviceID = $1;
	status = $4;
	SerialNumber = $8;
	getline;
	FruPartNumber = $3 " " $4 " " $5;
	print "MSHW;Crossbar Unit;" DeviceID ";Main;;" SerialNumber ";" FruPartNumber ";" status ";80"
}
($2 ~ /^CLKU_[A-Z]#[0-9]+$/) {
	DeviceID = $1;
	status = $4;
	SerialNumber = $8;
	getline;
	FruPartNumber = $3 " " $4 " " $5;
	print "MSHW;Clock Control Unit;" DeviceID ";Main;;" SerialNumber ";" FruPartNumber ";" status ";4"
}
END {
	IOUCount = split(IOUList, IOUArray, "§");
	for (i=2; i<=IOUCount; i++)
	{
		powerIndex = index(IOUArray[i], ";Power:");
		PowerConsumption = (length(IOUArray[i]) - powerIndex - 6) * 20;
		if (PowerConsumption < 0) { PowerConsumption = 20; }
		PowerConsumption = PowerConsumption + 40;
		print substr(IOUArray[i], 1, powerIndex) PowerConsumption
	}
	IOBCount = split(IOBList, IOBArray, "§");
	for (i=2; i<=IOBCount; i++)
	{
		powerIndex = index(IOBArray[i], ";Power:");
		PowerConsumption = (length(IOBArray[i]) - powerIndex - 6) * 20;
		if (PowerConsumption < 0) { PowerConsumption = 20; }
		PowerConsumption = PowerConsumption + 40;
		print substr(IOBArray[i], 1, powerIndex) PowerConsumption
	}
}