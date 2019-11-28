package equ.clt.handler;

import equ.api.*;
import equ.api.cashregister.*;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.*;

public abstract class DefaultCashRegisterHandler<S extends SalesBatch> extends CashRegisterHandler<S> {

    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    protected final static Logger processStopListLogger = Logger.getLogger("StopListLogger");
    protected final static Logger sendSalesLogger = Logger.getLogger("SendSalesLogger");
    protected final static Logger machineryExchangeLogger = Logger.getLogger("MachineryExchangeLogger");
    protected final static Logger softCheckLogger = Logger.getLogger("SoftCheckLogger");
    protected final static Logger deleteBarcodeLogger = Logger.getLogger("DeleteBarcodeLogger");

    protected Set<CashRegisterInfo> getCashRegisterSet(RequestExchange requestExchange, boolean extra) {
        Set<CashRegisterInfo> cashRegisterSet = new HashSet<>();
        for (CashRegisterInfo cashRegister : requestExchange.cashRegisterSet) {
            if (fitHandler(cashRegister))
                cashRegisterSet.add(cashRegister);
        }
        if (extra) {
            for (CashRegisterInfo cashRegister : requestExchange.extraCashRegisterSet) {
                if (fitHandler(cashRegister))
                    cashRegisterSet.add(cashRegister);
            }
        }
        return cashRegisterSet;
    }

    public Set<String> getDirectorySet(RequestExchange requestExchange) {
        Set<String> directorySet = new HashSet<>();
        for (CashRegisterInfo cashRegister : join(requestExchange.cashRegisterSet, requestExchange.extraCashRegisterSet)) {
            if (fitHandler(cashRegister) && cashRegister.directory != null)
                directorySet.add(cashRegister.directory);
        }
        return directorySet;
    }

    public Map<String, Set<String>> getDirectoryStockMap(RequestExchange requestExchange) {
        return getDirectoryStockMap(requestExchange, false);
    }

    public Map<String, Set<String>> getDirectoryStockMap(RequestExchange requestExchange, boolean useNumberGroupInShopIndices) {
        Map<String, Set<String>> directoryStockMap = new HashMap<>();
        for (CashRegisterInfo cashRegister : join(requestExchange.cashRegisterSet, requestExchange.extraCashRegisterSet)) {
            if (fitHandler(cashRegister) && cashRegister.directory != null) {
                Set<String> stockSet = directoryStockMap.get(cashRegister.directory);
                if (stockSet == null)
                    stockSet = new HashSet<>();
                String idStock = useNumberGroupInShopIndices ? String.valueOf(cashRegister.numberGroup) : cashRegister.idDepartmentStore;
                if (idStock != null)
                    stockSet.add(idStock);
                if(!useNumberGroupInShopIndices && requestExchange.idStock != null)
                    stockSet.add(requestExchange.idStock);
                directoryStockMap.put(cashRegister.directory, stockSet);
            }
        }
        return directoryStockMap;
    }

    public Map<String, Set<CashRegisterInfo>> getDirectoryCashRegisterMap(RequestExchange requestExchange) {
        Map<String, Set<CashRegisterInfo>> directoryCashRegisterMap = new HashMap<>();
        for (CashRegisterInfo cashRegister : join(requestExchange.cashRegisterSet, requestExchange.extraCashRegisterSet)) {
            if (fitHandler(cashRegister) && cashRegister.directory != null) {
                Set<CashRegisterInfo> stockSet = directoryCashRegisterMap.getOrDefault(cashRegister.directory, new HashSet<>());
                stockSet.add(cashRegister);
                directoryCashRegisterMap.put(cashRegister.directory, stockSet);
            }
        }
        return directoryCashRegisterMap;
    }

    Set<CashRegisterInfo> join(Set<CashRegisterInfo> set1, Set<CashRegisterInfo> set2) {
        Set<CashRegisterInfo> result = new HashSet<>(set1);
        result.addAll(set2);
        return result;
    }

    @Override
    public String getGroupId(TransactionCashRegisterInfo transactionInfo) {
        return null;
    }

    @Override
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionInfoList) {
        return null;
    }

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<String> directorySet) throws IOException {

    }

    @Override
    public boolean sendDeleteBarcodeInfo(DeleteBarcodeInfo deleteBarcodeInfo) {
        return true;
    }

    @Override
    public void sendDiscountCardList(List<DiscountCard> discountCardList, RequestExchange requestExchange) throws IOException {

    }

    @Override
    public void sendCashierInfoList(List<CashierInfo> cashierInfoList, RequestExchange requestExchange) throws IOException {

    }

    @Override
    public List<CashierTime> requestCashierTime(RequestExchange requestExchange, List<MachineryInfo> cashRegisterInfoList) throws ClassNotFoundException, SQLException {
        return null;
    }

    @Override
    public void sendPromotionInfo(PromotionInfo promotionInfo, RequestExchange requestExchange) throws IOException {

    }

    @Override
    public SalesBatch readSalesInfo(String directory, List<CashRegisterInfo> cashRegisterInfoList) throws IOException, ParseException {
        return null;
    }

    @Override
    public void requestSalesInfo(List<RequestExchange> requestExchangeList, Set<Long> succeededRequests, Map<Long, Throwable> failedRequests, Map<Long, Throwable> ignoredRequests) throws IOException {

    }

    @Override
    public CashDocumentBatch readCashDocumentInfo(List<CashRegisterInfo> cashRegisterInfoList, Set<String> cashDocumentSet) throws ClassNotFoundException {
        return null;
    }

    @Override
    public void finishReadingCashDocumentInfo(CashDocumentBatch cashDocumentBatch) {

    }

    @Override
    public Map<String, Timestamp> requestSucceededSoftCheckInfo(List<String> directoryList) throws ClassNotFoundException, SQLException {
        return null;
    }

    @Override
    public List<List<Object>> checkZReportSum(Map<String, List<Object>> zReportSumMap, List<List<Object>> cashRegisterList) throws ClassNotFoundException, SQLException {
        return null;
    }

    @Override
    public Map<String, List<Object>> readExtraCheckZReport(List<CashRegisterInfo> cashRegisterInfoList) {
        return null;
    }

    @Override
    public ExtraCheckZReportBatch compareExtraCheckZReport(Map<String, List<Object>> handlerZReportSumMap, Map<String, BigDecimal> baseZReportSumMap) {
        return null;
    }
}
