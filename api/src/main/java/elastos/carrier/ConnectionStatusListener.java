package elastos.carrier;

public interface ConnectionStatusListener {
	public default void statusChanged(Network network, ConnectionStatus newStatus, ConnectionStatus oldStatus) {
	}

	public default void connected(Network network) {
	}

	public default void profound(Network network) {
	}

	public default void disconnected(Network network) {
	}
}
