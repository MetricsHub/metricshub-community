BEGIN {
	path = "";
}
{
	gsub("[ ;:\*]", " ");
	
	if ($1 == "SPARC" || $1 == "+")
	{
		gsub(" +", " ");
		print $0;
	}
	else if (NF > 0)
	{
		name = $1;
		
		depth = (index($0, name) - 1) / 4;
		if (depth < 0) { indent = 0; }
		
		split(path, pathArray, "/");
		path = "";
		for (i=1 ; i<depth ; i++)
		{
			path = path pathArray[i] "/";
		}
		path = path name;
		
		gsub(" +", " ");
		
		ioxIndex = index(path, "IOX@");
		if (ioxIndex > 0)
		{
			print substr(path, ioxIndex, length(path) - ioxIndex + 1) " " $0
		}
		else
		{
			print path " " $0 " "
		}
	}
}