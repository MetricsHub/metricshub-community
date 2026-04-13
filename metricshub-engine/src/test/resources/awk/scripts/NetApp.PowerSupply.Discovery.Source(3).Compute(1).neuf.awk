BEGIN {FS="[;]";individualFound="false";globalFound="false";}
(NF >= 2) && ($2 ~ /.+/) {print ("MSHW;"$1";"$2";"$3";")
                          individualFound="true"
                          }
(NF == 1) && ($1 ~ /[0-9]+/) {globalFound = "true"}
(NF == 2) && ($1 ~ /[0-9]+/) && ($2 ~ /^$/) {globalFound = "true"}
END {if (individualFound == "false" && globalFound != "false") {print "MSHW;Global;;Global;"}}