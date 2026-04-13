BEGIN { ioDriveID=""; blockDeviceID=""; model=""; addInfo=""; physicalSize=""; internalTemp=""; internalVoltage=""; auxVoltage=""; boardTemp=""; status = ""; statusInformation = "";}
$1 ~ /fct[0-9]+/ && $2 ~ /Attached/ && $3 ~ /(as)|(to)/ {
     ioDriveID=$1 ;
     blockDeviceID=$4 ; gsub (/[']/,"",blockDeviceID)
     }
#'
$0 ~ /Product Number:/ {
     model = $0 ; gsub (/,? Product Number:.*/,"",model );gsub (/^ */,"",model )
     addInfo = $0 ; gsub (model,"",addInfo);gsub (/^[ ,]*/,"",addInfo  )
     }

$7 ~ /GBytes/ && $8 ~ /physical/ && $9 ~ /device/ && $10 ~ /size/ {
     physicalSize = $6
     }

$1 ~ /Internal/ && $2 ~ /temperature/ {
     if ($3 ~ /avg/) {internalTemp = $4}
     else {internalTemp = $3}
     }

$1 ~ /Internal/ && $2 ~ /voltage/ {
     if ($3 ~ /avg/) {internalVoltage = $4;gsub(/[Vv,]/,"",internalVoltage)}
     else {internalVoltage = $3}
     }

$1 ~ /Aux/ && $2 ~ /voltage/ {
     if ($3 ~ /avg/) {auxVoltage = $4;gsub(/[Vv,]/,"",auxVoltage)}
     else {auxVoltage = $3}
     }

# Parameter Activated as not always present:
$1 ~ /(Board)|(Ambient)/ && $2 ~ /temperature/ {
     if ($3 ~ /avg/) {boardTemp = $4}
     else {boardTemp = $3}
     }

# Media Status is translated here and print done
($1 ~ /Media/ && $2 ~ /[Ss]tatus/) || ($1 ~ /Reserve/ && $2 ~ /[Ss]pace/ && $3 ~ /[Ss]tatus/) && (ioDriveID != "") {
     mediaStatus = tolower($0); gsub (/^.*status: */,"",mediaStatus);gsub (/;.*$/,"",mediaStatus);
     if (mediaStatus ~ /healthy/) {if (status == "") {status = "OK"}}
     else if (mediaStatus ~ /low.*metadata/ || tolower(status) ~ /reduced.write/ || tolower(status) ~ /nearing wearout/ || tolower(status) ~ /write.reduced/) {if (status != "ALARM") {status = "WARN"}; statusInformation = statusInformation " " mediaStatus}
     else if (mediaStatus ~ /read.*only/) {status = "ALARM"; statusInformation = statusInformation " " mediaStatus}
     else {if (status != "ALARM") {status = "WARN"}; statusInformation = statusInformation " " mediaStatus}

# Print Physical Disk Information
     gsub (/^ */,"",statusInformation);gsub (/  /,"",statusInformation)
     print "MSHW_PD;" ioDriveID ";" blockDeviceID ";" model ";" addInfo ";" physicalSize ";" status ";" statusInformation ";"
# Print Internal Temperature Information (The ioDrive will start throttling write performance at 78°C. the ioDrive will shut down at 85°C.  So will alarm at 78°.
     if (internalTemp != "") {print "MSHW_TEMP;Internal " ioDriveID ";" internalTemp ";78;"}
     if (boardTemp != "") {print "MSHW_TEMP;Board " ioDriveID ";" boardTemp ";;"}
     if (auxVoltage != "") {print "MSHW_VOLT;Aux " ioDriveID ";" auxVoltage ";;"}
     if (internalVoltage != "") {print "MSHW_VOLT;Internal " ioDriveID ";" internalVoltage ";;"}
     ioDriveID=""; blockDeviceID=""; model=""; addInfo=""; physicalSize=""; internalTemp=""; internalVoltage=""; auxVoltage=""; boardTemp=""; status = ""; statusInformation = "";
     }

# Drive STATUS Collection
# Unfortunately the Status of each IO Drive is not always in the same place so we are going to have to search every line for known error messages.
# At least the documentation was nice enough to provide those.
tolower($0) ~ /additional active alarm/ {status = "ALARM"; statusInformation = " " statusInformation $0}
# Generic Catch All:
(tolower($0) ~ /warning/) || (tolower($0) ~ /minimal mode/) {if (status != "ALARM") {status = "WARN"};
	                                                           statusInformation = statusInformation " " $0
	                                                           if (tolower($0) ~ /warning.*temperature is above/) {status = "ALARM";}
                                                             if (tolower($0) ~ /warning.*voltage is out of range/) {status = "ALARM"; }
	                                                          }
