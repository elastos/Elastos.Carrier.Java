package elastos.carrier.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;

public class ApplicationLockTests {
	private static final String tmpDir = System.getProperty("java.io.tmpdir");

	@Test
	void testLock() {
		String lockFile = tmpDir + "/carrier/lock";

		try (ApplicationLock lock = new ApplicationLock(lockFile)) {
			System.out.println("Acquired lock!");

			File f = new File(lockFile);
			assertTrue(f.exists());
			assertTrue(f.isFile());
		} catch (IOException | IllegalStateException e) {
			System.out.println("Acquired lock failed!");
			fail("Acquired lock failed!", e);
			return;
		}

		File f = new File(lockFile);
		assertFalse(f.exists());
		assertFalse(f.isFile());
	}
}
