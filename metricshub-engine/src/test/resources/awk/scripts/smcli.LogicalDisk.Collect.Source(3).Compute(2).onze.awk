BEGIN {FS="[;]" ; size=0; freeCapacity=0;}
$1 ~ /MSHW/  { size = size + $5 ;
	             if ($9 ~ /Unassigned/)  {freeCapacity=freeCapacity+$5} ;
	           }  
END {print ("MSHW;Primordial Array;Optimal;" size ";Primordial Array;;Optimal;0;Array;" freeCapacity ";")}