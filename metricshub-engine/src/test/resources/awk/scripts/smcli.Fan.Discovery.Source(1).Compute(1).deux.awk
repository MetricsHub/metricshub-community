BEGIN {enclosureID="";fanID="";status="";}
$0 ~ /Controller.Drive/ {enclosureID="85"}
$0 ~ /Drive Enclosure [0-9]/ || $0 ~ /Controller Enclosure [0-9]/ || $0 ~ /Controller Module [0-9]/ {enclosureID=$3}
$0 ~ /Drive Expansion Enclosure [0-9]/ {enclosureID=$4}
$1 ~ /[Ff]an/ && $2 ~ /[Ss]tatus/ {status=$3 ;
                           if (NF > 3)  {status = (status " " $4)};
                           }
$1 ~ /[Ll]ocation/ && status !="" {fanID =$NF; gsub (/[()]/,"",fanID) ;
                           print ("MSHW;"enclosureID";"enclosureID " - " fanID";"status";"status";") ;
                              fanID="";status="";
                              }