package io.deephaven.engine.table.impl.tuplesource.generated;

import io.deephaven.chunk.Chunk;
import io.deephaven.chunk.DoubleChunk;
import io.deephaven.chunk.ObjectChunk;
import io.deephaven.chunk.WritableChunk;
import io.deephaven.chunk.WritableObjectChunk;
import io.deephaven.chunk.attributes.Values;
import io.deephaven.engine.table.ColumnSource;
import io.deephaven.engine.table.TupleSource;
import io.deephaven.engine.table.WritableColumnSource;
import io.deephaven.engine.table.impl.tuplesource.AbstractTupleSource;
import io.deephaven.engine.table.impl.tuplesource.ThreeColumnTupleSourceFactory;
import io.deephaven.time.DateTimeUtils;
import io.deephaven.tuple.generated.DoubleByteLongTuple;
import io.deephaven.util.BooleanUtils;
import io.deephaven.util.type.TypeUtils;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * <p>{@link TupleSource} that produces key column values from {@link ColumnSource} types Double, Boolean, and Instant.
 * <p>Generated by io.deephaven.replicators.TupleSourceCodeGenerator.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class DoubleBooleanInstantColumnTupleSource extends AbstractTupleSource<DoubleByteLongTuple> {

    /** {@link ThreeColumnTupleSourceFactory} instance to create instances of {@link DoubleBooleanInstantColumnTupleSource}. **/
    public static final ThreeColumnTupleSourceFactory<DoubleByteLongTuple, Double, Boolean, Instant> FACTORY = new Factory();

    private final ColumnSource<Double> columnSource1;
    private final ColumnSource<Boolean> columnSource2;
    private final ColumnSource<Instant> columnSource3;

    public DoubleBooleanInstantColumnTupleSource(
            @NotNull final ColumnSource<Double> columnSource1,
            @NotNull final ColumnSource<Boolean> columnSource2,
            @NotNull final ColumnSource<Instant> columnSource3
    ) {
        super(columnSource1, columnSource2, columnSource3);
        this.columnSource1 = columnSource1;
        this.columnSource2 = columnSource2;
        this.columnSource3 = columnSource3;
    }

    @Override
    public final DoubleByteLongTuple createTuple(final long rowKey) {
        return new DoubleByteLongTuple(
                columnSource1.getDouble(rowKey),
                BooleanUtils.booleanAsByte(columnSource2.getBoolean(rowKey)),
                DateTimeUtils.epochNanos(columnSource3.get(rowKey))
        );
    }

    @Override
    public final DoubleByteLongTuple createPreviousTuple(final long rowKey) {
        return new DoubleByteLongTuple(
                columnSource1.getPrevDouble(rowKey),
                BooleanUtils.booleanAsByte(columnSource2.getPrevBoolean(rowKey)),
                DateTimeUtils.epochNanos(columnSource3.getPrev(rowKey))
        );
    }

    @Override
    public final DoubleByteLongTuple createTupleFromValues(@NotNull final Object... values) {
        return new DoubleByteLongTuple(
                TypeUtils.unbox((Double)values[0]),
                BooleanUtils.booleanAsByte((Boolean)values[1]),
                DateTimeUtils.epochNanos((Instant)values[2])
        );
    }

    @Override
    public final DoubleByteLongTuple createTupleFromReinterpretedValues(@NotNull final Object... values) {
        return new DoubleByteLongTuple(
                TypeUtils.unbox((Double)values[0]),
                BooleanUtils.booleanAsByte((Boolean)values[1]),
                DateTimeUtils.epochNanos((Instant)values[2])
        );
    }

    @SuppressWarnings("unchecked")
    @Override
    public final <ELEMENT_TYPE> void exportElement(@NotNull final DoubleByteLongTuple tuple, final int elementIndex, @NotNull final WritableColumnSource<ELEMENT_TYPE> writableSource, final long destinationRowKey) {
        if (elementIndex == 0) {
            writableSource.set(destinationRowKey, tuple.getFirstElement());
            return;
        }
        if (elementIndex == 1) {
            writableSource.set(destinationRowKey, (ELEMENT_TYPE) BooleanUtils.byteAsBoolean(tuple.getSecondElement()));
            return;
        }
        if (elementIndex == 2) {
            writableSource.set(destinationRowKey, (ELEMENT_TYPE) DateTimeUtils.epochNanosToInstant(tuple.getThirdElement()));
            return;
        }
        throw new IndexOutOfBoundsException("Invalid element index " + elementIndex + " for export");
    }

    @Override
    public final Object exportElement(@NotNull final DoubleByteLongTuple tuple, int elementIndex) {
        if (elementIndex == 0) {
            return TypeUtils.box(tuple.getFirstElement());
        }
        if (elementIndex == 1) {
            return BooleanUtils.byteAsBoolean(tuple.getSecondElement());
        }
        if (elementIndex == 2) {
            return DateTimeUtils.epochNanosToInstant(tuple.getThirdElement());
        }
        throw new IllegalArgumentException("Bad elementIndex for 3 element tuple: " + elementIndex);
    }

    @Override
    public final Object exportElementReinterpreted(@NotNull final DoubleByteLongTuple tuple, int elementIndex) {
        if (elementIndex == 0) {
            return TypeUtils.box(tuple.getFirstElement());
        }
        if (elementIndex == 1) {
            return BooleanUtils.byteAsBoolean(tuple.getSecondElement());
        }
        if (elementIndex == 2) {
            return DateTimeUtils.epochNanosToInstant(tuple.getThirdElement());
        }
        throw new IllegalArgumentException("Bad elementIndex for 3 element tuple: " + elementIndex);
    }

    @Override
    protected void convertChunks(@NotNull WritableChunk<? super Values> destination, int chunkSize, Chunk<? extends Values> [] chunks) {
        WritableObjectChunk<DoubleByteLongTuple, ? super Values> destinationObjectChunk = destination.asWritableObjectChunk();
        DoubleChunk<? extends Values> chunk1 = chunks[0].asDoubleChunk();
        ObjectChunk<Boolean, ? extends Values> chunk2 = chunks[1].asObjectChunk();
        ObjectChunk<Instant, ? extends Values> chunk3 = chunks[2].asObjectChunk();
        for (int ii = 0; ii < chunkSize; ++ii) {
            destinationObjectChunk.set(ii, new DoubleByteLongTuple(chunk1.get(ii), BooleanUtils.booleanAsByte(chunk2.get(ii)), DateTimeUtils.epochNanos(chunk3.get(ii))));
        }
        destinationObjectChunk.setSize(chunkSize);
    }

    /** {@link ThreeColumnTupleSourceFactory} for instances of {@link DoubleBooleanInstantColumnTupleSource}. **/
    private static final class Factory implements ThreeColumnTupleSourceFactory<DoubleByteLongTuple, Double, Boolean, Instant> {

        private Factory() {
        }

        @Override
        public TupleSource<DoubleByteLongTuple> create(
                @NotNull final ColumnSource<Double> columnSource1,
                @NotNull final ColumnSource<Boolean> columnSource2,
                @NotNull final ColumnSource<Instant> columnSource3
        ) {
            return new DoubleBooleanInstantColumnTupleSource(
                    columnSource1,
                    columnSource2,
                    columnSource3
            );
        }
    }
}
