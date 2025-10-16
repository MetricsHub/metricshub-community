BEGIN { GroupID = ""; SubDeviceID = ""; }
{
	if ($1 ~ /^CPUM/ || $1 ~ /^MEMB/)
	{
		SubDeviceID = $1;
	}
	else if ($1 !~ /^[0-9\.]+V[#L]?[0-9]?$/)
	{
		GroupID = $1;
		if (GroupID !~ /^MBU_[A-Z0-9a-z]+$/ && GroupID !~ /^CMU#[0-9]+$/)
		{
			SubDeviceID = "";
		}
	}
	else if ($0 ~ /[0-9\.]+V#?[0-9]? Power Supply Group:[0-9\.]+V/)
	{
		if ((GroupID ~ /^MBU_[A-Z0-9a-z]+$/ || GroupID ~ /^CMU#[0-9]+$/) && SubDeviceID != "")
		{
			VoltageID = GroupID "/" SubDeviceID "/" $1
			VoltageType = GroupID "/" SubDeviceID " - " $1
		}
		else
		{
			VoltageID = GroupID "/" $1
			VoltageType = GroupID " - " $1
		}
		NominalVoltage = substr($1, 1, length($1) - 1)
		UpperThreshold = NominalVoltage * 1.1 * 1000;
		LowerThreshold = NominalVoltage * 0.9 * 1000;
		CurrentVoltage = substr($4, 7, length($4) - 7);
		CurrentVoltage = CurrentVoltage * 1000;
		print "MSHW;" VoltageID ";" VoltageType ";" LowerThreshold ";" UpperThreshold ";" CurrentVoltage
	}
}
