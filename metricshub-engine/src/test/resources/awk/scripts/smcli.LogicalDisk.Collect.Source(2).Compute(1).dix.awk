BEGIN {arrayName="";status="";size="";raidLevel="";LDSection="false";skip="true"}

$0 ~ /ARRAYS---------/ {skip="false"}
skip=="true" {next}

tolower($1) ~ /name/ && LDSection =="false" {arrayName=$NF;}
$1 ~ /ARRAY/ && $3 ~ /RAID/  {arrayName=$2 ; raidLevel=$4 ; gsub (/\051/,"",raidLevel) ;}

$1 ~ /[Ss]tatus/ && arrayName != "" {status = $2 ; 
                                     if (NF > 2)  {status = (status " " $3)};
                                    }
$1 ~ /[Aa]rray/ && $2 ~ /[Ss]tatus/ && arrayName != "" {status = $3 ; 
                                     if (NF > 3)  {status = (status " " $4)};
                                    }
tolower($1) ~ /raid/ && $2 ~ /[Ll]evel/  {raidLevel=$NF}
tolower($1) ~ /associated/ && tolower($2) ~ /logical/ && tolower($3) ~ /drives/  {LDSection="true" ; arraySize="0" ; freeCapacity="0" ; }

LDSection=="true" && $NF ~ /[GgTtMm][Bb]/ {size = $(NF-1) ; gsub (/,/,"",size) ; 
	                                         if ($NF ~ /MB/) {size=size/1024} 
	                                         else if ($NF ~ /TB/) {size=size*1024} ;
                                           arraySize = arraySize + size ;
                                           if (tolower($1) ~ /free/ && tolower($2) ~ /capacity/) {freeCapacity = freeCapacity + size }
	                                        }

LDSection=="true" && tolower($1) ~ /associated/ && tolower($2) ~ /drives/ {print ( "MSHW;ARRAY_" arrayName ";" status ";" arraySize ";" raidLevel ";" ";" status ";0;Array;" freeCapacity ";"  );

	                                                                          LDSection="false" ; status=""; raidLevel="";arrayName=""; 
	                                                                         }