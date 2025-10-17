/^ *[0-9A-Z]+ / {
	if ($1 != "DID" && $2 != "-")
	{
		printf("MSHW;Domain;%s;Main;;;;", $1);
		for (i=2 ; i<NF ; i++)
		{
			printf("%s ", $i);
		}
		if (NF > 1)
		{
			printf("%s\n", $NF);
		}
	}
}