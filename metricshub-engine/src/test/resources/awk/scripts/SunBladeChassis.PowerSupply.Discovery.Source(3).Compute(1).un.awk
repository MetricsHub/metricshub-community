BEGIN {FS="[;]"}
$1 ~ /^[0-9]+$/ && NF > 4  {ID=$2;gsub(/\057.*/,"",ID)
                            if (statusArray[ID] == "") {statusArray[ID]=$3}
                            if ((statusArray[ID] != "") && ($3 < statusArray[ID])) {statusArray[ID]=$3}
                           }
END { for (ID in statusArray) {print ("MSHW;"ID";"statusArray[ID]";")}}