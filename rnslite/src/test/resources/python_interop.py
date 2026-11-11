"""Offline Reticulum/LXMF oracle. Only synthetic test identities; no Reticulum daemon or interfaces."""
import json
import sys
from pathlib import Path
import RNS
import LXMF

# Disable only daemon usage accounting; keep real identity lookup and signature validation.
# No Reticulum instance, sockets or network interfaces are needed for a protocol oracle.
_recall = RNS.Identity.recall
RNS.Identity.recall = staticmethod(lambda address: _recall(address, _no_use=True))

source = json.loads(Path(sys.argv[1]).read_text())
alice = RNS.Identity.from_bytes(bytes.fromhex(source['alice_private']))
bob = RNS.Identity.from_bytes(bytes.fromhex(source['bob_private']))
src = RNS.Destination(alice, RNS.Destination.OUT, RNS.Destination.SINGLE, 'lxmf', 'delivery')
dst = RNS.Destination(bob, RNS.Destination.OUT, RNS.Destination.SINGLE, 'lxmf', 'delivery')
RNS.Identity.remember(bytes(32), src.hash, alice.get_public_key())
RNS.Identity.remember(bytes(32), dst.hash, bob.get_public_key())

packet = RNS.Packet(None, bytes.fromhex(source['packet']))
assert packet.unpack()
assert packet.destination_hash == dst.hash
assert packet.get_hash().hex() == source['packet_hash']
plain = bob.decrypt(packet.data)
assert plain is not None
message = LXMF.LXMessage.unpack_from_bytes(dst.hash + plain, LXMF.LXMessage.OPPORTUNISTIC)
assert message.signature_validated
assert message.content == bytes.fromhex(source['content'])
assert message.title == b'synthetic'
assert message.fields == {1: 'fixture'}
# Verify Kotlin's proof for a separate Bob->Alice packet as well as producing Python's acknowledgement.
proof = RNS.Packet(None, bytes.fromhex(source['proof']))
assert proof.unpack()
assert proof.destination_hash == packet.get_hash()[:16]
assert bob.validate(proof.data, packet.get_hash())
python_proof = bytes([3, 0]) + packet.get_hash()[:16] + bytes([0]) + bob.sign(packet.get_hash())

reply = LXMF.LXMessage(src, dst, 'python to kotlin: synthetic reply', 'synthetic', fields={1: 'fixture'},
                       desired_method=LXMF.LXMessage.OPPORTUNISTIC)
reply.timestamp = 1793029367.0
reply.pack()
response = RNS.Packet(src, reply.packed[LXMF.LXMessage.DESTINATION_LENGTH:], create_receipt=False)
response.pack()
Path(sys.argv[2]).write_text(json.dumps({
    'packet': response.raw.hex(), 'packet_hash': response.get_hash().hex(),
    'packed': reply.packed.hex(), 'content': reply.content.hex(), 'proof': python_proof.hex(),
    'rns_version': RNS.__version__, 'lxmf_version': LXMF.__version__,
}))
print('Python accepted Kotlin LXMF and proof; generated signed encrypted reply.')
