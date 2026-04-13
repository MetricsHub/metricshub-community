BEGIN { FS = "="; model = ""; serialNumber = ""; }
$1 ~ /^System Model *$/ { model = "Dell" $2; }
$1 ~ /^Service Tag *$/ { serialNumber = $2; }
END { gsub (/Power[Ee]dge/,"PE-",model);gsub (/Control ?[Pp]anel/,"",model);
	    print "MSHW;" model ";" serialNumber";" }