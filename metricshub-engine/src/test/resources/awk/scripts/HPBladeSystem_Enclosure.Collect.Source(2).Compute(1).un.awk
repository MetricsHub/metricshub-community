BEGIN { FS = ";" }
{
	enclosureID = $1 "/" $2
	power[enclosureID] += $3
}
END {
	for (enclosureID in power)
	{
		print "MSHW;" enclosureID ";" power[enclosureID]
	}
}