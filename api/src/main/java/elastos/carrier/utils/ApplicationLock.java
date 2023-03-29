package elastos.carrier.utils;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ApplicationLock implements AutoCloseable {
	private Path lockFile;
	private FileChannel fc;
	private FileLock lock;

	public ApplicationLock(Path lockFile) throws IOException, IllegalStateException {
		this.lockFile = lockFile;
		tryLock();
	}

	public ApplicationLock(String lockFile) throws IOException, IllegalStateException {
		this(Path.of(lockFile));
	}

	private void tryLock() throws IOException, IllegalStateException {
		fc = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
		lock = fc.tryLock(0, 0, false);
		if (lock == null)
			throw new IllegalStateException("Already locked by another instance.");
	}

	@Override
	public void close() {
		if (lock != null) {
			try {
				lock.close();
				Files.delete(lockFile);
				lock = null;
			} catch (IOException ignore) {
				lock = null;
			}
		}
	}
}
