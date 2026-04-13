{skip=1}
/show server info all/,/show enclosure powersupply all/ {skip = 0}
skip == 1 {next}

$0 ~ /^Server Blade #[0-9]/ { bladeID = $3 ; bladeName = "" ; serialNumber ="" ; gsub (/#/,"",bladeID) ; }
$1 ~ /^Product/ { bladeModel = $0 ; gsub (/Product Name:/,"",bladeModel) ; gsub (/\t/,"",bladeModel)}
$1 ~ /^Physical/ { serialNumber = $6 ; }
($1 ~ /Server/ && $2 ~ /Name/) { serverName = $3 ;
              print ( "MSHW;" bladeID ";" serverName ";" bladeModel ";" serialNumber ";") ;
             }
