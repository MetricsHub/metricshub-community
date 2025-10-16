/MS_HW_lscfg_Start/ {
	nicID = "";
	model = "";
	MACaddress = "";
	nicType = "";
	hwLocationCode = "";
}
($1 ~ /^ent[0-9]/) {
	nicID = $1;
	hwLocationCode = $2;
	getline;
	gsub("^ +", "");
	model = $0;
}
/^ *Network Address/ {
	gsub("^ *Network Address\\.+", "");
	MACaddress = $1;
	if (length(MACaddress) == 12)
	{
		MACaddress = substr(MACaddress, 1, 2) ":" substr(MACaddress, 3, 2) ":" substr(MACaddress, 5, 2) ":" substr(MACaddress, 7, 2) ":" substr(MACaddress, 9, 2) ":" substr(MACaddress, 11, 2);
	}
}
/MS_HW_lscfg_End/ {
	if (nicID != "")
	{
		if (model ~ /Virtual/ || model ~ /EtherChannel/ || model ~ /VLAN/)
		{
			nicType = "Logical";
		}
		else
		{
			nicType = "Physical";
		}
		print "MSHW;" nicID ";" model ";" nicType ";" MACaddress ";Hardware Location Code: " hwLocationCode ";"
	}

	nicID = "";
	model = "";
	MACaddress = "";
	nicType = "";
	hwLocationCode = "";

}
