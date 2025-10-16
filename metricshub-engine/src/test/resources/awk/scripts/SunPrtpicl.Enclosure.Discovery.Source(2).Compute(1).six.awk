BEGIN {
	path = "";
}
{
	name = $1;
	ID = substr($3, 1, length($3) - 1);
	className = substr($2, 2, length($2) - 2);
	
	depth = (index($0, name) - 1) / 4;
	if (depth < 0) { indent = 0; }
	
	split(path, pathArray, "/");
	path = "";
	for (i=1 ; i<depth ; i++)
	{
		path = path pathArray[i] "/";
	}
	path = path name;
	
	print "MSHW;" ID ";" className ";" path
}