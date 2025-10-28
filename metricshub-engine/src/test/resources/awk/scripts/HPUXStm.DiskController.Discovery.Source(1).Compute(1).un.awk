BEGIN {
	hardwarePath = "";
	productName = "";
}
/^Product +:/ {
	colonIndex = index($0, ":");
	if (colonIndex > 10)
	{
		productName = substr($0, colonIndex + 2)
	}
}
/^Hardware [pP]ath +:/ {
	hardwarePath = $4
	if (productName != "")
	{
		print "MSHW;" hardwarePath ";" productName;
		productName = "";
	}
}