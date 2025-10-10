BEGIN {FS="[;]"}
NF==6 { ElementName = $1
	      IdentifyingDescriptions = $2
	      Name = $3
	      OtherIdentifyingInfo = $4
	      PATH =  $5
	      split(IdentifyingDescriptions,Variables,"|")
	      split(OtherIdentifyingInfo,Values,"|")
	      for (Entry in Variables) {
	      	  if (Variables[Entry] ~ /Node WWN/) { NodeWWN = Values[Entry]}
	      	  if (Variables[Entry] ~ /Ipv4 Address/) { IPv4 = Values[Entry]}
	      	  if (Variables[Entry] ~ /Ipv6 Address/) { Ipv6 = Values[Entry]}
	          if (Variables[Entry] ~ /Fully Qualified Domain Name/) { FQDN = Values[Entry]}
	          if (Variables[Entry] ~ /System ID/) { SystemID = Values[Entry]}
	          }
	      print("MSHW;" ElementName ";" Name ";" NodeWWN ";" IPv4 ";" Ipv6 ";" FQDN ";" SystemID ";" PATH ";" )
	    }
