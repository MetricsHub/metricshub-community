BEGIN { offsetStatus = "" ; section = ""}
{skip=1}
/show environment/,/#/ {skip = 0}
skip == 1 {next}
(length($0) < 3) { section = "" ; offsetStatus = "" ; next }

(section == "Clock" && offsetStatus > 1) {status = substr ($0,offsetStatus) ; 
	                                        offsetSlash = index(status,"/") ;
	                                        if (offsetSlash > 1)  {status = substr (status,1,offsetSlash - 1)} ;
	                                        statusinfo = status;  if (statusinfo ~ /^[Oo][Kk]/) {statusinfo = "" } ;
	                                        if ((status !~ /[Nn]ot [Pp]resent/) && NF > 2 ){print ("MSHW;Clock;" $1 ";" status ";" statusinfo ";") };
	                                        }

$0 ~ /^Clock/ { section = "Clock" }
$NF == "Status" { offsetStatus = (index($0,"Status")) }