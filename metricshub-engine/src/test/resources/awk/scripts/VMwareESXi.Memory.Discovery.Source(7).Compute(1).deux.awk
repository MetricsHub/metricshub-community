BEGIN {FS="[;]";ipmiFound="false";globalFound="false";}
{if ($2 ~ /Global/) {if ($5 > 0) {globalFound=("MSHW;" $0)
	                               }
	                  }
 else {print ("MSHW;" $0);ipmiFound="true";}
 }
END {if (ipmiFound == "false" && globalFound != "false") {print globalFound}}