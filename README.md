# MUDGEE
Generate MUD Profiles using PCAP.
# Prerequisite
LibPcap (install tcpdump)

# Installation

```sh
$ git clone https://github.com/ayyoob/mudgee.git
$ cd mudgee
$ mvn clean install
```
Sample mud config is provided:
    pcap location, device mac, device name, gateway mac and gateway ip details needs to be passed through the config.

# Execute

```sh
$ java -jar target/mudgee-1.0.0-SNAPSHOT.jar target/mud_config.json 
```

After the execution, Check the result directory for the generated MUD.

