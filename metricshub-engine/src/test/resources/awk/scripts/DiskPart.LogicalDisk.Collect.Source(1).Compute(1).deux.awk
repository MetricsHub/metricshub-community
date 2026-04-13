BEGIN {
	foundHeader = 0;
}
($1 == "Volume" && $2 == "###" && $3 == "Ltr" && $4 == "Label" && $5 == "Fs" && $6 == "Type" && $7 == "Size" && $8 == "Status" && $9 == "Info") {
	ltrIndex = index($0, "Ltr")
	labelIndex = index($0, "Label")
	fsIndex = index($0, "Fs")
	typeIndex = index($0, "Type")
	sizeIndex = index($0, "Size")
	statusIndex = index($0, "Status")
	infoIndex = index($0, "Info")
	foundHeader = 1;
}
($1 == "Volume" && $2 ~ /^[0-9]+$/ && foundHeader == 1) {

	# Get the fields
	volumeID = $2;
	letter = substr($0, ltrIndex, 3);
	label = substr($0, labelIndex, fsIndex - labelIndex);
	fs = substr($0, fsIndex, typeIndex - fsIndex);
	type = substr($0, typeIndex, sizeIndex - typeIndex);
	sizeT = substr($0, sizeIndex, statusIndex - sizeIndex);
	status = substr($0, statusIndex, infoIndex - statusIndex);
	info = substr($0, infoIndex, length($0) - infoIndex + 1);
	
	# Do some processing, remove unnecessary white spaces
	gsub(" ", "", letter);
	sub("^ +", "", label); sub(" +$", "", label);
	sub("^ +", "", fs); sub(" +$", "", fs);
	sub("^ +", "", type); sub(" +$", "", type);
	gsub(" ", "", sizeT);
	sub("^ +", "", status); sub(" +$", "", status);
	sub("^ +", "", info); sub(" +$", "", info);

	# Convert size to bytes
	size = "";
	if (substr(sizeT, length(sizeT), 1) == "B")
	{
		size = substr(sizeT, 1, length(sizeT) - 1);
		
		# Handle unit multipliers
		if (substr(size, length(size), 1) == "K")
		{
			size = substr(size, 1, length(size) - 1) * 1024;
		}
		else if (substr(size, length(size), 1) == "M")
		{
			size = substr(size, 1, length(size) - 1) * 1024 * 1024;
		}
		else if (substr(size, length(size), 1) == "G")
		{
			size = substr(size, 1, length(size) - 1) * 1024 * 1024 * 1024;
		}
		else if (substr(size, length(size), 1) == "T")
		{
			size = substr(size, 1, length(size) - 1) * 1024 * 1024 * 1024 * 1024;
		}
		
		# Make sure we got a number
		if (size !~ /^[0-9]+$/)
		{
			size = "";
		}
	}
	
	# Add a colon to the drive letter, if any
	if (letter ~ /^[A-Z]$/)
	{
		letter = letter ":"
	}
	
	# Build the displayID from label and letter
	if (letter != "" && label != "")
	{
		displayID = letter " - " label;
	}
	else if (letter != "" && label == "")
	{
		displayID = letter;
	}
	else if (letter == "" && label != "")
	{
		displayID = label;
	}
	else
	{
		displayID = "";
	}
	
	# Replace "Partition" type with nothing
	if (type == "Partition")
	{
		type = "";
	}
	
	print "MSHW;" volumeID ";" displayID ";" letter ";" type ";" fs ";" size ";" status ";" info ";"
}