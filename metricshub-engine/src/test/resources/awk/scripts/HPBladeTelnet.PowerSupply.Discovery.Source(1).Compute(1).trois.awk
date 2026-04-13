BEGIN  {powerSupplyID = ""; status ="" ; powerCapacity ="" ; lastLine=$0 }
{skip=1}
/show enclosure powersupply all/,/show enclosure fan all/ {skip = 0}
skip == 1 {next}
$0 ~ /^Power Supply #[0-9]/ { powerSupplyID = $0 ; status ="" ; powerCapacity ="" ; partNumber = "" ; gsub (/ [iI]nformation:/,"",powerSupplyID) ; gsub (/#/,"",powerSupplyID) ;}
( lastLine ~ /^Power Supply #/ && $0 ~ /Status/ ) { status = $2}
$1 ~ /^Capacity:/ { powerCapacity = $2 }
$1 ~ /^Current/ { powerCurrent = $4 }
$1 ~ /^Spare/ && (powerSupplyID != "") { sparePartNumber = $4 ;
             print ("MSHW;" powerSupplyID ";" status ";" powerCapacity ";" powerCurrent ";" ); 
             powerSupplyID = "" ; } 
{ lastLine = $0 }