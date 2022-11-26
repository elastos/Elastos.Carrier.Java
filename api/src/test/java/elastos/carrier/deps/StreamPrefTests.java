package elastos.carrier.deps;

import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@Disabled("Performance")
@TestMethodOrder(OrderAnnotation.class)
public class StreamPrefTests {
	@Order(1)
	@Test
	public void testStreamFilterInWorstCase() {
		List<String> values = List.of(
				"Hello",
				"World",
				"Foobar",
				"Java",
				"List",
				"Initialization",
				"Literals",
				"reference"
			);

		@SuppressWarnings("unused")
		boolean lowercaseCaptial;

		long start = System.currentTimeMillis();

		for (int i = 0; i < 10000000; i++) {
			lowercaseCaptial = values.stream().filter(s -> Character.isLowerCase(s.charAt(0))).findFirst().isEmpty();
		}

		long end = System.currentTimeMillis();
		System.out.format("Stream filter in worst case spend: %d ms\n", end - start);

		start = System.currentTimeMillis();

		for (int i = 0; i < 10000000; i++) {
			for (int j = 0, l = values.size(); j < l; j++) {
				if (Character.isLowerCase(values.get(j).charAt(0))) {
					lowercaseCaptial = true;
					break;
				}
			}
		}

		end = System.currentTimeMillis();
		System.out.format("For loop spend: %d ms\n", end - start);
	}

	@Order(2)
	@Test
	public void testStreamFilterInBestCase() {
		List<String> values = List.of(
				"hello",
				"World",
				"foobar",
				"Java",
				"List",
				"Initialization",
				"literals",
				"reference"
			);

		@SuppressWarnings("unused")
		boolean lowercaseCaptial;

		long start = System.currentTimeMillis();

		for (int i = 0; i < 10000000; i++) {
			lowercaseCaptial = values.stream().filter(s -> Character.isLowerCase(s.charAt(0))).findAny().isEmpty();
		}

		long end = System.currentTimeMillis();
		System.out.format("Stream filter in best case spend: %d ms\n", end - start);

		start = System.currentTimeMillis();

		for (int i = 0; i < 10000000; i++) {
			for (int j = 0, l = values.size(); j < l; j++) {
				if (Character.isLowerCase(values.get(j).charAt(0))) {
					lowercaseCaptial = true;
					break;
				}
			}
		}

		end = System.currentTimeMillis();
		System.out.format("For loop spend: %d ms\n", end - start);
	}

	@Order(3)
	@Test
	public void testStreamMatchInWorstCase() {
		List<String> values = List.of(
				"Hello",
				"World",
				"Foobar",
				"Java",
				"List",
				"Initialization",
				"Literals",
				"reference"
			);

		@SuppressWarnings("unused")
		boolean lowercaseCaptial;

		long start = System.currentTimeMillis();

		for (int i = 0; i < 10000000; i++) {
			lowercaseCaptial = values.stream().anyMatch(s -> Character.isLowerCase(s.charAt(0)));
		}

		long end = System.currentTimeMillis();
		System.out.format("Stream anyMatch in worst case spend: %d ms\n", end - start);

		start = System.currentTimeMillis();

		for (int i = 0; i < 10000000; i++) {
			for (int j = 0, l = values.size(); j < l; j++) {
				if (Character.isLowerCase(values.get(j).charAt(0))) {
					lowercaseCaptial = true;
					break;
				}
			}
		}

		end = System.currentTimeMillis();
		System.out.format("For loop spend: %d ms\n", end - start);
	}

	@Order(4)
	@Test
	public void testStreamMatchInBestCase() {
		List<String> values = List.of(
				"hello",
				"World",
				"foobar",
				"Java",
				"List",
				"Initialization",
				"literals",
				"reference"
			);

		@SuppressWarnings("unused")
		boolean lowercaseCaptial;

		long start = System.currentTimeMillis();

		for (int i = 0; i < 10000000; i++) {
			lowercaseCaptial = values.stream().anyMatch(s -> Character.isLowerCase(s.charAt(0)));
		}

		long end = System.currentTimeMillis();
		System.out.format("Stream anyMatch in best case spend: %d ms\n", end - start);

		start = System.currentTimeMillis();

		for (int i = 0; i < 10000000; i++) {
			for (int j = 0, l = values.size(); j < l; j++) {
				if (Character.isLowerCase(values.get(j).charAt(0))) {
					lowercaseCaptial = true;
					break;
				}
			}
		}

		end = System.currentTimeMillis();
		System.out.format("For loop spend: %d ms\n", end - start);
	}
}
