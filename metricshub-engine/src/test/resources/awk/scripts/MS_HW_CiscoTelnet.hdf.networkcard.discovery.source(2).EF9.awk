$1 ~ /^fc[0-9]/     {port = $1 ; gsub (/fc/,"",port) ;}
$0 ~ /waits due to lack of transmit credits/ && $1 ~/^[0-9]+$/ {ZeroBufferCreditCount = $1
	                                                             print "MSHW;" port ";" ZeroBufferCreditCount ";"
	                                                             port = ""}
