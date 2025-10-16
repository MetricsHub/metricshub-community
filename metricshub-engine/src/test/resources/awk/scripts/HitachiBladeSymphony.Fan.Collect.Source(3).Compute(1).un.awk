BEGIN { FS = ";" }
$1 ~ /^[0-9]+$/ {print ("MSHW;"$9";Fan Module "$9"-Fan 1;"$3";FM"$9"-1;Fan;"$2";;")
                 print ("MSHW;"$9";Fan Module "$9"-Fan 2;"$5";FM"$9"-2;Fan;"$4";;")
                 print ("MSHW;"$9";Fan Module "$9"-Fan 3;"$7";FM"$9"-3;Fan;"$6";;")
                }