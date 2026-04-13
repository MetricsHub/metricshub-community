BEGIN {
	WWN = "";
	PortNumber = "";
	Model = "";
}

($1 == "Port" && $2 == "WWN") { WWN = $4; gsub(":", "", WWN); }
	
($1 == "Model") { Model = $3; }

(NF == 0 && WWN != "") {
	print "MSHW;" Model ";" WWN ";"
	WWN = "";
	Model = "";
}