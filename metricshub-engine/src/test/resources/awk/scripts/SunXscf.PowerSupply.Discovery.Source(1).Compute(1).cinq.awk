($2 ~ /^PSU#[0-9]+$/) {
	powersupplyID = $1;
	status = $4;
	SerialNumber = $6;
	getline;
	FruPartNumber = $3 " " $4 " " $5;
	getline;
	if ($2 ~ /Power_Status/ && $3 ~ /[Ii]nput/ && $4 ~ /[Ff]ail/ && status == "Normal") {status = "Input Fail"};
	print "MSHW;" powersupplyID ";;" SerialNumber ";FRU: " FruPartNumber ";" status
}
($2 ~ /^PS[0-9]$/ && $3 == "Status") {
	powersupplyID = $1;
	status = $4;
	SerialNumber = $6;
	getline;
	FruPartNumber = $3 " " $4 " " $5;
	if (index(powersupplyID, "/") > 0)
	{
		parentID = substr(powersupplyID, 1, index(powersupplyID, "/") - 1)
	}
	else
	{
		parentID = "Main";
	}
	print "MSHW;" powersupplyID ";" parentID ";" SerialNumber ";" FruPartNumber ";" status
}