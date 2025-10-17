BEGIN                  { FS = ":"; model = ""; serialNumber = ""; }
$1 ~ /Enclosure Type/  { model = $2; }
$1 ~ /Serial Number/   { serialNumber = $2; }
END                    { print "MSHW;" model ";" serialNumber; } 