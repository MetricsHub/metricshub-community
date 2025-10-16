BEGIN {outputMatch = 0}
$1 ~ /[Nn]ame/ && $2 ~ /[Tt]ype/ && $3 ~ /[Ss]tatus/ && $4 ~ /[Cc]apacity/ {outputMatch = 1}
outputMatch == 1 && $4 ~ /^[0-9.]+/ {
      enclosure = $1 ; gsub ("[.].+","",enclosure);
      displayID = $1 ; gsub ("^.+[.]","",displayID);
      size = $4 ;
      if (size ~ /[Gg]/) {gsub(/[Gg]/,"",size) }
      if (size ~ /[Mm]/) {gsub(/[Mm]/,"",size); size = size / 1024}
      if (size ~ /[Tt]/) {gsub(/[Tt]/,"",size); size = size * 1024}
#            MSHW;ID;Type;Status;Status;Capacity;Enclosure;DisplayID;
      print "MSHW;" $1 ";" $2 ";" $3 ";" $3 ";" size ";" enclosure ";" displayID ";"
    }
