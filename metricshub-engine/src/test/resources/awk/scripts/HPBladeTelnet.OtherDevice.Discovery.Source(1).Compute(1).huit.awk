{skip=1}
/show interconnect info all/,/show server info all/ {skip = 0}
skip == 1 {next}

( $1 ~ /^[0-9]/ && $1 ~ /.$/ ) { otherDeviceID = $1 ; deviceType = $2 ; gsub(/\./,"",otherDeviceID) ; }
$1 ~ /^Product/ { deviceName = $0 ; gsub(/Product Name: /,"",deviceName) ; gsub(/\t/,"",deviceName)
                  print "MSHW;" otherDeviceID ";IC Switch - " deviceType ";" deviceName ";" ;
                  otherDeviceID = "" ; }
