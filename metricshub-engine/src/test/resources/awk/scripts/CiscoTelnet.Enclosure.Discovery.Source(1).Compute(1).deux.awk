BEGIN {model = "" ; serialNumber = ""; bSwitch = ""}
( $0 ~ /^  cisco MDS 9/ ) && ( model == "" ) { model = $2 " " $3; }
/^Switch hardware ID information/ { bSwitch = "yes" ; getline; getline; }
( $0 ~ /Switch type is/  ) && ( bSwitch == "yes" ) && (model == "") { offset = (index($0, "is") + 3) ; model = substr($0,offset) ; gsub(/"/,"",model) ; } #"
( $0 ~ /Model number is/ ) && ( bSwitch == "yes" ) && (model == "") {offset = (index($0, "is") + 3) ; model = substr($0,offset)}
( $0 ~ /Serial number is/ ) && ( bSwitch == "yes" ) {offset = (index($0,"is") + 3) ; serialNumber = substr ($0,offset)}
/---/ { bSwitch = "" }
END { print("MSHW;" model ";" serialNumber ";") } 