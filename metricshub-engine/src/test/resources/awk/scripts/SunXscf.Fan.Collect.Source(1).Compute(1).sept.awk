($2 ~ /^FAN_[0-9A-Za-z#_]+$/) {
	fanID = $1;
	status = $4;
	print "MSHW;" fanID ";" fanID ";" status
}
