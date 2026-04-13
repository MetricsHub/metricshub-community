($1 == "PWR" && $3 ~ /^PS-[0-9]$/ && NF > 4) {
  if ($4 ~ /^Slot$/ && $5 ~ /^Empty$/) {next}
  else if ($4 ~ /^Redundant$/) {status="OK";statusInformation="Redundant"}
  else if ($4 ~ /^Online$/) {status="OK";statusInformation="Online"}
  else if ($4 ~ /^Failed$/) {status="ALARM";statusInformation="Failed"}
  else { status = "UNKNOWN"; statusInformation = "Unknown Status"; }
	if ($(NF-1) ~ /^[Nn]ot$/ && $NF ~ /^[Oo][Kk]$/) { status = "ALARM"; statusInformation = statusInformation " - No AC"; }
	print "MSHW;" $3 ";" status ";" statusInformation ";"
}