BEGIN { offsetStatus = "" ; section = ""}
{skip=1}
/show environment/,/#/ {skip = 0}
skip == 1 {next}
(length($0) < 3) { section = ""; offsetStatus = "" ; next }
(section == "Fan" && offsetStatus > 1) {if ($NF ~ /NotSupported/) { next}
                                        status = substr ($0,offsetStatus) ; 
	                                      gsub(/ /,"",status);
	                                      statusinfo = status;  if (statusinfo ~ /^[Oo][Kk]/) {statusinfo = "" } ;
	                                      if ((NF > 2) && ($1 ~ "Chassis") && ($1 !~ /[0-9]$/)) {chassis = chassis + 1 ; print ("MSHW;" $1 chassis ";" status";"statusinfo";")}
	                                      else if ((NF > 2) && (status != "")) {print ("MSHW;" $1 ";" status";"statusinfo";")} ;
	                                      }

$0 ~ /^Fan/ { section = "Fan" ; chassis = 0 ; }
$NF == "Status" { offsetStatus = (index($0,"Status")) }