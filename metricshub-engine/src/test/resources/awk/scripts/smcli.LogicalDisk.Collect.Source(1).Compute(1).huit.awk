BEGIN {logicalDriveID="";status="";size="";raidLevel="";logicalArray="";}
tolower($1) ~ /logical/ && tolower($2) ~ /drive/ && tolower($3) ~ /name/ {logicalDriveID=$4}
$1 ~ /[Ll]ogical/ && $2 ~/[Dd]rive/ && $3 ~ /[Ss]tatus/ {status = $4
                  if (NF > 4)  {status = (status " " $6)};
                  }
$1 ~ /[Cc]apacity/ {size=$2 ; gsub (/,/,"",size) ;
	                  if ($3 ~ /MB/) {size=size/1024}
	                  else if ($3 ~ /TB/) {size=size*1024}
	                  }
$1 ~ /[Aa]ssociated/ && $2 ~ /[Aa]rray/ {logicalArray = ("Array: " $3 )}
tolower($1) ~ /raid/ && $2 ~ /[Ll]evel/ && logicalDriveID != "" {raidLevel = $3
                                                           print ( "MSHW;" logicalDriveID ";" status ";" size ";" raidLevel ";Array: " logicalArray ";" status ";1;Logical Drive;;"  );
                                                           logicalDriveID="";status="";size="";raidLevel="";logicalArray="";
                                                          } 