/*
 * Copyright (c) 2022 - 2023 trinity-tech.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package elastos.carrier.kademlia;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteDataSource;

import elastos.carrier.Id;
import elastos.carrier.PeerInfo;
import elastos.carrier.Value;
import elastos.carrier.kademlia.exceptions.CasFail;
import elastos.carrier.kademlia.exceptions.IOError;
import elastos.carrier.kademlia.exceptions.ImmutableSubstitutionFail;
import elastos.carrier.kademlia.exceptions.InvalidSignature;
import elastos.carrier.kademlia.exceptions.KadException;
import elastos.carrier.kademlia.exceptions.NotValueOwner;
import elastos.carrier.kademlia.exceptions.SequenceNotMonotonic;

public class SQLiteStorage implements DataStorage {
	private static final int VERSION = 3;
	private static final String SET_USER_VERSION = "PRAGMA user_version = " + VERSION;
	private static final String GET_USER_VERSION = "PRAGMA user_version";

	private static final String CREATE_VALUES_TABLE = "CREATE TABLE IF NOT EXISTS valores(" +
			"id BLOB NOT NULL PRIMARY KEY, " +
			"publicKey BLOB, " +
			"privateKey BLOB, " +
			"recipient BLOB, " +
			"nonce BLOB, " +
			"signature BLOB, " +
			"sequenceNumber INTEGER, " +
			"data BLOB, " +
			"timestamp BIGINT NOT NULL" +
		") WITHOUT ROWID";

	private static final String CREATE_VALUES_INDEX =
			"CREATE INDEX IF NOT EXISTS idx_valores_timpstamp ON valores(timestamp)";

	private static final String CREATE_PEERS_TABLE = "CREATE TABLE IF NOT EXISTS peers(" +
			"id BLOB NOT NULL, " +
			"privateKey BLOB, " +
			"nodeId BLOB NOT NULL, " +
			"origin BLOB NOT NULL, " +
			"port INTEGER NOT NULL, " +
			"alternativeURL VARCHAR(512), " +
			"signature BLOB NOT NULL, " +
			"timestamp INTEGER NOT NULL, " +
			"PRIMARY KEY(id, nodeId, origin)" +
		") WITHOUT ROWID";

	private static final String CREATE_PEERS_INDEX =
			"CREATE INDEX IF NOT EXISTS idx_peers_timpstamp ON peers(timestamp)";

	private static final String CREATE_PEERS_ID_INDEX =
			"CREATE INDEX IF NOT EXISTS idx_peers_id ON peers(id)";

	private static final String SELECT_VALUE = "SELECT * from valores " +
			"WHERE id = ? and timestamp >= ?";

	private static final String UPSERT_VALUE = "INSERT INTO valores(" +
			"id, publicKey, privateKey, recipient, nonce, signature, sequenceNumber, data, timestamp) " +
			"VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET " +
			"publicKey=excluded.publicKey, privateKey=excluded.privateKey, " +
			"recipient=excluded.recipient, nonce=excluded.nonce, " +
			"signature=excluded.signature, sequenceNumber=excluded.sequenceNumber, " +
			"data=excluded.data, timestamp=excluded.timestamp";

	private static final String SELECT_PEER = "SELECT * from peers " +
			"WHERE id = ? and timestamp >= ? " +
			"ORDER BY RANDOM() LIMIT ?";

	private static final String SELECT_PEER_WITH_SRC = "SELECT * from peers " +
			"WHERE id = ? and origin = ? and timestamp >= ?";

	private static final String UPSERT_PEER = "INSERT INTO peers(" +
			"id, privateKey, nodeId, origin, port, alternativeURL, signature, timestamp) " +
			"VALUES(?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(id, nodeId, origin) DO UPDATE SET " +
			"privateKey=excluded.privateKey, port=excluded.port, alternativeURL=excluded.alternativeURL, " +
			"signature=excluded.signature, timestamp=excluded.timestamp";

	private static ThreadLocal<Connection> cp;

	private ScheduledFuture<?> expireFuture;

	private static final Logger log = LoggerFactory.getLogger(SQLiteStorage.class);

	public static DataStorage open(File path, ScheduledExecutorService scheduler) throws KadException {
		SQLiteStorage storage = new SQLiteStorage();
		storage.init(path, scheduler);
		return storage;
	}

	private void init(File path, ScheduledExecutorService scheduler) throws KadException {
		// SQLite connection global initialization
		// According to the SQLite documentation, using single connection in
		// multiple threads is safe and efficient.
		// But java maybe using green thread on top of the system thread.
		// So we should use the thread local connection make sure the database
		// operations are safe.
		SQLiteDataSource ds = new SQLiteDataSource();

		// URL for memory db: https://www.sqlite.org/inmemorydb.html
		ds.setUrl("jdbc:sqlite:" + (path != null ? path.toString() : "file:node?mode=memory&cache=shared"));

		cp = ThreadLocal.withInitial(() -> {
			try {
				return ds.getConnection();
			} catch (SQLException e) {
				log.error("Failed to open the SQLite storage.", e);
				//throw new IOError("Failed to open the SQLite storage", e);
				return null;
			}
		});

		int userVersion = getUserVersion();

		// Check and initialize the database schema.
		try (Statement stmt = getConnection().createStatement()) {
			// if we change the schema,
			// we should check the user version, do the schema update,
			// then increase the user_version;
			if (userVersion < 3) {
				stmt.executeUpdate("DROP TABLE IF EXISTS peers");
				stmt.executeUpdate("DROP INDEX IF EXISTS idx_peers_timpstamp");
				stmt.executeUpdate("DROP INDEX IF EXISTS idx_peers_id");
			}

			stmt.executeUpdate(SET_USER_VERSION);
			stmt.executeUpdate(CREATE_VALUES_TABLE);
			stmt.executeUpdate(CREATE_VALUES_INDEX);
			stmt.executeUpdate(CREATE_PEERS_TABLE);
			stmt.executeUpdate(CREATE_PEERS_INDEX);
			stmt.executeUpdate(CREATE_PEERS_ID_INDEX);
		} catch (SQLException e) {
			log.error("Failed to open the SQLite storage: " + e.getMessage(), e);
			throw new IOError("Failed to open the SQLite storage: " + e.getMessage(), e);
		}

		this.expireFuture = scheduler.scheduleWithFixedDelay(() -> {
			// Evict the expired entries from the local storage
			expire();
		}, 0, Constants.STORAGE_EXPIRE_INTERVAL, TimeUnit.MILLISECONDS);

		log.info("SQLite storage opened: {}", path != null ? path : "MEMORY");
	}

	private Connection getConnection() {
		return cp.get();
	}

	@Override
	public void close() throws IOException {
		expireFuture.cancel(false);
		// none of the scheduled tasks should experience exceptions,
		// log them if they did
		try {
			expireFuture.get();
		} catch (ExecutionException e) {
			log.error("Scheduled future error", e);
		} catch (InterruptedException e) {
			log.error("Scheduled future error", e);
		} catch (CancellationException ignore) {
		}

		/*
		try {
			connection.close();
		} catch (SQLException e) {
			log.error("Failed to close the SQLite storage.", e);
			throw new IOException("Failed to close the SQLite storage.", e);
		}
		*/
	}

	public int getUserVersion() {
		int userVersion = 0;
		try (PreparedStatement stmt = getConnection().prepareStatement(GET_USER_VERSION)) {
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					userVersion = rs.getInt("user_version");
				}
			}
		} catch (SQLException e) {
			log.error("SQLite get user version an error: " + e.getMessage(), e);
		}

		return userVersion;
	}


	@Override
	public Stream<Id> valueIdStream() throws KadException {
		PreparedStatement stmt = null;
		ResultSet rs = null;

		try {
			stmt = getConnection().prepareStatement("SELECT id from valores ORDER BY id");
			stmt.closeOnCompletion();
			rs = stmt.executeQuery();
		} catch (SQLException e) {
			try {
				if (rs != null)
					rs.close();

				if (stmt != null)
					stmt.close();
			} catch (SQLException ignore) {
				log.error("SQLite storage encounter an error: " + ignore.getMessage(), ignore);
			}

			log.error("SQLite storage encounter an error: " + e.getMessage(), e);
			throw new IOError("SQLite storage encounter an error: " + e.getMessage(), e);
		}

		final ResultSet idrs = rs;
		Stream<Id> s = StreamSupport.stream(new Spliterators.AbstractSpliterator<Id>(
				Long.MAX_VALUE, Spliterator.NONNULL | Spliterator.IMMUTABLE | Spliterator.ORDERED) {
			@Override
			public boolean tryAdvance(Consumer<? super Id> consumer) {
				try {
					if(!idrs.next())
						return false;

					byte[] binId = idrs.getBytes("id");
					consumer.accept(Id.of(binId));
					return true;
				} catch (SQLException e) {
					log.error("SQLite storage encounter an error: " + e.getMessage(), e);
					return false;
				}
			}
		}, false);

		s.onClose(() -> {
			try {
				idrs.close();
			} catch (SQLException ignore) {
				log.error("SQLite storage encounter an error: " + ignore.getMessage(), ignore);
			}
		});

		return s;
	}

	@Override
	public Value getValue(Id valueId) throws KadException {
		try (PreparedStatement stmt = getConnection().prepareStatement(SELECT_VALUE)) {
			long when = System.currentTimeMillis() - Constants.MAX_VALUE_AGE;
			stmt.setBytes(1, valueId.bytes());
			stmt.setLong(2, when);

			try (ResultSet rs = stmt.executeQuery()) {
				if (!rs.next())
					return null;

				byte[] v = rs.getBytes("publicKey");
				Id publicKey = v != null ? Id.of(v) : null;

				byte[] privateKey = rs.getBytes("privateKey");

				v = rs.getBytes("recipient");
				Id recipient = v != null ? Id.of(v) : null;

				byte[] nonce = rs.getBytes("nonce");
				byte[] signature = rs.getBytes("signature");
				int sequenceNumber = rs.getInt("sequenceNumber");
				byte[] data = rs.getBytes("data");

				return Value.of(publicKey, privateKey, recipient, nonce, sequenceNumber, signature, data);
			}
		} catch (SQLException e) {
			log.error("SQLite storage encounter an error: " + e.getMessage(), e);
			throw new IOError("SQLite storage encounter an error: " + e.getMessage(), e);
		}
	}

	@Override
	public Value putValue(Value value, int expectedSeq) throws KadException {
		if (value.isMutable() && !value.isValid())
			throw new InvalidSignature("Value signature validation failed");

		Value old = getValue(value.getId());
		if (old != null && old.isMutable()) {
			if (!value.isMutable())
				throw new ImmutableSubstitutionFail("Can not replace mutable value with immutable is not supported");
			if (old.hasPrivateKey() && !value.hasPrivateKey())
				throw new NotValueOwner("Not the owner of the value");
			if (value.getSequenceNumber() < old.getSequenceNumber())
				throw new SequenceNotMonotonic("Sequence number less than current");
			if (expectedSeq >= 0 && old.getSequenceNumber() >= 0 && old.getSequenceNumber() != expectedSeq)
				throw new CasFail("CAS failure");
		}

		try (PreparedStatement stmt = getConnection().prepareStatement(UPSERT_VALUE)) {
			stmt.setBytes(1, value.getId().bytes());

			if (value.getPublicKey() != null)
				stmt.setBytes(2, value.getPublicKey().bytes());
			else
				stmt.setNull(2, Types.BLOB);

			if (value.getPrivateKey() != null)
				stmt.setBytes(3, value.getPrivateKey());
			else
				stmt.setNull(3, Types.BLOB);

			if (value.getRecipient() != null)
				stmt.setBytes(4, value.getRecipient().bytes());
			else
				stmt.setNull(4, Types.BLOB);

			if (value.getNonce() != null)
				stmt.setBytes(5, value.getNonce());
			else
				stmt.setNull(5, Types.BLOB);

			if (value.getSignature() != null)
				stmt.setBytes(6, value.getSignature());
			else
				stmt.setNull(6, Types.BLOB);

			stmt.setInt(7, value.getSequenceNumber());

			if (value.getData() != null)
				stmt.setBytes(8, value.getData());
			else
				stmt.setNull(8, Types.BLOB);

			stmt.setLong(9, System.currentTimeMillis());

			stmt.executeUpdate();
		} catch (SQLException e) {
			log.error("SQLite storage encounter an error: " + e.getMessage(), e);
			throw new IOError("SQLite storage encounter an error: " + e.getMessage(), e);
		}

		return old;
	}

	@Override
	public Value putValue(Value value) throws KadException {
		return putValue(value, -1);
	}

	@Override
	public Stream<Id> peerIdStream() {
		PreparedStatement stmt = null;
		ResultSet rs = null;

		try {
			stmt = getConnection().prepareStatement("SELECT DISTINCT id from peers ORDER BY id");
			stmt.closeOnCompletion();
			rs = stmt.executeQuery();
		} catch (SQLException e) {
			try {
				if (rs != null)
					rs.close();

				if (stmt != null)
					stmt.close();
			} catch (SQLException ignore) {
				log.error("SQLite storage encounter an error: " + ignore.getMessage(), ignore);
			}
		}

		final ResultSet idrs = rs;
		Stream<Id> s = StreamSupport.stream(new Spliterators.AbstractSpliterator<Id>(
				Long.MAX_VALUE, Spliterator.NONNULL | Spliterator.IMMUTABLE | Spliterator.ORDERED) {
			@Override
			public boolean tryAdvance(Consumer<? super Id> consumer) {
				try {
					if(!idrs.next())
						return false;

					byte[] binId = idrs.getBytes("id");
					consumer.accept(Id.of(binId));
					return true;
				} catch (SQLException e) {
					log.error("SQLite storage encounter an error: " + e.getMessage(), e);
					return false;
				}
			}
		}, false);

		s.onClose(() -> {
			try {
				idrs.close();
			} catch (SQLException ignore) {
				log.error("SQLite storage encounter an error: " + ignore.getMessage(), ignore);
			}
		});

		return s;
	}

	@Override
	public List<PeerInfo> getPeer(Id peerId, int maxPeers) throws KadException {
		if (maxPeers <=0)
			maxPeers = Integer.MAX_VALUE;

		List<PeerInfo> peers = new ArrayList<>(maxPeers > 32 ? 32 : maxPeers);
		try (PreparedStatement stmt = getConnection().prepareStatement(SELECT_PEER)) {
			long when = System.currentTimeMillis() - Constants.MAX_VALUE_AGE;
			stmt.setBytes(1, peerId.bytes());
			stmt.setLong(2, when);
			stmt.setInt(3, maxPeers);

			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					byte[] privateKey = rs.getBytes("privateKey");
					Id nodeId = Id.of(rs.getBytes("nodeId"));
					Id origin = Id.of(rs.getBytes("origin"));
					int port = rs.getInt("port");
					String alt = rs.getString("alternativeURL");
					byte[] signature = rs.getBytes("signature");

					PeerInfo peer = PeerInfo.of(peerId, privateKey, nodeId, origin, port, alt, signature);
					peers.add(peer);
				}
			}
		} catch (SQLException e) {
			log.error("SQLite storage encounter an error: " + e.getMessage(), e);
			throw new IOError("SQLite storage encounter an error: " + e.getMessage(), e);
		}

		return peers.isEmpty() ? Collections.emptyList() : peers;
	}

	@Override
	public PeerInfo getPeer(Id peerId, Id origin) throws KadException {
		try (PreparedStatement stmt = getConnection().prepareStatement(SELECT_PEER_WITH_SRC)) {
			long when = System.currentTimeMillis() - Constants.MAX_VALUE_AGE;
			stmt.setBytes(1, peerId.bytes());
			stmt.setBytes(2, origin.bytes());
			stmt.setLong(3, when);

			try (ResultSet rs = stmt.executeQuery()) {
				if (!rs.next())
					return null;

				byte[] privateKey = rs.getBytes("privateKey");
				Id nodeId = Id.of(rs.getBytes("nodeId"));
				int port = rs.getInt("port");
				String alt = rs.getString("alternativeURL");
				byte[] signature = rs.getBytes("signature");

				return PeerInfo.of(peerId, privateKey, nodeId, origin, port, alt, signature);
			}
		} catch (SQLException e) {
			log.error("SQLite storage encounter an error: " + e.getMessage(), e);
			throw new IOError("SQLite storage encounter an error: " + e.getMessage(), e);
		}
	}

	@Override
	public void putPeer(Collection<PeerInfo> peers) throws KadException {
		long now = System.currentTimeMillis();
		Connection connection = getConnection();

		try {
			connection.setAutoCommit(false);
		} catch (SQLException e) {
			log.error("SQLite storage encounter an error: " + e.getMessage(), e);
			throw new IOError("SQLite storage encounter an error: " + e.getMessage(), e);
		}

		try (PreparedStatement stmt = connection.prepareStatement(UPSERT_PEER)) {
			for (PeerInfo peer : peers) {
				stmt.setBytes(1, peer.getId().bytes());
				stmt.setBytes(2, peer.getPrivateKey());
				stmt.setBytes(3, peer.getNodeId().bytes());
				stmt.setBytes(4, peer.getOrigin().bytes());
				stmt.setInt(5, peer.getPort());

				if (peer.hasAlternativeURL())
					stmt.setString(6, peer.getAlternativeURL());
				else
					stmt.setNull(6, Types.VARCHAR);

				stmt.setBytes(7, peer.getSignature());
				stmt.setLong(8, now);
				stmt.addBatch();
			}

			stmt.executeBatch();
			connection.commit();
			connection.setAutoCommit(true);
		} catch (SQLException e) {
			log.error("SQLite storage encounter an error: " + e.getMessage(), e);
			throw new IOError("SQLite storage encounter an error: " + e.getMessage(), e);
		}
	}

	@Override
	public void putPeer(PeerInfo peer) throws KadException {
		putPeer(Arrays.asList(peer));
	}

	private void expire() {
		long now = System.currentTimeMillis();
		Connection connection = getConnection();

		try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM valores WHERE timestamp < ?")) {
			long ts = now - Constants.MAX_VALUE_AGE;
			stmt.setLong(1, ts);
			stmt.executeUpdate();
		} catch (SQLException e) {
			log.error("Failed to evict the expired values: " + e.getMessage(), e);
		}

		try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM peers WHERE timestamp < ?")) {
			long ts = now - Constants.MAX_PEER_AGE;
			stmt.setLong(1, ts);
			stmt.executeUpdate();
		} catch (SQLException e) {
			log.error("Failed to evict the expired peers: " + e.getMessage(), e);
		}
	}

}
