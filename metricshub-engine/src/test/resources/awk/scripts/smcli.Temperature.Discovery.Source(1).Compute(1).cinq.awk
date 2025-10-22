BEGIN {enclosureID="";tempID="";status="";notAvailableCounter=1;}

$0 ~ /Controller.Drive/ {enclosureID="85"}
$0 ~ /Drive Enclosure [0-9]/ || $0 ~ /Controller Enclosure [0-9]/ || $0 ~ /Controller Module [0-9]/ {enclosureID=$3}
$0 ~ /Drive Expansion Enclosure [0-9]/ {enclosureID=$4}

$1 ~ /[Tt]emp/ &&  $2 ~ /[Ss]ensor/ && $3 ~ /[Ss]tatus/ {
                           status=$4 ;
                           if (NF > 4)  {status = (status " " $5)};
                           }
$1 ~ /[Ll]ocation/ && status !="" {
                           tempID =$2; 
                           if (NF > 2)  {tempID = (tempID " " $3)};
                           if (NF > 3)  {tempID = (tempID " " $4)};
                           if (NF > 4)  {tempID = (tempID " " $5)};
                           if (tempID ~ /[Nn]ot [Aa]vailable/) { tempID = ("Location Not Specified " notAvailableCounter) ; notAvailableCounter = (notAvailableCounter + 1) ;} ;
                           print ("MSHW;"enclosureID";"enclosureID " - " tempID";"status";"status";") ;
                              tempID="";status="";
                           } 