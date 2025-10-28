BEGIN { ioxList = ";"; }
(NR == 1) { ServerModel = $0 }
(NR == 2) { SerialNumber = $3 ; print "MSHW;Main;Computer;;" ServerModel ";" SerialNumber ";;;" ; }
($2 ~ /^IOX@/ && $3 == "Status") {
	DeviceID = $2;
	if (index(ioxList, ";" DeviceID ";") == 0)
	{
		ioxList = ioxList DeviceID ";";
		Name = DeviceID " I/O Expansion Unit";
		status = $4;
		SerialNumber = $6;
		getline;
		FruNumber = $3 " " $4;
		print "MSHW;" DeviceID ";Enclosure;;" Name ";" SerialNumber ";" FruNumber ";" status ;
	}
}