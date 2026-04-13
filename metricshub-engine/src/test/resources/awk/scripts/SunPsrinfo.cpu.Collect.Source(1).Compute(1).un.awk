{
	if ($1 == "Status" && $3 == "processor")
	{
	        processorId = $4
	        processorStatus = ""
	        processorType = ""
	        processorSpeed = ""
	}
	
	if ($1 == "Status" && $3 == "virtual" && $4 == "processor")
	{
			processorId = $5
			processorStatus = ""
			processorType = ""
			processorSpeed = ""
	}

	if ($1 == "Processor" && $2 == "has" && $3 == "been")
	{
	        processorStatus = $4
	}
	
	if ($2 == "since")
	{
			processorStatus = $1
	}
	if ($3 == "since")
	{
			processorStatus = $1 " " $2
	}

	if ($1 == "The" && $3 == "processor" && $4 == "operates" && length(processorId) > 0)
	{
	        processorType = $2
	        processorSpeed = $6
	
	        print "MSHW;" processorId ";" processorType ";" processorSpeed ";" processorStatus
	        
	        processorId = ""
	}
}