# DistributedHashTable-Chord
This is an android project implementing simple distributed hash table using Chord protocol (without finger table).It handles new node join and redistribution of keys when a new node joins.
Emulator-5554 receives all new node join requests.

The port configuration used in the app is as follows: 
Each emulator listens on port 10000, but it will connect to a different port number on the IP address 10.0.2.2 for each emulator, as follows: emulator serial port emulator-5554 11108 emulator-5556 11112 emulator-5558 11116 emulator-5560 11120 emulator-5562 11124

This project is a ContentProvider using DHT semantics similar to a simplified Chord to store and retrieve data across multiple devices. This project uses the package name edu.buffalo.cse.cse486586.simpledht, and defines a content provider authority and class. Please do not change the package name, and use the content provider authority and class for your implementation.
It uses SHA-1 as the hash function to generate IDs for the DHT.

Each content provider instance should have a node ID derived from its emulator serial number. This node ID must be obtained by applying the specified hash function (named genHash(), above) to the emulator serial number. For example, the node ID of the content provider running on emulator-5554 should be node_id = genHash("5554"). The ID string you should use is exactly the string found in portStr in the telephony hack. This is necessary to place each node in the correct place in the Chord ring.

Content provider implements insert(), query(), and delete().
• The key is hashed by genHash() before being inserted in the DHT in order to determine its position in the Chord ring.

Content provider implements ring-based routing but does not implement Chord Finger table. Following the Chord ring design, content provider maintains predecessor and successor pointers, then forward each request for data not stored locally to its successor until the request arrives at the correct node. When a node receives a request for an ID that it maintains, it process the request and send the result directly to the content provider instance initiating the request.

There are two buttons provided in the application and their purpose is as explained below:
• LDump
– When pressed, this button should dump and display all of the key-value pairs
stored on the local partition of the DHT.
– This can be accomplished by issuing a query with @ as the selection parameter,
and printing the results.
• GDump
– When pressed, this button should dump and display all of the key-value pairs in
the entire DHT.
– This can be accomplished by issuing a query with * as the selection parameter,
and printing the results.
