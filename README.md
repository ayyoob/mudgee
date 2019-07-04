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

After the execution, Check the result directory for the generated MUD.

# Configurations

We generate MUD profile for a device by monitoring its traffic trace.

Sample mud config is provided:
    pcap location, device mac, device name, gateway mac and gateway ip details needs to be passed through the config.

    "pcapLocation": "absolute file path of the pcap"

Location of the traffic trace.

    "deviceConfig":{ "device":"00:24:e4:20:28:c3", "deviceName": "augustdoorbellcam" }
 device" : Mac address of the device that we monitor to generate the MUD for.
 
 deviceName": name that appears on the MUD profile.

    "defaultGatewayConfig": { "macAddress" : "14:cc:20:51:33:ea", "ipAddress": "192.168.1.1", "ipv6Address": "fe80:0:0:0:16cc:20ff:fe51:33ea" }

In order to capture device to Internet communication, we require the default gateway details. Therefore mac address, IP addresses of the default gateway has to be given through the config. If you are using a router for your setup then this details can be fetched through its management page in its web UI.

    "controllers": {
            "urn:ietf:params:mud:camera_controller": "169.171.200.0/24"
        }
 Controller to IP mapping. If there are no controllers in your setup then leave it empty.
