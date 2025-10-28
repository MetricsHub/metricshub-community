($2 ~ /^MEM#[0-9A-Za-z]+$/) {
	memoryID = $1;
	status = $4;
	getline;
	SerialNumber = $3;
	getline;
	Size = $5
	SizeUnit = $6
	if (SizeUnit == "GB") { Size = Size * 1024; }
	print "MSHW;" memoryID ";" memoryID ";" Size ";" SerialNumber ";" status
}