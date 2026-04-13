# ENCLOSURE
$0 ~ /Agent.Host Information/ {Section="ENC"}
Section=="ENC" && $1 ~ /Model:/  {SystemModel = $2}
Section=="ENC" && $1 ~ /Prom/ && $2 ~ /Rev:/ {SystemFirmware = $NF}
Section=="ENC" && $1 ~ /Serial/ && $2 ~ /No:/  {SystemSerial = $NF}

# ARRAY
$0 ~ /Array Information/ {Section="ARRAY"}
Section=="ARRAY" && $1 ~ /Array/ && $2 ~ /Name:/ {ArrayName = $NF}

Section=="ARRAY" && ( $0 ~ /^Bus [0-9]* Enclosure [0-9]*.*State:/ || $0 ~ /^SP [A-Z0-9] State:/ ) {
	                                                         ComponentID = $0 ;
	                                                         gsub(/State:/,":",ComponentID)
	                                                         gsub(/ ?:.*$/,"",ComponentID)
	                                                         ComponentIDs[ComponentID] = ComponentID;
	                                                         Type[ComponentID] = $5 ; if ($0 ~ /^SP [A-Z0-9] State:/) {Type[ComponentID] = "SP"};
	                                                         Status[ComponentID] = $0
	                                                         gsub(/.*: */,"",Status[ComponentID])
	                                                         gsub(/ *$/,"",Status[ComponentID])

	                                                       }

# HBA
$0 ~ /^HBA Information/ {Section="HBA"}

# Ports
$0 ~ /Information about each SPPORT/ {Section="SPPORT"}
Section=="SPPORT" && $1 ~ /SP/ && $2 ~ /Name:/ {SPName = $0; gsub(/.*: */,"",SPName);}
Section=="SPPORT" && $1 ~ /SP/ && $2 ~ /Port/ && $3 ~ /ID:/ {SPID = $NF ; PortID = SPName " Port " SPID ; Ports[PortID] = PortID ;}
Section=="SPPORT" && $1 ~ /Link/ && $2 ~ /Status:/	 {SPLinkStatus[PortID] = $NF }
Section=="SPPORT" && $1 ~ /Port/ && $2 ~ /Status:/	 {SPPortStatus[PortID] = $NF }
Section=="SPPORT" && $1 ~ /SFP/ && $2 ~ /State:/	 {SFState[PortID] = $NF }
Section=="SPPORT" && $1 ~ /Speed/ && $2 ~ /Value/	 {SPPortSpeed[PortID] = $NF ; gsub(/[Gg]bps/,"000",SPPortSpeed[PortID]);gsub(/[Mm]bps/,"",SPPortSpeed[PortID]);gsub(/N.A/,"",SPPortSpeed[PortID])}
Section=="SPPORT" && $1 ~ /I.O/ && $2 ~ /Module/ && $3 ~ /Slot:/ {SPIOSlot[PortID] = "IO Module: " $NF}
Section=="SPPORT" && $1 ~ /Physical/ && $2 ~ /Port/ && $3 ~ /ID:/ {SPPhysPortID[PortID] = "Port ID: " $NF}
Section=="SPPORT" && $1 ~ /SP/ && $2 ~ /UID:/	 {SPUID[PortID] = "UID: "$NF }
Section=="SPPORT" && $1 ~ /SFP.Connector/ && $2 ~ /EMC/ && $3 ~ /Part/ {SPEMCPartNumber[PortID] = "EMC Part Number: " $NF}
Section=="SPPORT" && $1 ~ /SFP.Connector/ && $2 ~ /EMC/ && $3 ~ /Serial/ {SPEMCSerialNumber[PortID] = "EMC Serial Number: " $NF}
Section=="SPPORT" && $1 ~ /SFP.Connector/ && $2 ~ /Vendor/ && $3 ~ /Part/ {SPVendorPartNumber[PortID] = "Vendor Part Number: " $NF}
Section=="SPPORT" && $1 ~ /SFP.Connector/ && $2 ~ /Vendor/ && $3 ~ /Serial/ {SPVendorSerialNumber[PortID] = "Vendor Serial Number: " $NF}

# SP Information
$0 ~ /^SP Information/ {Section="SP"}
Section=="SP" && $1 ~ /System/ && $2 ~ /Fault/ && $3 ~ /LED:/ {SystemFaultLED = $NF}

# PHYSICAL DISK
$0 ~ /All Disks Information/ {Section="PD"}
Section=="PD" && $0 ~ /^Bus [0-9]+ Enclosure [0-9]+  Disk [0-9]+$/ {DiskID = $0; DiskIDs[DiskID]= DiskID;}
Section=="PD" && $1 ~ /Vendor/ && $2 ~ /Id:/ {DiskVendor[DiskID] = $NF}
Section=="PD" && $1 ~ /State/ {DiskState[DiskID] = $NF}
Section=="PD" && $1 ~ /Capacity:/ {Size[DiskID] = $NF}
Section=="PD" && $1 ~ /Product/ && $2 ~ /Id:/  {DiskModel[DiskID] = $0; gsub(/.*: */,"",DiskModel[DiskID])}
Section=="PD" && $1 ~ /Product/ && $2 ~ /Revision:/ {DiskRevision[DiskID] = $NF}
Section=="PD" && $1 ~ /Clariion/ && $2 ~ /TLA/ && $3 ~ /Part/ {DiskTLAPartNumber[DiskID] =  $NF; gsub (/Number:/,"",DiskTLAPartNumber[DiskID]) ;
	                                                             gsub (/PWR$/,"",DiskTLAPartNumber[DiskID]) ;
	                                                             DiskTLAPartNumber[DiskID] = "TLA Part Number: " DiskTLAPartNumber[DiskID] }
Section=="PD" && $1 ~ /Clariion/ && $2 ~ /Part/ && $3 ~ /Number:/ {DiskPartNumber[DiskID] = "Part Number: "$NF}



# Resume Information
$0 ~ /Resume Information/ {Section="FRU"}
$0 !~ /:/ {ComponentID=""}
#SP
Section=="FRU" && $1 ~ /SP/ && $2 ~ /[A-Z]/ { ComponentID = "SP " $2 ;}
#For non-SP, set StorageProcessor
Section=="FRU" && $1 ~ /Storage/ && $2 ~ /Processor/ && $3 ~ /^[A-Z]$/ {StorageProcessor = $3}
#For power supplies and LCC, Set the Location aswell
Section=="FRU" && $0 ~ /Bus [0-9] Enclosure [0-9]/ {Location = $0}
Section=="FRU" && $1 ~ /Power/ && $2 ~ /^[A-Z]$/ {StorageProcessor = $2 ; ComponentID = Location " Power " StorageProcessor }
Section=="FRU" && $1 ~ /LCC/ && $2 ~ /^[A-Z]$/ {StorageProcessor = $2 ; ComponentID = Location " LCC " StorageProcessor }

Section=="FRU" && $1 ~ /CPU/ && $2 ~ /Module/ { ComponentID = "Bus 0 Enclosure 0 CPU Module " StorageProcessor }
Section=="FRU" && $1 ~ /I.O/ && $2 ~ /Module/ { ComponentID = "Bus 0 Enclosure 0 SP " StorageProcessor " I/O Module " $3}

Section=="FRU" && $1 ~ /EMC/ && $2 ~ /Part/ && $3 ~ /Number:/ {ComponentPartNumber[ComponentID] = "Part Number: " $NF}
Section=="FRU" && $1 ~ /EMC/ && $2 ~ /Serial/ && $3 ~ /Number:/ {ComponentSerialNumber[ComponentID] = "Serial Number: " $NF}
Section=="FRU" && $1 ~ /EMC/ && $2 ~ /Assembly/ && $3 ~ /Revision:/ {ComponentRevision[ComponentID] = "Revision: "$NF}
Section=="FRU" && $1 ~ /Assembly/ && $2 ~ /Name:/ {ComponentDescription[ComponentID] = $0; gsub(/^.*: */,"",ComponentDescription[ComponentID])}

$0 ~ /All logical Units Information/ {Section="LD"}
$0 ~ /Snap View Information/ {Section="SV"}


END{
# ENCLOSURE
print "MSHW_ENCLOSURE;" SystemModel ";" SystemSerial ";" "Prom Rev:" SystemFirmware ";" SystemFaultLED ";"
# PHYSICAL DISK
for (DiskID in DiskIDs) { if (DiskID != "") {
	  print "MSHW_PHYDISK;" DiskID ";" DiskVendor[DiskID] ";" DiskState[DiskID] ";" DiskModel[DiskID] ";" DiskRevision[DiskID] ";" DiskTLAPartNumber[DiskID] ";"  DiskPartNumber[DiskID] ";" Size[DiskID] ";"
    }
    }

# Components
for (ComponentID in ComponentIDs) { if (ComponentID != "") {
  print "MSHW_OTHER;" Type[ComponentID] ";" ComponentID ";" Status[ComponentID] ";" ComponentPartNumber[ComponentID] ";" ComponentSerialNumber[ComponentID] ";" ComponentRevision[ComponentID] ";" ComponentDescription[ComponentID] ";"
  }
  }

# Ports
for (PortID in Ports) { if (PortID != "") {
	FRU = ""
	if ( SPEMCPartNumber[PortID] != "" ) { FRU = SPEMCPartNumber[PortID] " - " }
  if ( SPEMCSerialNumber[PortID] != "" ) { FRU = FRU SPEMCSerialNumber[PortID] " - " }
  if ( SPVendorPartNumber[PortID] != "" ) { FRU = FRU SPVendorPartNumber[PortID] " - " }
  if ( SPVendorSerialNumber[PortID] != "" ) { FRU = FRU SPVendorSerialNumber[PortID]}
  gsub (/ - $/,"",FRU)
  Location = ""
  if (SPIOSlot[PortID] != "" && SPPhysPortID[PortID] != "") { Location = SPIOSlot[PortID] " - " SPPhysPortID[PortID] }
  print "MSHW_PORT;" PortID ";" SPLinkStatus[PortID] ";" SPPortStatus[PortID] ";" SFState[PortID] ";"SPPortSpeed[PortID] ";" Location ";" SPUID[PortID] ";" FRU ";"
  }
}
}
