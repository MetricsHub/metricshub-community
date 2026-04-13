($1 == "Chassis" && $2 == "Present") {
	if ($4 ~ /^[Nn]\057[Aa]$/ ) { status = "UNKNOWN"; statusInformation = "Unknown Status"; }
	else if ($4 ~ /^[Oo][Kk]$/) { status = "OK"; statusInformation = ""; }
	else if ($4 ~ /^[Nn]ot$/ && $5 ~ /^[Oo][Kk]$/) { status = "ALARM"; statusInformation = "Not OK"; }
	else { status = "UNKNOWN"; statusInformation = "Unknown Status"; }
}
/^ *Input Power Allocated to Servers/ {powerConsumption = $(NF-1);}
/^ *Input Power Allocated to Chassis/ {powerConsumption = powerConsumption + $(NF-1);}
END {
	print "MSHW;" status ";" statusInformation ";" powerConsumption ";"
}