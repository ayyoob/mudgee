# MUD GENERATOR
PCAP BASED MUD GENERATOR

# Installation

```sh
$ git clone https://github.com/ayyoob/DEF.git
$ cd pcap-mud-generator
$ mvn clean install
$ java -jar target/mud-generator-1.0.0-SNAPSHOT.jar target/mud_config.json 
```
Sample mud config is provided:
    pcap location, device mac, device name, gateway mac and gateway ip details needs to be passed through the config.
    
After the execution, Check the result directory for the generated MUD.

