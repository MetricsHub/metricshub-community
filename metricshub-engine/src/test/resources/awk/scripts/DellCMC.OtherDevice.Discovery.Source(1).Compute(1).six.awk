(($1 ~ /CMC-[0-9]+/ || $1 ~ /Switch-[0-9]+/ || $1 ~ /KVM/) && $2 == "Present") {
	if ($4 ~ /^[Nn]\057[Aa]$/ ) { status = "UNKNOWN"; statusInformation = "Unknown Status"; }
	else if ($4 ~ /^[Oo][Kk]$/) { status = "OK"; statusInformation = ""; }
	else if ($4 ~ /^[Nn]ot$/ || $5 ~ /^[Oo][Kk]$/) { status = "ALARM"; statusInformation = "Not OK"; }
	else { status = "UNKNOWN"; statusInformation = "Unknown Status"; } ;
  deviceType = $1 ; gsub (/-[0-9]+/,"",deviceType) ;
	print "MSHW;" $1 ";" deviceType ";" status ";" statusInformation ";"
}