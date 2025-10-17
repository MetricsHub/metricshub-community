BEGIN {FS=";";}
NF > 4 && $1 != "" {if ($1 ~ /^[0-9]+$/) {print "MSHW;" $2 ";" $3 ";" $5 ";"}
                       else  {print "MSHW;" $1 ";" $2 ";" $4 ";"}
                   }   