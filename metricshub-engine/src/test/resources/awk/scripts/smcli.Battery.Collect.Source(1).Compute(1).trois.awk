BEGIN {enclosureID="";batteryID="";batteryStatus="";esmID="";esmStatus="";drawerID="";drawerStatus="";}

$0 ~ /Controller.Drive/ {enclosureID="85"}
$0 ~ /Drive Enclosure [0-9]/ || $0 ~ /Controller Enclosure [0-9]/ || $0 ~ /Controller Module [0-9]/ {enclosureID=$3}
$0 ~ /Drive Expansion Enclosure [0-9]/ {enclosureID=$4}
$1 ~ /[Bb]attery/ && $2 ~ /[Ss]tatus/ {batteryStatus=$3 ; batteryID = "";
                           if (NF > 3)  {batteryStatus = (batteryStatus " " $4)};
                           }
$1 ~ /[Ll]ocation/ && batteryStatus !="" {batteryID=($2" "$3); 
                                          if (NF > 3)  {batteryID = (batteryID " " $4)};
                                          if (NF > 4)  {batteryID = (batteryID " " $5)};
                                          if (NF > 5)  {batteryID = (batteryID " " $6)};
                                          if (NF > 6)  {batteryID = (batteryID " " $7)};
                                          print ("MSHW;Battery;"enclosureID";"enclosureID " - " batteryID";"batteryStatus";"batteryStatus";") ;
                                          batteryID="";batteryStatus="";
                                          }

($1 ~ /ESM/ && $2 ~ /[Cc]ard/) && $3 ~ /[Ss]tatus/ {esmStatus=$4 ; esmID="";
                           if (NF > 4)  {esmStatus = (esmStatus " " $5)};
                           }
                           
$1 ~ /[Ll]ocation/ && esmStatus !="" {esmID=($2" "$3);
                                          if (NF > 3)  {esmID = (esmID " " $4)};
                                          if (NF > 4)  {esmID = (esmID " " $5)};
                                          if (NF > 5)  {esmID = (esmID " " $6)};
                                          print ("MSHW;ESM;"enclosureID";"enclosureID " - " esmID";"esmStatus";"esmStatus";") ;
                                          esmID="";esmStatus="";
                                          }

$1 ~ /[Dd]rawer/ && $2 ~ /[Ss]tatus/ {drawerStatus = $3
	                                    if (NF > 3)  {drawerStatus = (drawerStatus " " $4)};}

$1 ~ /[Pp]osition/ && drawerStatus !="" {drawerID = $2 
	                                        print ("MSHW;Drawer;"enclosureID";"enclosureID " - " drawerID";"drawerStatus";"drawerStatus";") ;
	                                       drawerID="";drawerStatus="";
	                                      }