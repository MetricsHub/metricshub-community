{
	if ($0 ~ /^c[0-9]+t[0-9A-Z]+d[0-9]+/ || $0 ~ /^s+d[0-9]+/)
	{
		disk = $1
		product = ""
		transporterror = 0
		hardwareerror = 0
		mediaerror = 0
		devicenotready = 0
		nodevice = 0
		recoverable = 0
		illegalrequests = 0
		predictivefailureanalysis = 0
		statusinformation = ""
		totalerror = 0
		vendor = ""
		size = 0
	}
	
	if ($0 ~ /Product: /)
	{
		index1 = index($0, "Product: " ) + length("Product: ")
		product = substr($0, index1, length-index1)
		index1 = index(product, ":")
		if (index1 > 0)
		{
			product = substr(product, 1, index1-1)
			n = split(product, a, " ")
			product = ""
			for (i=1 ; i<n ; i++)
			{
				product = product a[i]
				if (i < n-1)
					product = product " "
			}
		}
	}
	
	if ($0 ~ /Vendor: /)
	{
		index1 = index($0, "Vendor: " ) + length("Vendor: ")
		vendor = substr($0, index1, length-index1)
		index1 = index(vendor, ":")
		if (index1 > 0)
		{
			vendor = substr(vendor, 1, index1-1)
			n = split(vendor, a, " ")
			vendor = ""
			for (i=1 ; i<n ; i++)
			{
				vendor = vendor a[i]
				if (i < n-1)
					vendor = vendor " "
			}
		}
	}
	
	if ($0 ~ /Size: /)
	{
		index1 = index($0, "<") + 1
		index2 = index($0, " bytes>")
		size = substr($0, index1, index2-index1)
	}
	
	if ($8 == "Transport" && $9 == "Errors:")
	{
		transporterror = $10
	}
	
	if ($5 == "Hard" && $6 == "Errors:")
	{
		hardwareerror = $7
	}
		
	if ($1 == "Media" && $2 == "Error:")
	{
		mediaerror = $3
	}
	
	if ($4 == "Device" && $5 == "Not" && $6 == "Ready:")
	{
		devicenotready = $7
	}
	
	if ($8 == "No" && $9 == "Device:")
	{
		nodevice = $10
	}
	
	if ($11 == "Recoverable:")
	{
		recoverable = $12
	}
	
	if ($1 == "Illegal" && $2 == "Request:")
	{
		illegalrequests = $3
	}

	if ($4 == "Predictive" && $5 == "Failure" && $6 == "Analysis:")
	{
		predictivefailureanalysis = $7
	}
	
	if ($0 ~/Illegal Request:/)
	{
		hardwareerror = hardwareerror - mediaerror - devicenotready - nodevice
		if (hardwareerror < 0)
		{
			hardwareerror = 0
		}
		
		if (predictivefailureanalysis > 0)
		{
			predictivefailureanalysis = "TRUE"
		}
		else
		{
			predictivefailureanalysis = "FALSE"
		}
		
		totalerror = recoverable + transporterror + mediaerror + devicenotready + nodevice + hardwareerror

# THE FOLLOWING LINE EXISTS 3 TIMES IN THIS CONNECTOR.  ALL 3 SHOULD BE IDENTICAL		
if (disk != "" && product !~ /SUN[0-9\.]+G/ && vendor != "EMC" && vendor != "LSILOGIC" && vendor != "DGC" && vendor != "StoreAge" && vendor != "SUN" && product != "StorEdge" && product != "StorEDGE" && product !~ /^HSV[0-9]/ && vendor != "EUROLOGC"  && vendor !~ /HP HSV[0-9]/ && product !~ /OPEN-V/ && product !~ /INF-01-00/ && size > 10)
		{
			print "MSHW;" disk ";" totalerror ";" hardwareerror ";" mediaerror ";" devicenotready ";" nodevice ";" transporterror ";" recoverable ";" illegalrequests ";" predictivefailureanalysis
		}

		product = ""
		transporterror = 0
		hardwareerror = 0
		mediaerror = 0
		devicenotready = 0
		nodevice = 0
		recoverable = 0
		illegalrequests = 0
		predictivefailureanalysis = 0
		statusinformation = ""
		totalerror = 0
		vendor = ""
		size = 0

	}

}