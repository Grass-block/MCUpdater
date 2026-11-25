# Reverse RTP CAS CDN

The standalone CDN executable now starts `me.gb2022.mcupdater.cdn.MCUpdaterCDNServer`.
On first launch it writes `config.properties` to the working directory and exits.
Configure it, then launch it again:

```properties
server.port=65322
storage.root=cas
accounts.my-server=use-a-long-random-token
```

Each `accounts.<serverId>` entry is a server ID and shared token. Keep the token private.
The CDN stores objects in its own CAS, reuses existing objects, and pulls missing objects
from the authenticated server through RTP.

## Publisher process

Run the publisher from the server distribution, passing the source CAS, server ID, token,
and CDN host. Port 65322 is used unless `host:port` is supplied; IPv6 uses `[address]:port`.

```text
java -cp mc-updater-server.jar me.gb2022.mcupdater.server.CDNClient <cas-path> <serverId> <token> <cdn-host[:port]>
```

The publisher opens the existing CAS metadata, dials out to the CDN, sends P30 credentials,
waits for P31, then sends a P32 snapshot of every valid local SHA-1 object ID and size. The
CDN compares its CAS and requests only missing objects with RTP batches. The server does not
need to expose an inbound listener: RTP control and B-FTP data use the established outbound
TCP connection as a combined master/worker channel.

P32 is capped at 200,000 objects and the CDN framing limit is 16 MiB. A larger CAS is rejected
until inventory paging is added. This first version performs one reconciliation per publisher
process run; restart the process to publish later CAS changes. Use a trusted network or a secure
tunnel for the connection because P30 currently carries the shared token over TCP without TLS.
