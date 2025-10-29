BEGIN {enclosureID="";sfpID="";status="";partNumber="";serialNumber"";vendor="";unknownCounter=0;
	     slot="";
	     MACaddress="";speed="";duplexMode="";IPaddress="";linkStatus="";
	     hostInterfaceID="";portType="";WWN="";}
# Note: It seems that link status is reported seperately to SFP status and there is no way to link the two, therefore I have to creat two ports.
# SFP Status
$0 ~ /Controller.Drive/ {enclosureID="85"}
$0 ~ /Drive Enclosure [0-9]/ || $0 ~ /Controller Enclosure [0-9]/ || $0 ~ /Controller Module [0-9]/ {enclosureID=$3}
$0 ~ /Drive Expansion Enclosure [0-9]/ {enclosureID=$4}
$1 ~ /SFP/ && $2 ~ /[Ss]tatus/ {status=$3 ;
                           if (NF > 3)  {status = (status " " $4)};
                           }
$1 ~ /[Aa]ttached/ && $2 ~ /[Tt]o/ && status !="" {sfpID =$3;
                                                   if (NF > 3)  {sfpID = (sfpID " " $4)};
                                                   if (NF > 4)  {sfpID = (sfpID " " $5)};
                                                   if (NF > 5)  {sfpID = (sfpID " " $6)};
                                                   if (NF > 6)  {sfpID = (sfpID " " $7)};
                                                   if (NF > 7)  {sfpID = (sfpID " " $8)};
                                                   }
$1 ~ /[Ll]ocation/ && status !="" && sfpID != "" && $2 !="Unknown" {sfpID = (sfpID " " $2)
                                                   if (NF > 2)  {sfpID = (sfpID " " $3)};
                                                   if (NF > 3)  {sfpID = (sfpID " " $4)};
                                                   if (NF > 4)  {sfpID = (sfpID " " $5)};
                                                   if (NF > 5)  {sfpID = (sfpID " " $6)};
                                                   }
$1 ~ /[Ll]ocation/ && status !="" && sfpID != "" && $2 =="Unknown" {sfpID = (sfpID " (Port / Channel Unknown " unknownCounter ")");unknownCounter=unknownCounter+1;}
$1 ~ /[Cc]onnector/  {connector=$2}
$1 ~ /[Pp]art/ && $2 ~ /[Nn]umber/ {partNumber=$NF}
$1 ~ /[Ss]erial/ && $2 ~ /[Nn]umber/ {serialNumber=$NF}
$1 ~ /[Vv]endor/  && sfpID != "" {vendor=$2 ;
                   if (NF > 2)  {vendor = (vendor " " $3)};
                   print ("MSHW;"enclosureID";"enclosureID " - " sfpID";"status";"status";FC Port;"partNumber";"serialNumber";"vendor";;;;;;;;");
                              sfpID="";status="";partNumber="";serialNumber"";vendor="";
                              }


# Controller IDs for Ethernet and Host Ports
$1 ~ /Controller/ && $3 ~ /Enclosure/ && $5 ~ /Slot/ {enclosureID = $4 ; gsub (/,/,"",enclosureID)
	                                                    slot = "Controller " $6
	                                                   }
                              
# Ethernet Ports                  
$1 ~ /Ethernet/ && $2 ~ /port/ {hostInterfaceID = $NF ; portType = "Ethernet Port" ;}
$1 ~ /Link/ && $2 ~ /status/   {linkStatus = $3}
$1 ~ /MAC/ && $2 ~ /address/   {MACaddress = $3}
$1 ~ /Port/ && $2 ~ /speed/    {speed = $3
	                              if ($5 ~ /Gbps/) {speed = speed * 1000}
	                             }
$1 ~ /Duplex/ && $2 ~ /mode/   {duplexMode = $3 " " $4 }

# Host Interfaces
$1 ~ /Host/ && $2 ~ /interface/ {portType = $NF}
$1 ~ /Channel/ && $2 ~ /^[0-9]*$/ {hostInterfaceID = $2}
$1 ~ /Current/ && $2 ~ /data/ && $3 ~/rate/ {speed = $4
	                                           if ($5 ~ /Gbps/) {speed = speed * 1000}
	                                          }
$1 ~ /Current/ && $2 ~ /port/ && $3 ~/speed/ {speed = $4
	                                           if ($5 ~ /Gbps/) {speed = speed * 1000}
	                                          }
#iSCSI Ports and Ethernet Ports
$1 ~ /IP/ && $2 ~ /address/  &&  (ethernetPortID != "" || hostInterfaceID != "") {IPaddress = $3
	                              print ("MSHW;"enclosureID";"enclosureID " - " slot " - " portType " " hostInterfaceID";" ";" ";" portType ";" ";" ";" ";" MACaddress ";MAC;" speed ";" duplexMode ";" IPaddress ";IP;" linkStatus ";" );
	                              hostInterfaceID="";MACaddress="";speed="";duplexMode="";IPaddress="";linkStatus="";
	                             }

#Fibre Ports
$1 ~ /World-wide/ && $2 ~ /port/ && $3 ~/identifier/ {WWN = $4
	                                                    print ("MSHW;"enclosureID";"enclosureID " - " slot " - " portType " " hostInterfaceID";" ";" ";" portType ";" ";" ";" ";" WWN ";WWN;" speed ";"  ";"  ";" ";" linkStatus ";" );
	                                                    hostInterfaceID="";portType="";WWN="";speed="";linkStatus="";
	                                                   }