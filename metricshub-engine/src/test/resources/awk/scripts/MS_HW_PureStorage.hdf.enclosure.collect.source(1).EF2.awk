BEGIN {outputMatch = 0}
$1 ~ /[Nn]ame/ && $2 ~ /[Ss]tatus/ && $3 ~ /[Ii]dentify/ {outputMatch = 1}
outputMatch == 1 && $1 ~ /^[SC][HT][0-9]/ {
      type = $1 ;
      enclosure = $1 ; gsub ("[.].+","",enclosure);
      displayID = $1 ; gsub ("^.+[.]","",displayID);
      if ($2 ~ /not_installed/) {next}
      if (type ~ /^[SC][HT][0-9]+\.BAY/) {next}
      if (type ~ /^[SC][HT][0-9]+\.DRV/) {next}
     	if (type ~ /^CT[0-9]+$/) {type = "MSHW_Enclosure;Controller" }
     	if (type ~ /^[SC][HT][0-9]+\.FAN/) {type = "MSHW_Fan" ;
     		                             CurrentReading = $6 ; gsub (/rpm/,"",CurrentReading);
     		                            }
      if (type ~ /^[SC][HT][0-9]+\.PWR/) {type = "MSHW_PSU" }
      if (type ~ /^[SC][HT][0-9]+\.ETH/ || type ~ /^[SC][HT][0-9]+\.FC/ || type ~ /^[SC][HT][0-9]+\.SAS/ || type ~ /^[SC][HT][0-9]+\.IB/) {
      	                            type = "MSHW_NetworkCard"
      	      	                    CurrentReading = $6
      	      	                    LinkStatus = 1
      	      	                    if ($7 ~ /Gb/) {
      	      	                    	                            CurrentReading = CurrentReading * 1000
      	      	                    	                           }
      	                            if (CurrentReading == 0) {
      	                            	CurrentReading = ""
      	                            	LinkStatus = 0
      	                            	}
      	                           }
      if (type ~ /^SH[0-9]+$/) {type = "MSHW_Enclosure;Disk Shelf" }
      if (type ~ /^CH[0-9]+$/) {type = "MSHW_Enclosure;Chassis" }
      if (type ~ /^[SC][HT][0-9]+\.IOM/) {type = "MSHW_OtherDevice" }
#All         MSHW_Type;ID;Status;Status;CurrentReading;Enclosure;DisplayID;
#OtherDevice/Controller/Shelf MSHW_Type;DeviceType;ID;Status;Status;CurrentReading;Enclosure;DisplayID;LinkStatus;
      print type ";" $1 ";" $2 ";" $2";" CurrentReading ";" enclosure ";" displayID ";" LinkStatus ";"
     }
