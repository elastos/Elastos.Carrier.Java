package elastos.carrier.kademlia;

import java.util.Arrays;

import elastos.carrier.Id;
import elastos.carrier.crypto.CryptoBox;
import elastos.carrier.crypto.CryptoBox.KeyPair;
import elastos.carrier.crypto.CryptoBox.PublicKey;

public class CryptoContext implements AutoCloseable {
	private CryptoBox box;
	private CryptoBox.Nonce nonce;

	public CryptoContext(PublicKey pk, KeyPair keyPair) {
		box = CryptoBox.fromKeys(pk, keyPair.privateKey());

		Id receiver = Id.of(pk.bytes());
		Id sender = Id.of(keyPair.publicKey().bytes());

		Id dist = Id.distance(sender, receiver);

		nonce = CryptoBox.Nonce.fromBytes(Arrays.copyOf(dist.bytes(), CryptoBox.Nonce.length()));
	}

	public byte[] encrypt(byte[] plain) {
		return box.encrypt(plain, nonce);
	}

	public byte[] decrypt(byte[] cipher) {
		return box.decrypt(cipher, nonce);
	}

	@Override
	public void close() {
		box.close();
	}
}
