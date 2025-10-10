($1 ~ /hdisk[0-9]/) {
	ID=$1;
	size = $(NF-1);
	gsub("[^0-9.]", "", size);
	if ($NF ~ /MB/) { size = size / 1024; }
	if ($NF ~ /TB/) { size = size * 1024; }
}

(ID != "" && $1 ~ /Manufacturer/) { make = $1; gsub (".+\\.", "", make); }

(ID != "" && $1 ~ /Machine/ && $2 ~ /Type/) { model = $NF; gsub(".+\\.", "", model); }

(ID != "" && $1 ~ /FRU/ && $2 ~ /Number/) { FRU = $NF; gsub(".+\\.", "", FRU); }

(ID != "" && $1 ~ /Serial/ && $2 ~ /Number/) {
	Serial = $NF;
	gsub(".+\\.", "", Serial);
	if (FRU != "")
	{
		print ("MSHW;" ID ";" size ";" make "-" model " FRU: " FRU ";" Serial ";");
	}
	ID=""; size=""; make=""; model=""; FRU=""; Serial="";
}
