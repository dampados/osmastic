<div align="center">
  <h4>Off-grid map pin editor prototype app based on independent radio. No cell towers.</h4>
  <img src="./assets/gif_crdt_convergence_compressed4.gif" alt="CRDT convergence based on logical counter and static nodes relationship tie-breaking instead of simple LWW rewrite" width="75%">
  <p>Demonstates an attempt to approach some level of consistency, document convergence in aggressive LoRa mesh without central node, ideal TCP broadband channel and meta data redundancy or other global truth sync means.</p>
</div>

## Features:
+ Local-first, Multi-Value pin register (history) with only winner pin rendered;
+ Uses AIDL interface of the official Meshtastic app;
+ CRDT: classic LWW, no timestamps though -- logical counter and Time To Live for pins;
+ TTL is static, once set -- unchangeable;
+ TTL on all rebroadcasts gets recalculated for the nodes, that missed initial creation event packet.
+ Random logical IDs 4 byte entropy long;
+ Tie-breaking: static relationships between every two nodes in a channel known to everyone: PSK + NodeID --> MD5 hashing;
+ Map widget is the old osmdroid, custom Marker class (visible label, metadata injected);
+ Supports rotation, GPS, region caching with UI indication in Layers modal;
+ Pins can be rotated, labeled, EMOJIes allowed as icons, dimmed (if stale), moved;
+ Full pin history in modal, no information gets droped on stale or conflict events;
+ Voyager map tiles, light variant. 

## Lots of other stuff gotta be implemented or fixed:
[] conflict detection indicator
[ ] on present conflict rebroadcasting button
2) disable broadcasting on edit modal dismiss ( reason in 6) )
3) green/red dot latest map caching result
4) emojie rotation indicator on edit modal
5) ugly coroutine pause redo ( reason in 6) )
6) fix two-way async callback hell -> MVI
7) mapnik/voyager/sat switch
8) light/dark themes
9) drop tiles cache button
10) ugly edit modal UI redo
11) ugly bottom bar UI redo
12) channels + another radio layer switch (other meshtastic channeles, gprc relay server, etc.)
13) gps not ready button deadlock fix
14) maplibre switch, when its markers ready
15) delete eternal pin button (local only)
16) add logical id collision pre-flight check
