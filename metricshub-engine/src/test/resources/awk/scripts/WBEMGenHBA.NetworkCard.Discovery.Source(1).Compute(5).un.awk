BEGIN {
	FS = "[;]"
	Column=6
}
{
	n = split($Column, PortWWN, "|");
	printf "MSHW;"
	for (i = 1 ; i <= NF-1 ; i++) {
		if (i == Column) {
			for (j = 1 ; j <= n-1 ; j++) {
				printf "%02X", PortWWN[j]
			}
      printf ";"
    } else {
			printf $i
			printf ";"
		}
	}
 print ""
}
