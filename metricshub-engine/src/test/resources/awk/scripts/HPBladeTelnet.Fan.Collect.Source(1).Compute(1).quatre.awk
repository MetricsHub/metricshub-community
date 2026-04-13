BEGIN  {fanID = ""; status ="" ; fanSpeedPercent ="" ; fanMin ="" ; fanMax = "" ; partNumber = "" ; lastLine=$0 }
{skip=1}
/show enclosure fan all/,/show enclosure temp/ {skip = 0}
skip == 1 {next}

$0 ~ /^Fan #[0-9]/ { fanID = $0 ; status ="" ; fanMin ="" ; fanMax = "" ; partNumber = "" ; gsub (/ [iI]nformation:/,"",fanID) ; gsub (/#/,"",fanID)}
( lastLine ~ /^Fan #/ && $0 ~ /Status/ ) { status = $2}
$1 ~ /^Speed/ {fanSpeedPercent = $2 }
$1 ~ /^Maximum/ { fanMax = $3 }
$1 ~ /^Minimum/ { fanMin = $3 }
($1 ~ /^Spare/) && (fanID != "") { sparePartNumber = $4  ;
             print ( "MSHW;" fanID ";" status ";" fanSpeedPercent ";" fanMin ";" fanMax ";" ) ;
             fanID = "" ;} 

{ lastLine = $0 }