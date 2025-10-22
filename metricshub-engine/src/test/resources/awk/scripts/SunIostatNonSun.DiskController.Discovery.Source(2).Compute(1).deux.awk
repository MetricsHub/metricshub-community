{
	if ($0 ~ /^c[0-9]+t[0-9A-Z]+d[0-9]+/ || $0 ~ /^s+d[0-9]+/)
	{
		disk = $1
		size = "0"
		vendor = ""
		product = ""
		model = ""
		productmodel = ""
		serial = ""
		if (disk ~ /^c[0-9]+t[0-9A-Z]+d[0-9]+/)
		{
			targetIndex = index(disk, "t")
			controller = substr(disk, 1, targetIndex - 1)
		}
		else
		{
			controller = "Other"
		}
	}
	
	if ($0 ~ /Size: /)
	{
		index1 = index($0, "<") + 1
		index2 = index($0, " bytes>")
		size = substr($0, index1, index2-index1)
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
	
	if ($0 ~ /Model: /)
	{
		index1 = index($0, "Model: " ) + length("Model: ")
		model = substr($0, index1, length-index1)
		index1 = index(model, ":")
		if (index1 > 0)
		{
			model = substr(model, 1, index1-1)
			n = split(model, a, " ")
			model = ""
			for (i=1 ; i<n ; i++)
			{
				model = model a[i]
				if (i < n-1)
					model = model " "
			}
		}
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
	
	if ($0 ~ /Serial No: /)
	{
		index1 = index($0, "Serial No: " ) + length("Serial No: ")
		serial = substr($0, index1, length-index1)
		index1 = index(serial, ":")
		if (index1 > 0)
		{
			serial = substr(serial, 1, index1-1)
			n = split(serial, a, " ")
			serial = ""
			for (i=1 ; i<n ; i++)
			{
				serial = serial a[i]
				if (i < n-1)
					serial = serial " "
			}
		}
		
	}
	
	if ($0 ~/Illegal Request:/)
	{
		if (product != "" && model != "")
		{
			productmodel = product " " model
		}
		else
		{
			productmodel = product model
		}
		
# THE FOLLOWING LINE EXISTS 3 TIMES IN THIS CONNECTOR.  ALL 3 SHOULD BE IDENTICAL
if (disk != "" && product !~ /SUN[0-9\.]+G/ && vendor != "EMC" && vendor != "LSILOGIC" && vendor != "DGC" && vendor != "StoreAge" && vendor != "SUN" && product != "StorEdge" && product != "StorEDGE" && product !~ /^HSV[0-9]/ && vendor != "EUROLOGC"  && vendor !~ /HP HSV[0-9]/ && product !~ /OPEN-V/ && product !~ /INF-01-00/ && size > 10)
		{
			print "MSHW;" disk ";" controller ";" size ";" vendor " " productmodel ";" serial
		}
		
		disk = ""
		size = "0"
		vendor = ""
		product = ""
		model = ""
		productmodel = ""
		serial = ""

	}

}