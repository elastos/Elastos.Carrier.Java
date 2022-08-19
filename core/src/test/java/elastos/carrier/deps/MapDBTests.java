/*
 * Copyright (c) 2022 Elastos Foundation
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

package elastos.carrier.deps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mapdb.BTreeMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.mapdb.serializer.SerializerArrayTuple;

public class MapDBTests {
	@Test
	public void testMapdbWrite() throws Exception {
		DB db = DBMaker.fileDB("/Users/jingyu/Temp/test.db")
				.fileMmapEnable()
				.concurrencyScale(8)
				.transactionEnable()
				.make();

		HTreeMap<String, String> map = db.hashMap("test", Serializer.STRING, Serializer.STRING)
				.layout(8, 32, 4)
				.createOrOpen();

		long start = System.currentTimeMillis();
		for (int i = 0; i < 1024; i++) {
			map.put("key-" + i, "this is the test value " + i);
			if ((i & 0x0f) == 0)
				db.commit();
		}
		db.commit();
		long end = System.currentTimeMillis();

		System.out.format("Total: %dms\n", end - start);

		map.close();
		db.close();
	}

	@Test
	public void testMapdbRead() throws Exception {
		DB db = DBMaker.fileDB("/Users/jingyu/Temp/test.db")
				.fileMmapEnable()
				.concurrencyScale(8)
				.transactionEnable()
				.make();

		HTreeMap<String, String> map = db.hashMap("test", Serializer.STRING, Serializer.STRING)
				.layout(8, 32, 4)
				.createOrOpen();

		long start = System.currentTimeMillis();
		for (int i = 0; i < 1024; i++) {
			String v = map.get("key-" + i);
			if (v == null)
				fail();
			//System.out.println(v);
		}
		long end = System.currentTimeMillis();

		System.out.format("Total: %dms\n", end - start);

		map.close();
		db.close();
	}

	@Test
	public void testBTreeMap() {
		DB db = DBMaker.fileDB("/Users/jingyu/Temp/test.db")
				.fileMmapEnable()
				.concurrencyScale(8)
				.transactionEnable()
				.make();

		BTreeMap<Object[], String> map = db.treeMap("multiple-keys")
				.keySerializer(new SerializerArrayTuple(
						Serializer.STRING, Serializer.STRING, Serializer.INTEGER))
				.valueSerializer(Serializer.STRING)
				.createOrOpen();

		//initial values
		String[] towns = {"Galway", "Ennis", "Gort", "Cong", "Tuam"};
		String[] streets = {"Main Street", "Shop Street", "Second Street", "Silver Strands"};
		int[] houseNums = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

		for (String town : towns)
			for (String street : streets)
				for (int house : houseNums) {
					String value = "Town: " + town + ", Street: " + street + ", HouseNo.: " + house;
					map.put(new Object[]{town, street, house}, value);
				}

		db.commit();

		Map<Object[], String> cong = map.prefixSubMap(new Object[] {"Cong"});
		assertEquals(houseNums.length*streets.length, cong.size());

		/*
		cong = map.subMap(
				new Object[]{"Cong"},           //shorter array is 'negative infinity'
				new Object[]{"Cong",null,null} // null is positive infinity'
				);
		assertEquals(houseNums.length*streets.length, cong.size());

		for(String town:towns){ //first loop iterates over towns
			for(String v: map.prefixSubMap(new Object[]{town, "Main Street"}).values()){
				System.out.println(v);
			}
		}
		 */

		Map<Object[], String> congMain = map.prefixSubMap(new Object[]{"Cong", "Main Street"});
		assertEquals(houseNums.length, congMain.size());

		for(String v: congMain.values())
			System.out.println(v);

		map.close();
		db.close();
	}
}
