BEGIN  {IDlength="";BayNumFound=0}
/show/,/show enclosure temp/ {next}
$0 ~ /Bay #/ {BayNumFound=1}
($1 ~ /^-+$/ && $2 ~ /^-+$/ && $3 ~ /^-+$/ && $4 ~ /^-+$/ && $5 ~ /^-+$/ ) { IDlength = length($1) 
	                                                                                if (BayNumFound==1) {IDlength = IDlength + 1 + length($2)}
	                                                                              }
NF < 5 { next }
{tempSensorID = ""}
{ if ($NF == "---" && $(NF-3) ~ /C/) { tempStatus = $(NF-4) ; temp = "" ; tempCaution = "" ; tempCritical = "" ; temperatureActivate = "" ; }
        else if ($NF == "---" ) { tempStatus = $(NF-3) ; temp = "" ; tempCaution = "" ; tempCritical = "" ; temperatureActivate = "" ; }
        else if ($NF ~ /C/ && $(NF-2) ~ /C/)  {tempStatus = $(NF-3) ; temp = $(NF-2) ; tempCaution = $(NF-1) ; tempCritical = $NF ; temperatureActivate = "2" }
        else if ($NF ~ /C/ && $(NF-3) ~ /C/)  {tempStatus = $(NF-4) ; temp = $(NF-3) ; tempCaution = $(NF-1) ; tempCritical = $NF ; temperatureActivate = "2" }
        else {next} 
        }
{ if ( tempStatus  ~ /^[nN].?[aA]/ ) { statusActivate=""}
	      else { statusActivate = "2" }
      }
{ tempSensorID = substr($0,1,IDlength) }    
      
#{ print ( "MSHWDB;" tempSensorID ";" tempStatus ";" temp ";" tempCaution ";" tempCritical ";" statusActivate ";" temperatureActivate ";" ) }

{ gsub (/ /,"",tempSensorID) }
{ gsub (/[ C\057]/,"",temp) }
{ gsub (/[ C]/,"",tempCaution) }
{ gsub (/[ C]/,"",tempCritical) }
{ gsub (/[oO]nboard[aA]dministrator/,"OA",tempSensorID) }
{ gsub (/[iI]nterconnect[mM]odule/,"IC",tempSensorID) }

(statusActivate != "" || temperatureActivate != "" ){ print ( "MSHW;" tempSensorID ";" tempStatus ";" temp ";" tempCaution ";" tempCritical ";" statusActivate ";" temperatureActivate ";" ) }