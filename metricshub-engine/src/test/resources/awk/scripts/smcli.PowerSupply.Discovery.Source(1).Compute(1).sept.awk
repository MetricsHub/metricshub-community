BEGIN {enclosureID="";psuID="";status="";}
$0 ~ /Controller.Drive/ {enclosureID="85"}
$0 ~ /Drive Enclosure [0-9]/ || $0 ~ /Controller Enclosure [0-9]/ || $0 ~ /Controller Module [0-9]/ {enclosureID=$3}
$0 ~ /Drive Expansion Enclosure [0-9]/ {enclosureID=$4}
$1 ~ /[Pp]ower/ && $2 ~ /[Ss]upply/ && $3 ~ /[Ss]tatus/ {status=$4 ;
                           if (NF > 4)  {status = (status " " $5)};
                           }
$1 ~ /[Ll]ocation/ && status !="" {psuID =$NF; gsub (/[()]/,"",psuID) ;
                           print ("MSHW;"enclosureID";"enclosureID " - " psuID";"status";"status";") ;
                              psuID="";status="";
                              }