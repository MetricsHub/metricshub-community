BEGIN { FS="[|]" }
$1 ~ /Cluster Name:/   {ClusterName = $0 ; gsub (/^.*: +/,"",ClusterName) }
$1 ~ /Cluster Health:/ {ClusterStatus = $0 ; gsub (/^.*\[ */,"",ClusterStatus) ; gsub (/ *\] *$/,"",ClusterStatus)
	                      print "MSHW_ENCLOSURE;Cluster;" "Cluster;" ClusterStatus ";" ClusterStatus ";;"
	                     }
$1 ~ /^[0-9 ]+$/ && NF == 8 {NodeStatus = $3 ; gsub (/ /,"",NodeStatus);
	                           NodeID = $1 ; gsub (/ /,"",NodeID);
	                           NodeIP = $2 ; gsub (/ /,"",NodeIP);
	                           print "MSHW_ENCLOSURE;Node;" ClusterName"-"NodeID ";" NodeStatus ";"  NodeStatus ";" NodeIP ";"
	                          }
