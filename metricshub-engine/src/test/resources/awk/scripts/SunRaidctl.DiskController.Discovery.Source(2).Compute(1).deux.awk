BEGIN {controller="";logicalDrive="";disk="";}
$2 ~ /"/ {controller=$1}
$1 ~ /c[0-9]t[0-9]d[0-9]/ {logicalDrive=$1; 
                           for (i=3; i<=(NF-2); i++) { if ($i ~ /[0-9]\056[0-9]\056[0-9]/) {print ("MSHW;"controller";"logicalDrive";"$i);}}
                           }