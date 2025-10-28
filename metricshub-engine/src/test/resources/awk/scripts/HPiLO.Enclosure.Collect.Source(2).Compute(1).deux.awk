BEGIN {
	powerState = "";
	powerUsage = "";
}
/^System Power state:/ {
	powerState = $4;
}
/^System Power state +:/ {
	powerState = $5;
}
/.*[a-z].*System power state:/ {
	systemPowerStateIndex = index($0, "System power state:");
	if (systemPowerStateIndex > 1)
	{
		systemPowerStateInformation = substr($0, systemPowerStateIndex, length($0) - systemPowerStateIndex + 1);
		split(systemPowerStateInformation, systemPowerStateWord);
		powerState = systemPowerStateWord[4];
	}
}
/^System Power usage:.*Watts/ {
	powerUsage = $4;
}
END {
	print "MSHW;" powerState ";" powerUsage ";"
}