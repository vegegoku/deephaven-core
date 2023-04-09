/**
 * Copyright (c) 2016-2022 Deephaven Data Labs and Patent Pending
 */
package io.deephaven.web.client.api;

import com.vertispan.tsdefs.annotations.TsInterface;
import com.vertispan.tsdefs.annotations.TsName;
import elemental2.core.JsArray;
import elemental2.core.JsString;
import elemental2.dom.CustomEvent;
import elemental2.dom.Event;
import elemental2.promise.Promise;
import io.deephaven.web.client.api.filter.FilterCondition;
import io.deephaven.web.shared.fu.RemoverFn;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsOptional;
import jsinterop.annotations.JsProperty;
import jsinterop.base.Js;

/**
 * Behaves like a Table, but doesn't expose all of its API for changing the internal state. Instead, state is driven by
 * the upstream table - when it changes handle, this listens and updates its own handle accordingly.
 *
 * Additionally, this is automatically subscribed to its one and only row, across all columns.
 *
 * A new config is returned any time it is accessed, to prevent accidental mutation, and to allow it to be used as a
 * template when fetching a new totals table, or changing the totals table in use.
 *
 * A simplistic Table, providing access to aggregation of the table it is sourced from. This table is always
 * automatically subscribed to its parent, and adopts changes automatically from it. This class has limited methods
 * found on Table. Instances of this type always have a size of one when no groupBy is set on the config, but may
 * potentially contain as few as zero rows, or as many as the parent table if each row gets its own group.
 *
 * When using the `groupBy` feature, it may be desireable to also provide a row to the user with all values across all
 * rows. To achieve this, request the same Totals Table again, but remove the `groupBy` setting.
 */
@TsInterface
@TsName(namespace = "dh", name = "TotalsTable")
public class JsTotalsTable {
    private final JsTable wrappedTable;
    private final String directive;
    private final JsArray<JsString> groupBy;

    // unlike JsTable, we need to track the viewport since our state will change without
    // explicit API calls to this instance. This assumption changes a bit if we decide to
    // support sort/filter or custom columns on totals tables
    private Double firstRow;
    private Double lastRow;
    private Column[] columns;
    private Double updateIntervalMs;

    /**
     * Table is wrapped to let us delegate calls to it, the directive is a serialized string, and the groupBy is copied
     * when passed in, as well as when it is accessed, to prevent accidental mutation of the array.
     */
    public JsTotalsTable(JsTable wrappedTable, String directive, JsArray<String> groupBy) {
        this.wrappedTable = wrappedTable;
        this.directive = directive;
        this.groupBy = Js.uncheckedCast(groupBy.slice());
    }

    public void refreshViewport() {
        if (firstRow != null && lastRow != null) {
            setViewport(firstRow, lastRow, Js.uncheckedCast(columns), updateIntervalMs);
        }
    }

    /**
     * @return Read-only. The configuration used when creating this Totals Table.
     */
    @JsProperty
    public JsTotalsTableConfig getTotalsTableConfig() {
        JsTotalsTableConfig parsed = JsTotalsTableConfig.parse(directive);
        parsed.groupBy = Js.uncheckedCast(groupBy.slice());
        return parsed;
    }

    /**
     * Specifies the range of items to pass to the client and update as they change. If the columns parameter is not
     * provided, all columns will be used. Until this is called, no data will be available. Invoking this will result in
     * events to be fired once data becomes available, starting with an `updated` event and one `rowadded` event per row
     * in that range.
     *
     * @param firstRow
     * @param lastRow
     * @param columns
     * @param updateIntervalMs
     */
    @JsMethod
    public void setViewport(double firstRow, double lastRow, @JsOptional JsArray<Column> columns,
            @JsOptional Double updateIntervalMs) {
        this.firstRow = firstRow;
        this.lastRow = lastRow;
        this.columns = columns != null ? Js.uncheckedCast(columns.slice()) : null;
        this.updateIntervalMs = updateIntervalMs;
        wrappedTable.setViewport(firstRow, lastRow, columns, updateIntervalMs);
    }

    /**
     * @return the currently visible viewport. If the current set of operations has not yet resulted in data, it will
     *         not resolve until that data is ready.
     */
    @JsMethod
    public Promise<TableData> getViewportData() {
        return wrappedTable.getViewportData();
    }

    /**
     * @return Read-only. The columns present on this table. Note that this may not include all columns in the parent
     *         table, and in cases where a given column has more than one aggregation applied, the column name will have
     *         a suffix indicating the aggregation used. This suffixed name will be of the form `columnName + '__' +
     *         aggregationName`.
     */
    @JsProperty
    public JsArray<Column> getColumns() {
        return wrappedTable.getColumns();
    }

    /**
     * @param key
     * @return a column by the given name. You should prefer to always retrieve a new Column instance instead of caching
     *         a returned value.
     */
    @JsMethod
    public Column findColumn(String key) {
        return wrappedTable.findColumn(key);
    }

    /**
     * @param keys
     * @return multiple columns specified by the given names.
     */
    @JsMethod
    public Column[] findColumns(String[] keys) {
        return wrappedTable.findColumns(keys);
    }

    /**
     * Indicates that the table will no longer be used, and resources used to provide it can be freed up on the server.
     */
    @JsMethod
    public void close() {
        wrappedTable.close();
    }

    /**
     * @return Read-only. The total number of rows in this table. This may change as the base table's configuration,
     *         filter, or contents change.
     */
    @JsProperty
    public double getSize() {
        return wrappedTable.getSize();
    }

    @Override
    public String toString() {
        return "JsTotalsTable { totalsTableConfig=" + getTotalsTableConfig() + " }";
    }

    @JsMethod
    public <T> RemoverFn addEventListener(String name, EventFn<T> callback) {
        return wrappedTable.addEventListener(name, callback);
    }

    @JsMethod
    public <T> boolean removeEventListener(String name, EventFn<T> callback) {
        return wrappedTable.removeEventListener(name, callback);
    }

    @JsMethod
    public <T> Promise<CustomEvent<T>> nextEvent(String eventName, Double timeoutInMillis) {
        return wrappedTable.nextEvent(eventName, timeoutInMillis);
    }

    @JsMethod
    public boolean hasListeners(String name) {
        return wrappedTable.hasListeners(name);
    }

    /**
     * Replace the currently set sort on this table. Returns the previously set value. Note that the sort property will
     * immediately return the new value, but you may receive update events using the old sort before the new sort is
     * applied, and the `sortchanged` event fires. Reusing existing, applied sorts may enable this to perform better on
     * the server. The `updated` event will also fire, but `rowadded` and `rowremoved` will not.
     *
     * @param sort
     * @return
     */
    @JsMethod
    public JsArray<Sort> applySort(Sort[] sort) {
        return wrappedTable.applySort(sort);
    }

    /**
     * Replace the current custom columns with a new set. These columns can be used when adding new filter and sort
     * operations to the table, as long as they are present.
     *
     * @param customColumns
     * @return
     */
    @JsMethod
    public JsArray<CustomColumn> applyCustomColumns(Object[] customColumns) {
        return wrappedTable.applyCustomColumns(customColumns);
    }

    /**
     * Replace the currently set filters on the table. Returns the previously set value. Note that the filter property
     * will immediately return the new value, but you may receive update events using the old filter before the new one
     * is applied, and the `filterchanged` event fires. Reusing existing, applied filters may enable this to perform
     * better on the server. The `updated` event will also fire, but `rowadded` and `rowremoved` will not.
     *
     * @param filter
     * @return
     */
    @JsMethod
    public JsArray<FilterCondition> applyFilter(FilterCondition[] filter) {
        return wrappedTable.applyFilter(filter);
    }

    public JsTable getWrappedTable() {
        return wrappedTable;
    }

    /**
     * @return Read-only. An ordered list of Sorts to apply to the table. To update, call applySort(). Note that this
     *         getter will return the new value immediately, even though it may take a little time to update on the
     *         server. You may listen for the `sortchanged` event to know when to update the UI.
     */
    @JsProperty
    public JsArray<Sort> getSort() {
        return wrappedTable.getSort();
    }

    /**
     * @return Read-only. An ordered list of Filters to apply to the table. To update, call applyFilter(). Note that
     *         this getter will return the new value immediately, even though it may take a little time to update on the
     *         server. You may listen for the `filterchanged` event to know when to update the UI.
     */
    @JsProperty
    public JsArray<FilterCondition> getFilter() {
        return wrappedTable.getFilter();
    }

    /**
     * @return Read-only. An ordered list of custom column formulas to add to the table, either adding new columns or
     *         replacing existing ones. To update, call `applyCustomColumns()`.
     */
    @JsProperty
    public JsArray<CustomColumn> getCustomColumns() {
        return wrappedTable.getCustomColumns();
    }



}
