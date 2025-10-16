BEGIN { alreadyFoundList = ";"; }
{
	if (index(alreadyFoundList, ";" $0 ";") == 0)
	{
		print $0
		alreadyFoundList = alreadyFoundList $0 ";"
	}
}