BEGIN { MotherBoardID = ""; }
($2 ~ /^CPUM#[0-9]+-CHIP#[0-9]+$/) {
	cpuID = $1;
	status = $4;
	SerialNumber = $8;
	getline;
	FruPartNumber = $3;
	getline;
	Speed = $3 * 1000;
	getline;
	CoreCount = $3;
	if (CoreCount == 4)
	{
		PowerConsumption = 135;
	}
	else
	{
		PowerConsumption = 120;
	}
	print "MSHW;" cpuID ";" cpuID " - " CoreCount "-core SPARC64;" Speed ";" SerialNumber ";" FruPartNumber ";" status ";" PowerConsumption
}