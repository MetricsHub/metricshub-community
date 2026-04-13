($1 == "FanSpeed" && $(NF-2) ~ /rpm/ && NF > 7) {
	if ($4 ~ /^[Nn]\057[Aa]$/ ) { status = "UNKNOWN"; statusInformation = "Unknown Status"; }
	else if ($4 ~ /^[Oo][Kk]$/) { status = "OK"; statusInformation = ""; }
	else if ($4 ~ /^[Nn]ot$/ && $5 ~ /^[Oo][Kk]$/) { status = "ALARM"; statusInformation = "Not OK"; }
	else { status = "UNKNOWN"; statusInformation = "Unknown Status"; }
print ("MSHW;" $2 ";" $3 ";;" $(NF-1) ";" $(NF-3) ";" status ";" statusInformation ";")
}