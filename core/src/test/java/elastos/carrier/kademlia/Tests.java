package elastos.carrier.kademlia;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import elastos.carrier.utils.ThreadLocals;

public class Tests {

	@RepeatedTest(10)
	@Test
	void test0() {
		int expect = 8;
		int total = 0;

		ThreadLocalRandom rnd = ThreadLocals.random();

		List<List<String>> bucketsCopy = new ArrayList<>();

		int n = rnd.nextInt(1,256);
		System.out.printf("\n%d ===============\n", n);

		for (int i = 0; i < n; i++) {
			List<String> l = new ArrayList<>();
			int size = rnd.nextInt(4, 8);
			for (int j = 0; j < size; j++)
				l.add("String[" + i + "," + j + "," + (total + j) + "]");

			bucketsCopy.add(l);
			total += size;
		}

		if (total <= expect) {
			Set<String> result = new HashSet<>();
			bucketsCopy.forEach(result::addAll);
			result.forEach(s -> System.out.println(s));
			return;
		}


		AtomicInteger bucketIndex = new AtomicInteger(0);
		AtomicInteger flatIndex = new AtomicInteger(0);
		final int totalEntries = total;

		Set<String> ss = IntStream.generate(() -> rnd.nextInt(totalEntries))
				.distinct()
				.limit(expect)
				.sorted()
				.mapToObj((i) -> {
					System.out.println("-> " + i);
					while (bucketIndex.get() < bucketsCopy.size()) {
						int pos = i - flatIndex.get();
						List<String> b = bucketsCopy.get(bucketIndex.get());
						int s = b.size();
						if (pos < s) {
							return b.get(pos);
						} else {
							bucketIndex.incrementAndGet();
							flatIndex.addAndGet(s);
						}
					}

					return null;
				}).filter(e -> e != null)
				.collect(Collectors.toSet());

		ss.forEach(s -> System.out.println(s));

	}
}
