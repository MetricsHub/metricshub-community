BEGIN {enclosureID="";partNumber="";serialNumber="";vendor="" }

$0 ~ /Controller.Drive/ {enclosureID="85"}
$0 ~ /Drive Enclosure [0-9]/ || $0 ~ /Controller Enclosure [0-9]/ || $0 ~ /Controller Module [0-9]/ {enclosureID=$3}
$0 ~ /Drive Expansion Enclosure [0-9]/ {enclosureID=$4}
$1 ~ /[Pp]art/ && $2 ~ /[Nn]umber/ {partNumber=$NF}
$1 ~ /[Ss]erial/ && $2 ~ /[Nn]umber/ {serialNumber=$NF}
$1 ~ /[Vv]endor/ && enclosureID != "" {vendor=$NF ;
                               print ("MSHW;"enclosureID";"partNumber";"serialNumber";"vendor";") ;
                               enclosureID="";partNumber="";serialNumber="";vendor="";

                               }
END {print ("MSHW;Global;Global;;;") }