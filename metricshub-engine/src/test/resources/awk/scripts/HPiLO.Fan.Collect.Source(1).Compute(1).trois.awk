BEGIN {
	doubleFanArray = 0;
}
/Fans +State +Fans +State/ {
	doubleFanArray = 1;
}
/^[0-9]+ .* \| / {
	fanID = $1
	fanLocation = ""
	pipeIndex = index($0, "|")
	status = substr($0, pipeIndex + 2, length($0) - pipeIndex - 1)
	while (substr(status, length(status), 1) == " ") { status = substr(status, 1, length(status) - 1); }
	print "MSHW;" fanID ";" fanLocation ";" status ";"
}
($0 ~ /^Fan  *[0-9]+/ && doubleFanArray == 0) {
	fanID = $2
	if (index($0, "(") > 1)
	{
		fanLocation = $3
	}
	else
	{
		fanLocation = ""
	}
	status = substr($0, 31, length($0) - 30)
	while (substr(status, length(status), 1) == " ") { status = substr(status, 1, length(status) - 1); }
	print "MSHW;" fanID ";" fanLocation ";" status ";"
}
/[A-Za-z]+ Fan [0-9]+/ {
	fanID = $3;
	fanLocation = $1
	status = substr($0, 31, length($0) - 30)
	while (substr(status, length(status), 1) == " ") { status = substr(status, 1, length(status) - 1); }
	print "MSHW;" fanID ";" fanLocation ";" status ";"	
}
($0 ~ /^Fan  *[0-9]+/ && doubleFanArray == 1) {
	fanID = $2
	if (index($0, "(") > 1)
	{
		fanLocation = $3
	}
	else
	{
		fanLocation = ""
	}
	if (length($0) > 40)
	{
		status = substr($0, 21, 20)
	}
	else
	{
		status = substr($0, 21, length($0) - 20)
	}
	while (substr(status, length(status), 1) == " ") { status = substr(status, 1, length(status) - 1); }
	print "MSHW;" fanID ";" fanLocation ";" status ";"
	
	if (length($0) > 40)
	{
		secondFanInformation = substr($0, 41, length($0)-40)
		if (split(secondFanInformation, secondFanWord) >= 3)
		{
			fanID = secondFanWord[2]
			if (index(secondFanInformation, "(") > 1)
			{
				fanLocation = secondFanWord[3]
			}
			else
			{
				fanLocation = ""
			}
			status = substr(secondFanInformation, 21, length(secondFanInformation) - 20)
			while (substr(status, length(status), 1) == " ") { status = substr(status, 1, length(status) - 1); }
			print "MSHW;" fanID ";" fanLocation ";" status ";"
		}
	}
}