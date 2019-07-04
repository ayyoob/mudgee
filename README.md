# MUDGEE
Generate MUD Profiles using PCAP.

# Prerequisite
1. LibPcap (install tcpdump)

```sh
    Linux: ``apt-get install tcpdump''
    OSX: readily available by default.
    Windows: follow instructions at: https://nmap.org/npcap/
 ```
    
2. Maven

```sh
    Follow instructions at: https://www.baeldung.com/install-maven-on-windows-linux-mac for installation.
```    

# Installation

```sh
$ git clone https://github.com/ayyoob/mudgee.git
$ cd mudgee
$ mvn clean install
```

A sample config file is provided in the target directory (i.e. /mudgee/src/main/resources/apps/mud_config.json).
Before executing the tool, make sure to declare three parameters including "defaultGatewayConfig", "deviceConfig" and "pcapLocation".

# Execute

```sh
$ java -jar target/mudgee-1.0.0-SNAPSHOT.jar target/mud_config.json 
```

After execution, this tool outputs the generated MUD profile into the results directory (i.e. /mudgee/result/)

# Configurations

This tool generates MUD profile of a device by analyzing its traffic trace.

A sample config file is provided in /mudgee/src/main/resources/apps/mud_config.json
you need to specify three parameters in the config file: "defaultGatewayConfig" (MAC/IPv4/IPv6 addresses), "deviceConfig" (MAC address, name) and "pcapLocation" (file path and name).

    "pcapLocation": "/Users/ayyoobhamza/Documents/mud/pcap/0024e42028c6.pcap"

Location/name of the traffic trace.

    "deviceConfig":{ "device":"00:24:e4:20:28:c3", "deviceName": "augustdoorbellcam" }
"device": MAC address of the device that we aim to generate the MUD profile for.
"deviceName": name that appears in the output MUD profile.

    "defaultGatewayConfig": { "macAddress" : "14:cc:20:51:33:ea", "ipAddress": "192.168.1.1", "ipv6Address": "fe80:0:0:0:16cc:20ff:fe51:33ea" }

In order to capture communications between the device and servers on the Internet , our tool requires the default gateway details. Therefore, MAC address, IP addresses of the default gateway are needed to be declared in the config file.

    "controllers": {
            "urn:ietf:params:mud:camera_controller": "169.171.200.0/24"
        }
 Controller to IP mapping. If there are no controllers in your setup then leave it empty.
