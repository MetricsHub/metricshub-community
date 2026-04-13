BEGIN {Cabinet="";IDIndex="";Present=0;Failure=0;State=0;fans="";powerSupplies=""}
/Cabinet [0-9]+ Hardware Inventory/,/=-+-=-+-=-+-=-+-=-+/ {

# Processes the "Overall Table"
if ($1 ~ /Cabinet/ && $2 ~ /[0-9]+/ && $3 ~ /Hardware/) {Cabinet = $2}
if ($1 ~ /Component/ && $(NF-2) ~ /Present/ && $(NF-1) ~ /Failure/ && $NF ~ /State/) {IDIndex = match($0,$2)
	                                                                                   }
if (IDIndex != "" && NF > 2 && $(NF-1) ~ /^[0-9]+$/) {
	                  DeviceID = substr($0,1,IDIndex) ; gsub (/^ +/,"",DeviceID);gsub (/ +$/,"",DeviceID);
                    if (DeviceID ~ /[Ff]an/) {type = "FAN-Global" ; if ($NF !~ /[Uu]nknown/) {fans = "Monitored" }}
                     	else if (DeviceID ~ /[Pp]ower/) {type = "PSU-Global"; if ($NF !~ /[Uu]nknown/) {powerSupplies = "Monitored"}}
                     		else {type = "OTHER"}
                    if ($Failure > 0) {status = "ALARM"; statusInformation = $Failure " Failed"}
                    	else {status = "OK" ; statusInformation = ""}
                    print ("MSHW;" type ";CAB-" Cabinet " " DeviceID ";" status ";" statusInformation ";")
                   }	

if (NF < 2) {IDIndex="";Present=0;Failure=0;State=0;}

# Processes the "Individual Components" - only if the Fans / Power Supplies are being monitored in the table above (not "unknown")  Flag fans & powerSupplies are used.

if ($0 ~ /\|[0-9]\|[0-9]/) {
                          ArrayLength=split($0,OutputArray,/\|/);
                          getline;
                          split($0,LineArray,/\|/);
                          for (i=2;i<=ArrayLength-1;i++) {OutputArray[i]=OutputArray[i] LineArray[i];}
                          getline;
                          getline;
                          while ($0 !~ /------------/) {
                                                        split($0,LineArray,/\|/);
                                                        gsub(/ /,"",LineArray[1]); 
                                                        for (i=2;i<=ArrayLength-1;i++) {
                                                        	                              gsub(/ /,"",LineArray[i]);
                                                        	                              if (LineArray[1] ~ /^cf/ && fans != "") {type = "FAN"}
                                                        	                              	else if (LineArray[1] ~ /^cp/ && powerSupplies != "") {type = "PSU"}
                                                        	                              		else {type = ""}
                                                        	                              if ( LineArray[i] == "p" ) {status="OK";statusInformation=""}
                                                        	                              	else if ( LineArray[i] == "F" ) {status="ALARM";statusInformation="Failed"}
                                                        	                              		else {status="";statusInformation=""}
                                                        	                              if (type != "" && status != "") {print ("MSHW;"type";CAB-" Cabinet " " LineArray[1]"-" OutputArray[i] ";" status ";" statusInformation";");}
                                                        	                              }
                                                        getline;
                                                       }
                          }
                       }