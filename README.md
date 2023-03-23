# Carrier Java - V2

Elastos Carrier is a decentralized and encrypted peer-to-peer (P2P)  communication framework that facilitates network traffic routing between virtual machines and decentralized Applications (dApps).  Carrier Java is a Java distribution designed to run on VPS servers with a public IP address, serving as a super Carrier Node service.

Carrier V2 is a new two-layered architecture that features a unified DHT network as the bottom layer and facilitates various application-oriented services on top of the DHT network, where a list of services includes, but is not limited to:

- An active proxy service forwards the service entries from third-parties originally located within a LAN network, making them accessible from the public;
- A federal-based decentralized communication system provides great efficiency and security, including similar features to Carrier V1;
- A content addressing based storage system allows the distribtion of data among peers for the application scenarios like P2P file sharing.

**Notice**:  *the later two features have not been developed yet, but they are already included in the TODO List*.

### The Strengths of Carrier V2 Compared to Carrier V1

**Carrier V2** is an improved and entirely new version from the **CarrierV1** (Classic version), with the main differences listed below:

- CarrierV1 is a fully decentralized, secure P2P  communication system with friend-to-friend messaging capabilities, therefore it has a narrow adoption oriented towards IM-like platforms;
- Carrier V1 only includes messaging and session layers without a DHT-level network layer, while V2 has an independent DHT network that allows for greater scalability;
- By utilizing the DHT network on CarrierV2, group messaging and offline messaging can be achieved with great efficiency and enhanced features

## Guide to compiling and building to Carrier Java

### Dependencies

- java ≥ 11
- libsoduium runtime (shared object)

### Build instructions

Download this repository using Git:

```shell
git clone https://github.com/elastos/Elastos.Carrier.Java
```

Then navigate to the directory with the source code downloaded:

```shell
./mvnw
```

If you want to skip the test cases, use the the following command instead of the command mentioned above:

```shell
./mvnw -Dmaven.test.skip=true 
```

## Contribution

We welcome contributions from passionate developers from open-source community who aspire to create a secure, decentralized communication platform and help expand the capabilities of Elastos Carrier to achieve wider adoption.

## Acknowledgments

A sincere thank you goes out to all the projects that we rely on directly or indirectly, for their contributions to the development of Elastos Carrier Project. We value the collaborative nature of the open-source community and recognize the importance of working together to create innovative, reliable software solutions.

## License

This project is licensed under the terms of the MIT license. We believe that open-source licensing  promotes transparency, collaboration, and innovation, and we encourage others to contribute to the project in accordance with the terms of the license.
