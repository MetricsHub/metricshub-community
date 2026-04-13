BEGIN { offsetStatus = "" ; section = ""}
{skip=1}
/show environment/,/(Power Supply:)|(show interface)/ {skip = 0}
skip == 1 {next}
(length($0) < 3) { Outlet=0 ; Intake=0 ; next }
(section == "Temperature" && offsetStatus > 1 && NF > 5) {
	                                      status = substr ($0,offsetStatus) ; gsub(/ /,"",status);
	                                      temp = $5 ;
	                                      tempWarn = $4 ;
	                                      tempAlarm = $3 ;
	                                      sensor = $1 "/" $2 ;
	                                      statusinfo = status;  if (statusinfo ~ /^[Oo][Kk]/) {statusinfo = "" } ;
	                                      module = $1 ; if (module ~ /^[0-9]/) { attachToType = "blade" } else { attachToType = "enclosure" ; module = "MDS9000Bay" } ;
	                                      if (sensor ~ /Outlet$/) {Outlet = Outlet + 1 ; print ("MSHW;" module ";" sensor Outlet";" status ";" statusinfo ";"temp  ";" tempWarn ";" tempAlarm  ";" attachToType ";")}
	                                      else if (sensor ~ /Intake$/) {Intake = Intake + 1 ; print ("MSHW;" module ";" sensor Intake";" status ";" statusinfo ";"temp  ";" tempWarn ";" tempAlarm  ";" attachToType ";")}
	                                      else {print ("MSHW;" module ";" sensor ";" status ";" statusinfo ";" temp  ";" tempWarn ";" tempAlarm  ";" attachToType ";")}
	                                      }

(section == "TemperatureOldStyle" && offsetStatus > 1 && NF > 2 && $1 ~ /^[0-9]+$/) {
                                       status = substr ($0,offsetStatus) ; gsub(/ /,"",status);
                                       statusinfo = status;  if (statusinfo ~ /^[Oo][Kk]/) {statusinfo = "" } ;
                                       temp = $2 ;
                                       sensor = $1 ;
                                       module = $1 ;
                                       print ("MSHW;" module ";" sensor ";" status ";" statusinfo ";" temp  ";"  ";"   ";blade;");
                                       }

$0 ~ /^Temperature/ { section = "Temperature" ; offsetStatus = "" }
(section == "Temperature" && $2 ~ /CurTemp/ && $3 ~ /Status/) { section = "TemperatureOldStyle"}
$NF == "Status" { offsetStatus = (index($0,"Status")) }
