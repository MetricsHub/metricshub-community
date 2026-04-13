BEGIN  {section = "" ; module = ""}
{skip=1}
/show module/,/show environment/ {skip = 0}
skip == 1 {next}
$1 ~ /^(Xbar)|(Mod)$/ && $2 ~ "^Ports" {section=$1 ; offset2 = index($0,"Ports") ; offset3 = index ($0,"Module-Type") ; offset4 = index ($0,"Model") ; offset5 = index ($0,"Status") }
($1 ~ /^[0-9]/ && section ~ /^(Xbar)|(Mod)$/ ){
	             if (section == "Mod")  {module = $1}
	             if (section == "Xbar") {module = "XBar" $1}
               port = $2 ;
               moduleType = substr ($0,offset3,(offset4 - offset3)) ; gsub (/  /,"",moduleType) ;
               model = substr ($0,offset4,(offset5 - offset4)) ; gsub (/ /,"",model) ;
               status = substr ($0,offset5) ; gsub (/[ *]/,"",status) ;
               print ( "MSHW;" module ";" port "-Port " moduleType ";" model ";" status ";") ; }
$1 ~ /^(Xbar)|(Mod)$/ && $2 ~ /(Power)|(Sw)/ {section=""}
