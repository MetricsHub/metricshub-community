BEGIN {
	pdtUsed = "";
	pdtTotal = "";
}
/^ *Memory Error Log Summary *$/,/^ *Page Deallocation Table \(PDT\) *$/ {
	if ($NF ~ /^[0-9]+$/)
	{
		if ($2 ~ /^0x[0-9a-fA-F]+$/)
		{
			print "MSHW;" $1 ";OK;" $3 " Errors Occured;" $NF
		}
		else if ($3 ~ /^0x[0-9a-fA-F]+$/)
		{
			print "MSHW;" $1 " " $2 ";OK;" $4 " Errors Occured;" $NF
		}
	}
}
/^ *PDT Entries Used: +[0-9]+ *$/ {
	pdtUsed = $NF;
}
/^ *PDT Total Size: +[0-9]+ *$/ {
	pdtTotal = $NF;
}
END {
	if (pdtUsed != "" && pdtTotal != "")
	{
		pdtPercentUsed = pdtUsed / pdtTotal * 100;
		if (pdtPercentUsed > 70)
		{
			pdtStatus = "ALARM";
		}
		else if (pdtPercentUsed > 50)
		{
			pdtStatus = "WARN";
		}
		else
		{
			pdtStatus = "OK";
		}
		printf("MSHW;PDT;%s;%d%% Used;%d\n", pdtStatus, pdtPercentUsed, pdtUsed);
	}
}
