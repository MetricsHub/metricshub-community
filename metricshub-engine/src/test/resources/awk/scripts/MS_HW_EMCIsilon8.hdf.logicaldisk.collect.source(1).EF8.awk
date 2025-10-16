BEGIN { activate = 0 ; poolEnum = 0; id = 0}
$1 ~ /^OK$/ && $2 ~ /=/ && $3 ~ /Ok/ {exit}
$2 !~ /^Drives/ && $2 != "" && activate==1 {
				 poolEnum = poolEnum + 1
	       Health[poolEnum] = $2
	       AdditionalInformation[poolEnum] = $3 $4 $5
	       # make sure that the pool name is not divided into two lines
	       if($1 ~ /-$/) {key=$1; getline; $id=key $1;} else $id=$1;
				 ID[poolEnum] = $id
	     }

$1 ~ /^[-]+$/ && $2 ~ /^[-]+$/ && $3 ~ /^[-]+$/ && $4 ~ /^[-]+$/ && $5 ~ /^[-]+$/ && $6 ~ /^[-]+$/ && $7 ~ /^[-]+$/ { activate = 1}

END { for (pool in ID)
          print "MSHW;" ID[pool] ";" Health[pool] ";" AdditionalInformation[pool] ";"
    }
