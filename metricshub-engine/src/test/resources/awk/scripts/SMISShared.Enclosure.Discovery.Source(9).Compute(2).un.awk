BEGIN { FS=";" }
{
	computerID = $1
	elementName = $2
	chassisID = $3
	vendor1 = $4
	model1 = $5
	serialNumber1 = $6
	vendor2 = $7
	model2 = $8
	serialNumber2 = $9
	
	if (vendor2 != "")
	{
		vendor = vendor2
	}
	else
	{
		vendor = vendor1
	}
	
	if (model2 != "")
	{
		model = model2
	}
	else
	{
		model = model1
	}
	
	if (length(serialNumber2) > 10)
	{
		serialNumber = serialNumber2
	}
	else
	{
		serialNumber = serialNumber1
	}
	
	if (model != "")
	{
		model = model " - " elementName
	}
	else
	{
		model =  elementName
	}
	
	print "MSHW;" computerID ";" chassisID ";" vendor ";" model ";" serialNumber
}