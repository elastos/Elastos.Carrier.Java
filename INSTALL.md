# Setting Up the Carrier Service: A Beginner's Guide

***Notice**: It is recommended to install the Carrier daemon on **Ubuntu Linux version 22.04** or later, and bind it to a public IP address.*

### 1. Installation of Runtime Dependencies

The Carrier daemon has dependencies on the following runtime components:

- Java Virtual Machine (JVM)
- sodium (libsodium)

To install these dependencies, please run the following commands:

```bash
$ sudo apt install openjdk-17-jre-headless libsodium23
```

### 2. Building Your Debian Package

Please ensure that JDK-11 has been installed on your building machine before building the Carrier deamon debian package. 

Use the following command to carry out the whole building process:

```bash
$ git clone git@github.com:elastos/Elastos.Carrier.Java.git  Carrier.Java
$ cd Carrier.Java
$ ./mvnw -Dmaven.test.skip=true
```

Once the build process finishes, the debian package will be generated under the directory `launcher/target`

with the name like ***carrier-launcher-<version>-<timestamp>.deb***

After the build process completes, a Debian package will be generated in the `launcher/target`directory with a name following the format ***carrier-launcher-<version>-<timestamp>.deb***. Please note that  `<version>` and `<timestamp>` will vary depending on the specific version of the package being built.

### 3. Installing the Carrier Service

After uploading the Debian Package to the target VPS server, run the following command to install the Carrier Service:

```bash
$ sudo dpkg -i *carrier-launcher-<version>-<timestamp>.deb*
```

<aside>
💡 The Carrier daemon installation includes several directories and files, which are organized as follows:
- `/usr/lib/carrier`: Contains the runtime libraries, including jar packages
- `/etc/carrier`: Contains the configuration file `default.conf`
- `/var/lib/carrier`: Contains the runtime data store
- `/var/log/carrier`: Contains the output log file `carrier.log`
- `/var/run/carrier`: Contains the runtime directory.

The data cached under `/var/lib/carrier` is organized into the following structure:
- `/var/lib/carrier/key`:  Contains a randomly generated private key
- `/var/lib/carrier/id`:  Contains the node ID
- `/var/lib/carrier/dht4.cache`:  Contains the routing table information for IPv4 addresses
- `/var/lib/carrier/dht6.cache`:  Contains the routing table information for IPv6 addresses if IPv6 is enabled
- `/var/lib/carrier/node.db`: Contains the information about Value and PeerInfo

</aside>

Once the Carrier Service has been installed as a service, it is necessary to open the designated port for usage (the default is `39001`):

```bash
$ sudo ufw allow 39001/udp
```

To check if the port is accessible, use the following command. Additionally, you can review the log file for more detailed information on the current running status:

```bash
$ sudo ufw status verbose
$ tail -f /var/log/carrier/carrier.log
```

We would also recommend using the '`systemctl`' command to check the status of the Carrier daemon service or to start/stop the service:

```bash
$ systemctl status carrier
$ sudo systemctl start carrier
$ sudo systemctl stop carrier
```

### 4. An example of config file

To officially launch the Carrier Service and improve the health of the Carrier network, the service config file should be updated to reference the following configuration file:

```json
{
  "ipv4": true,
  "ipv6": false,
  "address4": "your-ipv4-address",
  "address6": "your-ipv6-address",
  "port": 39001,
  "dataDir": "/var/lib/carrier",

  "bootstraps": [
    // carrier-node1
    {
      "id": "HZXXs9LTfNQjrDKvvexRhuMk8TTJhYCfrHwaj3jUzuhZ",
      "address": "155.138.245.211",
      "port": 39001
    },
    // carrier-node2
    {
      "id": "FRkR2NWhbSGMv3BqGui7FYAgCSAWySrz6xmTAx9Ny7zo",
      "address": "45.76.161.175",
      "port": 39001
    },
    // carrier-node3
    {
      "id": "8grFdb2f6LLJajHwARvXC95y73WXEanNS1rbBAZYbC5L",
      "address": "140.82.57.197",
      "port": 39001
    },
    // carrier-node4
    {
      "id": "4A6UDpARbKBJZmW5s6CmGDgeNmTxWFoGUi2Z5C4z7E41",
      "address": "107.191.62.45",
      "port": 39001
    },
    // carrier-node5
    {
      "id": "5BJ8SZZQ4z4izhw82W2ksyuTCQz3GwWUWBSaza4qzVT9",
      "address": "207.148.82.19",
      "port": 39001
    }
  ] 
}
```
