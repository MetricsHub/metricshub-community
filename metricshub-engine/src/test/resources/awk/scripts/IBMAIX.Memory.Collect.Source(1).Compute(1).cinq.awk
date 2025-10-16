BEGIN {
	MemoryObject = 0;
	DeviceID = "";
	Size = "";
	SerialNumber = "";
	PartNumber = "";
}
/Memory [DS]IMM:/ {
	MemoryObject = 1;
}

/Size\.+/ {
	Size = $0;
	gsub(" +Size\\.+", "", Size);
}
/Serial Number\.+/ {
	SerialNumber = $0;
	gsub(" +Serial Number\\.+", "", SerialNumber);
}
/Part Number\.+/ {
	PartNumber = $0;
	gsub(" +Part Number\\.+", "", PartNumber);
}
/Physical Location:/ {
	if (MemoryObject == 1)
	{
		DeviceID = $3;

		if (Size == 0)
		{
			Status = "ALARM";
			StatusInformation = "Failed";
		}
		else
		{
			Status = "OK";
			StatusInformation = "";
		}
		print "MSHW;" DeviceID ";" Size ";" SerialNumber " - FRU: " PartNumber ";" Status ";" StatusInformation
	}
	DeviceID = "";
	MemoryObject = 0;
	SerialNumber = "";
	PartNumber = "";
}	