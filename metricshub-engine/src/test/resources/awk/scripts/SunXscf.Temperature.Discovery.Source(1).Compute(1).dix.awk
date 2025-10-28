BEGIN { GroupID = ""; SensorID = ""; FS=":";}
$1 ~ /Temperature/ {
	                  Temp = $2 ; gsub(/[^0-9\.]/,"",Temp);
	                  Warn = "40"; Alarm = "45" ;
	                  print ("MSHW;Ambient Temperature;" Temp ";" Warn ";" Alarm ";")
	                 }
$1 ~ /CMU#[0-9]/ {
	                GroupID = $1; gsub(/ /,"",GroupID);
                  Temp = $2 ; gsub(/[^0-9\.]/,"",Temp);
                  Warn = "55"; Alarm = "60" ;
	                print ("MSHW;" GroupID ";" Temp ";" Warn ";" Alarm ";")
	               }

$1 ~ /CPUM#[0-9]/ {
	                SensorID = $1; gsub(/ /,"",SensorID); SensorID = GroupID "/" SensorID ;
	                Temp = $2 ; gsub(/[^0-9\.]/,"",Temp);
                  Warn = "85"; Alarm = "100" ;
	                print ("MSHW;" SensorID ";" Temp ";" Warn ";" Alarm ";")
	               }