BEGIN  { powerConsumption = ""; status = "" ; lastLine = "" ;}

( lastLine ~ /Enclosure/ && $0 ~ /Status/ ) { status = $2 }
( $0 ~ /Present Power/ ) { powerConsumption = $3	}
{ lastLine = $0}

END  { print "MSHW;" status ";" powerConsumption ";"}
