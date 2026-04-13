BEGIN { FS=";" }
{
	deviceID = $1
	parentID = $2
	grandParentID = $5
	
	if (grandParentID != "")
	{
		print deviceID ";" grandParentID
	}
	else
	{
		print deviceID ";" parentID
	}
}