package equ.clt.handler.astron;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.api.cashregister.CashRegisterInfo;
import equ.api.cashregister.CashRegisterItemInfo;
import equ.api.cashregister.DiscountCard;
import equ.api.cashregister.TransactionCashRegisterInfo;
import equ.api.stoplist.StopListInfo;
import equ.api.stoplist.StopListItemInfo;
import equ.clt.handler.DefaultCashRegisterHandler;
import equ.clt.handler.HandlerUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static equ.clt.handler.HandlerUtils.*;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@SuppressWarnings("SqlDialectInspection")
public class AstronHandler extends DefaultCashRegisterHandler<AstronSalesBatch> {

    protected final static Logger astronLogger = Logger.getLogger("AstronLogger");

    private static Map<String, Map<String, CashRegisterItemInfo>> deleteBarcodeConnectionStringMap = new HashMap<>();

    private FileSystemXmlApplicationContext springContext;

    public AstronHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    public String getGroupId(TransactionCashRegisterInfo transactionInfo) {
        return "astron";
    }

    private Set<String> connectionSemaphore = new HashSet<>();

    @Override
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionList) {

        Map<Long, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

        if (transactionList != null) {

            AstronSettings astronSettings = springContext.containsBean("astronSettings") ? (AstronSettings) springContext.getBean("astronSettings") : new AstronSettings();
            Integer timeout = astronSettings.getTimeout() == null ? 300 : astronSettings.getTimeout();
            Map<Integer, Integer> groupMachineryMap = astronSettings.getGroupMachineryMap();
            boolean exportExtraTables = astronSettings.isExportExtraTables();
            Integer transactionsAtATime = astronSettings.getTransactionsAtATime();
            Integer itemsAtATime = astronSettings.getItemsAtATime();
            Integer maxBatchSize = astronSettings.getMaxBatchSize();
            boolean useNewScheme = astronSettings.isNewScheme();

            Map<String, List<TransactionCashRegisterInfo>> directoryTransactionMap = new HashMap<>();
            for (TransactionCashRegisterInfo transaction : transactionList) {
                directoryTransactionMap.computeIfAbsent(getDirectory(transaction), t -> new ArrayList<>()).add(transaction);
            }

            if(transactionsAtATime > 1 || itemsAtATime > 0) {

                for(Map.Entry<String, List<TransactionCashRegisterInfo>> directoryTransactionEntry : directoryTransactionMap.entrySet()) {
                    int transactionCount = 1;
                    int itemCount = 0;
                    int totalCount = directoryTransactionEntry.getValue().size();
                    Throwable exception = null;
                    Map<Long, SendTransactionBatch> currentSendTransactionBatchMap = new HashMap<>();
                    for (TransactionCashRegisterInfo transaction : directoryTransactionEntry.getValue()) {
                        itemCount += transaction.itemsList.size();
                        boolean firstTransaction = transactionCount == 1;
                        boolean lastTransaction = transactionCount == transactionsAtATime || transactionCount == totalCount || itemCount >= itemsAtATime;

                        Set<String> deleteBarcodeSet = new HashSet<>();
                        if(exception == null) {
                            exception = exportTransaction(transaction, firstTransaction, lastTransaction, directoryTransactionEntry.getKey(),
                                    exportExtraTables, groupMachineryMap, deleteBarcodeSet, timeout, maxBatchSize, useNewScheme, transactionCount, itemCount);
                        }
                        currentSendTransactionBatchMap.put(transaction.id, new SendTransactionBatch(null, null, transaction.nppGroupMachinery, deleteBarcodeSet, exception));

                        if (lastTransaction) {
                            for(SendTransactionBatch batchEntry : currentSendTransactionBatchMap.values()) {
                                if(batchEntry.exception == null) {
                                    batchEntry.exception = exception;
                                }
                            }
                            sendTransactionBatchMap.putAll(currentSendTransactionBatchMap);
                            currentSendTransactionBatchMap = new HashMap<>();
                            transactionCount = 1;
                            itemCount = 0;
                            totalCount -= transactionsAtATime;
                        } else {
                            transactionCount++;
                        }
                    }
                }

            } else {
                for(Map.Entry<String, List<TransactionCashRegisterInfo>> directoryTransactionEntry : directoryTransactionMap.entrySet()) {
                    Exception exception = null;
                    for (TransactionCashRegisterInfo transaction : directoryTransactionEntry.getValue()) {
                        Set<String> deleteBarcodeSet = new HashSet<>();
                        if (exception == null) {
                            exception = exportTransaction(transaction, true, true, directoryTransactionEntry.getKey(),
                                    exportExtraTables, groupMachineryMap, deleteBarcodeSet, timeout, maxBatchSize, useNewScheme, 1, transaction.itemsList.size());
                        }
                        sendTransactionBatchMap.put(transaction.id, new SendTransactionBatch(null, null, transaction.nppGroupMachinery, deleteBarcodeSet, exception));
                    }
                }
            }
        }
        return sendTransactionBatchMap;
    }

    private String getDirectory(TransactionCashRegisterInfo transaction) {
        String directory = null;
        for (CashRegisterInfo cashRegister : transaction.machineryInfoList) {
            if (cashRegister.directory != null) {
                directory = cashRegister.directory;
            }
        }
        return directory;
    }

    private Exception exportTransaction(TransactionCashRegisterInfo transaction, boolean firstTransaction, boolean lastTransaction, String directory,
                                        boolean exportExtraTables, Map<Integer, Integer> groupMachineryMap, Set<String> deleteBarcodeSet, Integer timeout,
                                        Integer maxBatchSize, boolean useNewScheme, int transactionCount, int itemCount) {
        Exception exception;
        AstronConnectionString params = new AstronConnectionString(directory);
        if (params.connectionString == null) {
            String error = "no connectionString found";
            astronLogger.error(error);
            exception = new RuntimeException(error);
        } else {
            exception = waitConnectionSemaphore(params, timeout, false);
            if(exception == null) {
                try (Connection conn = getConnection(params)) {
                    connectionSemaphore.add(params.connectionString);

                    String deleteBarcodeKey = getDeleteBarcodeKey(params.connectionString, transaction.nppGroupMachinery);
                    Map<String, CashRegisterItemInfo> deleteBarcodeMap = deleteBarcodeConnectionStringMap.get(deleteBarcodeKey);

                    Integer extGrpId = groupMachineryMap.get(transaction.nppGroupMachinery);
                    String tables = "'GRP', 'ART', 'UNIT', 'PACK', 'EXBARC', 'PACKPRC'" + (extGrpId != null ? ", 'ARTEXTGRP'" : "") + (exportExtraTables ? ", 'PRCLEVEL', 'SAREA', 'SAREAPRC'" : "");

                    if (firstTransaction) {
                        Exception waitFlagsResult = waitFlags(conn, params, tables, null, null, timeout, true);
                        if (waitFlagsResult != null) {
                            throw new RuntimeException("data from previous transactions was not processed (flags not set to zero)");
                        }
                        truncateTables(conn, extGrpId);
                    }

                    List<CashRegisterItemInfo> usedDeleteBarcodeList = new ArrayList<>();
                    transaction.itemsList = transaction.itemsList.stream().filter(item -> isValidItem(transaction, deleteBarcodeMap, usedDeleteBarcodeList, item)).collect(Collectors.toList());

                    if (!transaction.itemsList.isEmpty()) {

                        checkItems(params, transaction);

                        boolean newScheme = !params.pgsql && useNewScheme; //используем новую схему только по опции и только для mssql

                        astronLogger.info(String.format("transaction %s, table grp", transaction.id));
                        exportGrp(conn, params, transaction, maxBatchSize, newScheme);

                        astronLogger.info(String.format("transaction %s, table art", transaction.id));
                        exportArt(conn, params, transaction.itemsList, false, false, maxBatchSize, newScheme);

                        astronLogger.info(String.format("transaction %s, table unit", transaction.id));
                        exportUnit(conn, params, transaction.itemsList, false, maxBatchSize, newScheme);

                        astronLogger.info(String.format("transaction %s, table pack", transaction.id));
                        exportPack(conn, params, transaction.itemsList, false, maxBatchSize, newScheme);

                        astronLogger.info(String.format("transaction %s, table pack delete : " + usedDeleteBarcodeList.size(), transaction.id));
                        exportPackDeleteBarcode(conn, params, usedDeleteBarcodeList, maxBatchSize, newScheme);

                        astronLogger.info(String.format("transaction %s, table exbarc", transaction.id));
                        exportExBarc(conn, params, transaction.itemsList, false, maxBatchSize, newScheme);

                        astronLogger.info(String.format("transaction %s, table exbarc delete", transaction.id));
                        exportExBarcDeleteBarcode(conn, params, usedDeleteBarcodeList, maxBatchSize, newScheme);

                        astronLogger.info(String.format("transaction %s, table packprc", transaction.id));
                        boolean hasSecondPrice = exportPackPrc(conn, params, transaction, exportExtraTables, maxBatchSize, newScheme);

                        astronLogger.info(String.format("transaction %s, table packprc delete", transaction.id));
                        exportPackPrcDeleteBarcode(conn, params, transaction, usedDeleteBarcodeList, exportExtraTables, maxBatchSize, newScheme);

                        if (exportExtraTables) {
                            astronLogger.info(String.format("transaction %s, table prclevel", transaction.id));
                            exportPrcLevel(conn, params, transaction, hasSecondPrice, newScheme);
                            astronLogger.info(String.format("transaction %s, table sarea", transaction.id));
                            exportSArea(conn, params, transaction, newScheme);
                            astronLogger.info(String.format("transaction %s, table sareaprc", transaction.id));
                            exportSAreaPrc(conn, params, transaction, hasSecondPrice, newScheme);
                        }

                        if (extGrpId != null) {
                            astronLogger.info(String.format("transaction %s, table ARTEXTGRP", transaction.id));
                            exportArtExtgrp(conn, params, transaction, extGrpId, maxBatchSize, newScheme);
                        }

                        if (lastTransaction) {
                            astronLogger.info(String.format("waiting for processing %s transaction(s) with %s item(s)", transactionCount, itemCount));
                            exportFlags(conn, params, tables);
                            Exception e = waitFlags(conn, params, tables, usedDeleteBarcodeList, deleteBarcodeKey, timeout, false);
                            if (e == null) {
                                for (CashRegisterItemInfo usedDeleteBarcode : usedDeleteBarcodeList) {
                                    deleteBarcodeSet.add(usedDeleteBarcode.idBarcode);
                                }
                            } else {
                                throw e;
                            }

                        }
                    }

                } catch (Exception e) {
                    astronLogger.error("exportTransaction error", e);
                    exception = e;
                } finally {
                    connectionSemaphore.remove(params.connectionString);
                }
            }
        }
        return exception;
    }

    private void checkItems(AstronConnectionString params, TransactionCashRegisterInfo transaction) {
        StringBuilder invalidItems = new StringBuilder();
        if (params.pgsql) {
            for (int i = 0; i < transaction.itemsList.size(); i++) {

                CashRegisterItemInfo item = transaction.itemsList.get(i);
                String grpId = parseGroup(item.extIdItemGroup);
                if (grpId == null || grpId.isEmpty()) {
                    invalidItems.append(invalidItems.length() == 0 ? "" : ", ").append(item.idItem);
                }
            }
        }
        if (invalidItems.length() > 0)
            throw new RuntimeException("No GRPID for item " + invalidItems.toString());
    }

    private void exportGrp(Connection conn, AstronConnectionString params, TransactionCashRegisterInfo transaction, Integer maxBatchSize, boolean newScheme) throws SQLException {
        String[] keys = new String[]{"GRPID"};
        String[] columns = new String[]{"GRPID", "PARENTGRPID", "GRPNAME", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, params, "GRP", columns, keys, newScheme)) {
            int offset = columns.length + keys.length;

            int batchCount = 0;
            Set<String> usedGrp = new HashSet<>();
            for (int i = 0; i < transaction.itemsList.size(); i++) {
                List<ItemGroup> itemGroupList = transaction.itemGroupMap.get(transaction.itemsList.get(i).extIdItemGroup);
                if (itemGroupList != null) {
                    for (ItemGroup itemGroup : itemGroupList) {
                        if (!Thread.currentThread().isInterrupted()) {
                            String idGroup = parseGroup(itemGroup.extIdItemGroup);
                            if (idGroup != null && !idGroup.isEmpty() && !usedGrp.contains(idGroup)) {
                                if(params.pgsql) {
                                    String parentId = parseGroup(itemGroup.idParentItemGroup, true);
                                    setObject(ps, Integer.parseInt(idGroup), 1); //GRPID
                                    setObject(ps, parentId != null ? Integer.parseInt(parentId) : parentId, 2); //PARENTGRPID
                                    setObject(ps, trim(itemGroup.nameItemGroup, "", 50), 3); //GRPNAME
                                    setObject(ps, 0, 4); //DELFLAG
                                } else {
                                    if(newScheme) {
                                        setObject(ps, parseGroup(itemGroup.idParentItemGroup), 1, offset); //PARENTGRPID
                                        setObject(ps, trim(itemGroup.nameItemGroup, "", 50), 2, offset); //GRPNAME
                                        setObject(ps, "0", 3, offset); //DELFLAG

                                        setObject(ps, parseGroup(itemGroup.extIdItemGroup), 4, keys.length); //GRPID
                                    } else {
                                        setObject(ps, idGroup, 1, offset); //GRPID
                                        setObject(ps, parseGroup(itemGroup.idParentItemGroup), 2, offset); //PARENTGRPID
                                        setObject(ps, trim(itemGroup.nameItemGroup, "", 50), 3, offset); //GRPNAME
                                        setObject(ps, "0", 4, offset); //DELFLAG

                                        setObject(ps, parseGroup(itemGroup.extIdItemGroup), 5); //GRPID
                                    }
                                }
                                ps.addBatch();
                                batchCount++;
                                if(maxBatchSize != null && batchCount == maxBatchSize) {
                                    ps.executeBatch();
                                    conn.commit();
                                    batchCount = 0;
                                    astronLogger.info("execute and commit batch");
                                }
                                usedGrp.add(idGroup);
                            }
                        } else break;
                    }
                }
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    private void exportArt(Connection conn, AstronConnectionString params, List<? extends ItemInfo> itemsList, boolean zeroGrpId, boolean delFlag, Integer maxBatchSize, boolean newScheme) throws SQLException {
        String[] keys = new String[]{"ARTID"};
        String[] columns = new String[]{"ARTID", "GRPID", "TAXGRPID", "ARTCODE", "ARTNAME", "ARTSNAME", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, params, "ART", columns, keys, newScheme)) {
            int offset = columns.length + keys.length;

            int batchCount = 0;
            for (ItemInfo item : itemsList) {
                if (!Thread.currentThread().isInterrupted()) {
                    Integer idItem = parseIdItem(item);
                    String grpId = getExtIdItemGroup(item, zeroGrpId);
                    if (params.pgsql) {
                        if (grpId != null && !grpId.isEmpty()) {
                            setObject(ps, idItem, 1); //ARTID
                            setObject(ps, Integer.parseInt(grpId), 2); //GRPID
                            setObject(ps, getIdVAT(item.vat), 3); //TAXGRPID
                            setObject(ps, idItem, 4); //ARTCODE
                            setObject(ps, trim(item.name, "", 50), 5); //ARTNAME
                            setObject(ps, trim(item.name, "", 50), 6); //ARTSNAME
                            setObject(ps, delFlag ? 1 : 0, 7); //DELFLAG
                        }
                    } else {
                        if(newScheme) {
                            setObject(ps, grpId, 1, offset); //GRPID
                            setObject(ps, getIdVAT(item.vat), 2, offset); //TAXGRPID
                            setObject(ps, idItem, 3, offset); //ARTCODE
                            setObject(ps, trim(item.name, "", 50), 4, offset); //ARTNAME
                            setObject(ps, trim(item.name, "", 50), 5, offset); //ARTSNAME
                            setObject(ps, delFlag ? "1" : "0", 6, offset); //DELFLAG

                            setObject(ps, idItem, 7, keys.length); //ARTID
                        } else {
                            setObject(ps, idItem, 1, offset); //ARTID
                            setObject(ps, grpId, 2, offset); //GRPID
                            setObject(ps, getIdVAT(item.vat), 3, offset); //TAXGRPID
                            setObject(ps, idItem, 4, offset); //ARTCODE
                            setObject(ps, trim(item.name, "", 50), 5, offset); //ARTNAME
                            setObject(ps, trim(item.name, "", 50), 6, offset); //ARTSNAME
                            setObject(ps, delFlag ? "1" : "0", 7, offset); //DELFLAG

                            setObject(ps, idItem, 8); //ARTID
                        }
                    }

                    ps.addBatch();
                    batchCount++;
                    if(maxBatchSize != null && batchCount == maxBatchSize) {
                        ps.executeBatch();
                        conn.commit();
                        batchCount = 0;
                        astronLogger.info("execute and commit batch");
                    }
                } else break;
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    private Integer getIdVAT(BigDecimal vat) {
        Integer result = 0;
        if (vat != null) {
            if (vat.compareTo(BigDecimal.valueOf(10)) == 0) {
                result = 1;
            } else if (vat.compareTo(BigDecimal.valueOf(20)) == 0) {
                result = 2;
            }
        }
        return result;
    }

    private void exportArtExtgrp(Connection conn, AstronConnectionString params, TransactionCashRegisterInfo transaction, Integer extGrpId, Integer maxBatchSize, boolean newScheme) throws SQLException {
        String[] keys = new String[]{"ARTID"};
        String[] columns = new String[]{"ARTID", "EXTGRPID", "DELFLAG "};
        try (PreparedStatement ps = getPreparedStatement(conn, params, "ARTEXTGRP", columns, keys, newScheme)) {
            int offset = columns.length + keys.length;

            int batchCount = 0;
            for (int i = 0; i < transaction.itemsList.size(); i++) {
                if (!Thread.currentThread().isInterrupted()) {
                    CashRegisterItemInfo item = transaction.itemsList.get(i);
                    Integer idItem = parseIdItem(item);
                    if(params.pgsql) {
                        setObject(ps, idItem, 1); //ARTID
                        setObject(ps, extGrpId, 2); //EXTGRPID
                        setObject(ps, 0, 3); //DELFLAG
                    } else {
                        if(newScheme) {
                            setObject(ps, extGrpId, 1, offset); //EXTGRPID
                            setObject(ps, 0, 2, offset); //DELFLAG

                            setObject(ps, idItem, 3, keys.length); //ARTID
                        } else {
                            setObject(ps, idItem, 1, offset); //ARTID
                            setObject(ps, extGrpId, 2, offset); //EXTGRPID
                            setObject(ps, 0, 3, offset); //DELFLAG

                            setObject(ps, idItem, 4); //ARTID
                        }
                    }

                    ps.addBatch();
                    batchCount++;
                    if(maxBatchSize != null && batchCount == maxBatchSize) {
                        ps.executeBatch();
                        conn.commit();
                        batchCount = 0;
                        astronLogger.info("execute and commit batch");
                    }
                } else break;
            }
            ps.executeBatch();
            conn.commit();
        }
    }


    private void exportUnit(Connection conn, AstronConnectionString params, List<? extends ItemInfo> itemsList, boolean delFlag, Integer maxBatchSize, boolean newScheme) throws SQLException {
        String[] keys = new String[]{"UNITID"};
        String[] columns = new String[]{"UNITID", "UNITNAME", "UNITFULLNAME", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, params, "UNIT", columns, keys, newScheme)) {
            int offset = columns.length + keys.length;

            Set<Integer> usedUOM = new HashSet<>();
            int batchCount = 0;
            for (ItemInfo item : itemsList) {
                if (!Thread.currentThread().isInterrupted()) {
                    Integer idUOM = parseUOM(item.idUOM);
                    if (!usedUOM.contains(idUOM)) {
                        usedUOM.add(idUOM);
                        if (params.pgsql) {
                            setObject(ps, idUOM, 1); //UNITID
                            setObject(ps, item.shortNameUOM, 2); //UNITNAME
                            setObject(ps, item.shortNameUOM, 3); //UNITFULLNAME
                            setObject(ps, delFlag ? 1 : 0, 4); //DELFLAG
                        } else {
                            if(newScheme) {
                                setObject(ps, item.shortNameUOM, 1, offset); //UNITNAME
                                setObject(ps, item.shortNameUOM, 2, offset); //UNITFULLNAME
                                setObject(ps, delFlag ? "1" : "0", 3, offset); //DELFLAG

                                setObject(ps, idUOM, 4, keys.length); //UNITID
                            } else {
                                setObject(ps, idUOM, 1, offset); //UNITID
                                setObject(ps, item.shortNameUOM, 2, offset); //UNITNAME
                                setObject(ps, item.shortNameUOM, 3, offset); //UNITFULLNAME
                                setObject(ps, delFlag ? "1" : "0", 4, offset); //DELFLAG

                                setObject(ps, idUOM, 5); //UNITID
                            }
                        }

                        ps.addBatch();
                        batchCount++;
                        if(maxBatchSize != null && batchCount == maxBatchSize) {
                            ps.executeBatch();
                            conn.commit();
                            batchCount = 0;
                            astronLogger.info("execute and commit batch");
                        }
                    }

                } else break;
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    private Integer parseUOM(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            astronLogger.error("Unable to parse UOM " + value, e);
            return null;
        }
    }

    private void exportPack(Connection conn, AstronConnectionString params, List<? extends ItemInfo> itemsList, boolean delFlag, Integer maxBatchSize, boolean newScheme) throws SQLException {
        String[] keys = new String[]{"PACKID"};
        String[] columns = new String[]{"PACKID", "ARTID", "PACKQUANT", "PACKSHELFLIFE", "ISDEFAULT", "UNITID", "QUANTMASK", "PACKDTYPE", "PACKNAME", "DELFLAG", "BARCID"};
        try (PreparedStatement ps = getPreparedStatement(conn, params, "PACK", columns, keys, newScheme)) {
            int offset = columns.length + keys.length;

            Set<Integer> idItems = new HashSet<>();
            int batchCount = 0;
            for (ItemInfo item : itemsList) {
                if (!Thread.currentThread().isInterrupted()) {
                    Integer idUOM = parseUOM(item.idUOM);
                    Integer idItem = parseIdItem(item);
                    List<Integer> packIds = getPackIds(item);

                    for (Integer packId : packIds) {
                        if (params.pgsql) {
                            setObject(ps, packId, 1); //PACKID
                            setObject(ps, idItem, 2); //ARTID
                            setObject(ps, item.passScalesItem || item.splitItem ? 1000 : 1, 3); //PACKQUANT
                            setObject(ps, 0, 4); //PACKSHELFLIFE
                            if (idItems.contains(idItem)) {
                                setObject(ps, 0, 5); //ISDEFAULT
                            } else {
                                setObject(ps, 1, 5); //ISDEFAULT
                                idItems.add(idItem);
                            }
                            setObject(ps, idUOM, 6); //UNITID
                            setObject(ps, item.splitItem ? 2 : 0, 7); //QUANTMASK
                            setObject(ps, item.passScalesItem ? 0 : item.splitItem ? 2 : 1, 8); //PACKDTYPE
                            setObject(ps, trim(item.name, "", 50), 9); //PACKNAME
                            setObject(ps, delFlag ? 1 : 0, 10); //DELFLAG
                            setObject(ps, item.passScalesItem ? 2 : null, 11); //BARCID
                        } else {
                            if(newScheme) {
                                setObject(ps, idItem, 1, offset); //ARTID
                                setObject(ps, item.passScalesItem || item.splitItem ? "1000" : "1", 2, offset); //PACKQUANT
                                setObject(ps, "0", 3, offset); //PACKSHELFLIFE
                                if (idItems.contains(idItem)) {
                                    setObject(ps, false, 4, offset); //ISDEFAULT
                                } else {
                                    setObject(ps, true, 4, offset); //ISDEFAULT
                                    idItems.add(idItem);
                                }
                                setObject(ps, idUOM, 5, offset); //UNITID
                                setObject(ps, item.splitItem ? 2 : "", 6, offset); //QUANTMASK
                                setObject(ps, item.passScalesItem ? 0 : item.splitItem ? 2 : 1, 7, offset); //PACKDTYPE
                                setObject(ps, trim(item.name, "", 50), 8, offset); //PACKNAME
                                setObject(ps, delFlag ? "1" : "0", 9, offset); //DELFLAG
                                setObject(ps, item.passScalesItem ? "2" : null, 10, offset); //BARCID

                                setObject(ps, packId, 11, keys.length); //PACKID
                            } else {
                                setObject(ps, packId, 1, offset); //PACKID
                                setObject(ps, idItem, 2, offset); //ARTID
                                setObject(ps, item.passScalesItem || item.splitItem ? "1000" : "1", 3, offset); //PACKQUANT
                                setObject(ps, "0", 4, offset); //PACKSHELFLIFE
                                if (idItems.contains(idItem)) {
                                    setObject(ps, false, 5, offset); //ISDEFAULT
                                } else {
                                    setObject(ps, true, 5, offset); //ISDEFAULT
                                    idItems.add(idItem);
                                }
                                setObject(ps, idUOM, 6, offset); //UNITID
                                setObject(ps, item.splitItem ? 2 : "", 7, offset); //QUANTMASK
                                setObject(ps, item.passScalesItem ? 0 : item.splitItem ? 2 : 1, 8, offset); //PACKDTYPE
                                setObject(ps, trim(item.name, "", 50), 9, offset); //PACKNAME
                                setObject(ps, delFlag ? "1" : "0", 10, offset); //DELFLAG
                                setObject(ps, item.passScalesItem ? "2" : null, 11, offset); //BARCID

                                setObject(ps, packId, 12); //PACKID
                            }
                        }

                        ps.addBatch();
                        batchCount++;
                        if(maxBatchSize != null && batchCount == maxBatchSize) {
                            ps.executeBatch();
                            conn.commit();
                            batchCount = 0;
                            astronLogger.info("execute and commit batch");
                        }
                    }
                } else break;
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    private void exportPackDeleteBarcode(Connection conn, AstronConnectionString params, List<CashRegisterItemInfo> usedDeleteBarcodeList, Integer maxBatchSize, boolean newScheme) throws SQLException {
        String[] keys = new String[]{"PACKID"};
        String[] columns = new String[]{"PACKID", "ARTID", "PACKQUANT", "PACKSHELFLIFE", "ISDEFAULT", "UNITID", "QUANTMASK", "PACKDTYPE", "PACKNAME", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, params, "PACK", columns, keys, newScheme)) {
            int offset = columns.length + keys.length;

            Set<Integer> idItems = new HashSet<>();
            int batchCount = 0;
            for (CashRegisterItemInfo item : usedDeleteBarcodeList) {
                if (!Thread.currentThread().isInterrupted()) {
                    Integer idItem = parseIdItem(item);
                    Integer packId = getPackId(item);
                    if(params.pgsql) {
                        setObject(ps, packId, 1); //PACKID
                        setObject(ps, idItem, 2); //ARTID
                        setObject(ps, item.passScalesItem || item.splitItem ? 1000 : 1, 3); //PACKQUANT
                        setObject(ps, 0, 4); //PACKSHELFLIFE
                        if (idItems.contains(idItem)) {
                            setObject(ps, 0, 5); //ISDEFAULT
                        } else {
                            setObject(ps, 1, 5); //ISDEFAULT
                            idItems.add(idItem);
                        }
                        setObject(ps, 0, 6); //UNITID
                        setObject(ps, 0, 7); //QUANTMASK
                        setObject(ps, item.passScalesItem ? 0 : 1, 8); //PACKDTYPE
                        setObject(ps, trim(item.name, "", 50), 9); //PACKNAME
                        setObject(ps, 1, 10); //DELFLAG
                    } else {
                        if(newScheme) {
                            setObject(ps, idItem, 1, offset); //ARTID
                            setObject(ps, item.passScalesItem || item.splitItem ? "1000" : "1", 2, offset); //PACKQUANT
                            setObject(ps, "0", 3, offset); //PACKSHELFLIFE
                            if (idItems.contains(idItem)) {
                                setObject(ps, false, 4, offset); //ISDEFAULT
                            } else {
                                setObject(ps, true, 4, offset); //ISDEFAULT
                                idItems.add(idItem);
                            }
                            setObject(ps, "0", 5, offset); //UNITID
                            setObject(ps, "", 6, offset); //QUANTMASK
                            setObject(ps, item.passScalesItem ? 0 : 1, 7, offset); //PACKDTYPE
                            setObject(ps, trim(item.name, "", 50), 8, offset); //PACKNAME
                            setObject(ps, "1", 9, offset); //DELFLAG

                            setObject(ps, packId, 10, keys.length); //PACKID
                        } else {
                            setObject(ps, packId, 1, offset); //PACKID
                            setObject(ps, idItem, 2, offset); //ARTID
                            setObject(ps, item.passScalesItem || item.splitItem ? "1000" : "1", 3, offset); //PACKQUANT
                            setObject(ps, "0", 4, offset); //PACKSHELFLIFE
                            if (idItems.contains(idItem)) {
                                setObject(ps, false, 5, offset); //ISDEFAULT
                            } else {
                                setObject(ps, true, 5, offset); //ISDEFAULT
                                idItems.add(idItem);
                            }
                            setObject(ps, "0", 6, offset); //UNITID
                            setObject(ps, "", 7, offset); //QUANTMASK
                            setObject(ps, item.passScalesItem ? 0 : 1, 8, offset); //PACKDTYPE
                            setObject(ps, trim(item.name, "", 50), 9, offset); //PACKNAME
                            setObject(ps, "1", 10, offset); //DELFLAG

                            setObject(ps, packId, 11); //PACKID
                        }
                    }

                    ps.addBatch();
                    batchCount++;
                    if(maxBatchSize != null && batchCount == maxBatchSize) {
                        ps.executeBatch();
                        conn.commit();
                        batchCount = 0;
                        astronLogger.info("execute and commit batch");
                    }
                } else break;
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    private List<Integer> getPackIds(ItemInfo item) {
        List<Integer> packIds = new ArrayList<>();
        if (item instanceof CashRegisterItemInfo) {
            packIds.add(getPackId((CashRegisterItemInfo) item));
        } else {
            for (Long barcodeObject : ((StopListItemInfo) item).barcodeObjectList) {
                packIds.add(getPackId(barcodeObject));
            }
        }
        return packIds;
    }

    private Integer getPackId(CashRegisterItemInfo item) {
        //Потенциальная опасность, если база перейдёт границу Integer.MAX_VALUE
        return item.barcodeObject.intValue();
    }

    private Integer getPackId(Long barcodeObject) {
        //Потенциальная опасность, если база перейдёт границу Integer.MAX_VALUE
        return barcodeObject.intValue();
    }

    private Integer parseIdItem(ItemInfo item) {
        try {
            return Integer.parseInt(item.idItem);
        } catch (Exception e) {
            return null;
        }
    }

    private void exportExBarc(Connection conn, AstronConnectionString params, List<? extends ItemInfo> itemsList, boolean delFlag, Integer maxBatchSize, boolean newScheme) throws SQLException {
        String[] keys = new String[]{"EXBARCID"};
        String[] columns = new String[]{"EXBARCID", "PACKID", "EXBARCTYPE", "EXBARCBODY", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, params, "EXBARC", columns, keys, newScheme)) {
            int offset = columns.length + keys.length;

            int batchCount = 0;
            for (ItemInfo item : itemsList) {
                if (!Thread.currentThread().isInterrupted()) {
                    if (item.idBarcode != null) {

                        List<Integer> packIds = getPackIds(item);

                        for (Integer packId : packIds) {
                            if (params.pgsql) {
                                setObject(ps, packId, 1); //EXBARCID
                                setObject(ps, packId, 2); //PACKID
                                setObject(ps, "", 3); //EXBARCTYPE
                                setObject(ps, getExBarcBody(item), 4); //EXBARCBODY
                                setObject(ps, delFlag ? 1 : 0, 5); //DELFLAG
                            } else {
                                if(newScheme) {
                                    setObject(ps, packId, 1, offset); //PACKID
                                    setObject(ps, "", 2, offset); //EXBARCTYPE
                                    setObject(ps, getExBarcBody(item), 3, offset); //EXBARCBODY
                                    setObject(ps, delFlag ? "1" : "0", 4, offset); //DELFLAG

                                    setObject(ps, packId, 5, keys.length); //EXBARCID
                                } else {
                                    setObject(ps, packId, 1, offset); //EXBARCID
                                    setObject(ps, packId, 2, offset); //PACKID
                                    setObject(ps, "", 3, offset); //EXBARCTYPE
                                    setObject(ps, getExBarcBody(item), 4, offset); //EXBARCBODY
                                    setObject(ps, delFlag ? "1" : "0", 5, offset); //DELFLAG

                                    setObject(ps, packId, 6); //EXBARCID
                                }
                            }

                            ps.addBatch();
                            batchCount++;
                            if(maxBatchSize != null && batchCount == maxBatchSize) {
                                ps.executeBatch();
                                conn.commit();
                                batchCount = 0;
                                astronLogger.info("execute and commit batch");
                            }
                        }
                    }
                } else break;
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    private void exportExBarcDeleteBarcode(Connection conn, AstronConnectionString params, List<CashRegisterItemInfo> usedDeleteBarcodeList, Integer maxBatchSize, boolean newScheme) throws SQLException {
        String[] keys = new String[]{"EXBARCID"};
        String[] columns = new String[]{"EXBARCID", "PACKID", "EXBARCTYPE", "EXBARCBODY", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, params, "EXBARC", columns, keys, newScheme)) {
            int offset = columns.length + keys.length;

            int batchCount = 0;
            for (CashRegisterItemInfo item : usedDeleteBarcodeList) {
                if (!Thread.currentThread().isInterrupted()) {
                    if (item.idBarcode != null) {
                        Integer packId = getPackId(item);
                        if(params.pgsql) {
                            setObject(ps, packId, 1); //EXBARCID
                            setObject(ps, packId, 2); //PACKID
                            setObject(ps, "", 3); //EXBARCTYPE
                            setObject(ps, getExBarcBody(item), 4); //EXBARCBODY
                            setObject(ps, 1, 5); //DELFLAG
                        } else {
                            if(newScheme) {
                                setObject(ps, packId, 1, offset); //PACKID
                                setObject(ps, "", 2, offset); //EXBARCTYPE
                                setObject(ps, getExBarcBody(item), 3, offset); //EXBARCBODY
                                setObject(ps, "1", 4, offset); //DELFLAG

                                setObject(ps, packId, 5, keys.length); //EXBARCID
                            } else {
                                setObject(ps, packId, 1, offset); //EXBARCID
                                setObject(ps, packId, 2, offset); //PACKID
                                setObject(ps, "", 3, offset); //EXBARCTYPE
                                setObject(ps, getExBarcBody(item), 4, offset); //EXBARCBODY
                                setObject(ps, "1", 5, offset); //DELFLAG

                                setObject(ps, packId, 6); //EXBARCID
                            }
                        }

                        ps.addBatch();
                        batchCount++;
                        if(maxBatchSize != null && batchCount == maxBatchSize) {
                            ps.executeBatch();
                            conn.commit();
                            batchCount = 0;
                            astronLogger.info("execute and commit batch");
                        }
                    }
                } else break;
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    private String getExBarcBody(ItemInfo item) {
        return item.idBarcode.replaceAll("[^0-9]", ""); //Должны быть только цифры, а откуда-то вдруг прилетает символ 0xe2 0x80 0x8e
    }

    private boolean exportPackPrc(Connection conn, AstronConnectionString params, TransactionCashRegisterInfo transaction, boolean exportExtraTables, Integer maxBatchSize, boolean newScheme) throws SQLException {
        boolean hasSecondPrice = false;
        String[] keys = new String[]{"PACKID", "PRCLEVELID"};
        String[] columns = new String[]{"PACKID", "PRCLEVELID", "PACKPRICE", "PACKMINPRICE", "PACKBONUSMINPRICE", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, params, "PACKPRC", columns, keys, newScheme)) {
            int offset = columns.length + keys.length;

            int batchCount = 0;
            for (int i = 0; i < transaction.itemsList.size(); i++) {
                if (!Thread.currentThread().isInterrupted()) {
                    CashRegisterItemInfo item = transaction.itemsList.get(i);
                    if (item.price != null) {
                        Integer packId = getPackId(item);
                        addPackPrcRow(ps, params, transaction.nppGroupMachinery, item, packId, offset, exportExtraTables, item.price, false, false, keys.length, newScheme);
                        batchCount++;
                        if(maxBatchSize != null && batchCount == maxBatchSize) {
                            ps.executeBatch();
                            conn.commit();
                            batchCount = 0;
                            astronLogger.info("execute and commit batch");
                        }
                        //{"astron": {"secondPrice":"1.23"}}
                        BigDecimal secondPrice = getJSONBigDecimal(item.info, "astron", "secondPrice");
                        if (exportExtraTables && secondPrice != null) {
                            addPackPrcRow(ps, params, transaction.nppGroupMachinery, item, packId, offset, true, secondPrice, true, false, keys.length, newScheme);
                            hasSecondPrice = true;
                        }
                        batchCount++;
                        if(maxBatchSize != null && batchCount == maxBatchSize) {
                            ps.executeBatch();
                            conn.commit();
                            batchCount = 0;
                            astronLogger.info("execute and commit batch");
                        }

                    } else {
                        astronLogger.error(String.format("transaction %s, table packprc, item %s without price", transaction.id, item.idItem));
                    }
                } else break;
            }
            ps.executeBatch();
            conn.commit();
        }
        return hasSecondPrice;
    }

    private void addPackPrcRow(PreparedStatement ps, AstronConnectionString params, Integer nppGroupMachinery,
                               ItemInfo item, Integer packId, int offset, boolean exportExtraTables,
                               BigDecimal price, boolean secondPrice, boolean delFlag, int keysOffset, boolean newScheme) throws SQLException {

        Integer priceLevelId = getPriceLevelId(nppGroupMachinery, exportExtraTables, secondPrice);
        BigDecimal packPrice = price == null || price.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : HandlerUtils.safeMultiply(price, 100);
        BigDecimal minPrice = item instanceof CashRegisterItemInfo ? HandlerUtils.safeMultiply(((CashRegisterItemInfo) item).minPrice, 100) : null;
        BigDecimal packMinPrice = (item.flags == null || ((item.flags & 16) == 0)) && HandlerUtils.safeMultiply(price, 100) != null ? HandlerUtils.safeMultiply(price, 100) : minPrice != null ? minPrice : BigDecimal.ZERO;

        if(params.pgsql) {
            setObject(ps, packId, 1); //PACKID
            setObject(ps, priceLevelId, 2); //PRCLEVELID
            setObject(ps, packPrice, 3); //PACKPRICE
            setObject(ps, packMinPrice, 4); //PACKMINPRICE
            setObject(ps, 0, 5); //PACKBONUSMINPRICE
            setObject(ps, delFlag ? 1 : 0, 6); //DELFLAG
        } else {
            if(newScheme) {
                setObject(ps, packPrice, 1, offset); //PACKPRICE
                setObject(ps, packMinPrice, 2, offset); //PACKMINPRICE
                setObject(ps, 0, 3, offset); //PACKBONUSMINPRICE
                setObject(ps, delFlag ? "1" : "0", 4, offset); //DELFLAG

                setObject(ps, packId, 5, keysOffset); //PACKID
                setObject(ps, priceLevelId, 6, keysOffset); //PRCLEVELID
            } else {
                setObject(ps, packId, 1, offset); //PACKID
                setObject(ps, priceLevelId, 2, offset); //PRCLEVELID
                setObject(ps, packPrice, 3, offset); //PACKPRICE
                setObject(ps, packMinPrice, 4, offset); //PACKMINPRICE
                setObject(ps, 0, 5, offset); //PACKBONUSMINPRICE
                setObject(ps, delFlag ? "1" : "0", 6, offset); //DELFLAG

                setObject(ps, packId, 7); //PACKID
                setObject(ps, priceLevelId, 8); //PRCLEVELID
            }
        }

        ps.addBatch();
    }

    private void exportPackPrcDeleteBarcode(Connection conn, AstronConnectionString params, TransactionCashRegisterInfo transaction,
                                            List<CashRegisterItemInfo> usedDeleteBarcodeList, boolean exportExtraTables, Integer maxBatchSize, boolean newScheme) throws SQLException {
        String[] keys = new String[]{"PACKID", "PRCLEVELID"};
        String[] columns = new String[]{"PACKID", "PRCLEVELID", "PACKPRICE", "PACKMINPRICE", "PACKBONUSMINPRICE", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, params, "PACKPRC", columns, keys, newScheme)) {
            int offset = columns.length + keys.length;

            int batchCount = 0;
            for (CashRegisterItemInfo item : usedDeleteBarcodeList) {
                if (!Thread.currentThread().isInterrupted()) {
                    Integer packId = getPackId(item);
                    if(params.pgsql) {
                        setObject(ps, packId, 1); //PACKID
                        setObject(ps, getPriceLevelId(transaction.nppGroupMachinery, exportExtraTables), 2); //PRCLEVELID
                        setObject(ps, BigDecimal.ZERO, 3); //PACKPRICE
                        setObject(ps, BigDecimal.ZERO, 4); //PACKMINPRICE
                        setObject(ps, 0, 5); //PACKBONUSMINPRICE
                        setObject(ps, 0, 6); //DELFLAG
                    } else {
                        if(newScheme) {
                            setObject(ps, BigDecimal.ZERO, 1, offset); //PACKPRICE
                            setObject(ps, BigDecimal.ZERO, 2, offset); //PACKMINPRICE
                            setObject(ps, 0, 3, offset); //PACKBONUSMINPRICE
                            setObject(ps, "0", 4, offset); //DELFLAG

                            setObject(ps, packId, 5, keys.length); //PACKID
                            setObject(ps, getPriceLevelId(transaction.nppGroupMachinery, exportExtraTables), 6, keys.length); //PRCLEVELID
                        } else {
                            setObject(ps, packId, 1, offset); //PACKID
                            setObject(ps, getPriceLevelId(transaction.nppGroupMachinery, exportExtraTables), 2, offset); //PRCLEVELID
                            setObject(ps, BigDecimal.ZERO, 3, offset); //PACKPRICE
                            setObject(ps, BigDecimal.ZERO, 4, offset); //PACKMINPRICE
                            setObject(ps, 0, 5, offset); //PACKBONUSMINPRICE
                            setObject(ps, "0", 6, offset); //DELFLAG

                            setObject(ps, packId, 7); //PACKID
                            setObject(ps, getPriceLevelId(transaction.nppGroupMachinery, exportExtraTables), 8); //PRCLEVELID
                        }

                    }

                    ps.addBatch();
                    batchCount++;
                    if(maxBatchSize != null && batchCount == maxBatchSize) {
                        ps.executeBatch();
                        conn.commit();
                        batchCount = 0;
                        astronLogger.info("execute and commit batch");
                    }
                } else break;
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    private void exportPackPrcStopList(Connection conn, AstronConnectionString params, StopListInfo stopListInfo, boolean exportExtraTables, boolean delFlag, Integer maxBatchSize, boolean newScheme) throws SQLException {
        String[] keys = new String[]{"PACKID", "PRCLEVELID"};
        String[] columns = new String[]{"PACKID", "PRCLEVELID", "PACKPRICE", "PACKMINPRICE", "PACKBONUSMINPRICE", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, params, "PACKPRC", columns, keys, newScheme)) {
            int offset = columns.length + keys.length;

            //берём groupMachinery только своего хэндлера
            Set<Integer> groupMachinerySet = new HashSet<>();
            for(Map.Entry<String, Set<MachineryInfo>> entry : stopListInfo.handlerMachineryMap.entrySet()) {
                String handler = entry.getKey();
                if(handler.endsWith(getClass().getName())) {
                    for(MachineryInfo machinery : entry.getValue()) {
                        if(stopListInfo.inGroupMachineryItemMap.containsKey(machinery.numberGroup)) {
                            groupMachinerySet.add(machinery.numberGroup);
                        }
                    }
                }
            }

            int recordCount = 0;
            int packIdCount = 0;
            int itemCount = 0;
            for (ItemInfo item : stopListInfo.stopListItemMap.values()) {
                itemCount++;
                if (!Thread.currentThread().isInterrupted()) {
                    List<Integer> packIds = getPackIds(item);
                    for (Integer packId : packIds) {
                        packIdCount++;
                        for (Integer nppGroupMachinery : groupMachinerySet) {
                            recordCount++;
                            addPackPrcRow(ps, params, nppGroupMachinery, item, packId, offset, exportExtraTables, item.price, false, delFlag, keys.length, newScheme);
                            if(maxBatchSize != null && recordCount == maxBatchSize) {
                                //todo: temp log
                                astronLogger.info(String.format("exportPackPrcStopList records: %s; items: %s; machineries: %s, packIds: %s",
                                        recordCount, itemCount, groupMachinerySet.size(), packIdCount));
                                ps.executeBatch();
                                conn.commit();
                                recordCount = 0;
                                packIdCount = 0;
                                itemCount = 0;
                                astronLogger.info("execute and commit batch");
                            }
                        }
                    }
                } else break;
            }
            //todo: temp log
            astronLogger.info(String.format("exportPackPrcStopList records: %s; items: %s; machineries: %s, packIds: %s",
                    recordCount, itemCount, groupMachinerySet.size(), packIdCount));
            ps.executeBatch();
            conn.commit();
        }
    }

    private void exportPrcLevel(Connection conn, AstronConnectionString params, TransactionCashRegisterInfo transaction, boolean hasSecondPrice, boolean newScheme) throws SQLException {
        String[] keys = new String[]{"PRCLEVELID"};
        String[] columns = new String[]{"PRCLEVELID", "PRCLEVELNAME", "PRCLEVELKEY", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, params, "PRCLEVEL", columns, keys, newScheme)) {
            int offset = columns.length + keys.length;

            addPrcLevelRow(ps, params, transaction, offset, false, keys.length, newScheme);
            if(hasSecondPrice) {
                addPrcLevelRow(ps, params, transaction, offset, true, keys.length, newScheme);
            }

            ps.executeBatch();
            conn.commit();
        }
    }

    private void addPrcLevelRow(PreparedStatement ps, AstronConnectionString params, TransactionCashRegisterInfo transaction, int offset, boolean secondPrice, int keysOffset, boolean newScheme) throws SQLException {
        Integer priceLevelId = getPriceLevelId(transaction.nppGroupMachinery, true, secondPrice);
        String priceLevelName = trim(transaction.nameGroupMachinery, secondPrice ? 47 : 50) + (secondPrice ? " №2" : "");
        if(params.pgsql) {
            setObject(ps, priceLevelId, 1); //PRCLEVELID
            setObject(ps, priceLevelName, 2); //PRCLEVELNAME
            setObject(ps, 0, 3); //PRCLEVELKEY
            setObject(ps, 0, 4); //DELFLAG
        } else {
            if(newScheme) {
                setObject(ps, priceLevelName, 1, offset); //PRCLEVELNAME
                setObject(ps, 0, 2, offset); //PRCLEVELKEY
                setObject(ps, "0", 3, offset); //DELFLAG

                setObject(ps, priceLevelId, 4, keysOffset); //PRCLEVELID
            } else {
                setObject(ps, priceLevelId, 1, offset); //PRCLEVELID
                setObject(ps, priceLevelName, 2, offset); //PRCLEVELNAME
                setObject(ps, 0, 3, offset); //PRCLEVELKEY
                setObject(ps, "0", 4, offset); //DELFLAG

                setObject(ps, priceLevelId, 5); //PRCLEVELID
            }
        }

        ps.addBatch();
    }

    private void exportSArea(Connection conn, AstronConnectionString params, TransactionCashRegisterInfo transaction, boolean newScheme) throws SQLException {
        String[] keys = new String[]{"SAREAID"};
        String[] columns = new String[]{"SAREAID", "PRCLEVELID", "CASHPROFILEID", "FIRMID", "CURRENCYID", "SAREANAME", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, params, "SAREA", columns, keys, newScheme)) {
            int offset = columns.length + keys.length;
            if(params.pgsql) {
                setObject(ps, transaction.nppGroupMachinery, 1); //SAREAID
                setObject(ps, getPriceLevelId(transaction.nppGroupMachinery, true), 2); //PRCLEVELID
                setObject(ps, 1, 3); //CASHPROFILEID
                setObject(ps, 1, 4); //FIRMID
                setObject(ps, 933, 5); //CURRENCYID
                setObject(ps, trim(transaction.nameGroupMachinery, 50), 6); //SAREANAME
                setObject(ps, 0, 7); //DELFLAG
            } else {
                if(newScheme) {
                    setObject(ps, getPriceLevelId(transaction.nppGroupMachinery, true), 1, offset); //PRCLEVELID
                    setObject(ps, 1, 2, offset); //CASHPROFILEID
                    setObject(ps, 1, 3, offset); //FIRMID
                    setObject(ps, 933, 4, offset); //CURRENCYID
                    setObject(ps, trim(transaction.nameGroupMachinery, 50), 5, offset); //SAREANAME
                    setObject(ps, "0", 6, offset); //DELFLAG

                    setObject(ps, transaction.nppGroupMachinery, 7, keys.length); //SAREAID
                } else {
                    setObject(ps, transaction.nppGroupMachinery, 1, offset); //SAREAID
                    setObject(ps, getPriceLevelId(transaction.nppGroupMachinery, true), 2, offset); //PRCLEVELID
                    setObject(ps, 1, 3, offset); //CASHPROFILEID
                    setObject(ps, 1, 4, offset); //FIRMID
                    setObject(ps, 933, 5, offset); //CURRENCYID
                    setObject(ps, trim(transaction.nameGroupMachinery, 50), 6, offset); //SAREANAME
                    setObject(ps, "0", 7, offset); //DELFLAG

                    setObject(ps, transaction.nppGroupMachinery, 8); //SAREAID
                }
            }

            ps.addBatch();

            ps.executeBatch();
            conn.commit();
        }
    }

    private void exportSAreaPrc(Connection conn, AstronConnectionString params, TransactionCashRegisterInfo transaction, boolean hasSecondPrice, boolean newScheme) throws SQLException {
        String[] keys = new String[]{"SAREAID", "PRCLEVELID"};
        String[] columns = new String[]{"SAREAID", "PRCLEVELID", "DELFLAG"};
        try (PreparedStatement ps = getPreparedStatement(conn, params, "SAREAPRC", columns, keys, newScheme)) {
            int offset = columns.length + keys.length;

            addSAreaPrcRow(ps, params, transaction, offset, false, keys.length, newScheme);
            if(hasSecondPrice) {
                addSAreaPrcRow(ps, params, transaction, offset, true, keys.length, newScheme);
            }

            ps.executeBatch();
            conn.commit();
        }
    }

    private void addSAreaPrcRow(PreparedStatement ps, AstronConnectionString params, TransactionCashRegisterInfo transaction, int offset, boolean secondPrice, int keysOffset, boolean newScheme) throws SQLException {
        Integer priceLevelId = getPriceLevelId(transaction.nppGroupMachinery, true, secondPrice);
        if(params.pgsql) {
            setObject(ps, transaction.nppGroupMachinery, 1); //SAREAID
            setObject(ps, priceLevelId, 2); //PRCLEVELID
            setObject(ps, 0, 3); //DELFLAG
        } else {
            if(newScheme) {
                setObject(ps, "0", 1, offset); //DELFLAG

                setObject(ps, transaction.nppGroupMachinery, 2, keysOffset); //SAREAID
                setObject(ps, priceLevelId, 3, keysOffset); //PRCLEVELID
            } else {
                setObject(ps, transaction.nppGroupMachinery, 1, offset); //SAREAID
                setObject(ps, priceLevelId, 2, offset); //PRCLEVELID
                setObject(ps, "0", 3, offset); //DELFLAG

                setObject(ps, transaction.nppGroupMachinery, 4); //SAREAID
                setObject(ps, priceLevelId, 5); //PRCLEVELID
            }
        }

        ps.addBatch();
    }

    private void exportDCard(Connection conn, AstronConnectionString params, List<DiscountCard> discountCardList, boolean newScheme) throws SQLException {
        String[] keys = new String[]{"DCARDID"};
        String[] columns = new String[]{"DCARDID", "CLNTID", "DCARDCODE", "DCARDNAME", "ISPAYMENT", "DELFLAG", "LOCKED"};
        try (PreparedStatement ps = getPreparedStatement(conn, params, "DCARD", columns, keys, newScheme)) {
            int offset = columns.length + keys.length;

            for (DiscountCard discountCard : discountCardList) {
                if (!Thread.currentThread().isInterrupted()) {
                    if(params.pgsql) {
                        setObject(ps, discountCard.idDiscountCard, 1); //DCARDID
                        setObject(ps, Integer.parseInt(discountCard.idDiscountCard), 2); //CLNTID
                        setObject(ps, discountCard.idDiscountCard, 3); //DCARDCODE
                        setObject(ps, discountCard.nameDiscountCard, 4); //DCARDNAME
                        setObject(ps, 0, 5); //ISPAYMENT
                        setObject(ps, 0, 6); //DELFLAG
                        setObject(ps, 0, 7); //LOCKED
                    } else {
                        if(newScheme) {
                            setObject(ps, discountCard.idDiscountCard, 1, offset); //CLNTID
                            setObject(ps, discountCard.idDiscountCard, 2, offset); //DCARDCODE
                            setObject(ps, discountCard.nameDiscountCard, 3, offset); //DCARDNAME
                            setObject(ps, false, 4, offset); //ISPAYMENT
                            setObject(ps, "0", 5, offset); //DELFLAG
                            setObject(ps, 0, 6, offset); //LOCKED

                            setObject(ps, discountCard.idDiscountCard, 7, keys.length); //DCARDID
                        } else {
                            setObject(ps, discountCard.idDiscountCard, 1, offset); //DCARDID
                            setObject(ps, discountCard.idDiscountCard, 2, offset); //CLNTID
                            setObject(ps, discountCard.idDiscountCard, 3, offset); //DCARDCODE
                            setObject(ps, discountCard.nameDiscountCard, 4, offset); //DCARDNAME
                            setObject(ps, false, 5, offset); //ISPAYMENT
                            setObject(ps, "0", 6, offset); //DELFLAG
                            setObject(ps, 0, 7, offset); //LOCKED

                            setObject(ps, discountCard.idDiscountCard, 8, offset); //DCARDID
                        }

                    }

                    ps.addBatch();
                } else break;
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    private boolean isValidItem(TransactionCashRegisterInfo transaction, Map<String, CashRegisterItemInfo> deleteBarcodeMap, List<CashRegisterItemInfo> usedDeleteBarcodeList, CashRegisterItemInfo item) {
        boolean isValidItem = parseUOM(item.idUOM) != null && parseIdItem(item) != null;
        if(isValidItem) {
            if(deleteBarcodeMap != null && deleteBarcodeMap.containsKey(item.idItem)) {
                usedDeleteBarcodeList.add(deleteBarcodeMap.get(item.idItem));
            }
        } else {
            astronLogger.info(String.format("transaction %s, invalid item: barcode %s, id %s, uom %s", transaction.id, item.idBarcode, item.idItem, item.idUOM));
        }
        return isValidItem;
    }

    private void exportFlags(Connection conn, AstronConnectionString params, String tables) throws SQLException {
        conn.setAutoCommit(true);
        try (Statement statement = conn.createStatement()) {
            String sql = params.pgsql ?
                    "UPDATE DATAPUMP SET recordnum = 1 WHERE dirname in (" + tables + ")" :
                    "UPDATE [DATAPUMP] SET recordnum = 1 WHERE dirname in (" + tables + ")";
            statement.executeUpdate(sql);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private Exception waitFlags(Connection conn, AstronConnectionString params, String tables, List<CashRegisterItemInfo> usedDeleteBarcodeList, String deleteBarcodeKey, int timeout, boolean skipDeleteBarcode) throws InterruptedException {
        int count = 0;
        int flags;
        while ((flags = checkFlags(conn, params, tables)) != 0) {
            if (count > (timeout / 5)) {
                String message = String.format("data was sent to db but %s flag records were not set to zero", flags);
                astronLogger.error(message);
                return new RuntimeException(message);
            } else {
                count++;
                astronLogger.info(String.format("Waiting for setting to zero %s flag records", flags));
                Thread.sleep(5000);
            }
        }
        if(!skipDeleteBarcode) {
            Map<String, CashRegisterItemInfo> deleteBarcodeMap = deleteBarcodeConnectionStringMap.get(deleteBarcodeKey);
            for (CashRegisterItemInfo usedDeleteBarcode : usedDeleteBarcodeList) {
                deleteBarcodeMap.remove(usedDeleteBarcode.idItem);
            }
            deleteBarcodeConnectionStringMap.put(deleteBarcodeKey, deleteBarcodeMap);
        }
        return null;
    }

    private int checkFlags(Connection conn, AstronConnectionString params, String tables) {
        try (Statement statement = conn.createStatement()) {
            String sql = params.pgsql ?
                    "SELECT COUNT(*) FROM DATAPUMP WHERE dirname in (" + tables + ") AND recordnum = 1" :
                    "SELECT COUNT(*) FROM [DATAPUMP] WHERE dirname in (" + tables + ") AND recordnum = 1";
            ResultSet resultSet = statement.executeQuery(sql);
            return resultSet.next() ? resultSet.getInt(1) : 0;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private Exception waitConnectionSemaphore(AstronConnectionString params, int timeout, boolean stopList) {
        try {
            int count = 0;
            while (connectionSemaphore.contains(params.connectionString)) {
                if (count > (timeout / 5)) {
                    String message;
                    if (stopList) {
                        message = "Timeout exception. ProcessTransaction thread uses " + params.connectionString;
                        astronLogger.error(message);
                    } else {
                        message = "Timeout exception. StopList thread uses " + params.connectionString;
                        astronLogger.error(message);
                    }
                    return new Exception(message);
                } else {
                    count++;
                    astronLogger.info("Waiting connection semaphore");
                    Thread.sleep(5000);
                }
            }
        } catch (InterruptedException e) {
            astronLogger.error(e.getMessage());
            return e;
        }
        return null;
    }

    private void truncateTables(Connection conn, Integer extGrpId) throws SQLException {
        String[] tables = extGrpId != null ? new String[]{"GRP", "ART", "UNIT", "PACK", "EXBARC", "PACKPRC", "ARTEXTGRP"} : new String[]{"GRP", "ART", "UNIT", "PACK", "EXBARC", "PACKPRC"};
        for (String table : tables) {
            try (Statement s = conn.createStatement()) {
                s.execute("TRUNCATE TABLE " + table);
            }
        }
        conn.commit();
    }

    private void truncateTable(Connection conn, String name) throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("TRUNCATE TABLE " + name);
        }
        conn.commit();
    }

    private void setObject(PreparedStatement ps, Object value, int index, int columnsSize) throws SQLException {
        setObject(ps, value, index);
        setObject(ps, value, index + columnsSize);
    }

    private void setObject(PreparedStatement ps, Object value, int index) throws SQLException {
        if (value instanceof Date) ps.setDate(index, (Date) value);
        else if (value instanceof Timestamp) ps.setTimestamp(index, ((Timestamp) value));
        else if (value instanceof String) ps.setString(index, ((String) value).trim());
        else ps.setObject(index, value);
    }

    private PreparedStatement getPreparedStatement(Connection conn, AstronConnectionString connectionParams, String table, String[] columnNames, String[] keyNames, boolean newScheme) throws SQLException {
        String set = "";
        String columns = "";
        String params = "";
        List<String> keyList = Arrays.asList(keyNames);
        for (String columnName : columnNames) {
            columns = concat(columns, columnName, ",");
            if(!newScheme || !keyList.contains(columnName)) {
                set = concat(set, columnName + "=?", ",");
            }
            params = concat(params, "?", ",");
        }
        if(connectionParams.pgsql) {
            List<String> conflicts = new ArrayList<>();
            for(String columnName : columnNames) {
                conflicts.add(columnName + " = EXCLUDED." + columnName);
            }
            return conn.prepareStatement(String.format("INSERT INTO %s(%s) VALUES (%s) ON CONFLICT (%s) DO UPDATE SET %s",
                    table, columns, params, StringUtils.join(keyNames, ","),  StringUtils.join(conflicts, ",")));
        } else {
            String wheres = "";
            for (String keyName : keyNames) {
                wheres = concat(wheres, keyName + "=?", " AND ");
            }
            return conn.prepareStatement(String.format("UPDATE [%s] SET %s WHERE %s IF @@ROWCOUNT=0 INSERT INTO %s(%s) VALUES (%s)", table, set, wheres, table, columns, params));
        }
    }

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<String> directorySet) {
        AstronSettings astronSettings = springContext.containsBean("astronSettings") ? (AstronSettings) springContext.getBean("astronSettings") : new AstronSettings();
        Integer timeout = astronSettings.getTimeout() == null ? 300 : astronSettings.getTimeout();
        boolean exportExtraTables = astronSettings.isExportExtraTables();
        Integer maxBatchSize =astronSettings.getMaxBatchSize();
        boolean useNewScheme = astronSettings.isNewScheme();

        for (String directory : directorySet) {
            AstronConnectionString params = new AstronConnectionString(directory);
            if (params.connectionString != null && !stopListInfo.stopListItemMap.isEmpty()) {
                Exception exception = waitConnectionSemaphore(params, timeout, true);
                if((exception != null)) {
                    throw new RuntimeException(exception);
                } else {
                    try (Connection conn = getConnection(params)) {

                        connectionSemaphore.add(params.connectionString);

                        List<StopListItemInfo> itemsList = new ArrayList<>(stopListInfo.stopListItemMap.values());

                        boolean newScheme = !params.pgsql && useNewScheme; //используем новую схему только по опции и только для mssql

                        astronLogger.info(String.format("stoplist %s, table art", stopListInfo.number));
                        exportArt(conn, params, itemsList, true, !stopListInfo.exclude, maxBatchSize, newScheme);

                        astronLogger.info(String.format("stoplist %s, table unit", stopListInfo.number));
                        exportUnit(conn, params, itemsList, !stopListInfo.exclude, maxBatchSize, newScheme);

                        astronLogger.info(String.format("stoplist %s, table pack", stopListInfo.number));
                        exportPack(conn, params, itemsList, !stopListInfo.exclude, maxBatchSize, newScheme);

                        astronLogger.info(String.format("stoplist %s, table exbarc", stopListInfo.number));
                        exportExBarc(conn, params, itemsList, !stopListInfo.exclude, maxBatchSize, newScheme);

                        astronLogger.info(String.format("stoplist %s, table packprc", stopListInfo.number));
                        exportPackPrcStopList(conn, params, stopListInfo, exportExtraTables, !stopListInfo.exclude, maxBatchSize, newScheme);

                        String tables = "'ART', 'UNIT', 'PACK', 'EXBARC', 'PACKPRC'";

                        astronLogger.info("waiting for processing stopLists");
                        exportFlags(conn, params, tables);

                        Exception e = waitFlags(conn, params, tables, new ArrayList<>(), null, timeout, true);
                        if (e != null) {
                            throw e;
                        }

                    } catch (Exception e) {
                        astronLogger.error("sendStopListInfo error", e);
                    } finally {
                        connectionSemaphore.remove(params.connectionString);
                    }
                }
            }
        }
    }

    @Override
    public boolean sendDeleteBarcodeInfo(DeleteBarcodeInfo deleteBarcodeInfo) {
        AstronConnectionString params = new AstronConnectionString(deleteBarcodeInfo.directoryGroupMachinery);
        if (params.connectionString == null) {
            astronLogger.error("no connectionString found");
        } else if (deleteBarcodeInfo.directoryGroupMachinery != null && !deleteBarcodeInfo.barcodeList.isEmpty()) {

            String key = getDeleteBarcodeKey(params.connectionString, deleteBarcodeInfo.nppGroupMachinery);
            Map<String, CashRegisterItemInfo> deleteBarcodeMap = deleteBarcodeConnectionStringMap.get(key);
            if (deleteBarcodeMap == null)
                deleteBarcodeMap = new HashMap<>();
            for (CashRegisterItemInfo item : deleteBarcodeInfo.barcodeList) {
                if (!deleteBarcodeMap.containsKey(item.idItem)) {
                    deleteBarcodeMap.put(item.idItem, item);
                }
            }
            if (!deleteBarcodeMap.isEmpty())
                deleteBarcodeConnectionStringMap.put(key, deleteBarcodeMap);
            return false;
        }
        return true;
    }

    @Override
    public void sendDiscountCardList(List<DiscountCard> discountCardList, RequestExchange requestExchange) {

        AstronSettings astronSettings = springContext.containsBean("astronSettings") ? (AstronSettings) springContext.getBean("astronSettings") : null;
        Integer timeout = astronSettings == null || astronSettings.getTimeout() == null ? 300 : astronSettings.getTimeout();
        boolean useNewScheme = astronSettings.isNewScheme();

        for (String directory : getDirectorySet(requestExchange)) {

            Throwable exception;

            AstronConnectionString params = new AstronConnectionString(directory);
            if (params.connectionString == null) {
                astronLogger.error("no connectionString found");
                exception = new RuntimeException("no connectionString found");
            } else {

                try (Connection conn = getConnection(params)) {
                    String table = "'DCARD'";

                    int flags = checkFlags(conn, params, table);
                    if (flags > 0) {
                        exception = new RuntimeException(String.format("data from previous transactions was not processed (%s flags not set to zero)", flags));
                    } else {
                        truncateTable(conn, "DCARD");

                        boolean newScheme = !params.pgsql && useNewScheme; //используем новую схему только по опции и только для mssql

                        astronLogger.info("export table dcard");
                        exportDCard(conn, params, discountCardList, newScheme);

                        astronLogger.info("waiting for processing transactions");
                        exportFlags(conn, params, table);
                        exception = waitFlags(conn, params, table, null, null, timeout, true);
                    }
                } catch (Exception e) {
                    astronLogger.error("sendDiscountCardList error", e);
                    exception = e;
                }
            }

            if(exception != null) {
                throw new RuntimeException(exception);
            }
        }
    }

    @Override
    public AstronSalesBatch readSalesInfo(String directory, List<CashRegisterInfo> cashRegisterInfoList) {

        AstronSalesBatch salesBatch = null;

        Map<Integer, CashRegisterInfo> machineryMap = new HashMap<>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (fitHandler(c)) {
                if (c.number != null && c.numberGroup != null) {
                    machineryMap.put(c.number, c);
                }
            }
        }

        try {
            AstronConnectionString params = new AstronConnectionString(directory);
            if (params.connectionString == null) {
                astronLogger.error("readSalesInfo: no connectionString found");
            } else {
                try (Connection conn = getConnection(params)) {
                    salesBatch = readSalesInfoFromSQL(conn, params, machineryMap, directory);
                }
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        return salesBatch;
    }

    private AstronSalesBatch readSalesInfoFromSQL(Connection conn, AstronConnectionString params, Map<Integer, CashRegisterInfo> machineryMap, String directory) {

        List<SalesInfo> salesInfoList = new ArrayList<>();
        List<AstronRecord> recordList = new ArrayList<>();

        AstronSettings astronSettings = springContext.containsBean("astronSettings") ? (AstronSettings) springContext.getBean("astronSettings") : null;
        Set<Integer> cashPayments = astronSettings == null ? new HashSet<>() : parsePayments(astronSettings.getCashPayments());
        Set<Integer> cardPayments = astronSettings == null ? new HashSet<>() : parsePayments(astronSettings.getCardPayments());
        Set<Integer> giftCardPayments = astronSettings == null ? new HashSet<>() : parsePayments(astronSettings.getGiftCardPayments());
        Set<Integer> customPayments = astronSettings == null ? new HashSet<>() : parsePayments(astronSettings.getCustomPayments());
        boolean ignoreSalesInfoWithoutCashRegister = astronSettings != null && astronSettings.isIgnoreSalesInfoWithoutCashRegister();

        checkExtraColumns(conn, params);
        createFusionProcessedIndex(conn, params);
        createSalesIndex(conn, params);

        try (Statement statement = conn.createStatement()) {
            String query = "SELECT sales.SALESATTRS, sales.SYSTEMID, sales.SESSID, sales.SALESTIME, sales.FRECNUM, sales.CASHIERID, cashier.CASHIERNAME, " +
                    "sales.SALESTAG, sales.SALESBARC, sales.SALESCODE, sales.SALESCOUNT, sales.SALESPRICE, sales.SALESSUM, sales.SALESDISC, sales.SALESTYPE, " +
                    "sales." + getSalesNumField() + ", sales.SAREAID, sales." + getSalesRefundField() + ", sales.PRCLEVELID, sales.SALESATTRI, " +
                    "COALESCE(sess.SESSSTART,sales.SALESTIME) AS SESSSTART FROM SALES sales " +
                    "LEFT JOIN (SELECT SESSID, SYSTEMID, SAREAID, max(SESSSTART) AS SESSSTART FROM SESS GROUP BY SESSID, SYSTEMID, SAREAID) sess " +
                    "ON sales.SESSID=sess.SESSID AND sales.SYSTEMID=sess.SYSTEMID AND sales.SAREAID=sess.SAREAID " +
                    "LEFT JOIN CASHIER cashier ON sales.CASHIERID=cashier.CASHIERID " +
                    "WHERE (FUSION_PROCESSED IS NULL OR FUSION_PROCESSED = 0) AND SALESCANC = 0 ORDER BY SAREAID, SYSTEMID, SESSID, sales.FRECNUM, SALESTAG DESC";
            ResultSet rs = statement.executeQuery(query);

            List<SalesInfo> curSalesInfoList = new ArrayList<>();
            List<AstronRecord> curRecordList = new ArrayList<>();
            String currentUniqueReceiptId = null;
            Map<String, Integer> uniqueReceiptIdNumberReceiptMap = new HashMap<>();
            Set<String> uniqueReceiptDetailIdSet = new HashSet<>();
            BigDecimal prologSum = BigDecimal.ZERO;
            String idDiscountCard = null;

            BigDecimal sumCash = null;
            BigDecimal sumCard = null;
            BigDecimal sumGiftCard = null;
            Map<String, BigDecimal> customPaymentsMap = new HashMap<>();
            String idSaleReceiptReceiptReturnDetail = null;
            while (rs.next()) {

                Integer sAreaId = rs.getInt("SAREAID");
                Integer nppCashRegister = rs.getInt("SYSTEMID");
                CashRegisterInfo cashRegister = machineryMap.get(nppCashRegister);
                Integer nppGroupMachinery = cashRegister == null ? null : cashRegister.numberGroup;

                Integer salesNum = rs.getInt(getSalesNumField());

                Integer sessionId = rs.getInt("SESSID");
                String numberZReport = String.valueOf(sessionId);

                LocalDateTime salesTime = LocalDateTime.parse(rs.getString("SALESTIME"), DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                LocalDate dateReceipt = salesTime.toLocalDate();
                LocalTime timeReceipt = salesTime.toLocalTime();

                Integer numberReceipt = rs.getInt("FRECNUM");

                if (numberReceipt == 0) {
                    astronLogger.info(String.format("incorrect record with FRECNUM = 0: SAREAID %s, SYSTEMID %s, dateReceipt %s, timeReceipt %s, SALESNUM %s, SESSIONID %s", sAreaId, nppCashRegister, dateReceipt, timeReceipt, salesNum, sessionId));
                } else {

                    String uniqueReceiptDetailId = getUniqueReceiptDetailId(sAreaId, nppCashRegister, sessionId, numberReceipt, salesNum);
                    //некоторые записи просто дублируются, такие игнорируем
                    if ((cashRegister != null || !ignoreSalesInfoWithoutCashRegister) && !uniqueReceiptDetailIdSet.contains(uniqueReceiptDetailId)) {
                        uniqueReceiptDetailIdSet.add(uniqueReceiptDetailId);

                        LocalDateTime sessStart = LocalDateTime.parse(rs.getString("SESSSTART"), DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                        LocalDate dateZReport = sessStart.toLocalDate();
                        LocalTime timeZReport = sessStart.toLocalTime();

                        String idEmployee = String.valueOf(rs.getInt("CASHIERID"));
                        String nameEmployee = rs.getString("CASHIERNAME");

                        Map<String, Object> receiptDetailExtraFields = new HashMap<>();
                        receiptDetailExtraFields.put("priceLevelId", rs.getInt("PRCLEVELID"));
                        receiptDetailExtraFields.put("salesAttri", rs.getInt("SALESATTRI"));

                        Integer type = rs.getInt("SALESTYPE");
                        boolean customPaymentType = customPayments.contains(type);
                        if (!customPaymentType) {
                            if (cashPayments.contains(type)) type = 0;
                            else if (cardPayments.contains(type)) type = 1;
                            else if (giftCardPayments.contains(type)) type = 2;
                        }

                        Integer recordType = rs.getInt("SALESTAG");
                        boolean isReturn = rs.getInt(getSalesRefundField()) != 0; // 0 - продажа, 1 - возврат, 2 - аннулирование

                        switch (recordType) {
                            case 0: {//товарная позиция
                                numberReceipt = uniqueReceiptIdNumberReceiptMap.get(currentUniqueReceiptId);
                                String idBarcode = trimToNull(rs.getString("SALESBARC"));
                                String idItem = String.valueOf(rs.getInt("SALESCODE"));
                                boolean isWeight = !customPaymentType && (type == 0 || type == 2);
                                BigDecimal totalQuantity = safeDivide(rs.getBigDecimal("SALESCOUNT"), isWeight ? 1000 : 1);
                                BigDecimal price = safeDivide(rs.getBigDecimal("SALESPRICE"), 100);
                                BigDecimal sumReceiptDetail = safeDivide(rs.getBigDecimal("SALESSUM"), 100);
                                BigDecimal discountSumReceiptDetail = safeDivide(rs.getBigDecimal("SALESDISC"), 100);
                                totalQuantity = isReturn ? totalQuantity.negate() : totalQuantity;
                                sumReceiptDetail = isReturn ? sumReceiptDetail.negate() : sumReceiptDetail;
                                curSalesInfoList.add(getSalesInfo(nppGroupMachinery, nppCashRegister, numberZReport, dateZReport, timeZReport, numberReceipt, dateReceipt, timeReceipt, idEmployee, nameEmployee, sumCard, sumCash, sumGiftCard, customPaymentsMap, idBarcode, idItem, null, idSaleReceiptReceiptReturnDetail, totalQuantity, price, sumReceiptDetail, discountSumReceiptDetail, null, idDiscountCard, salesNum, null, null, receiptDetailExtraFields, cashRegister));
                                curRecordList.add(new AstronRecord(salesNum, sessionId, nppCashRegister, sAreaId));
                                prologSum = safeSubtract(prologSum, rs.getBigDecimal("SALESSUM"));
                                break;
                            }
                            case 1: {//оплата
                                BigDecimal sum = safeDivide(rs.getBigDecimal("SALESSUM"), 100);
                                if (isReturn) sum = safeNegate(sum);
                                if (customPaymentType) {
                                    BigDecimal customPaymentSum = customPaymentsMap.get(String.valueOf(type));
                                    customPaymentsMap.put(String.valueOf(type), safeAdd(customPaymentSum, sum));
                                } else {
                                    switch (type) {
                                        case 1:
                                            sumCard = safeAdd(sumCard, sum);
                                            break;
                                        case 2:
                                            sumGiftCard = safeAdd(sumGiftCard, sum);
                                            break;
                                        case 0:
                                        default:
                                            sumCash = safeAdd(sumCash, sum);
                                            break;
                                    }
                                }
                                curRecordList.add(new AstronRecord(salesNum, sessionId, nppCashRegister, sAreaId));
                                break;
                            }
                            case 2: {//пролог чека
                                String uniqueReceiptId;
                                //увеличиваем id, если номер чека совпадает
                                while (uniqueReceiptIdNumberReceiptMap.containsKey(uniqueReceiptId = getUniqueReceiptId(sAreaId, nppCashRegister, sessionId, numberReceipt))) {
                                    numberReceipt += 100000000; //считаем, что касса никогда сама не достигнет стомиллионного чека. Может возникнуть переполнение, если таких чеков будет больше 21
                                    astronLogger.info(String.format("Повтор номера чека: Касса %s, Z-отчёт %s, Чек %s", nppCashRegister, numberZReport, numberReceipt));
                                }
                                currentUniqueReceiptId = uniqueReceiptId;
                                uniqueReceiptIdNumberReceiptMap.put(uniqueReceiptId, numberReceipt);

                                sumCash = null;
                                sumCard = null;
                                sumGiftCard = null;
                                customPaymentsMap = new HashMap<>();
                                if (prologSum.compareTo(BigDecimal.ZERO) == 0) {
                                    salesInfoList.addAll(curSalesInfoList);
                                    recordList.addAll(curRecordList);
                                } else {
                                    for(AstronRecord record : curRecordList) {
                                        astronLogger.info(String.format("incorrect record: SAREAID %s, SYSTEMID %s, SALESNUM %s, SESSIONID %s", record.sAreaId, record.systemId, record.salesNum, record.sessId));
                                    }
                                }
                                curSalesInfoList = new ArrayList<>();
                                curRecordList = new ArrayList<>();
                                prologSum = rs.getBigDecimal("SALESSUM");
                                idDiscountCard = trimToNull(rs.getString("SALESBARC"));

                                if (isReturn) { //чек возврата
                                    String salesAttrs = rs.getString("SALESATTRS");
                                    String[] salesAttrsSplitted = salesAttrs != null ? salesAttrs.split(":") : new String[0];
                                    String numberReceiptOriginal = salesAttrsSplitted.length > 3 ? salesAttrsSplitted[3] : null;
                                    String numberZReportOriginal = salesAttrsSplitted.length > 4 ? salesAttrsSplitted[4] : null;
                                    String numberCashRegisterOriginal = salesAttrsSplitted.length > 5 ? salesAttrsSplitted[5] : null;
                                    LocalDate dateReceiptOriginal = salesAttrsSplitted.length > 7 ? LocalDateTime.parse(salesAttrsSplitted[7], DateTimeFormatter.ofPattern("yyyyMMddHHmmss")).toLocalDate() : null;
                                    idSaleReceiptReceiptReturnDetail = nppGroupMachinery + "_" + numberCashRegisterOriginal + "_" + numberZReportOriginal + "_" + (dateReceiptOriginal != null ? dateReceiptOriginal.format(DateTimeFormatter.ofPattern("ddMMyyyy")) : "") + "_" + numberReceiptOriginal;
                                } else {
                                    idSaleReceiptReceiptReturnDetail = null;
                                }
                                curRecordList.add(new AstronRecord(salesNum, sessionId, nppCashRegister, sAreaId));
                                break;
                            }
                            case 3: {//Возвращенная товарная позиция - игнорируем эту запись. В дополнение к ней создаётся новая, с SALESTAG = 0 и SALESREFUND = 1
                                curRecordList.add(new AstronRecord(salesNum, sessionId, nppCashRegister, sAreaId));
                                break;
                            }
                        }
                    }
                }
            }

            if(prologSum.compareTo(BigDecimal.ZERO) == 0) {
                salesInfoList.addAll(curSalesInfoList);
                recordList.addAll(curRecordList);
            }

            if (salesInfoList.size() > 0)
                astronLogger.info(String.format("found %s records", salesInfoList.size()));
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
        return new AstronSalesBatch(salesInfoList, recordList, directory);
    }

    private String getUniqueReceiptId(Integer sAreaId, Integer nppCashRegister, Integer sessionId, Integer numberReceipt) {
        return sAreaId + "/" + nppCashRegister + "/" + sessionId + "/" + numberReceipt;
    }

    private String getUniqueReceiptDetailId(Integer sAreaId, Integer nppCashRegister, Integer sessionId, Integer numberReceipt, Integer salesNum) {
        return getUniqueReceiptId(sAreaId, nppCashRegister, sessionId, numberReceipt) + "/" + salesNum;
    }

    @Override
    public void requestSalesInfo(List<RequestExchange> requestExchangeList, Set<Long> succeededRequests, Map<Long, Throwable> failedRequests, Map<Long, Throwable> ignoredRequests) {
        for (RequestExchange entry : requestExchangeList) {
            for (String directory : getDirectorySet(entry)) {
                AstronConnectionString params = new AstronConnectionString(directory);
                if (params.connectionString != null) {
                    astronLogger.info("connecting to " + params.connectionString);
                    try (Connection conn = getConnection(params)) {

                        checkExtraColumns(conn, params);
                        createFusionProcessedIndex(conn, params);

                        Statement statement = null;
                        try {
                            StringBuilder dateWhere = new StringBuilder();
                            LocalDate dateFrom = entry.dateFrom;
                            LocalDate dateTo = entry.dateTo;
                            while (dateFrom.compareTo(dateTo) <= 0) {
                                String dateString = dateFrom.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                                dateWhere.append((dateWhere.length() == 0) ? "" : " OR ").append("SALESTIME LIKE '").append(dateString).append("%'");
                                dateFrom = dateFrom.plusDays(1);
                            }
                            StringBuilder stockWhere = new StringBuilder();
                            for(CashRegisterInfo cashRegister : getCashRegisterSet(entry, true)) {
                                stockWhere.append((stockWhere.length() == 0) ? "" : " OR ").append("SYSTEMID = ").append(cashRegister.number);
                            }
                            if (dateWhere.length() > 0) {
                                statement = conn.createStatement();
                                String query = params.pgsql ?
                                        "UPDATE sales SET fusion_processed = 0 WHERE SALESCANC = 0 AND (" + dateWhere + ")" + (stockWhere.length() > 0 ? (" AND (" + stockWhere + ")") : "") :
                                        "UPDATE [SALES] SET FUSION_PROCESSED = 0 WHERE SALESCANC = 0 AND (" + dateWhere + ")" + (stockWhere.length() > 0 ? (" AND (" + stockWhere + ")") : "");
                                astronLogger.info("RequestSalesInfo: " + query);
                                statement.executeUpdate(query);
                                conn.commit();
                            }
                            succeededRequests.add(entry.requestExchange);

                        } catch (SQLException e) {
                            failedRequests.put(entry.requestExchange, e);
                            astronLogger.error("RequestSalesInfo error", e);
                        } finally {
                            if (statement != null) statement.close();
                        }
                    } catch (ClassNotFoundException | SQLException e) {
                        throw Throwables.propagate(e);
                    }
                }
            }
        }
    }

    @Override
    public void finishReadingSalesInfo(AstronSalesBatch salesBatch) {
        for (String directory : salesBatch.directorySet) {
            AstronConnectionString params = new AstronConnectionString(directory);
            if (params.connectionString != null && salesBatch.recordList != null) {

                try (Connection conn = getConnection(params);
                     PreparedStatement ps = conn.prepareStatement(params.pgsql ?
                             "UPDATE sales SET fusion_processed = 1 WHERE " + getSalesNumField() + " = ? AND SESSID = ? AND SYSTEMID = ? AND SAREAID = ?" :
                             "UPDATE [SALES] SET FUSION_PROCESSED = 1 WHERE " + getSalesNumField() + " = ? AND SESSID = ? AND SYSTEMID = ? AND SAREAID = ?" )) {
                    int count = 0;
                    for (AstronRecord record : salesBatch.recordList) {
                        ps.setInt(1, record.salesNum);
                        ps.setInt(2, record.sessId);
                        ps.setInt(3, record.systemId);
                        ps.setInt(4, record.sAreaId);
                        ps.addBatch();
                        count++;
                        if(count > 20000) {
                            ps.executeBatch();
                            conn.commit();
                            count = 0;
                        }
                    }
                    ps.executeBatch();
                    conn.commit();

                } catch (SQLException | ClassNotFoundException e) {
                    throw Throwables.propagate(e);
                }
            }
        }
    }

    private void checkExtraColumns(Connection conn, AstronConnectionString params) {
        try (Statement statement = conn.createStatement()) {
            String query = params.pgsql ? "SELECT column_name FROM information_schema.columns WHERE table_name='sales' and column_name='fusion_processed'":
                                          "SELECT COL_LENGTH('SALES', 'FUSION_PROCESSED')";
            ResultSet result = statement.executeQuery(query);
            int exists = result.next() ? (params.pgsql ? 1 : result.getInt(1)) : 0;
            if(exists == 0) {
                throw new RuntimeException("Column 'FUSION_PROCESSED' doesn't exists");
            }
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
    }

    private void createFusionProcessedIndex(Connection conn, AstronConnectionString params) {
        try (Statement statement = conn.createStatement()) {
            String query = params.pgsql ?
                    String.format("CREATE INDEX IF NOT EXISTS %s ON sales(fusion_processed)", getFusionProcessedIndexName()) :
                    String.format("IF NOT EXISTS (SELECT 1 WHERE IndexProperty(Object_Id('SALES'), '%s', 'IndexId') > 0) BEGIN CREATE INDEX %s ON SALES (FUSION_PROCESSED) END",
                    getFusionProcessedIndexName(), getFusionProcessedIndexName());
            statement.execute(query);
            conn.commit();
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
    }

    protected String getFusionProcessedIndexName() {
        return "fusion";
    }


    protected void createSalesIndex(Connection conn, AstronConnectionString params) {
        try (Statement statement = conn.createStatement()) {
            String query = params.pgsql ?
                    String.format("CREATE INDEX IF NOT EXISTS sale ON sales(%s, SESSID, SYSTEMID, SAREAID)", getSalesNumField()) :
                    "IF NOT EXISTS (SELECT 1 WHERE IndexProperty(Object_Id('SALES'), 'sale', 'IndexId') > 0) BEGIN CREATE INDEX sale ON SALES (" + getSalesNumField() + ", SESSID, SYSTEMID, SAREAID) END";
            statement.execute(query);
            conn.commit();
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
    }

    public class AstronRecord {
        Integer salesNum;
        Integer sessId;
        Integer systemId;
        Integer sAreaId;

        public AstronRecord(Integer salesNum, Integer sessId, Integer systemId, Integer sAreaId) {
            this.salesNum = salesNum;
            this.sessId = sessId;
            this.systemId = systemId;
            this.sAreaId = sAreaId;
        }
    }

    private Connection getConnection(AstronConnectionString params) throws SQLException, ClassNotFoundException {
        Connection conn;
        if(params.pgsql) {
            Class.forName("org.postgresql.Driver");
            conn = DriverManager.getConnection(params.connectionString, params.user, params.password);
            conn.setAutoCommit(false);
        } else {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            //https://github.com/Microsoft/mssql-jdbc/wiki/QueryTimeout
            conn = DriverManager.getConnection(params.connectionString + ";queryTimeout=5", params.user, params.password);
            conn.setAutoCommit(false);
        }
        return conn;
    }

    private String getExtIdItemGroup(ItemInfo item, boolean zeroGrpId) {
        return item instanceof CashRegisterItemInfo ? parseGroup(((CashRegisterItemInfo) item).extIdItemGroup) : (zeroGrpId ? "0" : null);
    }

    private String parseGroup(String idItemGroup) {
        try {
            return idItemGroup == null || idItemGroup.equals("Все") ? null : idItemGroup.replaceAll("[^0-9]", "");
        } catch (Exception e) {
            return null;
        }
    }

    private String parseGroup(String idItemGroup, boolean trimToNull) {
        try {
            String result = idItemGroup == null || idItemGroup.equals("Все") ? null : idItemGroup.replaceAll("[^0-9]", "");
            return trimToNull && result != null && result.isEmpty() ? null : result;
        } catch (Exception e) {
            return null;
        }
    }

    private String concat(String left, String right, String splitter) {
        return left + (left.isEmpty() ? "" : splitter) + right;
    }

    private String getDeleteBarcodeKey(String connectionString, Integer nppGroupMachinery) {
        return  connectionString + "/" + nppGroupMachinery;
    }

    private Integer getPriceLevelId(Integer nppGroupMachinery, boolean exportExtraTables) {
       return getPriceLevelId(nppGroupMachinery, exportExtraTables, false);
    }

    private Integer getPriceLevelId(Integer nppGroupMachinery, boolean exportExtraTables, boolean secondPrice) {
        return exportExtraTables ? (nppGroupMachinery * 1000 + (secondPrice ? 2 : 1)) : nppGroupMachinery;
    }

    protected String getSalesNumField() {
        return "SALESNUM";
    }

    protected String getSalesRefundField() {
        return "SALESREFUND";
    }

}