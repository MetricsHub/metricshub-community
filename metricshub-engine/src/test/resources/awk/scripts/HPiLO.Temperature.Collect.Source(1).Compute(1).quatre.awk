BEGIN {
	temperatureStatus = "";
}
/^Temperature +: / {
	if (length($0) > 34)
	{
		temperatureStatus = substr($0, 21, 14)
	}
	else
	{
		temperatureStatus = substr($0, 21, length($0) - 20)
	}
	while (substr(temperatureStatus, length(temperatureStatus), 1) == " ") { temperatureStatus = substr(temperatureStatus, 1, length(temperatureStatus) - 1); }
}
/^Temperature status +: / {
	if (length($0) > 40)
	{
		temperatureStatus = substr($0, 27, 14)
	}
	else
	{
		temperatureStatus = substr($0, 27, length($0) - 26)
	}
	while (substr(temperatureStatus, length(temperatureStatus), 1) == " ") { temperatureStatus = substr(temperatureStatus, 1, length(temperatureStatus) - 1); }
}
END {
	print "MSHW;" temperatureStatus ";"
}