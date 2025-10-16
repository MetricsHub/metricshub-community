{skip=1}
/show server status all/,/show enclosure powersupply all/ {skip = 0}
skip == 1 {next}

$0 ~ /^Blade #[0-9]/ { bladeID = $2 ; gsub (/#/,"",bladeID) ; }
$1 ~ /^Power/ && $2 ~ /^Off/ { bladeOff = $2 }
$1 ~ /^Health/ { bladeStatus = $2 ;  
                 print ( "MSHW;" bladeID ";" bladeStatus ";" bladeStatus " " bladeOff ";") ;
                 bladeID = "" ; bladeOff = "" ; } 