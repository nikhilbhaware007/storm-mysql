/**
 * Copyright 2016 Flipkart Internet Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.flipkart.storm.mysql;

import com.flipkart.storm.mysql.schema.ColumnInfo;
import com.flipkart.storm.mysql.schema.DatabaseInfo;
import com.google.code.or.binlog.BinlogEventListener;
import com.google.code.or.binlog.BinlogEventV4;
import com.google.code.or.binlog.impl.event.DeleteRowsEvent;
import com.google.code.or.binlog.impl.event.DeleteRowsEventV2;
import com.google.code.or.binlog.impl.event.QueryEvent;
import com.google.code.or.binlog.impl.event.RotateEvent;
import com.google.code.or.binlog.impl.event.TableMapEvent;
import com.google.code.or.binlog.impl.event.UpdateRowsEvent;
import com.google.code.or.binlog.impl.event.UpdateRowsEventV2;
import com.google.code.or.binlog.impl.event.WriteRowsEvent;
import com.google.code.or.binlog.impl.event.WriteRowsEventV2;
import com.google.code.or.binlog.impl.event.XidEvent;
import com.google.code.or.common.glossary.Column;
import com.google.code.or.common.glossary.Pair;
import com.google.code.or.common.glossary.Row;
import com.google.code.or.common.util.MySQLConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import static java.lang.Math.toIntExact;

/**
 * The listener for all bin log events generated by Open Replicator.
 * This class emits a {@code TransactionEvent} for the spout to forward.
 */
public class SpoutBinLogEventListener implements BinlogEventListener {

    /** The Logger. */
    public static final Logger  LOGGER               = LoggerFactory.getLogger(SpoutBinLogEventListener.class);
    private final TransactionEvent.Builder txBuilder = CentralTxEventBuilder.INSTANCE.getBuilder();

    private final LinkedBlockingQueue<TransactionEvent> queue;
    private final DatabaseInfo                          databaseInfo;
    private final Map<Long, String>                     tableCache;
    private String                                      currentBinLogFileName;

    /**
     * Instantiating the listener with complete database info and buffer.
     *
     * @param queue the communication channel between the listener and the spout.
     * @param databaseInfo the schema of the database.
     * @param binLogFileName the bin log file name to start replicating from.
     */
    public SpoutBinLogEventListener(LinkedBlockingQueue<TransactionEvent> queue,
                                    DatabaseInfo databaseInfo,
                                    String binLogFileName) {
        this.queue = queue;
        this.databaseInfo = databaseInfo;
        this.tableCache = new HashMap<Long, String>();
        this.currentBinLogFileName = binLogFileName;
    }

    private List<Map<String, Object>> getData(String tableName, List<Row> rows) {
        List<Map<String, Object>> dataList = new ArrayList<Map<String, Object>>();
        for (Row row : rows) {
            Map<String, Object> data = new HashMap<String, Object>();
            List<Column> columns = row.getColumns();
            List<ColumnInfo> columnSchemaInfo = databaseInfo.getTableInfo(tableName).getRowInfo().getColumnInfo();
            for (int index = 0; index < columns.size(); ++index) {
                ColumnInfo columnInfo = columnSchemaInfo.get(index);
                Column col = columns.get(index);
                data.put(columnInfo.getName(),
                         columnInfo.getColumnDataType().getConvertedValue(columnInfo.getColumnDataType(),
                                 col.getValue()));
            }
            dataList.add(data);
        }
        return dataList;
    }

    /**
     * This call is a callback for all events as generated by the open replicator.
     * If the open replicator is considered a black box, this would be the event
     * entry point in the system.
     *
     * @param event the bin log event
     */
    @Override
    public void onEvents(BinlogEventV4 event) {

        LOGGER.trace("Received bin log event {}", event);

        String tableName = "";
        switch (event.getHeader().getEventType()) {
            case MySQLConstants.WRITE_ROWS_EVENT:
                WriteRowsEvent writeRowsEvent = (WriteRowsEvent) event;
                tableName = tableCache.get(writeRowsEvent.getTableId());
                if (tableName != null && txBuilder.getInnerTxState() == TransactionState.STARTED) {

                    DataEvent dataEvent = new DataEvent(tableName, DataEventType.INSERT,
                                                        null,
                                                        getData(tableName, writeRowsEvent.getRows()));
                    txBuilder.addDataEvent(dataEvent);
                }
                break;

            case MySQLConstants.WRITE_ROWS_EVENT_V2:
                WriteRowsEventV2 writeRowsEventV2 = (WriteRowsEventV2) event;
                tableName = tableCache.get(writeRowsEventV2.getTableId());
                if (tableName != null && txBuilder.getInnerTxState() == TransactionState.STARTED) {

                    DataEvent dataEvent = new DataEvent(tableName, DataEventType.INSERT,
                                                        null,
                                                        getData(tableName, writeRowsEventV2.getRows()));
                    txBuilder.addDataEvent(dataEvent);
                }
                break;

            case MySQLConstants.UPDATE_ROWS_EVENT:
                UpdateRowsEvent updateRowsEvent = (UpdateRowsEvent) event;
                tableName = tableCache.get(updateRowsEvent.getTableId());
                if (tableName != null && txBuilder.getInnerTxState() == TransactionState.STARTED) {

                    List<Row> oldData = new ArrayList<Row>();
                    List<Row> newData = new ArrayList<Row>();
                    for (Pair<Row> rows : updateRowsEvent.getRows()) {
                        oldData.add(rows.getBefore());
                        newData.add(rows.getAfter());
                    }
                    DataEvent dataEvent = new DataEvent(tableName, DataEventType.UPDATE,
                                                        getData(tableName, oldData),
                                                        getData(tableName, newData));
                    txBuilder.addDataEvent(dataEvent);
                }
                break;

            case MySQLConstants.UPDATE_ROWS_EVENT_V2:
                UpdateRowsEventV2 updateRowsEventV2 = (UpdateRowsEventV2) event;
                tableName = tableCache.get(updateRowsEventV2.getTableId());
                if (tableName != null && txBuilder.getInnerTxState() == TransactionState.STARTED) {

                    List<Row> oldData = new ArrayList<Row>();
                    List<Row> newData = new ArrayList<Row>();
                    for (Pair<Row> rows : updateRowsEventV2.getRows()) {
                        oldData.add(rows.getBefore());
                        newData.add(rows.getAfter());
                    }
                    DataEvent dataEvent = new DataEvent(tableName, DataEventType.UPDATE,
                                                        getData(tableName, oldData),
                                                        getData(tableName, newData));
                    txBuilder.addDataEvent(dataEvent);
                }
                break;

            case MySQLConstants.DELETE_ROWS_EVENT:
                DeleteRowsEvent deleteRowsEvent = (DeleteRowsEvent) event;
                tableName = tableCache.get(deleteRowsEvent.getTableId());
                if (tableName != null && txBuilder.getInnerTxState() == TransactionState.STARTED) {

                    DataEvent dataEvent = new DataEvent(tableName, DataEventType.DELETE,
                                                        null,
                                                        getData(tableName, deleteRowsEvent.getRows()));
                    txBuilder.addDataEvent(dataEvent);
                }
                break;

            case MySQLConstants.DELETE_ROWS_EVENT_V2:
                DeleteRowsEventV2 deleteRowsEventV2 = (DeleteRowsEventV2) event;
                tableName = tableCache.get(deleteRowsEventV2.getTableId());
                if (tableName != null && txBuilder.getInnerTxState() == TransactionState.STARTED) {

                    DataEvent dataEvent = new DataEvent(tableName, DataEventType.DELETE,
                                                        null,
                                                        getData(tableName, deleteRowsEventV2.getRows()));
                    txBuilder.addDataEvent(dataEvent);
                }
                break;

            case MySQLConstants.TABLE_MAP_EVENT:
                TableMapEvent tableMapEvent = (TableMapEvent) event;
                Long tableId = tableMapEvent.getTableId();
                if (!tableCache.containsKey(tableId)) {
                    String databaseName = tableMapEvent.getDatabaseName().toString();
                    tableName = tableMapEvent.getTableName().toString();
                    if (databaseName.equals(this.databaseInfo.getDatabaseName())) {
                        if (this.databaseInfo.getAllTableNames().contains(tableName)) {
                            this.tableCache.put(tableId, tableName);
                        }
                    }
                }
                break;

            case MySQLConstants.QUERY_EVENT:
                QueryEvent queryEvent = (QueryEvent) event;
                String sql = queryEvent.getSql().toString();
                if ("BEGIN".equalsIgnoreCase(sql)) {
                    String databaseName = queryEvent.getDatabaseName().toString();
                    if (databaseName.equals(this.databaseInfo.getDatabaseName())) {
                        txBuilder.reset()
                                .txState(TransactionState.STARTED)
                                .txTimeStart(System.nanoTime())
                                .database(databaseName)
                                .serverId(toIntExact(queryEvent.getHeader().getServerId()))
                                .binLogFileName(this.currentBinLogFileName)
                                .binLogPosition(toIntExact(queryEvent.getHeader().getPosition()));
                    }
                }
                break;

            case MySQLConstants.XID_EVENT:
                XidEvent xidEvent = (XidEvent) event;
                if (txBuilder.getInnerTxState() == TransactionState.STARTED &&
                    txBuilder.getInnerDataEvents().size() > 0 &&
                    txBuilder.getInnerServerId() == xidEvent.getHeader().getServerId()) {
                    TransactionEvent txEvent = txBuilder.txState(TransactionState.END)
                                                        .txTimeEnd(System.nanoTime())
                                                        .txId(xidEvent.getXid())
                                                        .build();
                    this.queue.offer(txEvent);
                }
                txBuilder.reset();
                break;

            case MySQLConstants.ROTATE_EVENT:
                RotateEvent rotateEvent = (RotateEvent) event;
                LOGGER.info("File was rotated from {} to new file {}", this.currentBinLogFileName,
                                                    rotateEvent.getBinlogFileName().toString());
                this.currentBinLogFileName = rotateEvent.getBinlogFileName().toString();
                break;

            default:
                break;
        }
    }
}
