BEGIN {FS="[;]"}
{ if ($1 in tags2) {
	                  if (tags3[$1] < $3) {tags3[$1] = ($3);}
	                  if ($3 > 5) {if (tags2[$1]=="") {tags2[$1] = $2}
	                               else {tags2[$1] = (tags2[$1] " - " $2)}
	                              }
	                 }
	else {if ($3 > 0) {tags3[$1] = ($3)}; 
		    if ($3 > 5) {tags2[$1] = $2};
		   }
}
END { for (x in tags3)
      print ("MSHW;"x";"tags2[x]";"tags3[x]";") 
    }
    
