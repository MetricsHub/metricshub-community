BEGIN {enclosure="";slot="";status="";size="";;productID="";serialNumber="";vendor="";driveMode="";associatedArray=""}

$1 ~ /[Dd]rive/ && $2 ~ /[Aa]t/ && $3 ~ /[Ee]nclosure/ {enclosure = $4 ; gsub (/,/,"",enclosure) ; slot = $NF ;  ;
	                                                      if ($5 ~ /[Dd]rawer/) {slot = $6 "-" slot; gsub (/,/,"",slot);}
	                                                     }

$1 ~ /[Ss]tatus/ {status = $2 ;
                  if (NF > 2)  {status = (status " " $3)};
                  }
$1 ~ /[Mm]ode/    {driveMode = $2 ;
                  if (NF > 2)  {driveMode = (driveMode " " $3)};
                  }                  
$1 ~ /[Aa]ssociated/ && $2 ~ /[Aa]rray/   {associatedArray = $3 ; }
$0 ~ /[Rr]aw [Cc]apacity/ && $NF ~ /[GgTtMm][Bb]/ {size = $(NF-1) ; gsub (/,/,"",size) 
	                                             if ($NF ~ /MB/) {size=size/1024}
	                                             else if ($NF ~ /TB/) {size=size*1024}
	                                             }
$0 ~ /[Pp]roduct ID/ { productID=$3; 
                   if (NF > 3)  {productID = (productID " " $4)} ;
                   if (NF > 4)  {productID = (productID " " $5)} ;
                  } 
$0 ~ /[Ss]erial [Nn]umber/ {serialNumber = $NF ;}
($1 ~ /[Vv]endor/ || $1 ~ /[Mm]anufacturer/ ) && slot != ""   {vendor = $2 ;
                                  if (NF > 2)  {vendor = (vendor " " $3)};
                                  if (NF > 3)  {vendor = (vendor " " $4)};
                                  if (vendor ~ /[Nn]ot [Aa]vailable/) {print ("MSHW;" enclosure ";" enclosure "-" slot ";" status ";" size ";"productID ";" serialNumber ";" status ";" driveMode ";Array: " associatedArray ";")} 
                                  else {print ("MSHW;" enclosure ";" enclosure "-" slot ";" status ";" size ";" vendor " "productID ";" serialNumber ";" status ";" driveMode ";Array: " associatedArray ";")}; 
                                  enclosure="";slot="";status="";size="";productID="";serialNumber="";vendor="";driveMode="";associatedArray="";
                                  }