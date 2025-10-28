BEGIN { offsetStatus = "" ; section = ""}
{skip=1}
/show environment/,/#/ {skip = 0}
skip == 1 {next}
(length($0) < 3) { section = ""; offsetStatus = "" ; Outlet=0 ; Intake=0 ; next }
(section == "Power Supply" && offsetStatus > 1 && NF > 3) {
	                                      status = substr ($0,offsetStatus) ; gsub(/ /,"",status);
	                                      powerSupply = $1 ;
	                                      statusinfo = status;  if (statusinfo ~ /^[Oo][Kk]/) {statusinfo = "" } ;
	                                      print ("MSHW;" powerSupply ";" status ";" statusinfo ";") ;
	                                      }
(section == "Power Supply Old Style" && offsetStatus > 1 && $1 ~ /^[0-9]+$/ ) {
	                                      status = substr ($0,offsetStatus) ; gsub(/ /,"",status);
	                                      powerSupply = $1 ;      
	                                      statusinfo = status;  if (statusinfo ~ /^[Oo][Kk]/) {statusinfo = "" } ;                        
	                                      print ("MSHW;" powerSupply ";" status ";" statusinfo ";") ;
	                                     }

$0 ~ /^Power Supply/ { section = "Power Supply" }
$1 ~ /^PS$/ && $2 ~ /^Status$/ { section = "Power Supply Old Style" }
$NF == "Status" { offsetStatus = (index($0,"Status")) }