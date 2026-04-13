BEGIN { FS = ";" }
/^swEnvStats;/ {
	split($2, pathArray, "/");
	parentID = pathArray[1] "/" pathArray[2];

	if ($3 ~ /^[0-9]/) { print "MSHW;" parentID "/fanCtrlrInlet1;fanCtrlrInlet1;" parentID ";Fan Controller Inlet 1;" $3 }
	if ($4 ~ /^[0-9]/) { print "MSHW;" parentID "/fanCtrlrInlet2;fanCtrlrInlet2;" parentID ";Fan Controller Inlet 2;" $4 }
	if ($5 ~ /^[0-9]/) { print "MSHW;" parentID "/fanCtrlrInlet3;fanCtrlrInlet3;" parentID ";Fan Controller Inlet 3;" $5 }
	if ($6 ~ /^[0-9]/) { print "MSHW;" parentID "/fanCtrlrInlet4;fanCtrlrInlet4;" parentID ";Fan Controller Inlet 4;" $6 }
	if ($7 ~ /^[0-9]/) { print "MSHW;" parentID "/mainBoardOutlet1;mainBoardOutlet1;" parentID ";Main Board Outlet 1;" $7 }
	if ($8 ~ /^[0-9]/) { print "MSHW;" parentID "/mainBoardOutlet2;mainBoardOutlet2;" parentID ";Main Board Outlet 2;" $8 }

}
/^computeMbTempStats;/ {
	split($2, pathArray, "/");
	parentID = pathArray[1] "/" pathArray[2];
	bladeID = pathArray[3];

	print "MSHW;" parentID "/" bladeID "/fmTempSenIo;" bladeID "/fmTempSenIo;" parentID ";" bladeID " - MB Front;" $3
	print "MSHW;" parentID "/" bladeID "/fmTempSenRear;" bladeID "/fmTempSenRead;" parentID ";" bladeID " - MB Rear;" $4

}
