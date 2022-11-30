/**
 * Copyright (c) 2016-2022 Deephaven Data Labs and Patent Pending
 */
package io.deephaven.engine.testutil;

import io.deephaven.base.Pair;
import io.deephaven.base.verify.Assert;
import io.deephaven.base.verify.Require;
import io.deephaven.configuration.Configuration;
import io.deephaven.engine.liveness.LivenessScopeStack;
import io.deephaven.engine.liveness.LivenessStateException;
import io.deephaven.engine.rowset.*;
import io.deephaven.engine.table.ColumnSource;
import io.deephaven.engine.table.ElementSource;
import io.deephaven.engine.table.Table;
import io.deephaven.engine.table.impl.AbstractColumnSource;
import io.deephaven.engine.table.impl.PrevColumnSource;
import io.deephaven.engine.table.impl.QueryTable;
import io.deephaven.engine.table.impl.util.ColumnHolder;
import io.deephaven.engine.testutil.generator.*;
import io.deephaven.engine.testutil.rowset.RowSetTstUtils;
import io.deephaven.engine.testutil.sources.DateTimeTreeMapSource;
import io.deephaven.engine.testutil.sources.ImmutableColumnHolder;
import io.deephaven.engine.testutil.sources.ImmutableTreeMapSource;
import io.deephaven.engine.testutil.sources.TreeMapSource;
import io.deephaven.engine.testutil.testcase.RefreshingTableTestCase;
import io.deephaven.engine.util.TableDiff;
import io.deephaven.engine.util.TableTools;
import io.deephaven.stringset.HashStringSet;
import io.deephaven.stringset.StringSet;
import io.deephaven.time.DateTime;
import io.deephaven.util.QueryConstants;
import io.deephaven.util.SafeCloseable;
import io.deephaven.util.type.ArrayTypeUtils;
import junit.framework.AssertionFailedError;
import junit.framework.ComparisonFailure;
import junit.framework.TestCase;
import org.apache.commons.lang3.mutable.MutableInt;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiConsumer;

public class TstUtils {
    public static boolean SHORT_TESTS = Configuration.getInstance()
            .getBooleanForClassWithDefault(TstUtils.class, "shortTests", false);

    public static int scaleToDesiredTestLength(final int maxIter) {
        if (!SHORT_TESTS) {
            return maxIter;
        }
        final double shortTestFactor = 0.2;
        return (int) Math.ceil(maxIter * shortTestFactor);
    }

    public static <T> ColumnHolder c(String name, T... data) {
        return TableTools.col(name, data);
    }

    public static WritableRowSet i(long... keys) {
        return RowSetFactory.fromKeys(keys);
    }

    public static WritableRowSet ir(final long firstKey, final long lastKey) {
        return RowSetFactory.fromRange(firstKey, lastKey);
    }

    public static void addToTable(final Table table, final RowSet rowSet, final ColumnHolder... columnHolders) {
        Require.requirement(table.isRefreshing(), "table.isRefreshing()");
        final Set<String> usedNames = new HashSet<>();
        for (ColumnHolder columnHolder : columnHolders) {
            if (!usedNames.add(columnHolder.name)) {
                throw new IllegalStateException("Added to the same column twice!");
            }
            final ColumnSource columnSource = table.getColumnSource(columnHolder.name);
            final Object[] boxedArray = ArrayTypeUtils.getBoxedArray(columnHolder.data);
            final RowSet colRowSet = (boxedArray.length == 0) ? TstUtils.i() : rowSet;
            if (colRowSet.size() != boxedArray.length) {
                throw new IllegalArgumentException(columnHolder.name + ": Invalid data addition: rowSet="
                        + colRowSet.size() + ", boxedArray=" + boxedArray.length);
            }

            if (colRowSet.size() == 0) {
                continue;
            }

            if (columnSource instanceof DateTimeTreeMapSource && columnHolder.dataType == long.class) {
                final DateTimeTreeMapSource treeMapSource = (DateTimeTreeMapSource) columnSource;
                treeMapSource.add(colRowSet, (Long[]) boxedArray);
            } else if (columnSource.getType() != columnHolder.dataType) {
                throw new UnsupportedOperationException(columnHolder.name + ": Adding invalid type: source.getType()="
                        + columnSource.getType() + ", columnHolder=" + columnHolder.dataType);
            }

            if (columnSource instanceof TreeMapSource) {
                // noinspection unchecked
                final TreeMapSource<Object> treeMapSource = (TreeMapSource<Object>) columnSource;
                treeMapSource.add(colRowSet, boxedArray);
            } else if (columnSource instanceof DateTimeTreeMapSource) {
                final DateTimeTreeMapSource treeMapSource = (DateTimeTreeMapSource) columnSource;
                treeMapSource.add(colRowSet, (Long[]) boxedArray);
            }
        }

        if (!usedNames.containsAll(table.getColumnSourceMap().keySet())) {
            final Set<String> expected = new LinkedHashSet<>(table.getColumnSourceMap().keySet());
            expected.removeAll(usedNames);
            throw new IllegalStateException("Not all columns were populated, missing " + expected);
        }

        table.getRowSet().writableCast().insert(rowSet);
        if (table.isFlat()) {
            Assert.assertion(table.getRowSet().isFlat(), "table.build().isFlat()", table.getRowSet(),
                    "table.build()", rowSet, "rowSet");
        }
    }

    public static void removeRows(Table table, RowSet rowSet) {
        Require.requirement(table.isRefreshing(), "table.isRefreshing()");
        table.getRowSet().writableCast().remove(rowSet);
        if (table.isFlat()) {
            Assert.assertion(table.getRowSet().isFlat(), "table.build().isFlat()", table.getRowSet(),
                    "table.build()", rowSet, "rowSet");
        }
        for (ColumnSource columnSource : table.getColumnSources()) {
            if (columnSource instanceof TreeMapSource) {
                final TreeMapSource treeMapSource = (TreeMapSource) columnSource;
                treeMapSource.remove(rowSet);
            }
        }
    }

    // TODO: this is just laziness, make it go away
    public static <T> ColumnHolder cG(String name, T... data) {
        return ColumnHolder.createColumnHolder(name, true, data);
    }

    public static ColumnHolder getRandomStringCol(String colName, int size, Random random) {
        final String[] data = new String[size];
        for (int i = 0; i < data.length; i++) {
            data[i] = Long.toString(random.nextLong(), 'z' - 'a' + 10);
        }
        return c(colName, data);
    }

    public static ColumnHolder getRandomStringArrayCol(String colName, int size, Random random, int maxSz) {
        final String[][] data = new String[size][];
        for (int i = 0; i < data.length; i++) {
            final String[] v = new String[random.nextInt(maxSz)];
            for (int j = 0; j < v.length; j++) {
                v[j] = Long.toString(random.nextLong(), 'z' - 'a' + 10);
            }
            data[i] = v;
        }
        return c(colName, data);
    }

    public static ColumnHolder getRandomStringSetCol(String colName, int size, Random random, int maxSz) {
        final StringSet[] data = new StringSet[size];
        for (int i = 0; i < data.length; i++) {
            final String[] v = new String[random.nextInt(maxSz)];
            for (int j = 0; j < v.length; j++) {
                v[j] = Long.toString(random.nextLong(), 'z' - 'a' + 10);
            }
            data[i] = new HashStringSet(Arrays.asList(v));
        }
        return c(colName, data);
    }

    public static ColumnHolder getRandomIntCol(String colName, int size, Random random) {
        final Integer[] data = new Integer[size];
        for (int i = 0; i < data.length; i++) {
            data[i] = random.nextInt(1000);
        }
        return c(colName, data);
    }

    public static ColumnHolder getRandomDoubleCol(String colName, int size, Random random) {
        final Double[] data = new Double[size];
        for (int i = 0; i < data.length; i++) {
            data[i] = random.nextDouble();
        }
        return c(colName, data);
    }

    public static ColumnHolder getRandomFloatCol(String colName, int size, Random random) {
        final float[] data = new float[size];
        for (int i = 0; i < data.length; i++) {
            data[i] = random.nextFloat();
        }
        return new ColumnHolder(colName, false, data);
    }

    public static ColumnHolder getRandomShortCol(String colName, int size, Random random) {
        final short[] data = new short[size];
        for (int i = 0; i < data.length; i++) {
            data[i] = (short) random.nextInt(Short.MAX_VALUE);
        }
        return new ColumnHolder(colName, false, data);
    }

    public static ColumnHolder getRandomLongCol(String colName, int size, Random random) {
        final long[] data = new long[size];
        for (int i = 0; i < data.length; i++) {
            data[i] = random.nextLong();
        }
        return new ColumnHolder(colName, false, data);
    }

    public static ColumnHolder getRandomBooleanCol(String colName, int size, Random random) {
        final Boolean[] data = new Boolean[size];
        for (int i = 0; i < data.length; i++) {
            data[i] = random.nextBoolean();
        }
        return ColumnHolder.createColumnHolder(colName, false, data);
    }

    public static ColumnHolder getRandomCharCol(String colName, int size, Random random) {
        final char[] data = new char[size];
        for (int i = 0; i < data.length; i++) {
            data[i] = (char) random.nextInt();
        }
        return new ColumnHolder(colName, false, data);
    }

    public static ColumnHolder getRandomByteCol(String colName, int size, Random random) {
        final byte[] data = new byte[size];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) random.nextInt();
        }
        return new ColumnHolder(colName, false, data);
    }

    public static ColumnHolder getRandomByteArrayCol(String colName, int size, Random random, int maxSz) {
        final byte[][] data = new byte[size][];
        for (int i = 0; i < size; i++) {
            final byte[] b = new byte[random.nextInt(maxSz)];
            random.nextBytes(b);
            data[i] = b;
        }
        return c(colName, data);
    }

    public static ColumnHolder getRandomBooleanArrayCol(String colName, int size, Random random, int maxSz) {
        final Boolean[][] data = new Boolean[size][];
        for (int i = 0; i < size; i++) {
            final Boolean[] v = new Boolean[random.nextInt(maxSz)];
            for (int j = 0; j < v.length; j++) {
                v[j] = random.nextBoolean();
            }
            data[i] = v;
        }
        return c(colName, data);
    }

    public static ColumnHolder getRandomIntArrayCol(String colName, int size, Random random, int maxSz) {
        final int[][] data = new int[size][];
        for (int i = 0; i < size; i++) {
            final int[] v = new int[random.nextInt(maxSz)];
            for (int j = 0; j < v.length; j++) {
                v[j] = random.nextInt();
            }
            data[i] = v;
        }
        return c(colName, data);
    }

    public static ColumnHolder getRandomLongArrayCol(String colName, int size, Random random, int maxSz) {
        final long[][] data = new long[size][];
        for (int i = 0; i < size; i++) {
            final long[] v = new long[random.nextInt(maxSz)];
            for (int j = 0; j < v.length; j++) {
                v[j] = random.nextLong();
            }
            data[i] = v;
        }
        return c(colName, data);
    }

    public static ColumnHolder getRandomShortArrayCol(String colName, int size, Random random, int maxSz) {
        final short[][] data = new short[size][];
        for (int i = 0; i < size; i++) {
            final short[] v = new short[random.nextInt(maxSz)];
            for (int j = 0; j < v.length; j++) {
                v[j] = (short) random.nextInt();
            }
            data[i] = v;
        }
        return c(colName, data);
    }

    public static ColumnHolder getRandomDoubleArrayCol(String colName, int size, Random random, int maxSz) {
        final double[][] data = new double[size][];
        for (int i = 0; i < size; i++) {
            final double[] v = new double[random.nextInt(maxSz)];
            for (int j = 0; j < v.length; j++) {
                v[j] = random.nextDouble();
            }
            data[i] = v;
        }
        return c(colName, data);
    }

    public static ColumnHolder getRandomFloatArrayCol(String colName, int size, Random random, int maxSz) {
        final float[][] data = new float[size][];
        for (int i = 0; i < size; i++) {
            final float[] v = new float[random.nextInt(maxSz)];
            for (int j = 0; j < v.length; j++) {
                v[j] = random.nextFloat();
            }
            data[i] = v;
        }
        return c(colName, data);
    }

    public static ColumnHolder getRandomCharArrayCol(String colName, int size, Random random, int maxSz) {
        final char[][] data = new char[size][];
        for (int i = 0; i < size; i++) {
            final char[] v = new char[random.nextInt(maxSz)];
            for (int j = 0; j < v.length; j++) {
                v[j] = (char) random.nextInt();
            }
            data[i] = v;
        }
        return c(colName, data);
    }

    public static ColumnHolder getRandomBigDecimalCol(String colName, int size, Random random) {
        final BigDecimal[] data = new BigDecimal[size];
        for (int i = 0; i < data.length; i++) {
            data[i] = BigDecimal.valueOf(random.nextDouble());
        }
        return c(colName, data);
    }

    public static ColumnHolder getRandomDateTimeCol(String colName, int size, Random random) {
        final DateTime[] data = new DateTime[size];
        for (int i = 0; i < data.length; i++) {
            data[i] = new DateTime(random.nextLong());
        }
        return ColumnHolder.createColumnHolder(colName, false, data);
    }

    public static void validate(final EvalNuggetInterface[] en) {
        validate("", en);
    }

    public static void validate(final String ctxt, final EvalNuggetInterface[] en) {
        if (RefreshingTableTestCase.printTableUpdates) {
            System.out.println();
            System.out.println("================ NEXT ITERATION ================");
        }
        for (int i = 0; i < en.length; i++) {
            try (final SafeCloseable ignored = LivenessScopeStack.open()) {
                if (RefreshingTableTestCase.printTableUpdates) {
                    if (i != 0) {
                        System.out.println("================ NUGGET (" + i + ") ================");
                    }
                    try {
                        en[i].show();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    System.out.println();
                }
                en[i].validate(ctxt + " en_i = " + i);
            }
        }
    }

    static WritableRowSet getInitialIndex(int size, Random random) {
        final RowSetBuilderRandom builder = RowSetFactory.builderRandom();
        long firstKey = 10;
        for (int i = 0; i < size; i++) {
            builder.addKey(firstKey = firstKey + random.nextInt(3));
        }
        return builder.build();
    }

    public static WritableRowSet selectSubIndexSet(int size, RowSet sourceRowSet, Random random) {
        Assert.assertion(size <= sourceRowSet.size(), "size <= sourceRowSet.size()", size, "size", sourceRowSet,
                "sourceRowSet.size()");

        // generate an array that is the size of our RowSet, then shuffle it, and those are the positions we'll pick
        final Integer[] positions = new Integer[(int) sourceRowSet.size()];
        for (int ii = 0; ii < positions.length; ++ii) {
            positions[ii] = ii;
        }
        Collections.shuffle(Arrays.asList(positions), random);

        // now create a RowSet with each of our selected positions
        final RowSetBuilderRandom resultBuilder = RowSetFactory.builderRandom();
        for (int ii = 0; ii < size; ++ii) {
            resultBuilder.addKey(sourceRowSet.get(positions[ii]));
        }

        return resultBuilder.build();
    }

    public static RowSet newIndex(int targetSize, RowSet sourceRowSet, Random random) {
        final long maxKey = (sourceRowSet.size() == 0 ? 0 : sourceRowSet.lastRowKey());
        final long emptySlots = maxKey - sourceRowSet.size();
        final int slotsToFill = Math.min(
                Math.min((int) (Math.max(0.0, ((random.nextGaussian() / 0.1) + 0.9)) * emptySlots), targetSize),
                (int) emptySlots);

        final WritableRowSet fillIn =
                selectSubIndexSet(slotsToFill,
                        RowSetFactory.fromRange(0, maxKey).minus(sourceRowSet), random);

        final int endSlots = targetSize - (int) fillIn.size();

        double density = ((random.nextGaussian() / 0.25) + 0.5);
        density = Math.max(density, 0.1);
        density = Math.min(density, 1);
        final long rangeSize = (long) ((1.0 / density) * endSlots);
        final RowSet expansion =
                selectSubIndexSet(endSlots,
                        RowSetFactory.fromRange(maxKey + 1, maxKey + rangeSize + 1), random);

        fillIn.insert(expansion);

        Assert.assertion(fillIn.size() == targetSize, "fillIn.size() == targetSize", fillIn.size(), "fillIn.size()",
                targetSize, "targetSize", endSlots, "endSlots", slotsToFill, "slotsToFill");

        return fillIn;
    }

    public static ColumnInfo[] initColumnInfos(String[] names, Generator... generators) {
        if (names.length != generators.length) {
            throw new IllegalArgumentException(
                    "names and generator lengths mismatch: " + names.length + " != " + generators.length);
        }

        final ColumnInfo[] result = new ColumnInfo[names.length];
        for (int i = 0; i < result.length; i++) {
            // noinspection unchecked
            result[i] = new ColumnInfo(generators[i], names[i]);
        }
        return result;
    }

    public static ColumnInfo[] initColumnInfos(String[] names, ColumnInfo.ColAttributes[] attributes,
            Generator... generators) {
        if (names.length != generators.length) {
            throw new IllegalArgumentException(
                    "names and generator lengths mismatch: " + names.length + " != " + generators.length);
        }

        final ColumnInfo[] result = new ColumnInfo[names.length];
        for (int i = 0; i < result.length; i++) {
            // noinspection unchecked
            result[i] = new ColumnInfo(generators[i], names[i], attributes);
        }
        return result;
    }

    public static ColumnInfo[] initColumnInfos(String[] names, List<List<ColumnInfo.ColAttributes>> attributes,
            Generator... generators) {
        if (names.length != generators.length) {
            throw new IllegalArgumentException(
                    "names and generator lengths mismatch: " + names.length + " != " + generators.length);
        }

        final ColumnInfo[] result = new ColumnInfo[names.length];
        for (int ii = 0; ii < result.length; ii++) {
            // noinspection unchecked
            result[ii] = new ColumnInfo(generators[ii], names[ii],
                    attributes.get(ii).toArray(ColumnInfo.ZERO_LENGTH_COLUMN_ATTRIBUTES_ARRAY));
        }
        return result;
    }

    public static QueryTable getTable(int size, Random random, ColumnInfo[] columnInfos) {
        return getTable(true, size, random, columnInfos);
    }

    public static QueryTable getTable(boolean refreshing, int size, Random random, ColumnInfo[] columnInfos) {
        final TrackingWritableRowSet rowSet = getInitialIndex(size, random).toTracking();
        for (ColumnInfo columnInfo : columnInfos) {
            columnInfo.populateMap(rowSet, random);
        }
        final ColumnHolder[] sources = new ColumnHolder[columnInfos.length];
        for (int i = 0; i < columnInfos.length; i++) {
            sources[i] = columnInfos[i].c();
        }
        if (refreshing) {
            return testRefreshingTable(rowSet, sources);
        } else {
            return testTable(rowSet, sources);
        }
    }

    public static QueryTable testTable(ColumnHolder... columnHolders) {
        final Object[] boxedData = ArrayTypeUtils.getBoxedArray(columnHolders[0].data);
        final TrackingRowSet rowSet = RowSetFactory.flat(boxedData.length).toTracking();
        return testTable(rowSet, columnHolders);
    }

    public static QueryTable testTable(TrackingRowSet rowSet, ColumnHolder... columnHolders) {
        final Map<String, ColumnSource<?>> columns = new LinkedHashMap<>();
        for (ColumnHolder columnHolder : columnHolders) {
            columns.put(columnHolder.name, getTreeMapColumnSource(rowSet, columnHolder));
        }
        return new QueryTable(rowSet, columns);
    }

    public static QueryTable testRefreshingTable(TrackingRowSet rowSet, ColumnHolder... columnHolders) {
        final QueryTable queryTable = testTable(rowSet, columnHolders);
        queryTable.setRefreshing(true);
        return queryTable;
    }

    public static QueryTable testFlatRefreshingTable(TrackingRowSet rowSet, ColumnHolder... columnHolders) {
        Assert.assertion(rowSet.isFlat(), "rowSet.isFlat()", rowSet, "rowSet");
        final QueryTable queryTable = testTable(rowSet, columnHolders);
        queryTable.setRefreshing(true);
        queryTable.setFlat();
        return queryTable;
    }

    public static QueryTable testRefreshingTable(ColumnHolder... columnHolders) {
        final TrackingWritableRowSet rowSet = (columnHolders.length == 0
                ? RowSetFactory.empty()
                : RowSetFactory.flat(Array.getLength(columnHolders[0].data))).toTracking();
        final Map<String, ColumnSource<?>> columns = new LinkedHashMap<>();
        for (ColumnHolder columnHolder : columnHolders) {
            columns.put(columnHolder.name, getTreeMapColumnSource(rowSet, columnHolder));
        }
        final QueryTable queryTable = new QueryTable(rowSet, columns);
        queryTable.setRefreshing(true);
        return queryTable;
    }

    public static ColumnSource getTreeMapColumnSource(RowSet rowSet, ColumnHolder columnHolder) {
        final Object[] boxedData = ArrayTypeUtils.getBoxedArray(columnHolder.data);

        final AbstractColumnSource result;
        if (columnHolder instanceof ImmutableColumnHolder) {
            // noinspection unchecked,rawtypes
            result = new ImmutableTreeMapSource<>(columnHolder.dataType, rowSet, boxedData);
        } else if (columnHolder.dataType.equals(DateTime.class) && columnHolder.data instanceof long[]) {
            result = new DateTimeTreeMapSource(rowSet, (long[]) columnHolder.data);
        } else {
            // noinspection unchecked,rawtypes
            result = new TreeMapSource<>(columnHolder.dataType, rowSet, boxedData);
        }

        if (columnHolder.grouped) {
            // noinspection unchecked
            result.setGroupToRange(result.getValuesMapping(rowSet));
        }
        return result;
    }

    public static Table prevTableColumnSources(Table table) {
        final TrackingWritableRowSet rowSet = table.getRowSet().copyPrev().toTracking();
        final Map<String, ColumnSource<?>> columnSourceMap = new LinkedHashMap<>();
        table.getColumnSourceMap().forEach((k, cs) -> {
            columnSourceMap.put(k, new PrevColumnSource<>(cs));
        });
        return new QueryTable(rowSet, columnSourceMap);
    }

    public static Table prevTable(Table table) {
        final RowSet rowSet = table.getRowSet().copyPrev();

        final List<ColumnHolder<?>> cols = new ArrayList<>();
        for (Map.Entry<String, ? extends ColumnSource<?>> mapEntry : table.getColumnSourceMap().entrySet()) {
            final String name = mapEntry.getKey();
            final ColumnSource<?> columnSource = mapEntry.getValue();
            final List<Object> data = new ArrayList<>();

            for (final RowSet.Iterator it = rowSet.iterator(); it.hasNext();) {
                final long key = it.nextLong();
                final Object item = columnSource.getPrev(key);
                data.add(item);
            }

            if (columnSource.getType() == int.class) {
                cols.add(new ColumnHolder<>(name, false, data.stream()
                        .mapToInt(x -> x == null ? io.deephaven.util.QueryConstants.NULL_INT : (int) x).toArray()));
            } else if (columnSource.getType() == long.class) {
                cols.add(new ColumnHolder<>(name, false, data.stream()
                        .mapToLong(x -> x == null ? io.deephaven.util.QueryConstants.NULL_LONG : (long) x).toArray()));
            } else if (columnSource.getType() == boolean.class) {
                cols.add(ColumnHolder.createColumnHolder(name, false,
                        data.stream().map(x -> (Boolean) x).toArray(Boolean[]::new)));
            } else if (columnSource.getType() == String.class) {
                cols.add(ColumnHolder.createColumnHolder(name, false,
                        data.stream().map(x -> (String) x).toArray(String[]::new)));
            } else if (columnSource.getType() == double.class) {
                cols.add(new ColumnHolder<>(name, false,
                        data.stream()
                                .mapToDouble(x -> x == null ? io.deephaven.util.QueryConstants.NULL_DOUBLE : (double) x)
                                .toArray()));
            } else if (columnSource.getType() == float.class) {
                final float[] floatArray = new float[data.size()];
                for (int ii = 0; ii < data.size(); ++ii) {
                    final Object value = data.get(ii);
                    floatArray[ii] = value == null ? QueryConstants.NULL_FLOAT : (float) value;
                }
                cols.add(new ColumnHolder<>(name, false, floatArray));
            } else if (columnSource.getType() == char.class) {
                final char[] charArray = new char[data.size()];
                for (int ii = 0; ii < data.size(); ++ii) {
                    final Object value = data.get(ii);
                    charArray[ii] = value == null ? QueryConstants.NULL_CHAR : (char) value;
                }
                cols.add(new ColumnHolder<>(name, false, charArray));
            } else if (columnSource.getType() == byte.class) {
                final byte[] byteArray = new byte[data.size()];
                for (int ii = 0; ii < data.size(); ++ii) {
                    final Object value = data.get(ii);
                    byteArray[ii] = value == null ? QueryConstants.NULL_BYTE : (byte) value;
                }
                cols.add(new ColumnHolder<>(name, false, byteArray));
            } else if (columnSource.getType() == short.class) {
                final short[] shortArray = new short[data.size()];
                for (int ii = 0; ii < data.size(); ++ii) {
                    final Object value = data.get(ii);
                    shortArray[ii] = value == null ? QueryConstants.NULL_SHORT : (short) value;
                }
                cols.add(new ColumnHolder<>(name, false, shortArray));
            } else {
                cols.add(new ColumnHolder(name, columnSource.getType(), columnSource.getComponentType(), false,
                        data.toArray((Object[]) Array.newInstance(columnSource.getType(), data.size()))));
            }
        }

        return TableTools.newTable(cols.toArray(ColumnHolder.ZERO_LENGTH_COLUMN_HOLDER_ARRAY));
    }


    public static long getRandom(Random random, int bits) {
        final long value = random.nextLong();
        return bits >= 64 ? value : ((1L << bits) - 1L) & value;
    }

    public static void assertIndexEquals(@NotNull final RowSet expected, @NotNull final RowSet actual) {
        try {
            TestCase.assertEquals(expected, actual);
        } catch (AssertionFailedError error) {
            System.err.println("TrackingWritableRowSet equality check failed:"
                    + "\n\texpected: " + expected.toString()
                    + "\n]tactual: " + actual.toString()
                    + "\n]terror: " + error);
            throw error;
        }
    }

    public static void assertTableEquals(@NotNull final Table expected, @NotNull final Table actual,
            final TableDiff.DiffItems... itemsToSkip) {
        assertTableEquals("", expected, actual, itemsToSkip);
    }

    public static void assertEqualsByElements(Table actual, Table expected) {
        final Map<String, ? extends ColumnSource<?>> mapActual = actual.getColumnSourceMap();
        final Map<String, ? extends ColumnSource<?>> mapExpected = expected.getColumnSourceMap();
        Assertions.assertThat(mapActual.keySet()).containsExactlyInAnyOrderElementsOf(mapExpected.keySet());
        for (String key : mapActual.keySet()) {
            final ColumnSource<?> srcActual = mapActual.get(key);
            final ColumnSource<?> srcExpected = mapExpected.get(key);
            // noinspection unchecked,rawtypes
            assertEquals(
                    actual.getRowSet(), (ElementSource) srcActual,
                    expected.getRowSet(), (ElementSource) srcExpected);
        }
    }

    public static <T> void assertEquals(
            RowSet rowsActual, ElementSource<T> srcActual,
            RowSet rowsExpected, ElementSource<T> srcExpected) {
        Assertions.assertThat(rowsActual.size()).isEqualTo(rowsExpected.size());
        try (
                final RowSet.Iterator itActual = rowsActual.iterator();
                final RowSet.Iterator itExpected = rowsExpected.iterator()) {
            while (itActual.hasNext()) {
                if (!itExpected.hasNext()) {
                    throw new IllegalStateException();
                }
                Assertions.assertThat(srcActual.get(itActual.nextLong()))
                        .isEqualTo(srcExpected.get(itExpected.nextLong()));
            }
            if (itExpected.hasNext()) {
                throw new IllegalStateException();
            }
        }
    }

    /**
     * Equivalent to {@code input.getSubTable(RowSetTstUtils.subset(input.getRowSet(), dutyOn, dutyOff).toTracking())}.
     *
     * @param input the input table
     * @param dutyOn the duty-on size
     * @param dutyOff the duty-off size
     * @return a duty-limited subset
     * @see RowSetTstUtils#subset(RowSet, int, int)
     */
    public static Table subset(Table input, int dutyOn, int dutyOff) {
        return input.getSubTable(RowSetTstUtils.subset(input.getRowSet(), dutyOn, dutyOff).toTracking());
    }

    public static void assertTableEquals(final String context, @NotNull final Table expected,
            @NotNull final Table actual, final TableDiff.DiffItems... itemsToSkip) {
        if (itemsToSkip.length > 0) {
            assertTableEquals(context, expected, actual, EnumSet.of(itemsToSkip[0], itemsToSkip));
        } else {
            assertTableEquals(context, expected, actual, EnumSet.noneOf(TableDiff.DiffItems.class));
        }
    }

    public static void assertTableEquals(final String context, @NotNull final Table expected,
            @NotNull final Table actual, final EnumSet<TableDiff.DiffItems> itemsToSkip) {
        final Pair<String, Long> diffPair = TableTools.diffPair(actual, expected, 10, itemsToSkip);
        if (diffPair.getFirst().equals("")) {
            return;
        }
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            final long firstRow = Math.max(0, diffPair.getSecond() - 5);
            final long lastRow = Math.max(10, diffPair.getSecond() + 5);

            try (final PrintStream ps = new PrintStream(baos, true, "UTF-8")) {
                TableTools.showWithRowSet(expected, firstRow, lastRow, ps);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
            final String expectedString = baos.toString();
            baos.reset();

            try (final PrintStream ps = new PrintStream(baos, true, "UTF-8")) {
                TableTools.showWithRowSet(actual, firstRow, lastRow, ps);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
            final String actualString = baos.toString();

            throw new ComparisonFailure(context + "\n" + diffPair.getFirst(), expectedString, actualString);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reruns test cases trying new seeds while minimizing the number of steps to catch the failing test. The test
     * should mutate the provided MutableInt so that when it fails it is equal to the current step iteration. This
     * allows the test to minimize the total number of steps per seed as it discovers better candidate parameters.
     *
     * @param test A test instance to tearDown and setUp between each test.
     * @param initialSeed The seed to begin using.
     * @param maxSeed The highest seed to try.
     * @param initialSteps Number of steps to start with.
     * @param runner A method whose first param is the random seed to use, and second parameter is the number of steps.
     */
    public static void findMinimalTestCase(final RefreshingTableTestCase test, final int initialSeed, final int maxSeed,
            final int initialSteps, final BiConsumer<Integer, MutableInt> runner) {
        final boolean origPrintTableUpdates = RefreshingTableTestCase.printTableUpdates;
        RefreshingTableTestCase.printTableUpdates = false;

        int bestSeed = initialSeed;
        int bestSteps = initialSteps;
        boolean failed = false;
        MutableInt maxSteps = new MutableInt(initialSteps);
        for (int seed = initialSeed; seed < maxSeed; ++seed) {
            if (maxSteps.intValue() <= 0) {
                System.out.println("Best Run: bestSeed=" + bestSeed + " bestSteps=" + bestSteps);
                return;
            }
            System.out.println("Running: seed=" + seed + " numSteps=" + maxSteps.intValue() + " bestSeed=" + bestSeed
                    + " bestSteps=" + bestSteps);
            if (seed != initialSeed) {
                try {
                    test.tearDown();
                } catch (Exception | Error e) {
                    System.out.println("Error on test.tearDown():");
                    e.printStackTrace();
                }
                try {
                    test.setUp();
                } catch (Exception | Error e) {
                    System.out.println("Error on test.setUp():");
                    e.printStackTrace();
                }
            }
            try {
                runner.accept(seed, maxSteps);
            } catch (Exception | Error e) {
                failed = true;
                bestSeed = seed;
                bestSteps = maxSteps.intValue() + 1;
                e.printStackTrace();
                System.out.println("Candidate: seed=" + seed + " numSteps=" + (maxSteps.intValue() + 1));
            }
        }

        RefreshingTableTestCase.printTableUpdates = origPrintTableUpdates;
        if (failed) {
            throw new RuntimeException("Debug candidate: seed=" + bestSeed + " steps=" + bestSteps);
        }
    }

    public static void expectLivenessException(@NotNull final Runnable action) {
        try {
            action.run();
            // noinspection ThrowableNotThrown
            Assert.statementNeverExecuted("Expected LivenessStateException");
        } catch (LivenessStateException expected) {
        }
    }

    public static void assertThrows(final Runnable runnable) {
        boolean threwException = false;
        try {
            runnable.run();
        } catch (final Exception ignored) {
            threwException = true;
        }
        TestCase.assertTrue(threwException);
    }

    public static void tableRangesAreEqual(Table table1, Table table2, long from1, long from2, long size) {
        org.junit.Assert.assertEquals("",
                TableTools.diff(table1.tail(table1.size() - from1).head(size),
                        table2.tail(table2.size() - from2).head(size), 10));
    }
}
