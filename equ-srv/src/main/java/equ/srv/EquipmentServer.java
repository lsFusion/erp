package equ.srv;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.api.cashregister.*;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItemInfo;
import equ.api.scales.TransactionScalesInfo;
import equ.api.terminal.*;
import lsfusion.base.BaseUtils;
import lsfusion.base.DateConverter;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImOrderSet;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.Compare;
import lsfusion.server.classes.*;
import lsfusion.server.context.ThreadLocalContext;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.expr.ValueExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.integration.*;
import lsfusion.server.lifecycle.LifecycleAdapter;
import lsfusion.server.lifecycle.LifecycleEvent;
import lsfusion.server.logics.*;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.PropertyInterface;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;
import org.apache.log4j.Logger;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.rmi.RemoteException;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.apache.commons.lang3.StringUtils.trim;

public class EquipmentServer extends LifecycleAdapter implements EquipmentServerInterface, InitializingBean {
    private static final Logger logger = Logger.getLogger(EquipmentServer.class);

    public static final String EXPORT_NAME = "EquipmentServer";

    private LogicsInstance logicsInstance;

    private SoftCheckInterface softCheck;

    private PromotionInterface promotionInterface;
    
    private ScriptingLogicsModule equLM;

    //Опциональные модули
    private ScriptingLogicsModule cashRegisterLM;
    private ScriptingLogicsModule cashRegisterItemLM;
    private ScriptingLogicsModule collectionLM;
    private ScriptingLogicsModule discountCardLM;
    private ScriptingLogicsModule equipmentLM;
    private ScriptingLogicsModule equipmentCashRegisterLM;
    private ScriptingLogicsModule giftCardLM;
    private ScriptingLogicsModule itemLM;
    private ScriptingLogicsModule itemFashionLM;
    private ScriptingLogicsModule machineryLM;
    private ScriptingLogicsModule machineryPriceTransactionLM;
    private ScriptingLogicsModule machineryPriceTransactionSectionLM;
    private ScriptingLogicsModule machineryPriceTransactionBalanceLM;
    private ScriptingLogicsModule machineryPriceTransactionStockTaxLM;
    private ScriptingLogicsModule priceCheckerLM;
    private ScriptingLogicsModule purchaseInvoiceAgreementLM;
    private ScriptingLogicsModule scalesLM;
    private ScriptingLogicsModule scalesItemLM;
    private ScriptingLogicsModule stopListLM;
    private ScriptingLogicsModule terminalLM;
    private ScriptingLogicsModule zReportLM;
    private ScriptingLogicsModule zReportDiscountCardLM;
    private ScriptingLogicsModule zReportSectionLM;
    
    private boolean started = false;

    private int skipTroubleCounter = 1;

    public void setLogicsInstance(LogicsInstance logicsInstance) {
        this.logicsInstance = logicsInstance;
    }

    public LogicsInstance getLogicsInstance() {
        return logicsInstance;
    }

    public void setSoftCheckHandler(SoftCheckInterface softCheck) {
        this.softCheck = softCheck;
    }

    public SoftCheckInterface getSoftCheckHandler() {
        return softCheck;
    }

    public void setPromotionHandler(PromotionInterface promotionInterface) {
        this.promotionInterface = promotionInterface;
    }   
    
    public RMIManager getRmiManager() {
        return logicsInstance.getRmiManager();
    }

    public BusinessLogics getBusinessLogics() {
        return logicsInstance.getBusinessLogics();
    }

    public DBManager getDbManager() {
        return logicsInstance.getDbManager();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
    }

    @Override
    protected void onInit(LifecycleEvent event) {
        equLM = getBusinessLogics().getModule("Equipment");
        Assert.notNull(equLM, "can't find Equipment module");
        cashRegisterLM = getBusinessLogics().getModule("EquipmentCashRegister");
        cashRegisterItemLM = getBusinessLogics().getModule("CashRegisterItem");
        collectionLM = getBusinessLogics().getModule("Collection");
        discountCardLM = getBusinessLogics().getModule("DiscountCard");
        equipmentLM = getBusinessLogics().getModule("Equipment");
        equipmentCashRegisterLM = getBusinessLogics().getModule("EquipmentCashRegister");
        giftCardLM = getBusinessLogics().getModule("GiftCard");
        itemLM = getBusinessLogics().getModule("Item");
        itemFashionLM = getBusinessLogics().getModule("ItemFashion");
        machineryLM = getBusinessLogics().getModule("Machinery");
        machineryPriceTransactionLM = getBusinessLogics().getModule("MachineryPriceTransaction");
        machineryPriceTransactionSectionLM = getBusinessLogics().getModule("MachineryPriceTransactionSection");
        machineryPriceTransactionBalanceLM = getBusinessLogics().getModule("MachineryPriceTransactionBalance");
        machineryPriceTransactionStockTaxLM = getBusinessLogics().getModule("MachineryPriceTransactionStockTax");
        priceCheckerLM = getBusinessLogics().getModule("EquipmentPriceChecker");
        purchaseInvoiceAgreementLM = getBusinessLogics().getModule("PurchaseInvoiceAgreement");
        scalesLM = getBusinessLogics().getModule("EquipmentScales");
        scalesItemLM = getBusinessLogics().getModule("ScalesItem");
        stopListLM = getBusinessLogics().getModule("StopList");
        terminalLM = getBusinessLogics().getModule("EquipmentTerminal");
        zReportLM = getBusinessLogics().getModule("ZReport");
        zReportDiscountCardLM = getBusinessLogics().getModule("ZReportDiscountCard");
        zReportSectionLM = getBusinessLogics().getModule("ZReportSection");
    }

    @Override
    protected void onStarted(LifecycleEvent event) {
        if(getDbManager().isServer()) {
            logger.info("Binding Equipment Server.");
            try {
                getRmiManager().bindAndExport(EXPORT_NAME, this);
                started = true;
            } catch (Exception e) {
                throw new RuntimeException("Error exporting Equipment Server: ", e);
            }
        } else {
            logger.info("Equipment Server disabled, change serverComputer() to enable");
        }
    }

    @Override
    protected void onStopping(LifecycleEvent event) {
        if (started) {
            logger.info("Stopping Equipment Server.");
            try {
                getRmiManager().unbindAndUnexport(EXPORT_NAME, this);
            } catch (Exception e) {
                throw new RuntimeException("Error stopping Equipment Server: ", e);
            }
        }
    }

    public EquipmentServer() {
        super(HIGH_DAEMON_ORDER);
    }

    @Override
    public boolean enabledSoftCheckInfo() throws RemoteException, SQLException {
        return softCheck != null;
    }

    @Override
    public List<SoftCheckInfo> readSoftCheckInfo() throws RemoteException, SQLException {
        return softCheck == null ? null : softCheck.readSoftCheckInfo();
    }

    @Override
    public void finishSoftCheckInfo(Map<String, SoftCheckInvoice> invoiceMap) throws RemoteException, SQLException {
        if(softCheck != null)
            softCheck.finishSoftCheckInfo(invoiceMap);
    }

    @Override
    public String sendSucceededSoftCheckInfo(Map<String, Timestamp> invoiceSet) throws RemoteException, SQLException {
        return softCheck == null ? null : softCheck.sendSucceededSoftCheckInfo(invoiceSet);
    }

    @Override
    public String sendCashierTimeList(List<CashierTime> cashierTimeList) throws RemoteException, SQLException {
        return softCheck == null ? null : softCheck.sendCashierTimeList(cashierTimeList);
    }

    @Override
    public List<TransactionInfo> readTransactionInfo(String sidEquipmentServer) throws RemoteException, SQLException {
        try (DataSession session = getDbManager().createSession()) {
            List<TransactionInfo> transactionList = new ArrayList<>();

            KeyExpr machineryPriceTransactionExpr = new KeyExpr("machineryPriceTransaction");
            ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "machineryPriceTransaction", machineryPriceTransactionExpr);
            QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);

            String[] mptNames = new String[]{"dateTimeMachineryPriceTransaction", "groupMachineryMachineryPriceTransaction",
                    "nppGroupMachineryMachineryPriceTransaction", "nameGroupMachineryMachineryPriceTransaction", "snapshotMachineryPriceTransaction",
                    "descriptionMachineryPriceTransaction", "lastDateMachineryPriceTransactionErrorMachineryPriceTransaction"};
            LCP[] mptProperties = equLM.findProperties("dateTime[MachineryPriceTransaction]", "groupMachinery[MachineryPriceTransaction]",
                    "nppGroupMachinery[MachineryPriceTransaction]", "nameGroupMachinery[MachineryPriceTransaction]", "snapshot[MachineryPriceTransaction]",
                    "description[MachineryPriceTransaction]", "lastDateMachineryPriceTransactionError[MachineryPriceTransaction]");
            for (int i = 0; i < mptProperties.length; i++) {
                query.addProperty(mptNames[i], mptProperties[i].getExpr(machineryPriceTransactionExpr));
            }
            query.and(equLM.findProperty("sidEquipmentServer[MachineryPriceTransaction]").getExpr(machineryPriceTransactionExpr).compare(new DataObject(sidEquipmentServer), Compare.EQUALS));
            query.and(equLM.findProperty("process[MachineryPriceTransaction]").getExpr(machineryPriceTransactionExpr).getWhere());

            ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> result = query.executeClasses(session);
            List<Object[]> transactionObjects = new ArrayList<>();
            for (int i = 0, size = result.size(); i < size; i++) {
                ImMap<Object, ObjectValue> value = result.getValue(i);
                ObjectValue dateTimeMPT = value.get("dateTimeMachineryPriceTransaction");
                if(dateTimeMPT instanceof DataObject) {
                    DataObject groupMachineryMPT = (DataObject) value.get("groupMachineryMachineryPriceTransaction");
                    Integer nppGroupMachineryMPT = (Integer) value.get("nppGroupMachineryMachineryPriceTransaction").getValue();
                    String nameGroupMachineryMPT = (String) value.get("nameGroupMachineryMachineryPriceTransaction").getValue();
                    DataObject transactionObject = result.getKey(i).singleValue();
                    boolean snapshotMPT = value.get("snapshotMachineryPriceTransaction") instanceof DataObject;
                    String descriptionMPT = (String) value.get("descriptionMachineryPriceTransaction").getValue();
                    Timestamp lastErrorDate = (Timestamp) value.get("lastDateMachineryPriceTransactionErrorMachineryPriceTransaction").getValue();
                    transactionObjects.add(new Object[]{groupMachineryMPT, nppGroupMachineryMPT, nameGroupMachineryMPT, transactionObject,
                            dateTimeCode((Timestamp) dateTimeMPT.getValue()), dateTimeMPT, snapshotMPT, descriptionMPT, lastErrorDate});
                }
            }

            Map<String, List<ItemGroup>> itemGroupMap = transactionObjects.isEmpty() ? null : readItemGroupMap(session);
            ObjectValue equipmentServerObject = equipmentLM.findProperty("sidTo[VARSTRING[20]]").readClasses(session, new DataObject(sidEquipmentServer));
            Integer minutesTroubleMachineryGroups = (Integer) equipmentLM.findProperty("minutesTroubleMachineryGroup[EquipmentServer]").read(session, equipmentServerObject);
            Integer skipTroubles = (Integer) equipmentLM.findProperty("skipTroublesDelay[EquipmentServer]").read(session, equipmentServerObject);
            List<Integer> troubleMachineryGroups = readTroubleMachineryGroups(session, minutesTroubleMachineryGroups);
            boolean skipTroubleMachineryGroups = skipTroubles != null && skipTroubleCounter < skipTroubles;
            if (skipTroubleMachineryGroups)
                skipTroubleCounter++;
            else
                skipTroubleCounter = 1;

            for (Object[] transaction : transactionObjects) {

                DataObject groupMachineryObject = (DataObject) transaction[0];
                Integer nppGroupMachinery = (Integer) transaction[1];
                if(troubleMachineryGroups.contains(nppGroupMachinery) && skipTroubleMachineryGroups)
                    continue;

                String nameGroupMachinery = (String) transaction[2];
                DataObject transactionObject = (DataObject) transaction[3];
                String dateTimeCode = (String) transaction[4];
                Date date = new Date(((Timestamp) ((DataObject) transaction[5]).getValue()).getTime());
                boolean snapshotTransaction = (boolean) transaction[6];
                String descriptionTransaction = (String) transaction[7];
                Timestamp lastErrorDateTransaction = (Timestamp) transaction[8];

                boolean isCashRegisterPriceTransaction = cashRegisterLM != null && transactionObject.objectClass.equals(cashRegisterLM.findClass("CashRegisterPriceTransaction"));
                boolean isScalesPriceTransaction = scalesLM != null && transactionObject.objectClass.equals(scalesLM.findClass("ScalesPriceTransaction"));
                boolean isPriceCheckerPriceTransaction = priceCheckerLM != null && transactionObject.objectClass.equals(priceCheckerLM.findClass("PriceCheckerPriceTransaction"));
                boolean isTerminalPriceTransaction = terminalLM != null && transactionObject.objectClass.equals(terminalLM.findClass("TerminalPriceTransaction"));
                
                String handlerModelGroupMachinery = (String) equLM.findProperty("handlerModel[GroupMachinery]").read(session, groupMachineryObject);
                String nameModelGroupMachinery = (String) equLM.findProperty("nameModel[GroupMachinery]").read(session, groupMachineryObject);
                String denominationStageGroupMachinery = (String) cashRegisterLM.findProperty("nameDenominationStage[GroupMachinery]").read(session, groupMachineryObject);

                ValueExpr transactionExpr = transactionObject.getExpr();
                KeyExpr barcodeExpr = new KeyExpr("barcode");
                ImRevMap<Object, KeyExpr> skuKeys = MapFact.singletonRev((Object) "barcode", barcodeExpr);

                QueryBuilder<Object, Object> skuQuery = new QueryBuilder<>(skuKeys);

                String[] skuNames = new String[]{"nameMachineryPriceTransactionBarcode", "priceMachineryPriceTransactionBarcode",
                        "expiryDateMachineryPriceTransactionBarcode", "splitMachineryPriceTransactionBarcode", "passScalesMachineryPriceTransactionBarcode",
                        "idUOMMachineryPriceTransactionBarcode", "shortNameUOMMachineryPriceTransactionBarcode", "pluNumberMachineryPriceTransactionBarcode",
                        "flagsMachineryPriceTransactionBarcode", "expiryDaysMachineryPriceTransactionBarcode", "minPriceMachineryPriceTransactionBarcode",
                        "canonicalNameSkuGroupMachineryPriceTransactionBarcode"};
                LCP[] skuProperties = equLM.findProperties("name[MachineryPriceTransaction,Barcode]", "price[MachineryPriceTransaction,Barcode]",
                        "expiryDate[MachineryPriceTransaction,Barcode]", "split[MachineryPriceTransaction,Barcode]", "passScales[MachineryPriceTransaction,Barcode]",
                        "idUOM[MachineryPriceTransaction,Barcode]", "shortNameUOM[MachineryPriceTransaction,Barcode]", "pluNumber[MachineryPriceTransaction,Barcode]",
                        "flags[MachineryPriceTransaction,Barcode]", "expiryDays[MachineryPriceTransaction,Barcode]", "minPrice[MachineryPriceTransaction,Barcode]",
                        "canonicalNameSkuGroup[MachineryPriceTransaction,Barcode]");
                for (int i = 0; i < skuProperties.length; i++) {
                    skuQuery.addProperty(skuNames[i], skuProperties[i].getExpr(transactionExpr, barcodeExpr));
                }

                String[] barcodeNames = new String[]{"idBarcode", "skuBarcode", "idSkuBarcode", "skuGroupBarcode"};
                LCP[] barcodeProperties = equLM.findProperties("id[Barcode]", "sku[Barcode]", "idSku[Barcode]", "skuGroup[Barcode]");
                for (int i = 0; i < barcodeProperties.length; i++) {
                    skuQuery.addProperty(barcodeNames[i], barcodeProperties[i].getExpr(barcodeExpr));
                }

                if (isCashRegisterPriceTransaction) {
                    skuQuery.addProperty("amountBarcode", equLM.findProperty("amount[Barcode]").getExpr(barcodeExpr));
                }
                
                if(itemLM != null) {
                    skuQuery.addProperty("idBrandBarcode", itemLM.findProperty("idBrand[Barcode]").getExpr(barcodeExpr));
                    skuQuery.addProperty("nameBrandBarcode", itemLM.findProperty("nameBrand[Barcode]").getExpr(barcodeExpr));
                }
                
                if(itemFashionLM != null) {
                    skuQuery.addProperty("idSeasonBarcode", itemFashionLM.findProperty("idSeason[Barcode]").getExpr(barcodeExpr));
                    skuQuery.addProperty("nameSeasonBarcode", itemFashionLM.findProperty("nameSeason[Barcode]").getExpr(barcodeExpr));
                }

                if(cashRegisterItemLM != null) {
                    skuQuery.addProperty("CashRegisterItem.idSkuGroupMachineryPriceTransactionBarcode", 
                            cashRegisterItemLM.findProperty("idSkuGroup[MachineryPriceTransaction,Barcode]").getExpr(transactionExpr, barcodeExpr));
                    skuQuery.addProperty("CashRegisterItem.overIdSkuGroupMachineryPriceTransactionBarcode",
                            cashRegisterItemLM.findProperty("overIdSkuGroup[MachineryPriceTransaction,Barcode]").getExpr(transactionExpr, barcodeExpr));
                }
                
                if(scalesItemLM != null) {
                    skuQuery.addProperty("ScalesItem.idSkuGroupMachineryPriceTransactionBarcode", 
                            scalesItemLM.findProperty("idSkuGroup[MachineryPriceTransaction,Barcode]").getExpr(transactionExpr, barcodeExpr));
                }
                
                if (scalesItemLM != null) {
                    String[] scalesSkuNames = new String[]{"hoursExpiryMachineryPriceTransactionBarcode",
                            "labelFormatMachineryPriceTransactionBarcode", "descriptionMachineryPriceTransactionBarcode", "descriptionNumberMachineryPriceTransactionBarcode"};
                    LCP[] scalesSkuProperties = scalesItemLM.findProperties("hoursExpiry[MachineryPriceTransaction,Barcode]",
                            "labelFormat[MachineryPriceTransaction,Barcode]", "description[MachineryPriceTransaction,Barcode]", "descriptionNumber[MachineryPriceTransaction,Barcode]");
                    for (int i = 0; i < scalesSkuProperties.length; i++) {
                        skuQuery.addProperty(scalesSkuNames[i], scalesSkuProperties[i].getExpr(transactionExpr, barcodeExpr));
                    }
                    skuQuery.addProperty("extraPercent", scalesItemLM.findProperty("extraPercent[MachineryPriceTransaction, Barcode]").getExpr(transactionExpr, barcodeExpr));
                }
                
                if (machineryPriceTransactionStockTaxLM != null) {
                    String[] taxNames = new String[]{"VATMachineryPriceTransactionBarcode"};
                    LCP[] taxProperties = machineryPriceTransactionStockTaxLM.findProperties("VAT[MachineryPriceTransaction,Barcode]");
                    for (int i = 0; i < taxProperties.length; i++) {
                        skuQuery.addProperty(taxNames[i], taxProperties[i].getExpr(transactionExpr, barcodeExpr));
                    }
                }

                if(machineryPriceTransactionSectionLM != null) {
                    skuQuery.addProperty("sectionMachineryPriceTransactionBarcode",
                            machineryPriceTransactionSectionLM.findProperty("section[MachineryPriceTransaction,Barcode]").getExpr(transactionExpr, barcodeExpr));
                    skuQuery.addProperty("deleteSectionBarcode",
                            machineryPriceTransactionSectionLM.findProperty("deleteSectionBarcode[?]").getExpr(barcodeExpr));
                }

                if(machineryPriceTransactionBalanceLM != null) {
                    skuQuery.addProperty("balanceMachineryPriceTransactionBarcode",
                            machineryPriceTransactionBalanceLM.findProperty("balance[MachineryPriceTransaction,Barcode]").getExpr(transactionExpr, barcodeExpr));
                }

                skuQuery.and(equLM.findProperty("in[MachineryPriceTransaction,Barcode]").getExpr(transactionExpr, barcodeExpr).getWhere());

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> skuResult = skuQuery.execute(session);

                String[] machineryNames = new String[]{"nppMachinery", "portMachinery", "overDirectoryMachinery"};
                LCP[] machineryProperties = machineryLM.findProperties("npp[Machinery]", "port[Machinery]", "overDirectory[Machinery]");
                
                if (isCashRegisterPriceTransaction) {
                    
                    java.sql.Date startDateGroupCashRegister = (java.sql.Date) cashRegisterLM.findProperty("startDate[GroupCashRegister]").read(session, groupMachineryObject);
                    boolean notDetailedGroupCashRegister = cashRegisterLM.findProperty("notDetailed[GroupCashRegister]").read(session, groupMachineryObject) != null;
                    Integer overDepartmentNumberGroupCashRegister = (Integer) cashRegisterLM.findProperty("overDepartmentNumberCashRegister[GroupMachinery]").read(session, groupMachineryObject);
                    String idDepartmentStoreGroupCashRegister = (String) cashRegisterLM.findProperty("idDepartmentStore[GroupCashRegister]").read(session, groupMachineryObject);
                    String pieceCodeGroupCashRegister = (String) cashRegisterLM.findProperty("pieceCode[GroupCashRegister]").read(session, groupMachineryObject);
                    String weightCodeGroupCashRegister = (String) cashRegisterLM.findProperty("weightCode[GroupCashRegister]").read(session, groupMachineryObject);
                    String nameStockGroupCashRegister = (String) cashRegisterLM.findProperty("nameStock[GroupMachinery]").read(session, groupMachineryObject);
                    String sectionGroupCashRegister = (String) cashRegisterLM.findProperty("section[GroupMachinery]").read(session, groupMachineryObject);

                    List<CashRegisterInfo> cashRegisterInfoList = new ArrayList<>();
                    KeyExpr cashRegisterExpr = new KeyExpr("cashRegister");
                    ImRevMap<Object, KeyExpr> cashRegisterKeys = MapFact.singletonRev((Object) "cashRegister", cashRegisterExpr);
                    QueryBuilder<Object, Object> cashRegisterQuery = new QueryBuilder<>(cashRegisterKeys);
                    
                    for (int i = 0; i < machineryProperties.length; i++) {
                        cashRegisterQuery.addProperty(machineryNames[i], machineryProperties[i].getExpr(cashRegisterExpr));
                    }
                    cashRegisterQuery.addProperty("disableSalesCashRegister", cashRegisterLM.findProperty("disableSales[CashRegister]").getExpr(cashRegisterExpr));
                    cashRegisterQuery.addProperty("succeededMachineryMachineryPriceTransaction",
                            cashRegisterLM.findProperty("succeeded[Machinery,MachineryPriceTransaction]").getExpr(cashRegisterExpr, transactionExpr));
                    cashRegisterQuery.addProperty("clearedMachineryMachineryPriceTransaction",
                            cashRegisterLM.findProperty("cleared[Machinery,MachineryPriceTransaction]").getExpr(cashRegisterExpr, transactionExpr));
                    cashRegisterQuery.addProperty("inMachineryPriceTransactionMachinery",
                            equLM.findProperty("in[MachineryPriceTransaction,Machinery]").getExpr(transactionExpr, cashRegisterExpr));
                    cashRegisterQuery.and(cashRegisterLM.findProperty("groupCashRegister[CashRegister]").getExpr(cashRegisterExpr).compare(groupMachineryObject, Compare.EQUALS));
                    query.and(cashRegisterLM.findProperty("active[CashRegister]").getExpr(cashRegisterExpr).getWhere());

                    ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> cashRegisterResult = cashRegisterQuery.execute(session);

                    for (ImMap<Object, Object> row : cashRegisterResult.valueIt()) {
                        Integer nppMachinery = (Integer) row.get("nppMachinery");
                        String portMachinery = (String) row.get("portMachinery");
                        String directoryCashRegister = (String) row.get("overDirectoryMachinery");
                        boolean succeeded = row.get("succeededMachineryMachineryPriceTransaction") != null;
                        boolean cleared = row.get("clearedMachineryPriceTransaction") != null;
                        Boolean disableSalesCashRegister = row.get("disableSalesCashRegister") != null;
                        boolean enabled = row.get("inMachineryPriceTransactionMachinery") != null;
                        CashRegisterInfo c = new CashRegisterInfo(enabled, cleared, succeeded, nppGroupMachinery, nppMachinery,
                                nameModelGroupMachinery, handlerModelGroupMachinery, portMachinery, directoryCashRegister,
                                /*denominationStageGroupMachinery, */startDateGroupCashRegister, overDepartmentNumberGroupCashRegister, idDepartmentStoreGroupCashRegister, notDetailedGroupCashRegister,
                                disableSalesCashRegister, pieceCodeGroupCashRegister, weightCodeGroupCashRegister);
                        //todo: idStock переименовать в section и добавить в конструктор
                        c.idStock = sectionGroupCashRegister;
                        cashRegisterInfoList.add(c);
                    }

                    List<CashRegisterItemInfo> cashRegisterItemInfoList = new ArrayList<>();

                    for (ImMap<Object, Object> row : skuResult.valueIt()) {
                        String barcode = getRowValue(row, "idBarcode");
                        BigDecimal amountBarcode = (BigDecimal) row.get("amountBarcode");
                        String name = getRowValue(row, "nameMachineryPriceTransactionBarcode");
                        BigDecimal price = (BigDecimal) row.get("priceMachineryPriceTransactionBarcode");
                        boolean split = row.get("splitMachineryPriceTransactionBarcode") != null;
                        Integer daysExpiry = (Integer) row.get("expiryDaysMachineryPriceTransactionBarcode");
                        Date expiryDate = (Date) row.get("expiryDateMachineryPriceTransactionBarcode");
                        Integer flags = (Integer) row.get("flagsMachineryPriceTransactionBarcode");
                        boolean passScales = row.get("passScalesMachineryPriceTransactionBarcode") != null;
                        String idUOM = (String) row.get("idUOMMachineryPriceTransactionBarcode");
                        String shortNameUOM = (String) row.get("shortNameUOMMachineryPriceTransactionBarcode");
                        String idBrand = itemLM == null ? null : (String) row.get("idBrandBarcode");
                        String nameBrand = itemLM == null ? null : (String) row.get("nameBrandBarcode");
                        String idSeason = itemFashionLM == null ? null : (String) row.get("idSeasonBarcode");
                        String nameSeason = itemFashionLM == null ? null : (String) row.get("nameSeasonBarcode");
                        BigDecimal valueVAT = machineryPriceTransactionStockTaxLM == null ? null : (BigDecimal) row.get("VATMachineryPriceTransactionBarcode");
                        String idItem = (String) row.get("idSkuBarcode");
                        Integer itemGroupObject = (Integer) row.get("skuGroupBarcode");
                        Integer pluNumber = (Integer) row.get("pluNumberMachineryPriceTransactionBarcode");
                        String description = scalesItemLM == null ? null : (String) row.get("descriptionMachineryPriceTransactionBarcode");

                        String idItemGroup = cashRegisterItemLM == null ? null : (String) row.get("CashRegisterItem.idSkuGroupMachineryPriceTransactionBarcode");
                        String overIdItemGroup = cashRegisterItemLM == null ? null : (String) row.get("CashRegisterItem.overIdSkuGroupMachineryPriceTransactionBarcode");
                        String canonicalNameSkuGroup = (String) row.get("canonicalNameSkuGroupMachineryPriceTransactionBarcode");
                        String section = machineryPriceTransactionSectionLM == null ? null : (String) row.get("sectionMachineryPriceTransactionBarcode");
                        String deleteSection = machineryPriceTransactionSectionLM == null ? null : (String) row.get("deleteSectionBarcode");
                        BigDecimal balance = machineryPriceTransactionBalanceLM == null ? null : (BigDecimal) row.get("balanceMachineryPriceTransactionBarcode");
                        BigDecimal minPrice = (BigDecimal) row.get("minPriceMachineryPriceTransactionBarcode");

                        CashRegisterItemInfo c = new CashRegisterItemInfo(idItem, barcode, name, price, split, daysExpiry, expiryDate, passScales, valueVAT,
                                pluNumber, flags, idItemGroup, canonicalNameSkuGroup, idUOM, shortNameUOM, itemGroupObject, description, idBrand, nameBrand, idSeason,
                                nameSeason, idDepartmentStoreGroupCashRegister, section, deleteSection, minPrice, overIdItemGroup, amountBarcode);
                        c.balance = balance;
                        cashRegisterItemInfoList.add(c);
                    }
                    
                    transactionList.add(new TransactionCashRegisterInfo((Integer) transactionObject.getValue(), dateTimeCode, 
                            date, handlerModelGroupMachinery, (Integer) groupMachineryObject.object, nppGroupMachinery,
                            nameGroupMachinery, descriptionTransaction, itemGroupMap, cashRegisterItemInfoList,
                            cashRegisterInfoList, snapshotTransaction, lastErrorDateTransaction, /*denominationStageGroupMachinery, */overDepartmentNumberGroupCashRegister, weightCodeGroupCashRegister, nameStockGroupCashRegister));

                } else if (isScalesPriceTransaction) {
                    List<ScalesInfo> scalesInfoList = new ArrayList<>();
                    String directory = (String) scalesLM.findProperty("directory[GroupScales]").read(session, groupMachineryObject);
                    String pieceCodeGroupScales = (String) scalesLM.findProperty("pieceCode[GroupScales]").read(session, groupMachineryObject);
                    String weightCodeGroupScales = (String) scalesLM.findProperty("weightCode[GroupScales]").read(session, groupMachineryObject);

                    KeyExpr scalesExpr = new KeyExpr("scales");
                    ImRevMap<Object, KeyExpr> scalesKeys = MapFact.singletonRev((Object) "scales", scalesExpr);
                    QueryBuilder<Object, Object> scalesQuery = new QueryBuilder<>(scalesKeys);
                    
                    for (int i = 0; i < machineryProperties.length; i++) {
                        scalesQuery.addProperty(machineryNames[i], machineryProperties[i].getExpr(scalesExpr));
                    }
                    scalesQuery.addProperty("inMachineryPriceTransactionMachinery", 
                            scalesLM.findProperty("in[MachineryPriceTransaction,Machinery]").getExpr(transactionExpr, scalesExpr));
                    scalesQuery.addProperty("succeededMachineryMachineryPriceTransaction",
                                scalesLM.findProperty("succeeded[Machinery,MachineryPriceTransaction]").getExpr(scalesExpr, transactionExpr));
                    scalesQuery.addProperty("clearedMachineryMachineryPriceTransaction",
                            scalesLM.findProperty("cleared[Machinery,MachineryPriceTransaction]").getExpr(scalesExpr, transactionExpr));
                    scalesQuery.and(scalesLM.findProperty("groupScales[Scales]").getExpr(scalesExpr).compare(groupMachineryObject, Compare.EQUALS));
                    scalesQuery.and(scalesLM.findProperty("active[Scales]").getExpr(scalesExpr).getWhere());

                    ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> scalesResult = scalesQuery.execute(session);

                    for (ImMap<Object, Object> values : scalesResult.valueIt()) {
                        String portMachinery = (String) values.get("portMachinery");
                        Integer nppMachinery = (Integer) values.get("nppMachinery");
                        boolean enabled = values.get("inMachineryPriceTransactionMachinery") != null;
                        boolean succeeded = values.get("succeededMachineryMachineryPriceTransaction") != null;
                        boolean cleared = values.get("clearedMachineryMachineryPriceTransaction") != null;
                        scalesInfoList.add(new ScalesInfo(enabled, cleared, succeeded, nppGroupMachinery, nppMachinery,
                                nameModelGroupMachinery, handlerModelGroupMachinery, portMachinery, directory, denominationStageGroupMachinery,
                                pieceCodeGroupScales, weightCodeGroupScales));
                    }

                    List<ScalesItemInfo> scalesItemInfoList = new ArrayList<>();
                    
                    for (ImMap<Object, Object> row : skuResult.valueIt()) {
                        String idItem = getRowValue(row, "idSkuBarcode");
                        String barcode = getRowValue(row, "idBarcode");
                        String name = getRowValue(row, "nameMachineryPriceTransactionBarcode");
                        BigDecimal price = (BigDecimal) row.get("priceMachineryPriceTransactionBarcode");
                        Integer pluNumber = (Integer) row.get("pluNumberMachineryPriceTransactionBarcode");
                        Integer flags = (Integer) row.get("flagsMachineryPriceTransactionBarcode");
                        Date expiryDate = (Date) row.get("expiryDateMachineryPriceTransactionBarcode");
                        boolean split = row.get("splitMachineryPriceTransactionBarcode") != null;
                        Integer daysExpiry = (Integer) row.get("expiryDaysMachineryPriceTransactionBarcode");
                        Integer hoursExpiry = (Integer) row.get("hoursExpiryMachineryPriceTransactionBarcode");
                        Integer labelFormat = (Integer) row.get("labelFormatMachineryPriceTransactionBarcode");
                        String description = (String) row.get("descriptionMachineryPriceTransactionBarcode");
                        Integer descriptionNumberCellScales = (Integer) row.get("descriptionNumberMachineryPriceTransactionBarcode");
                        boolean passScales = row.get("passScalesMachineryPriceTransactionBarcode") != null;
                        BigDecimal valueVAT = machineryPriceTransactionStockTaxLM == null ? null : (BigDecimal) row.get("VATMachineryPriceTransactionBarcode");
                        String idUOM = (String) row.get("idUOMMachineryPriceTransactionBarcode");
                        String shortNameUOM = (String) row.get("shortNameUOMMachineryPriceTransactionBarcode");
                        
                        String idItemGroup = scalesItemLM == null ? null : (String) row.get("ScalesItem.idSkuGroupMachineryPriceTransactionBarcode");
                        String canonicalNameSkuGroup = (String) row.get("canonicalNameSkuGroupMachineryPriceTransactionBarcode");
                        BigDecimal extraPercent = scalesItemLM == null ? null : (BigDecimal) row.get("extraPercent");

                        scalesItemInfoList.add(new ScalesItemInfo(idItem, barcode, name, price, split, daysExpiry, expiryDate, 
                                passScales, valueVAT, pluNumber, flags, idItemGroup, canonicalNameSkuGroup, hoursExpiry,
                                labelFormat, description, descriptionNumberCellScales, idUOM, shortNameUOM, extraPercent));
                    }

                    transactionList.add(new TransactionScalesInfo((Integer) transactionObject.getValue(), dateTimeCode, 
                            date, handlerModelGroupMachinery, (Integer) groupMachineryObject.object, nppGroupMachinery,
                            nameGroupMachinery, descriptionTransaction, scalesItemInfoList, scalesInfoList, snapshotTransaction,
                            lastErrorDateTransaction, denominationStageGroupMachinery));

                } else if (isPriceCheckerPriceTransaction) {
                    List<PriceCheckerInfo> priceCheckerInfoList = new ArrayList<>();
                    KeyExpr priceCheckerExpr = new KeyExpr("priceChecker");
                    ImRevMap<Object, KeyExpr> priceCheckerKeys = MapFact.singletonRev((Object) "priceChecker", priceCheckerExpr);
                    QueryBuilder<Object, Object> priceCheckerQuery = new QueryBuilder<>(priceCheckerKeys);
                    
                    for (int i = 0; i < machineryProperties.length; i++) {
                        priceCheckerQuery.addProperty(machineryNames[i], machineryProperties[i].getExpr(priceCheckerExpr));
                    }
                    priceCheckerQuery.addProperty("inMachineryPriceTransactionMachinery",
                            priceCheckerLM.findProperty("in[MachineryPriceTransaction,Machinery]").getExpr(transactionExpr, priceCheckerExpr));
                    priceCheckerQuery.addProperty("succeededMachineryMachineryPriceTransaction",
                            priceCheckerLM.findProperty("succeeded[Machinery,MachineryPriceTransaction]").getExpr(priceCheckerExpr, transactionExpr));
                    priceCheckerQuery.addProperty("clearedMachineryMachineryPriceTransaction",
                            priceCheckerLM.findProperty("cleared[Machinery,MachineryPriceTransaction]").getExpr(priceCheckerExpr, transactionExpr));
                    priceCheckerQuery.and(priceCheckerLM.findProperty("groupPriceChecker[PriceChecker]").getExpr(priceCheckerExpr).compare(groupMachineryObject, Compare.EQUALS));

                    ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> priceCheckerResult = priceCheckerQuery.execute(session);

                    for (ImMap<Object, Object> row : priceCheckerResult.valueIt()) {
                        boolean enabled = row.get("inMachineryPriceTransactionMachinery") != null;
                        Boolean succeeded = row.get("succeededMachineryMachineryPriceTransaction") != null;
                        Boolean cleared = row.get("clearedMachineryMachineryPriceTransaction") != null;
                        priceCheckerInfoList.add(new PriceCheckerInfo(enabled, cleared, succeeded, nppGroupMachinery, (Integer) row.get("nppMachinery"),
                                nameModelGroupMachinery, handlerModelGroupMachinery, (String) row.get("portMachinery")/*, denominationStageGroupMachinery*/));
                    }

                    List<PriceCheckerItemInfo> priceCheckerItemInfoList = new ArrayList<>();
                    for (ImMap<Object, Object> row : skuResult.valueIt()) {
                        String idItem = getRowValue(row, "idSkuBarcode");
                        String barcode = getRowValue(row, "idBarcode");
                        String name = getRowValue(row, "nameMachineryPriceTransactionBarcode");
                        BigDecimal price = (BigDecimal) row.get("priceMachineryPriceTransactionBarcode");
                        boolean split = row.get("splitMachineryPriceTransactionBarcode") != null;
                        Integer daysExpiry = (Integer) row.get("expiryDaysMachineryPriceTransactionBarcode");
                        Date expiryDate = (Date) row.get("expiryDateMachineryPriceTransactionBarcode");
                        boolean passScales = row.get("passScalesMachineryPriceTransactionBarcode") != null;
                        BigDecimal valueVAT = machineryPriceTransactionStockTaxLM == null ? null : (BigDecimal) row.get("VATMachineryPriceTransactionBarcode");
                        Integer pluNumber = (Integer) row.get("pluNumberMachineryPriceTransactionBarcode");
                        Integer flags = (Integer) row.get("flagsMachineryPriceTransactionBarcode");
                        
                        priceCheckerItemInfoList.add(new PriceCheckerItemInfo(idItem, barcode, name, price, split, 
                                daysExpiry, expiryDate, passScales, valueVAT, pluNumber, flags, null, null));
                    }
                    
                    transactionList.add(new TransactionPriceCheckerInfo((Integer) transactionObject.getValue(), dateTimeCode,
                            date, handlerModelGroupMachinery, (Integer) groupMachineryObject.object, nppGroupMachinery,
                            nameGroupMachinery, descriptionTransaction, priceCheckerItemInfoList, priceCheckerInfoList,
                            snapshotTransaction, lastErrorDateTransaction/*, denominationStageGroupMachinery*/));


                } else if (isTerminalPriceTransaction) {
                    List<TerminalInfo> terminalInfoList = new ArrayList<>();
                    
                    Integer nppGroupTerminal = (Integer) terminalLM.findProperty("npp[GroupMachinery]").read(session, groupMachineryObject);
                    String directoryGroupTerminal = (String) terminalLM.findProperty("directory[GroupTerminal]").read(session, groupMachineryObject);
                    ObjectValue priceListTypeGroupMachinery = terminalLM.findProperty("priceListType[GroupMachinery]").readClasses(session, groupMachineryObject);
                    ObjectValue stockGroupTerminal = terminalLM.findProperty("stock[GroupTerminal]").readClasses(session, groupMachineryObject);
                    String idPriceListType = (String) terminalLM.findProperty("id[PriceListType]").read(session, priceListTypeGroupMachinery);

                    KeyExpr terminalExpr = new KeyExpr("terminal");
                    ImRevMap<Object, KeyExpr> terminalKeys = MapFact.singletonRev((Object) "terminal", terminalExpr);
                    QueryBuilder<Object, Object> terminalQuery = new QueryBuilder<>(terminalKeys);
                    
                    for (int i = 0; i < machineryProperties.length; i++) {
                        terminalQuery.addProperty(machineryNames[i], machineryProperties[i].getExpr(terminalExpr));
                    }
                    terminalQuery.addProperty("inMachineryPriceTransactionMachinery",
                            terminalLM.findProperty("in[MachineryPriceTransaction,Machinery]").getExpr(transactionExpr, terminalExpr));
                    terminalQuery.addProperty("succeededMachineryMachineryPriceTransaction",
                            terminalLM.findProperty("succeeded[Machinery,MachineryPriceTransaction]").getExpr(terminalExpr, transactionExpr));
                    terminalQuery.addProperty("clearedMachineryMachineryPriceTransaction",
                            terminalLM.findProperty("cleared[Machinery,MachineryPriceTransaction]").getExpr(terminalExpr, transactionExpr));
                    terminalQuery.and(terminalLM.findProperty("groupTerminal[Terminal]").getExpr(terminalExpr).compare(groupMachineryObject, Compare.EQUALS));

                    ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> terminalResult = terminalQuery.execute(session);

                    for (ImMap<Object, Object> row : terminalResult.valueIt()) {
                        boolean enabled = row.get("inMachineryPriceTransactionMachinery") != null;
                        Boolean succeeded = row.get("succeededMachineryMachineryPriceTransaction") != null;
                        Boolean cleared = row.get("clearedMachineryMachineryPriceTransaction") != null;
                        terminalInfoList.add(new TerminalInfo(enabled, cleared, succeeded, nppGroupMachinery, (Integer) row.get("nppMachinery"),
                                nameModelGroupMachinery, handlerModelGroupMachinery, getRowValue(row, "portMachinery"),
                                directoryGroupTerminal, /*denominationStageGroupMachinery, */idPriceListType));
                    }

                    List<TerminalItemInfo> terminalItemInfoList = new ArrayList<>();
                    for (ImMap<Object, Object> row : skuResult.valueIt()) {
                        String idItem = getRowValue(row, "idSkuBarcode");
                        String barcode = getRowValue(row, "idBarcode");
                        String name = getRowValue(row, "nameMachineryPriceTransactionBarcode");
                        BigDecimal price = (BigDecimal) row.get("priceMachineryPriceTransactionBarcode");
                        boolean split = row.get("splitMachineryPriceTransactionBarcode") != null;
                        Integer daysExpiry = (Integer) row.get("expiryDaysMachineryPriceTransactionBarcode");
                        Date expiryDate = (Date) row.get("expiryDateMachineryPriceTransactionBarcode");
                        Integer pluNumber = (Integer) row.get("pluNumberMachineryPriceTransactionBarcode");
                        Integer flags = (Integer) row.get("flagsMachineryPriceTransactionBarcode");
                        boolean passScales = row.get("passScalesMachineryPriceTransactionBarcode") != null;
                        BigDecimal valueVAT = machineryPriceTransactionStockTaxLM == null ? null : (BigDecimal) row.get("VATMachineryPriceTransactionBarcode");
                        String canonicalNameSkuGroup = (String) row.get("canonicalNameSkuGroupMachineryPriceTransactionBarcode");

                        terminalItemInfoList.add(new TerminalItemInfo(idItem, barcode, name, price, split, daysExpiry, 
                                expiryDate, passScales, valueVAT, pluNumber, flags, null, canonicalNameSkuGroup, null, null));
                    }

                    List<TerminalHandbookType> terminalHandbookTypeList = readTerminalHandbookTypeList(session, getBusinessLogics());
                    List<TerminalDocumentType> terminalDocumentTypeList = readTerminalDocumentTypeList(session, getBusinessLogics());
                    List<TerminalLegalEntity> terminalLegalEntityList = readTerminalLegalEntityList(session, getBusinessLogics());
                    List<TerminalAssortment> terminalAssortmentList = readTerminalAssortmentList(session, getBusinessLogics(), priceListTypeGroupMachinery, stockGroupTerminal);
                    
                    transactionList.add(new TransactionTerminalInfo((Integer) transactionObject.getValue(), dateTimeCode, 
                            date, handlerModelGroupMachinery, (Integer) groupMachineryObject.object, nppGroupMachinery, nameGroupMachinery,
                            descriptionTransaction, terminalItemInfoList, terminalInfoList, snapshotTransaction, lastErrorDateTransaction,
                            /*denominationStageGroupMachinery, */terminalHandbookTypeList, terminalDocumentTypeList, terminalLegalEntityList, terminalAssortmentList,
                            nppGroupTerminal, directoryGroupTerminal));
                }
            }
            return transactionList;
        } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
            throw Throwables.propagate(e);
        }
    }

    private List<Integer> readTroubleMachineryGroups(DataSession session, Integer minutes) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<Integer> result = new ArrayList();
        if(minutes != null) {
            KeyExpr groupMachineryExpr = new KeyExpr("GroupMachinery");
            ImRevMap<Object, KeyExpr> groupMachineryKeys = MapFact.singletonRev((Object) "groupMachinery", groupMachineryExpr);
            QueryBuilder<Object, Object> groupMachineryQuery = new QueryBuilder<>(groupMachineryKeys);

            String[] groupMachineryNames = new String[]{"npp", "lastTime"};
            LCP[] groupMachineryProperties = machineryPriceTransactionLM.findProperties("npp[GroupMachinery]", "lastTime[GroupMachinery]");
            for (int i = 0; i < groupMachineryProperties.length; i++) {
                groupMachineryQuery.addProperty(groupMachineryNames[i], groupMachineryProperties[i].getExpr(groupMachineryExpr));
            }
            groupMachineryQuery.and(machineryPriceTransactionLM.findProperty("lastTime[GroupMachinery]").getExpr(groupMachineryExpr).getWhere());

            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> groupMachineryResult = groupMachineryQuery.execute(session);

            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MINUTE, -minutes);
            Timestamp minTime = new Timestamp(cal.getTimeInMillis());
            for (ImMap<Object, Object> row : groupMachineryResult.valueIt()) {
                Integer npp = (Integer) row.get("npp");
                Timestamp lastTime = (Timestamp) row.get("lastTime");
                if (lastTime.compareTo(minTime) <= 0)
                    result.add(npp);
            }
        }
        return result;
    }

    private Map<String, List<ItemGroup>> readItemGroupMap(DataSession session) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, List<ItemGroup>> result = new HashMap<>();
        Map<String, ItemGroup> itemGroupMap = new HashMap<>();
        
        KeyExpr itemGroupExpr = new KeyExpr("ItemGroup");        
        ImRevMap<Object, KeyExpr> itemGroupKeys = MapFact.singletonRev((Object) "itemGroup", itemGroupExpr);
        QueryBuilder<Object, Object> itemGroupQuery = new QueryBuilder<>(itemGroupKeys);

        String[] itemGroupNames = new String[] {"idItemGroup", "overIdItemGroup", "nameItemGroup", "idParentItemGroup"};
        LCP[] itemGroupProperties = itemLM.findProperties("id[ItemGroup]", "overId[ItemGroup]", "name[ItemGroup]", "idParent[ItemGroup]");
        for (int i = 0; i < itemGroupProperties.length; i++) {
            itemGroupQuery.addProperty(itemGroupNames[i], itemGroupProperties[i].getExpr(itemGroupExpr));
        }
        
        itemGroupQuery.and(itemLM.findProperty("id[ItemGroup]").getExpr(itemGroupExpr).getWhere());
        
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> itemGroupResult = itemGroupQuery.execute(session);

        for (ImMap<Object, Object> row : itemGroupResult.valueIt()) {
            String idItemGroup = getRowValue(row, "idItemGroup");
            String overIdItemGroup = getRowValue(row, "overIdItemGroup");
            String nameItemGroup = getRowValue(row, "nameItemGroup");
            String idParentItemGroup = getRowValue(row, "idParentItemGroup");
            itemGroupMap.put(overIdItemGroup, new ItemGroup(idItemGroup, overIdItemGroup, nameItemGroup, idParentItemGroup));
        }
        
        for(Map.Entry<String, ItemGroup> entry : itemGroupMap.entrySet()) {
            List<ItemGroup> hierarchy = new ArrayList<>(Collections.singletonList(entry.getValue()));
            String idParent = entry.getValue().idParentItemGroup;
            while(itemGroupMap.containsKey(idParent)) {
                ItemGroup parentItemGroup = itemGroupMap.get(idParent);
                hierarchy.add(parentItemGroup);
                idParent = parentItemGroup.idParentItemGroup;
            }
            result.put(entry.getKey(), hierarchy);
        }
        return result;
    }

    @Override
    public List<DiscountCard> readDiscountCardList(String idDiscountCardFrom, String idDiscountCardTo) throws RemoteException, SQLException {
        List<DiscountCard> discountCardList = new ArrayList<>();
        if(discountCardLM != null) {
            try (DataSession session = getDbManager().createSession()) {

                KeyExpr discountCardExpr = new KeyExpr("discountCard");
                ImRevMap<Object, KeyExpr> discountCardKeys = MapFact.singletonRev((Object) "discountCard", discountCardExpr);

                QueryBuilder<Object, Object> discountCardQuery = new QueryBuilder<>(discountCardKeys);
                String[] discountCardNames = new String[]{"idDiscountCard", "numberDiscountCard", "nameDiscountCard", 
                        "percentDiscountCard", "dateDiscountCard", "dateToDiscountCard", "initialSumDiscountCard"};
                LCP[] discountCardProperties = discountCardLM.findProperties("id[DiscountCard]", "number[DiscountCard]", "name[DiscountCard]",
                        "percent[DiscountCard]", "date[DiscountCard]", "dateTo[DiscountCard]", "initialSum[DiscountCard]");
                for (int i = 0; i < discountCardProperties.length; i++) {
                    discountCardQuery.addProperty(discountCardNames[i], discountCardProperties[i].getExpr(discountCardExpr));
                }
                discountCardQuery.and(discountCardLM.findProperty("number[DiscountCard]").getExpr(discountCardExpr).getWhere());
                Integer idFrom = parseInt(idDiscountCardFrom);
                if (idFrom != null)
                    discountCardQuery.and(discountCardLM.findProperty("intId[DiscountCard]").getExpr(discountCardExpr).compare(new DataObject(idFrom).getExpr(), Compare.GREATER_EQUALS));
                Integer idTo = parseInt(idDiscountCardTo);
                if (idTo != null)
                    discountCardQuery.and(discountCardLM.findProperty("intId[DiscountCard]").getExpr(discountCardExpr).compare(new DataObject(idTo).getExpr(), Compare.LESS_EQUALS));

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> discountCardResult = discountCardQuery.execute(session, MapFact.singletonOrder((Object) "idDiscountCard", false));

                for (int i = 0, size = discountCardResult.size(); i < size; i++) {
                    ImMap<Object, Object> row = discountCardResult.getValue(i);

                    String idDiscountCard = getRowValue(row, "idDiscountCard");
                    if (idDiscountCard == null)
                        idDiscountCard = String.valueOf(discountCardResult.getKey(i).get("discountCard"));
                    String numberDiscountCard = getRowValue(row, "numberDiscountCard");
                    String nameDiscountCard = getRowValue(row, "nameDiscountCard");
                    BigDecimal percentDiscountCard = (BigDecimal) row.get("percentDiscountCard");
                    BigDecimal initialSumDiscountCard = (BigDecimal) row.get("initialSumDiscountCard");
                    Date dateFromDiscountCard = (Date) row.get("dateDiscountCard");
                    Date dateToDiscountCard = (Date) row.get("dateToDiscountCard");

                    discountCardList.add(new DiscountCard(idDiscountCard, numberDiscountCard, nameDiscountCard,
                            percentDiscountCard, initialSumDiscountCard, dateFromDiscountCard, dateToDiscountCard));
                }
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
        return discountCardList;
    }

    private Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch(Exception e) {
            return null;
        }
    }

//    private int discountCardCompare(String d1, String d2) {
//        int result = 0;
//        if (d1 != null && d2 != null) {
//            try {
//                result = Integer.parseInt(d1) - Integer.parseInt(d2);
//            } catch (Exception e) {
//                result = 0;
//            }
//        }
//        return result;
//    }

    @Override
    public List<CashierInfo> readCashierInfoList() throws RemoteException, SQLException {
        List<CashierInfo> cashierInfoList = new ArrayList<>();
        try (DataSession session = getDbManager().createSession()) {

            KeyExpr employeeExpr = new KeyExpr("employee");
            ImRevMap<Object, KeyExpr> employeeKeys = MapFact.singletonRev((Object) "employee", employeeExpr);

            QueryBuilder<Object, Object> employeeQuery = new QueryBuilder<>(employeeKeys);
            String[] employeeNames = new String[]{"idEmployee", "shortNameContact", "idPositionEmployee", "idStockEmployee"};
            LCP[] employeeProperties = equLM.findProperties("id[Employee]", "shortName[Contact]", "idPosition[Employee]", "idStock[Employee]");
            for (int i = 0; i < employeeProperties.length; i++) {
                employeeQuery.addProperty(employeeNames[i], employeeProperties[i].getExpr(employeeExpr));
            }
            employeeQuery.and(equLM.findProperty("idStock[Employee]").getExpr(employeeExpr).getWhere());
            employeeQuery.and(equLM.findProperty("id[Employee]").getExpr(employeeExpr).getWhere());
            employeeQuery.and(equLM.findProperty("shortName[Contact]").getExpr(employeeExpr).getWhere());

            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> employeeResult = employeeQuery.execute(session);

            for (int i = 0, size = employeeResult.size(); i < size; i++) {
                ImMap<Object, Object> row = employeeResult.getValue(i);

                String numberCashier = getRowValue(row, "idEmployee");
                String nameCashier = getRowValue(row, "shortNameContact");
                String idPosition = getRowValue(row, "idPositionEmployee");
                String idStock = getRowValue(row, "idStockEmployee");
                cashierInfoList.add(new CashierInfo(numberCashier, nameCashier, idPosition, idStock));
            }
        } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
            throw Throwables.propagate(e);
        }
        return cashierInfoList;
    }

    @Override
    public boolean enabledStopListInfo() throws RemoteException, SQLException {
        return (cashRegisterLM != null || scalesLM != null)  && stopListLM != null;
    }

    @Override
    public List<StopListInfo> readStopListInfo() throws RemoteException, SQLException {

        List<StopListInfo> stopListInfoList = new ArrayList<>();
        Map<String, StopListInfo> stopListInfoMap = new HashMap<>();
        if(machineryLM != null && stopListLM != null) {
            try (DataSession session = getDbManager().createSession()) {
                Map<String, Map<String, Set<MachineryInfo>>> stockMap = null;
                KeyExpr stopListExpr = new KeyExpr("stopList");
                ImRevMap<Object, KeyExpr> slKeys = MapFact.singletonRev((Object) "stopList", stopListExpr);
                QueryBuilder<Object, Object> slQuery = new QueryBuilder<>(slKeys);
                String[] slNames = new String[]{"excludeStopList", "numberStopList", "fromDateStopList", "fromTimeStopList", 
                        "toDateStopList", "toTimeStopList"};
                LCP<?>[] slProperties = stopListLM.findProperties("exclude[StopList]", "number[StopList]", "fromDate[StopList]", "fromTime[StopList]",
                        "toDate[StopList]", "toTime[StopList]");
                for (int i = 0; i < slProperties.length; i++) {
                    slQuery.addProperty(slNames[i], slProperties[i].getExpr(stopListExpr));
                }
                slQuery.and(stopListLM.findProperty("number[StopList]").getExpr(stopListExpr).getWhere());
                slQuery.and(stopListLM.findProperty("isPosted[StopList]").getExpr(stopListExpr).getWhere());
                slQuery.and(stopListLM.findProperty("toExport[StopList]").getExpr(stopListExpr).getWhere());
                ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> slResult = slQuery.executeClasses(session);
                for (int i = 0, size = slResult.size(); i < size; i++) {
                    DataObject stopListObject = slResult.getKey(i).get("stopList");
                    ImMap<Object, ObjectValue> slEntry = slResult.getValue(i);
                    String numberStopList = trim((String) slEntry.get("numberStopList").getValue());
                    boolean excludeStopList = slEntry.get("excludeStopList").getValue() != null;
                    Date dateFrom = (Date) slEntry.get("fromDateStopList").getValue();
                    Date dateTo = (Date) slEntry.get("toDateStopList").getValue();
                    Time timeFrom = (Time) slEntry.get("fromTimeStopList").getValue();
                    Time timeTo = (Time) slEntry.get("toTimeStopList").getValue();                    
                                                                              
                    Set<String> idStockSet = new HashSet<>();
                    Map<String, Set<MachineryInfo>> handlerMachineryMap = new HashMap<>();
                    Map<Integer, Set<String>> itemsInGroupMachineryMap = new HashMap();
                    KeyExpr stockExpr = new KeyExpr("stock");
                    KeyExpr groupMachineryExpr = new KeyExpr("groupMachinery");
                    ImRevMap<Object, KeyExpr> stockKeys = MapFact.toRevMap((Object) "stock", stockExpr, "groupMachinery", groupMachineryExpr);
                    QueryBuilder<Object, Object> stockQuery = new QueryBuilder<>(stockKeys);
                    stockQuery.addProperty("idStock", stopListLM.findProperty("id[Stock]").getExpr(stockExpr));
                    stockQuery.addProperty("nppGroupMachinery", machineryLM.findProperty("npp[GroupMachinery]").getExpr(groupMachineryExpr));
                    stockQuery.and(stopListLM.findProperty("in[Stock,StopList]").getExpr(stockExpr, stopListObject.getExpr()).getWhere());
                    stockQuery.and(stopListLM.findProperty("notSucceeded[Stock,StopList]").getExpr(stockExpr, stopListObject.getExpr()).getWhere());
                    stockQuery.and(machineryLM.findProperty("stock[GroupMachinery]").getExpr(groupMachineryExpr).compare(stockExpr, Compare.EQUALS));
                    stockQuery.and(equipmentLM.findProperty("equipmentServer[GroupMachinery]").getExpr(groupMachineryExpr).getWhere());
                    stockQuery.and(machineryLM.findProperty("active[GroupMachinery]").getExpr(groupMachineryExpr).getWhere());
                    ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> stockResult = stockQuery.executeClasses(session);
                    for (int j = 0; j < stockResult.size(); j++) {
                        ImMap<Object, ObjectValue> stockEntry = stockResult.getValue(j);
                        String idStock = trim((String) stockEntry.get("idStock").getValue());
                        idStockSet.add(idStock);
                        if(stockMap == null)
                            stockMap = getStockMap(session);
                        if(stockMap.containsKey(idStock))
                        for (Map.Entry<String, Set<MachineryInfo>> entry : stockMap.get(idStock).entrySet()) {
                            if (handlerMachineryMap.containsKey(entry.getKey()))
                                handlerMachineryMap.get(entry.getKey()).addAll(entry.getValue());
                            else
                                handlerMachineryMap.put(entry.getKey(), entry.getValue());
                        }
                        Integer groupMachinery = (Integer) stockEntry.get("nppGroupMachinery").getValue();
                        Set<String> itemsInGroupMachinerySet = new HashSet<>();
                        if(!itemsInGroupMachineryMap.containsKey(groupMachinery)) {
                            for (String item : getInGroupMachineryItemSet(session, stopListObject, stockResult.getKey(j).get("groupMachinery")))
                                itemsInGroupMachinerySet.add(item);
                            itemsInGroupMachineryMap.put(groupMachinery, itemsInGroupMachinerySet);
                        }
                    }
                    
                    if(!handlerMachineryMap.isEmpty()) {
                        Map<String, ItemInfo> stopListItemMap = getStopListItemMap(session, stopListObject, idStockSet);
                        StopListInfo stopList = stopListInfoMap.get(numberStopList);
                        Map<Integer, Set<String>> inGroupMachineryItemMap = stopList == null ? new HashMap<Integer, Set<String>>() : stopList.inGroupMachineryItemMap;
                        inGroupMachineryItemMap.putAll(itemsInGroupMachineryMap);
                        stopListInfoMap.put(numberStopList, new StopListInfo(excludeStopList, numberStopList, dateFrom, timeFrom, dateTo, timeTo,
                                idStockSet, inGroupMachineryItemMap, stopListItemMap, handlerMachineryMap));
                    }
                }

                for(StopListInfo stopList : stopListInfoMap.values()) {
                    stopListInfoList.add(stopList);
                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
       
        return stopListInfoList;
    }

    private Map<String, Map<String, Set<MachineryInfo>>> getStockMap(DataSession session) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, Map<String, Set<MachineryInfo>>> stockMap = new HashMap<>();

        KeyExpr groupMachineryExpr = new KeyExpr("groupMachinery");
        KeyExpr machineryExpr = new KeyExpr("machinery");
        ImRevMap<Object, KeyExpr> machineryKeys = MapFact.toRevMap((Object) "groupMachinery", groupMachineryExpr, "machinery", machineryExpr);
        QueryBuilder<Object, Object> machineryQuery = new QueryBuilder<>(machineryKeys);

        String[] groupMachineryNames = new String[] {"nppGroupMachinery", "handlerModelGroupMachinery", "idStockGroupMachinery", "denominationStage"};
        LCP[] groupMachineryProperties = machineryLM.findProperties("npp[GroupMachinery]", "handlerModel[GroupMachinery]", "idStock[GroupMachinery]", "nameDenominationStage[GroupMachinery]");
        for (int i = 0; i < groupMachineryProperties.length; i++) {
            machineryQuery.addProperty(groupMachineryNames[i], groupMachineryProperties[i].getExpr(groupMachineryExpr));
        }
        machineryQuery.addProperty("overDirectoryMachinery", machineryLM.findProperty("overDirectory[Machinery]").getExpr(machineryExpr));
        machineryQuery.addProperty("portMachinery", machineryLM.findProperty("port[Machinery]").getExpr(machineryExpr));
        machineryQuery.addProperty("nppMachinery", machineryLM.findProperty("npp[Machinery]").getExpr(machineryExpr));
        machineryQuery.addProperty("overDepartmentNumber", cashRegisterLM.findProperty("overDepartmentNumber[Machinery]").getExpr(machineryExpr));

        machineryQuery.and(machineryLM.findProperty("handlerModel[GroupMachinery]").getExpr(groupMachineryExpr).getWhere());
        machineryQuery.and(machineryLM.findProperty("idStock[GroupMachinery]").getExpr(groupMachineryExpr).getWhere());
        machineryQuery.and(machineryLM.findProperty("groupMachinery[Machinery]").getExpr(machineryExpr).compare(groupMachineryExpr, Compare.EQUALS));
        machineryQuery.and(machineryLM.findProperty("active[GroupMachinery]").getExpr(groupMachineryExpr).getWhere());
        machineryQuery.and(equipmentLM.findProperty("equipmentServer[GroupMachinery]").getExpr(groupMachineryExpr).getWhere());
        ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> machineryResult = machineryQuery.executeClasses(session);
        ValueClass cashRegisterClass = cashRegisterLM == null ? null : cashRegisterLM.findClass("CashRegister");
        ValueClass scalesClass = scalesLM == null ? null : scalesLM.findClass("Scales");
        for (int i = 0; i < machineryResult.size(); i++) {
            ImMap<Object, ObjectValue> values = machineryResult.getValue(i);
            Integer nppGroupMachinery = (Integer) values.get("nppGroupMachinery").getValue();
            String handlerModel = (String) values.get("handlerModelGroupMachinery").getValue();
            String directory = (String) values.get("overDirectoryMachinery").getValue();
            String port = (String) values.get("portMachinery").getValue();
            Integer nppMachinery = (Integer) values.get("nppMachinery").getValue();
            ConcreteClass machineryClass = machineryResult.getKey(i).get("machinery").objectClass;
            boolean isCashRegister = machineryClass != null && machineryClass.equals(cashRegisterClass);
            boolean isScales = machineryClass != null && machineryClass.equals(scalesClass);
            String idStockGroupMachinery = (String) values.get("idStockGroupMachinery").getValue();
            Integer overDepartNumber = (Integer) values.get("overDepartmentNumber").getValue();
            String denominationStage = trim((String) values.get("denominationStage").getValue());

            Map<String, Set<MachineryInfo>> handlerMap = stockMap.containsKey(idStockGroupMachinery) ? stockMap.get(idStockGroupMachinery) : new HashMap<String, Set<MachineryInfo>>();
            if(!handlerMap.containsKey(handlerModel))
                handlerMap.put(handlerModel, new HashSet<MachineryInfo>());
            if(isCashRegister) {
                //todo: убрать неиспользуемый конструктор
                //CashRegisterInfo e = new CashRegisterInfo(nppGroupMachinery, nppMachinery, handlerModel, port, directory, /*denominationStage, */idStockGroupMachinery);
                CashRegisterInfo e = new CashRegisterInfo(nppGroupMachinery, nppMachinery, null, handlerModel, port, directory, null, idStockGroupMachinery, false, null, null);
                // чтобы не менять api
                e.overDepartNumber = overDepartNumber;
                handlerMap.get(handlerModel).add(e);
            } else if(isScales){
                handlerMap.get(handlerModel).add(new ScalesInfo(nppGroupMachinery, nppMachinery, handlerModel, port, directory, denominationStage, idStockGroupMachinery));
            }
            stockMap.put(idStockGroupMachinery, handlerMap);
        }
        return stockMap;
    }
    
    private Map<String, ItemInfo> getStopListItemMap(DataSession session, DataObject stopListObject, Set<String> idStockSet) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, ItemInfo> stopListItemList = new HashMap<>();

        KeyExpr sldExpr = new KeyExpr("stopListDetail");
        ImRevMap<Object, KeyExpr> sldKeys = MapFact.singletonRev((Object) "stopListDetail", sldExpr);
        QueryBuilder<Object, Object> sldQuery = new QueryBuilder<>(sldKeys);
        String[] sldNames = new String[] {"idBarcodeSkuStopListDetail", "idSkuStopListDetail", "nameSkuStopListDetail", "idSkuGroupStopListDetail",
                "nameSkuGroupStopListDetail", "idUOMSkuStopListDetail", "shortNameUOMSkuStopListDetail", "splitSkuStopListDetail", "passScalesSkuStopListDetail",
                "flagsSkuStopListDetail"};
        LCP[] sldProperties = stopListLM.findProperties("idBarcodeSku[StopListDetail]", "idSku[StopListDetail]", "nameSku[StopListDetail]", "idSkuGroup[StopListDetail]",
                "nameSkuGroup[StopListDetail]", "idUOMSku[StopListDetail]", "shortNameUOMSku[StopListDetail]", "splitSku[StopListDetail]", "passScalesSku[StopListDetail]",
                "flagsSku[StopListDetail]");
        for (int i = 0; i < sldProperties.length; i++) {
            sldQuery.addProperty(sldNames[i], sldProperties[i].getExpr(sldExpr));
        }
        if(scalesItemLM != null) {
            sldQuery.addProperty("skuStopListDetail", stopListLM.findProperty("sku[StopListDetail]").getExpr(sldExpr));
            sldQuery.and(stopListLM.findProperty("sku[StopListDetail]").getExpr(sldExpr).getWhere());
        }
        sldQuery.and(stopListLM.findProperty("idBarcodeSku[StopListDetail]").getExpr(sldExpr).getWhere());
        sldQuery.and(stopListLM.findProperty("stopList[StopListDetail]").getExpr(sldExpr).compare(stopListObject, Compare.EQUALS));
        ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> sldResult = sldQuery.executeClasses(session);
        for (int i = 0; i < sldResult.size(); i++) {
            ImMap<Object, ObjectValue> values = sldResult.getValue(i);
            ObjectValue skuObject = values.get("skuStopListDetail");
            String idBarcode = trim((String) values.get("idBarcodeSkuStopListDetail").getValue());
            String idItem = trim((String) values.get("idSkuStopListDetail").getValue());
            String nameItem = trim((String) values.get("nameSkuStopListDetail").getValue());
            String idSkuGroup = trim((String) values.get("idSkuGroupStopListDetail").getValue());
            String nameSkuGroup = trim((String) values.get("nameSkuGroupStopListDetail").getValue());
            String idUOM = trim((String) values.get("idUOMSkuStopListDetail").getValue());
            String shortNameUOM = trim((String) values.get("shortNameUOMSkuStopListDetail").getValue());
            boolean split = values.get("splitSkuStopListDetail").getValue() != null;
            boolean passScales = values.get("passScalesSkuStopListDetail").getValue() != null;
            Integer flags = (Integer) values.get("flagsSkuStopListDetail").getValue();
            Map<String, Integer> stockPluNumberMap = new HashMap();
            for(String idStock : idStockSet) {
                Integer pluNumber = (Integer) scalesItemLM.findProperty("pluIdStockSku[VARSTRING[100],Item]").read(session, new DataObject(idStock), skuObject);
                stockPluNumberMap.put(idStock, pluNumber);
            }
            stopListItemList.put(idBarcode, new ItemInfo(stockPluNumberMap, idItem, idBarcode, nameItem, null, split, null, null, passScales,
                    null, null, flags, idSkuGroup, nameSkuGroup, idUOM, shortNameUOM));
        }
        return stopListItemList;
    }

    private Set<String> getInGroupMachineryItemSet(DataSession session, DataObject stopListObject, DataObject groupMachineryObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Set<String> inGroupMachineryItemSet = new HashSet<>();

        KeyExpr sldExpr = new KeyExpr("stopListDetail");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "stopListDetail", sldExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
        query.addProperty("idSkuStopListDetail", stopListLM.findProperty("idSku[StopListDetail]").getExpr(sldExpr));
        query.and(stopListLM.findProperty("in[GroupMachinery,StopListDetail]").getExpr(groupMachineryObject.getExpr(), sldExpr).getWhere());
        query.and(stopListLM.findProperty("stopList[StopListDetail]").getExpr(sldExpr).compare(stopListObject, Compare.EQUALS));
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
        for (int i = 0; i < result.size(); i++) {
            String idItem = trim((String) result.getValue(i).get("idSkuStopListDetail"));
            inGroupMachineryItemSet.add(idItem);
        }
        return inGroupMachineryItemSet;
    }

    @Override
    public void errorStopListReport(String numberStopList, Exception e) throws RemoteException, SQLException {
        try (DataSession session = getDbManager().createSession()) {
            DataObject errorObject = session.addObject((ConcreteCustomClass) stopListLM.findClass("StopListError"));
            ObjectValue stopListObject = stopListLM.findProperty("stopList[STRING[18]]").readClasses(session, new DataObject(numberStopList));
            stopListLM.findProperty("stopList[StopListError]").change(stopListObject.getValue(), session, errorObject);
            stopListLM.findProperty("data[StopListError]").change(e.toString(), session, errorObject);
            stopListLM.findProperty("date[StopListError]").change(getCurrentTimestamp(), session, errorObject);
            OutputStream os = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(os));
            stopListLM.findProperty("errorTrace[StopListError]").change(os.toString(), session, errorObject);

            session.apply(getBusinessLogics());
        } catch (Exception e2) {
            throw Throwables.propagate(e2);
        }
    }

    @Override
    public void succeedStopList(String numberStopList, Set<String> idStockSet) throws RemoteException, SQLException {
        try (DataSession session = getDbManager().createSession()) {
            DataObject stopListObject = (DataObject) stopListLM.findProperty("stopList[STRING[18]]").readClasses(session, new DataObject(numberStopList));
            for(String idStock : idStockSet) {
                DataObject stockObject = (DataObject) stopListLM.findProperty("stock[VARSTRING[100]]").readClasses(session, new DataObject(idStock));
                stopListLM.findProperty("succeeded[Stock,StopList]").change(true, session, stockObject, stopListObject);
            }
            session.apply(getBusinessLogics());
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }


    public static List<TerminalHandbookType> readTerminalHandbookTypeList(DataSession session, BusinessLogics BL) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalHandbookType> terminalHandbookTypeList = new ArrayList<>();
        ScriptingLogicsModule terminalLM = BL.getModule("EquipmentTerminal");
        if(terminalLM != null) {
            KeyExpr terminalHandbookTypeExpr = new KeyExpr("terminalHandbookType");
            ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "terminalHandbookType", terminalHandbookTypeExpr);
            QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
            String[] names = new String[]{"idTerminalHandbookType", "nameTerminalHandbookType"};
            LCP<?>[] properties = terminalLM.findProperties("id[TerminalHandbookType]", "name[TerminalHandbookType]");
            for (int i = 0, propertiesLength = properties.length; i < propertiesLength; i++) {
                query.addProperty(names[i], properties[i].getExpr(terminalHandbookTypeExpr));
            }
            query.and(terminalLM.findProperty("id[TerminalHandbookType]").getExpr(terminalHandbookTypeExpr).getWhere());
            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
            for (ImMap<Object, Object> entry : result.values()) {
                String id = trim((String) entry.get("idTerminalHandbookType"));
                String name = trim((String) entry.get("nameTerminalHandbookType"));
                terminalHandbookTypeList.add(new TerminalHandbookType(id, name));
            }
        }
        return terminalHandbookTypeList;
    }
    
    public static List<TerminalDocumentType> readTerminalDocumentTypeList(DataSession session, BusinessLogics BL) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalDocumentType> terminalDocumentTypeList = new ArrayList<>();
        ScriptingLogicsModule terminalLM = BL.getModule("EquipmentTerminal");
        if(terminalLM != null) {
            KeyExpr terminalDocumentTypeExpr = new KeyExpr("terminalDocumentType");
            ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "terminalDocumentType", terminalDocumentTypeExpr);
            QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
            String[] names = new String[]{"idTerminalDocumentType", "nameTerminalDocumentType", "flagTerminalDocumentType",
                    "idTerminalHandbookType1TerminalDocumentType", "idTerminalHandbookType2TerminalDocumentType"};
            LCP<?>[] properties = terminalLM.findProperties("id[TerminalDocumentType]", "name[TerminalDocumentType]", "flag[TerminalDocumentType]",
                    "idTerminalHandbookType1[TerminalDocumentType]", "idTerminalHandbookType2[TerminalDocumentType]");
            for (int i = 0; i < properties.length; i++) {
                query.addProperty(names[i], properties[i].getExpr(terminalDocumentTypeExpr));
            }
            query.and(terminalLM.findProperty("id[TerminalDocumentType]").getExpr(terminalDocumentTypeExpr).getWhere());
            query.and(terminalLM.findProperty("notSkip[TerminalDocumentType]").getExpr(terminalDocumentTypeExpr).getWhere());
            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
            for (ImMap<Object, Object> entry : result.values()) {
                String id = trim((String) entry.get("idTerminalDocumentType"));
                String name = trim((String) entry.get("nameTerminalDocumentType"));
                Integer flag = (Integer) entry.get("flagTerminalDocumentType");
                String analytics1 = trim((String) entry.get("idTerminalHandbookType1TerminalDocumentType"));
                String analytics2 = trim((String) entry.get("idTerminalHandbookType2TerminalDocumentType"));
                terminalDocumentTypeList.add(new TerminalDocumentType(id, name, analytics1, analytics2, flag));
            }
        }
        return terminalDocumentTypeList;
    }

    @Override
    public List<TerminalOrder> readTerminalOrderList(RequestExchange requestExchange) throws RemoteException, SQLException {
        List<TerminalOrder> terminalOrderList = new ArrayList<>();
        if (purchaseInvoiceAgreementLM != null) {
            try (DataSession session = getDbManager().createSession()) {
                KeyExpr orderExpr = new KeyExpr("order");
                KeyExpr orderDetailExpr = new KeyExpr("orderDetail");
                ImRevMap<Object, KeyExpr> orderKeys = MapFact.toRevMap((Object) "Order", orderExpr, "OrderDetail", orderDetailExpr);
                QueryBuilder<Object, Object> orderQuery = new QueryBuilder<>(orderKeys);
                String[] orderNames = new String[]{"dateOrder", "numberOrder", "idSupplierOrder"};
                LCP<?>[] orderProperties = purchaseInvoiceAgreementLM.findProperties("date[Purchase.Order]", "number[Purchase.Order]", "idSupplier[Purchase.Order]");
                for (int i = 0; i < orderProperties.length; i++) {
                    orderQuery.addProperty(orderNames[i], orderProperties[i].getExpr(orderExpr));
                }
                String[] orderDetailNames = new String[]{"idBarcodeSkuOrderDetail", "nameSkuOrderDetail", "priceOrderDetail",
                        "quantityOrderDetail", "minDeviationQuantityOrderDetail", "maxDeviationQuantityOrderDetail",
                        "minDeviationPriceOrderDetail", "maxDeviationPriceOrderDetail"};
                LCP<?>[] orderDetailProperties = purchaseInvoiceAgreementLM.findProperties("idBarcodeSku[Purchase.OrderDetail]", "nameSku[Purchase.OrderDetail]", "price[Purchase.OrderDetail]",
                        "quantity[Purchase.OrderDetail]", "minDeviationQuantity[Purchase.OrderDetail]", "maxDeviationQuantity[Purchase.OrderDetail]",
                        "minDeviationPrice[Purchase.OrderDetail]", "maxDeviationPrice[Purchase.OrderDetail]");
                for (int i = 0; i < orderDetailProperties.length; i++) {
                    orderQuery.addProperty(orderDetailNames[i], orderDetailProperties[i].getExpr(orderDetailExpr));
                }
                if (requestExchange.dateFrom != null)
                    orderQuery.and(purchaseInvoiceAgreementLM.findProperty("date[Purchase.Order]").getExpr(orderExpr).compare(
                            new DataObject(requestExchange.dateFrom, DateClass.instance).getExpr(), Compare.GREATER_EQUALS));
                if (requestExchange.dateTo != null)
                    orderQuery.and(purchaseInvoiceAgreementLM.findProperty("date[Purchase.Order]").getExpr(orderExpr).compare(
                            new DataObject(requestExchange.dateTo, DateClass.instance).getExpr(), Compare.LESS_EQUALS));
                if (requestExchange.idStock != null)
                    orderQuery.and(purchaseInvoiceAgreementLM.findProperty("customerStock[Purchase.Order]").getExpr(orderExpr).compare(
                            purchaseInvoiceAgreementLM.findProperty("stock[VARSTRING[100]]").readClasses(session, new DataObject(requestExchange.idStock)).getExpr(), Compare.EQUALS));
                orderQuery.and(purchaseInvoiceAgreementLM.findProperty("order[Purchase.OrderDetail]").getExpr(orderDetailExpr).compare(orderExpr, Compare.EQUALS));
                orderQuery.and(purchaseInvoiceAgreementLM.findProperty("number[Purchase.Order]").getExpr(orderExpr).getWhere());
                orderQuery.and(purchaseInvoiceAgreementLM.findProperty("isOpened[Purchase.Order]").getExpr(orderExpr).getWhere());
                orderQuery.and(purchaseInvoiceAgreementLM.findProperty("idBarcodeSku[Purchase.OrderDetail]").getExpr(orderDetailExpr).getWhere());
                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> orderResult = orderQuery.execute(session);
                for (ImMap<Object, Object> entry : orderResult.values()) {
                    Date dateOrder = (Date) entry.get("dateOrder");
                    String numberOrder = trim((String) entry.get("numberOrder"));
                    String idSupplier = trim((String) entry.get("idSupplierOrder"));
                    String barcode = trim((String) entry.get("idBarcodeSkuOrderDetail"));
                    String name = trim((String) entry.get("nameSkuOrderDetail"));
                    BigDecimal price = (BigDecimal) entry.get("priceOrderDetail");
                    BigDecimal quantity = (BigDecimal) entry.get("quantityOrderDetail");
                    BigDecimal minQuantity = (BigDecimal) entry.get("minDeviationQuantityOrderDetail");
                    BigDecimal maxQuantity = (BigDecimal) entry.get("maxDeviationQuantityOrderDetail");
                    BigDecimal minPrice = (BigDecimal) entry.get("minDeviationPriceOrderDetail");
                    BigDecimal maxPrice = (BigDecimal) entry.get("maxDeviationPriceOrderDetail");
                    terminalOrderList.add(new TerminalOrder(dateOrder, numberOrder, idSupplier, barcode, name, price,
                            quantity, minQuantity, maxQuantity, minPrice, maxPrice));
                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return terminalOrderList;
    }

    public static List<TerminalOrder> readTerminalOrderList(DataSession session, BusinessLogics BL, ObjectValue customerStockObject) throws RemoteException, SQLException {
        List<TerminalOrder> terminalOrderList = new ArrayList<>();
        ScriptingLogicsModule purchaseOrderLM = BL.getModule("PurchaseOrder");
        if (purchaseOrderLM != null) {
            try {
                KeyExpr orderExpr = new KeyExpr("order");
                KeyExpr orderDetailExpr = new KeyExpr("orderDetail");
                ImRevMap<Object, KeyExpr> orderKeys = MapFact.toRevMap((Object) "Order", orderExpr, "OrderDetail", orderDetailExpr);
                QueryBuilder<Object, Object> orderQuery = new QueryBuilder<>(orderKeys);
                String[] orderNames = new String[]{"dateOrder", "numberOrder", "idSupplierOrder"};
                LCP<?>[] orderProperties = purchaseOrderLM.findProperties("date[Purchase.Order]", "number[Purchase.Order]", "idSupplier[Purchase.Order]");
                for (int i = 0; i < orderProperties.length; i++) {
                    orderQuery.addProperty(orderNames[i], orderProperties[i].getExpr(orderExpr));
                }
                String[] orderDetailNames = new String[]{"idBarcodeSkuOrderDetail", "nameSkuOrderDetail", "priceOrderDetail",
                        "quantityOrderDetail"};
                LCP<?>[] orderDetailProperties = purchaseOrderLM.findProperties("idBarcodeSku[Purchase.OrderDetail]", "nameSku[Purchase.OrderDetail]", "price[Purchase.OrderDetail]",
                        "quantity[Purchase.OrderDetail]");
                for (int i = 0; i < orderDetailProperties.length; i++) {
                    orderQuery.addProperty(orderDetailNames[i], orderDetailProperties[i].getExpr(orderDetailExpr));
                }

                ScriptingLogicsModule purchaseInvoiceAgreementLM = BL.getModule("PurchaseInvoiceAgreement");
                if(purchaseInvoiceAgreementLM != null) {
                    String[] deviationNames = new String[]{"minDeviationQuantityOrderDetail", "maxDeviationQuantityOrderDetail",
                            "minDeviationPriceOrderDetail", "maxDeviationPriceOrderDetail"};
                    LCP<?>[]  deviationProperties = purchaseInvoiceAgreementLM.findProperties("minDeviationQuantity[Purchase.OrderDetail]", "maxDeviationQuantity[Purchase.OrderDetail]",
                            "minDeviationPrice[Purchase.OrderDetail]", "maxDeviationPrice[Purchase.OrderDetail]");
                    for (int i = 0; i < deviationNames.length; i++) {
                        orderQuery.addProperty(deviationNames[i], deviationProperties[i].getExpr(orderDetailExpr));
                    }
                }

                orderQuery.and(purchaseOrderLM.findProperty("isOpened[Purchase.Order]").getExpr(orderExpr).getWhere());
                orderQuery.and(purchaseOrderLM.findProperty("customerStock[Purchase.Order]").getExpr(orderExpr).compare(
                            customerStockObject.getExpr(), Compare.EQUALS));
                orderQuery.and(purchaseOrderLM.findProperty("order[Purchase.OrderDetail]").getExpr(orderDetailExpr).compare(orderExpr, Compare.EQUALS));
                orderQuery.and(purchaseOrderLM.findProperty("number[Purchase.Order]").getExpr(orderExpr).getWhere());
                orderQuery.and(purchaseOrderLM.findProperty("idBarcodeSku[Purchase.OrderDetail]").getExpr(orderDetailExpr).getWhere());
                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> orderResult = orderQuery.execute(session);
                for (ImMap<Object, Object> entry : orderResult.values()) {
                    Date dateOrder = (Date) entry.get("dateOrder");
                    String numberOrder = trim((String) entry.get("numberOrder"));
                    String idSupplier = trim((String) entry.get("idSupplierOrder"));
                    String barcode = trim((String) entry.get("idBarcodeSkuOrderDetail"));
                    String name = trim((String) entry.get("nameSkuOrderDetail"));
                    BigDecimal price = (BigDecimal) entry.get("priceOrderDetail");
                    BigDecimal quantity = (BigDecimal) entry.get("quantityOrderDetail");
                    BigDecimal minQuantity = (BigDecimal) entry.get("minDeviationQuantityOrderDetail");
                    BigDecimal maxQuantity = (BigDecimal) entry.get("maxDeviationQuantityOrderDetail");
                    BigDecimal minPrice = (BigDecimal) entry.get("minDeviationPriceOrderDetail");
                    BigDecimal maxPrice = (BigDecimal) entry.get("maxDeviationPriceOrderDetail");
                    terminalOrderList.add(new TerminalOrder(dateOrder, numberOrder, idSupplier, barcode, name, price,
                            quantity, minQuantity, maxQuantity, minPrice, maxPrice));
                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return terminalOrderList;
    }

    public static List<TerminalLegalEntity> readTerminalLegalEntityList(DataSession session, BusinessLogics BL) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalLegalEntity> terminalLegalEntityList = new ArrayList<>();
        ScriptingLogicsModule terminalLM = BL.getModule("EquipmentTerminal");
        if (terminalLM != null) {
            KeyExpr legalEntityExpr = new KeyExpr("legalEntity");
            ImRevMap<Object, KeyExpr> legalEntityKeys = MapFact.singletonRev((Object) "LegalEntity", legalEntityExpr);
            QueryBuilder<Object, Object> legalEntityQuery = new QueryBuilder<>(legalEntityKeys);
            String[] legalEntityNames = new String[]{"idLegalEntity", "nameLegalEntity"};
            LCP<?>[] legalEntityProperties = terminalLM.findProperties("id[LegalEntity]", "name[LegalEntity]");
            for (int i = 0; i < legalEntityProperties.length; i++) {
                legalEntityQuery.addProperty(legalEntityNames[i], legalEntityProperties[i].getExpr(legalEntityExpr));
            }
            legalEntityQuery.and(terminalLM.findProperty("id[LegalEntity]").getExpr(legalEntityExpr).getWhere());
            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> legalEntityResult = legalEntityQuery.execute(session);
            for (ImMap<Object, Object> entry : legalEntityResult.values()) {
                String idLegalEntity = trim((String) entry.get("idLegalEntity"));
                String nameLegalEntity = trim((String) entry.get("nameLegalEntity"));
                terminalLegalEntityList.add(new TerminalLegalEntity(idLegalEntity, nameLegalEntity));
            }
        }
        return terminalLegalEntityList;
    }

    public static List<TerminalLegalEntity> readCustomANAList(DataSession session, BusinessLogics BL) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalLegalEntity> customANAList = new ArrayList<>();
        ScriptingLogicsModule terminalLM = BL.getModule("EquipmentTerminal");
        if (terminalLM != null) {

            KeyExpr terminalHandbookTypeExpr = new KeyExpr("terminalHandbookType");
            ImRevMap<Object, KeyExpr> terminalHandbookTypeKeys = MapFact.singletonRev((Object) "terminalHandbookType", terminalHandbookTypeExpr);
            QueryBuilder<Object, Object> query = new QueryBuilder<>(terminalHandbookTypeKeys);
            String[] names = new String[]{"id", "name", "propertyID", "propertyName"};
            LCP<?>[] properties = terminalLM.findProperties("id[TerminalHandbookType]", "name[TerminalHandbookType]", "canonicalNamePropertyID[TerminalHandbookType]", "canonicalNamePropertyName[TerminalHandbookType]");
            for (int i = 0, propertiesLength = properties.length; i < propertiesLength; i++) {
                query.addProperty(names[i], properties[i].getExpr(terminalHandbookTypeExpr));
            }
            query.and(terminalLM.findProperty("id[TerminalHandbookType]").getExpr(terminalHandbookTypeExpr).getWhere());
            query.and(terminalLM.findProperty("canonicalNamePropertyID[TerminalHandbookType]").getExpr(terminalHandbookTypeExpr).getWhere());
            query.and(terminalLM.findProperty("canonicalNamePropertyName[TerminalHandbookType]").getExpr(terminalHandbookTypeExpr).getWhere());
            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
            for (ImMap<Object, Object> entry : result.values()) {
                String prefix = trim((String) entry.get("id"));
                LCP propertyID = (LCP<?>) BL.findSafeProperty(trim((String) entry.get("propertyID")));
                LCP propertyName = (LCP<?>) BL.findSafeProperty(trim((String) entry.get("propertyName")));

                if(propertyID != null && propertyName != null) {
                    ImOrderSet<PropertyInterface> interfaces = propertyID.listInterfaces;
                    if (interfaces.size() == 1) {
                        KeyExpr customANAExpr = new KeyExpr("customANA");
                        ImRevMap<Object, KeyExpr> customANAKeys = MapFact.singletonRev((Object) "customANA", customANAExpr);
                        QueryBuilder<Object, Object> customANAQuery = new QueryBuilder<>(customANAKeys);
                        customANAQuery.addProperty("id", propertyID.getExpr(customANAExpr));
                        customANAQuery.addProperty("name", propertyName.getExpr(customANAExpr));
                        customANAQuery.and(propertyID.getExpr(customANAExpr).getWhere());
                        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> customANAResult = customANAQuery.execute(session);
                        for (ImMap<Object, Object> customANAEntry : customANAResult.values()) {
                            String idCustomANA = trim((String) customANAEntry.get("id"));
                            String nameCustomANA = trim((String) customANAEntry.get("name"));
                            customANAList.add(new TerminalLegalEntity(prefix + idCustomANA, nameCustomANA));
                        }
                    }
                }
            }
        }
        return customANAList;
    }

    public static List<TerminalAssortment> readTerminalAssortmentList(DataSession session, BusinessLogics BL, ObjectValue priceListTypeObject, ObjectValue stockGroupMachineryObject)
            throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalAssortment> terminalAssortmentList = new ArrayList<>();
        ScriptingLogicsModule terminalLM = BL.getModule("EquipmentTerminal");
        if (terminalLM != null) {
            
            DataObject currentDateTimeObject = new DataObject(new Timestamp(Calendar.getInstance().getTime().getTime()), DateTimeClass.instance);
            
            KeyExpr skuExpr = new KeyExpr("Sku");
            KeyExpr legalEntityExpr = new KeyExpr("legalEntity");
            ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap((Object) "Sku", skuExpr, "LegalEntity", legalEntityExpr);
            QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
            query.addProperty("priceALedgerPriceListTypeSkuStockCompanyDateTime", terminalLM.findProperty("priceA[LedgerPriceListType,Sku,Stock,LegalEntity,DATETIME]").getExpr(priceListTypeObject.getExpr(),
                    skuExpr, stockGroupMachineryObject.getExpr(), legalEntityExpr, currentDateTimeObject.getExpr()));
            query.addProperty("idBarcodeSku", terminalLM.findProperty("idBarcode[Sku]").getExpr(skuExpr));
            query.addProperty("idLegalEntity", terminalLM.findProperty("id[LegalEntity]").getExpr(legalEntityExpr));
            query.and(terminalLM.findProperty("id[LegalEntity]").getExpr(legalEntityExpr).getWhere());
            query.and(terminalLM.findProperty("idBarcode[Sku]").getExpr(skuExpr).getWhere());
            query.and(terminalLM.findProperty("priceA[LedgerPriceListType,Sku,Stock,LegalEntity,DATETIME]").getExpr(priceListTypeObject.getExpr(),
                    skuExpr, stockGroupMachineryObject.getExpr(), legalEntityExpr, currentDateTimeObject.getExpr()).getWhere());
            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
            for (ImMap<Object, Object> entry : result.values()) {
                String idBarcodeSku = trim((String) entry.get("idBarcodeSku"));
                String idLegalEntity = trim((String) entry.get("idLegalEntity"));
                terminalAssortmentList.add(new TerminalAssortment(idBarcodeSku, idLegalEntity));
            }
        } 
        return terminalAssortmentList;
    }

    @Override
    public List<RequestExchange> readRequestExchange(String sidEquipmentServer) throws RemoteException, SQLException {

        List<RequestExchange> requestExchangeList = new ArrayList();
        if(machineryLM != null && machineryPriceTransactionLM != null) {

            try (DataSession session = getDbManager().createSession()) {

                KeyExpr requestExchangeExpr = new KeyExpr("requestExchange");
                ImRevMap<Object, KeyExpr> requestExchangeKeys = MapFact.singletonRev((Object) "requestExchange", requestExchangeExpr);
                QueryBuilder<Object, Object> requestExchangeQuery = new QueryBuilder<>(requestExchangeKeys);

                String[] requestExchangeNames = new String[]{"dateFromRequestExchange", "dateToRequestExchange", "startDateRequestExchange",
                        "nameRequestExchangeTypeRequestExchange", "idDiscountCardFromRequestExchange", "idDiscountCardToRequestExchange"};
                LCP[] requestExchangeProperties = machineryPriceTransactionLM.findProperties("dateFrom[RequestExchange]", "dateTo[RequestExchange]", "startDate[RequestExchange]",
                        "nameRequestExchangeType[RequestExchange]", "idDiscountCardFrom[RequestExchange]", "idDiscountCardTo[RequestExchange]");
                for (int i = 0; i < requestExchangeProperties.length; i++) {
                    requestExchangeQuery.addProperty(requestExchangeNames[i], requestExchangeProperties[i].getExpr(requestExchangeExpr));
                }
                requestExchangeQuery.and(machineryPriceTransactionLM.findProperty("notSucceeded[RequestExchange]").getExpr(requestExchangeExpr).getWhere());
                ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> requestExchangeResult = requestExchangeQuery.executeClasses(session);
                for (int i = 0; i < requestExchangeResult.size(); i++) {

                    DataObject requestExchangeObject = requestExchangeResult.getKey(i).get("requestExchange");
                    Date dateFromRequestExchange = (Date) requestExchangeResult.getValue(i).get("dateFromRequestExchange").getValue();
                    Date dateToRequestExchange = (Date) requestExchangeResult.getValue(i).get("dateToRequestExchange").getValue();
                    Date startDateRequestExchange = (Date) requestExchangeResult.getValue(i).get("startDateRequestExchange").getValue();
                    String idDiscountCardFrom = trim((String) requestExchangeResult.getValue(i).get("idDiscountCardFromRequestExchange").getValue());
                    String idDiscountCardTo = trim((String) requestExchangeResult.getValue(i).get("idDiscountCardToRequestExchange").getValue());
                    String typeRequestExchange = trim((String) requestExchangeResult.getValue(i).get("nameRequestExchangeTypeRequestExchange").getValue());

                    Set<Integer/*CashRegisterInfo*/> cashRegisterSet = new HashSet<> ();
                    Map<String, Set<String>> directoryStockMap = readExtraStockRequestExchange(session, requestExchangeObject);
                    String idStock = null;

                    KeyExpr machineryExpr = new KeyExpr("machinery");
                    ImRevMap<Object, KeyExpr> machineryKeys = MapFact.singletonRev((Object) "machinery", machineryExpr);
                    QueryBuilder<Object, Object> machineryQuery = new QueryBuilder<>(machineryKeys);

                    String[] machineryNames = new String[]{"overDirectoryMachinery", "idStockMachinery", "nppMachinery", "denominationStage"};
                    LCP[] machineryProperties = machineryPriceTransactionLM.findProperties("overDirectory[Machinery]", "idStock[Machinery]",
                            "npp[Machinery]", "nameDenominationStage[Machinery]");
                    for (int j = 0; j < machineryProperties.length; j++) {
                        machineryQuery.addProperty(machineryNames[j], machineryProperties[j].getExpr(machineryExpr));
                    }
                    machineryQuery.and(machineryPriceTransactionLM.findProperty("in[Machinery,RequestExchange]").getExpr(machineryExpr, requestExchangeObject.getExpr()).getWhere());
                    machineryQuery.and(machineryLM.findProperty("stock[Machinery]").getExpr(machineryExpr).compare(
                            machineryPriceTransactionLM.findProperty("stock[RequestExchange]").getExpr(requestExchangeObject.getExpr()), Compare.EQUALS));
                    machineryQuery.and(machineryLM.findProperty("inactive[Machinery]").getExpr(machineryExpr).getWhere().not());
                    ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> result = machineryQuery.executeClasses(session);
                    for (int j = 0; j < result.size(); j++) {

                        String directoryMachinery = trim((String) result.getValue(j).get("overDirectoryMachinery").getValue());
                        idStock = trim((String) result.getValue(j).get("idStockMachinery").getValue());
                        Integer nppMachinery = (Integer) result.getValue(j).get("nppMachinery").getValue();
                        //String denominationStage = trim((String) result.getValue(j).get("denominationStage").getValue());

                        cashRegisterSet.add(nppMachinery/*new CashRegisterInfo(null, nppMachinery, null, null, directoryMachinery, denominationStage, null)*/);
                        putDirectoryStockMap(directoryStockMap, directoryMachinery, idStock);
                    }

                    requestExchangeList.add(new RequestExchange((Integer) requestExchangeObject.getValue(), cashRegisterSet,
                            idStock, directoryStockMap, dateFromRequestExchange, dateToRequestExchange,
                            startDateRequestExchange, idDiscountCardFrom, idDiscountCardTo, typeRequestExchange));
                }
                session.apply(getBusinessLogics());
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return requestExchangeList;
    }
        
    private Map<String, Set<String>> readExtraStockRequestExchange(DataSession session, DataObject requestExchangeObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, Set<String>> directoryStockMap = new HashMap<>();
        KeyExpr stockExpr = new KeyExpr("stock");
        KeyExpr machineryExpr = new KeyExpr("machinery");
        ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap((Object) "stock", stockExpr, "machinery", machineryExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);

        query.addProperty("idStock", machineryPriceTransactionLM.findProperty("id[Stock]").getExpr(stockExpr));
        query.addProperty("overDirectoryMachinery", machineryPriceTransactionLM.findProperty("overDirectory[Machinery]").getExpr(machineryExpr));
        query.and(machineryPriceTransactionLM.findProperty("in[Stock,RequestExchange]").getExpr(stockExpr, requestExchangeObject.getExpr()).getWhere());
        query.and(machineryPriceTransactionLM.findProperty("overDirectory[Machinery]").getExpr(machineryExpr).getWhere());
        query.and(machineryLM.findProperty("stock[Machinery]").getExpr(machineryExpr).compare(stockExpr, Compare.EQUALS));
        query.and(machineryLM.findProperty("inactive[Machinery]").getExpr(machineryExpr).getWhere().not());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
        for (ImMap<Object, Object> entry : result.values())
            putDirectoryStockMap(directoryStockMap, trim((String) entry.get("overDirectoryMachinery")), trim((String) entry.get("idStock")));
        return directoryStockMap;
    }

    private void putDirectoryStockMap(Map<String, Set<String>> directoryStockMap, String directory, String idStock) {
        if(directory != null) {
            Set<String> stockSet = directoryStockMap.get(directory);
            if (stockSet == null)
                stockSet = new HashSet();
            stockSet.add(idStock);
            directoryStockMap.put(directory, stockSet);
        }
    }

    @Override
    public void finishRequestExchange(Set<Integer> succeededRequestsSet) throws RemoteException, SQLException {
        if (machineryPriceTransactionLM != null) {
            try (DataSession session = getDbManager().createSession()) {
                for (Integer request : succeededRequestsSet) {
                    DataObject requestExchangeObject = new DataObject(request, (ConcreteClass) machineryPriceTransactionLM.findClass("RequestExchange"));
                    machineryPriceTransactionLM.findProperty("succeeded[RequestExchange]").change(true, session, requestExchangeObject);
                    machineryPriceTransactionLM.findProperty("dateTimeSucceeded[RequestExchange]").change(getCurrentTimestamp(), session, requestExchangeObject);
                }
                session.apply(getBusinessLogics());
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    @Override
    public void errorRequestExchange(Map<Integer, String> succeededRequestsMap) throws RemoteException, SQLException {
        if (machineryPriceTransactionLM != null) {
            try (DataSession session = getDbManager().createSession()) {
                for (Map.Entry<Integer, String> request : succeededRequestsMap.entrySet()) {
                    DataObject errorObject = session.addObject((ConcreteCustomClass) machineryPriceTransactionLM.findClass("RequestExchangeError"));
                    machineryPriceTransactionLM.findProperty("message[RequestExchangeError]").change(request.getValue(), session, errorObject);
                    machineryPriceTransactionLM.findProperty("date[RequestExchangeError]").change(getCurrentTimestamp(), session, errorObject);
                    machineryPriceTransactionLM.findProperty("requestExchange[RequestExchangeError]").change(request.getKey(), session, errorObject);
                }
                session.apply(getBusinessLogics());
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    @Override
    public Map<String, BigDecimal> readZReportSumMap() throws RemoteException, SQLException {
        Map<String, BigDecimal> zReportSumMap = new HashMap<>();
        if (zReportLM != null) {
            try (DataSession session = getDbManager().createSession()) {

                KeyExpr zReportExpr = new KeyExpr("zReport");

                ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "ZReport", zReportExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);

                query.addProperty("idZReport", zReportLM.findProperty("id[ZReport]").getExpr(zReportExpr));
                query.addProperty("sumReceiptDetailZReport", zReportLM.findProperty("sumReceiptDetail[ZReport]").getExpr(zReportExpr));

                query.and(zReportLM.findProperty("id[ZReport]").getExpr(zReportExpr).getWhere());
                query.and(zReportLM.findProperty("succeededExtraCheck[ZReport]").getExpr(zReportExpr).getWhere());

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);

                for (ImMap<Object, Object> row : result.values()) {
                    zReportSumMap.put((String) row.get("idZReport"), (BigDecimal) row.get("sumReceiptDetailZReport"));
                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return zReportSumMap;
    }
    
    @Override
    public void succeedExtraCheckZReport(List<String> idZReportList) throws RemoteException, SQLException {
        try {
            if (zReportLM != null) {
                for (String idZReport : idZReportList) {
                    try (DataSession session = getDbManager().createSession()) {
                        zReportLM.findProperty("succeededExtraCheck[ZReport]").change(true, session, (DataObject) zReportLM.findProperty("zReport[VARSTRING[100]]").readClasses(session, new DataObject(idZReport)));
                        session.apply(getBusinessLogics());
                    }
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public List<CashRegisterInfo> readCashRegisterInfo(String sidEquipmentServer) throws RemoteException, SQLException {
        List<CashRegisterInfo> cashRegisterInfoList = new ArrayList<>();
        if (cashRegisterLM != null) {
            try (DataSession session = getDbManager().createSession()) {

                KeyExpr groupCashRegisterExpr = new KeyExpr("groupCashRegister");
                KeyExpr cashRegisterExpr = new KeyExpr("cashRegister");

                ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap((Object) "groupCashRegister", groupCashRegisterExpr, "cashRegister", cashRegisterExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);

                String[] cashRegisterNames = new String[]{"nppMachinery", "portMachinery", "overDirectoryMachinery", "disableSalesCashRegister"};
                LCP[] cashRegisterProperties = cashRegisterLM.findProperties("npp[Machinery]", "port[Machinery]", "overDirectory[Machinery]", "disableSales[CashRegister]");
                for (int i = 0; i < cashRegisterProperties.length; i++) {
                    query.addProperty(cashRegisterNames[i], cashRegisterProperties[i].getExpr(cashRegisterExpr));
                }

                String[] groupCashRegisterNames = new String[]{"nppGroupMachinery", "handlerModelGroupMachinery", "nameModelGroupMachinery",
                        "overDepartmentNumberGroupCashRegister", "pieceCodeGroupCashRegister", "weightCodeGroupCashRegister",
                        "idStockGroupMachinery"/*, "denominationStage"*/, "section"};
                LCP[] groupCashRegisterProperties = cashRegisterLM.findProperties("npp[GroupMachinery]", "handlerModel[GroupMachinery]", "nameModel[GroupMachinery]",
                        "overDepartmentNumberCashRegister[GroupMachinery]", "pieceCode[GroupCashRegister]", "weightCode[GroupCashRegister]", "idStock[GroupMachinery]"/*,
                        "nameDenominationStage[GroupMachinery]"*/, "section[GroupCashRegister]");
                for (int i = 0; i < groupCashRegisterProperties.length; i++) {
                    query.addProperty(groupCashRegisterNames[i], groupCashRegisterProperties[i].getExpr(groupCashRegisterExpr));
                }

                query.and(cashRegisterLM.findProperty("handlerModel[GroupMachinery]").getExpr(groupCashRegisterExpr).getWhere());
                //query.and(cashRegisterLM.findProperty("overDirectoryMachinery").getExpr(cashRegisterExpr).getWhere());
                query.and(cashRegisterLM.findProperty("groupMachinery[Machinery]").getExpr(cashRegisterExpr).compare(groupCashRegisterExpr, Compare.EQUALS));
                query.and(equLM.findProperty("sidEquipmentServer[GroupMachinery]").getExpr(groupCashRegisterExpr).compare(new DataObject(sidEquipmentServer), Compare.EQUALS));
                query.and(cashRegisterLM.findProperty("active[GroupCashRegister]").getExpr(groupCashRegisterExpr).getWhere());

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);

                for (ImMap<Object, Object> row : result.values()) {
                    CashRegisterInfo c = new CashRegisterInfo((Integer) row.get("nppGroupMachinery"), (Integer) row.get("nppMachinery"),
                            (String) row.get("nameModelGroupMachinery"), (String) row.get("handlerModelGroupMachinery"), (String) row.get("portMachinery"),
                            (String) row.get("overDirectoryMachinery"), /*trim((String) row.get("denominationStage")), */(Integer) row.get("overDepartmentNumberGroupCashRegister"),
                            (String) row.get("idStockGroupMachinery"), row.get("disableSalesCashRegister") != null, (String) row.get("pieceCodeGroupCashRegister"),
                            (String) row.get("weightCodeGroupCashRegister"));
                    c.idStock = (String) row.get("section");
                    cashRegisterInfoList.add(c);
                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return cashRegisterInfoList;
    }

    @Override
    public boolean enabledTerminalInfo() throws RemoteException, SQLException {
        return terminalLM != null;
    }

    @Override
    public List<TerminalInfo> readTerminalInfo(String sidEquipmentServer) throws RemoteException, SQLException {
        List<TerminalInfo> terminalInfoList;
        terminalInfoList = new ArrayList<>();
        if (terminalLM != null) {
            try (DataSession session = getDbManager().createSession()) {

                KeyExpr groupTerminalExpr = new KeyExpr("groupTerminal");
                KeyExpr terminalExpr = new KeyExpr("terminal");

                ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap((Object) "GroupTerminal", groupTerminalExpr, "terminal", terminalExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);

                String[] terminalNames = new String[]{"nppMachinery", "portMachinery"};
                LCP[] terminalProperties = machineryLM.findProperties("npp[Machinery]", "port[Machinery]");
                for (int i = 0; i < terminalProperties.length; i++) {
                    query.addProperty(terminalNames[i], terminalProperties[i].getExpr(terminalExpr));
                }

                String[] groupTerminalNames = new String[]{"nameModelGroupMachinery", "handlerModelGroupMachinery",
                        "directoryGroupTerminal", "idPriceListTypeGroupMachinery", "nppGroupMachinery"/*, "denominationStage"*/};
                LCP[] groupTerminalProperties = terminalLM.findProperties("nameModel[GroupMachinery]", "handlerModel[GroupMachinery]",
                        "directory[GroupTerminal]", "idPriceListType[GroupMachinery]", "npp[GroupMachinery]"/*, "nameDenominationStage[GroupMachinery]"*/);
                for (int i = 0; i < groupTerminalProperties.length; i++) {
                    query.addProperty(groupTerminalNames[i], groupTerminalProperties[i].getExpr(groupTerminalExpr));
                }

                query.and(terminalLM.findProperty("handlerModel[GroupMachinery]").getExpr(groupTerminalExpr).getWhere());
                query.and(terminalLM.findProperty("directory[GroupTerminal]").getExpr(groupTerminalExpr).getWhere());
                query.and(terminalLM.findProperty("groupTerminal[Terminal]").getExpr(terminalExpr).compare(groupTerminalExpr, Compare.EQUALS));
                query.and(terminalLM.findProperty("active[GroupTerminal]").getExpr(groupTerminalExpr).getWhere());
                query.and(equLM.findProperty("sidEquipmentServer[GroupMachinery]").getExpr(groupTerminalExpr).compare(new DataObject(sidEquipmentServer), Compare.EQUALS));

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);

                for (ImMap<Object, Object> row : result.values()) {
                    terminalInfoList.add(new TerminalInfo(true, false, false, (Integer) row.get("nppGroupMachinery"), (Integer) row.get("nppMachinery"),
                            (String) row.get("nameModelGroupMachinery"), (String) row.get("handlerModelGroupMachinery"), (String) row.get("portMachinery"),
                            (String) row.get("directoryGroupTerminal"), /*trim((String) row.get("denominationStage")), */(String) row.get("idPriceListTypeGroupMachinery")));
                }
            } catch (ScriptingErrorLog.SemanticErrorException e) {
                throw new RuntimeException(e.toString());
            } catch (SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return terminalInfoList;
    }

    @Override
    public List<MachineryInfo> readMachineryInfo(String sidEquipmentServer) throws RemoteException, SQLException {
        List<MachineryInfo> machineryInfoList = new ArrayList<>();
        if (machineryLM != null) {
            try (DataSession session = getDbManager().createSession()) {

                KeyExpr groupMachineryExpr = new KeyExpr("groupMachinery");
                KeyExpr machineryExpr = new KeyExpr("machinery");

                ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap((Object) "groupMachinery", groupMachineryExpr, "machinery", machineryExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);

                String[] machineryNames = new String[]{"nppMachinery", "portMachinery", "overDirectoryMachinery"};
                LCP[] machineryProperties = machineryLM.findProperties("npp[Machinery]", "port[Machinery]", "overDirectory[Machinery]");
                for (int i = 0; i < machineryProperties.length; i++) {
                    query.addProperty(machineryNames[i], machineryProperties[i].getExpr(machineryExpr));
                }

                String[] groupMachineryNames = new String[]{"nppGroupMachinery", "handlerModelGroupMachinery", "nameModelGroupMachinery",
                        "denominationStage"};
                LCP[] groupMachineryProperties = machineryLM.findProperties("npp[GroupMachinery]", "handlerModel[GroupMachinery]", "nameModel[GroupMachinery]",
                        "nameDenominationStage[GroupMachinery]");
                for (int i = 0; i < groupMachineryProperties.length; i++) {
                    query.addProperty(groupMachineryNames[i], groupMachineryProperties[i].getExpr(groupMachineryExpr));
                }

                query.and(machineryLM.findProperty("handlerModel[GroupMachinery]").getExpr(groupMachineryExpr).getWhere());
                query.and(machineryLM.findProperty("overDirectory[Machinery]").getExpr(machineryExpr).getWhere());
                query.and(machineryLM.findProperty("groupMachinery[Machinery]").getExpr(machineryExpr).compare(groupMachineryExpr, Compare.EQUALS));
                query.and(equLM.findProperty("sidEquipmentServer[GroupMachinery]").getExpr(groupMachineryExpr).compare(new DataObject(sidEquipmentServer), Compare.EQUALS));

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);

                for (ImMap<Object, Object> row : result.values()) {
                    machineryInfoList.add(new MachineryInfo(true, false, false, (Integer) row.get("nppGroupMachinery"), (Integer) row.get("nppMachinery"),
                            (String) row.get("nameModelGroupMachinery"), (String) row.get("handlerModelGroupMachinery"), (String) row.get("portMachinery"),
                            (String) row.get("overDirectoryMachinery"), trim((String) row.get("denominationStage"))));
                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return machineryInfoList;
    }

    @Override
    public String sendTerminalInfo(List<TerminalDocumentDetail> terminalDocumentDetailList, String sidEquipmentServer) throws RemoteException, SQLException {

        try {

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            List<List<Object>> data = initData(terminalDocumentDetailList.size());

            if (notNullNorEmpty(terminalDocumentDetailList)) {

                ImportField idTerminalDocumentField = new ImportField(terminalLM.findProperty("id[TerminalDocument]"));
                ImportKey<?> terminalDocumentKey = new ImportKey((ConcreteCustomClass) terminalLM.findClass("TerminalDocument"),
                        terminalLM.findProperty("terminalDocument[VARSTRING[100]]").getMapping(idTerminalDocumentField));
                keys.add(terminalDocumentKey);
                props.add(new ImportProperty(idTerminalDocumentField, terminalLM.findProperty("id[TerminalDocument]").getMapping(terminalDocumentKey)));
                fields.add(idTerminalDocumentField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).idDocument);

                ImportField titleTerminalDocumentField = new ImportField(terminalLM.findProperty("title[TerminalDocument]"));
                props.add(new ImportProperty(titleTerminalDocumentField, terminalLM.findProperty("title[TerminalDocument]").getMapping(terminalDocumentKey)));
                fields.add(titleTerminalDocumentField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).numberDocument);

                ImportField dateTerminalDocumentField = new ImportField(terminalLM.findProperty("date[TerminalDocument]"));
                props.add(new ImportProperty(dateTerminalDocumentField, terminalLM.findProperty("date[TerminalDocument]").getMapping(terminalDocumentKey)));
                fields.add(dateTerminalDocumentField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).dateDocument);

                ImportField timeTerminalDocumentField = new ImportField(terminalLM.findProperty("time[TerminalDocument]"));
                props.add(new ImportProperty(timeTerminalDocumentField, terminalLM.findProperty("time[TerminalDocument]").getMapping(terminalDocumentKey)));
                fields.add(timeTerminalDocumentField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).timeDocument);

                ImportField commentTerminalDocumentField = new ImportField(terminalLM.findProperty("comment[TerminalDocument]"));
                props.add(new ImportProperty(commentTerminalDocumentField, terminalLM.findProperty("comment[TerminalDocument]").getMapping(terminalDocumentKey)));
                fields.add(commentTerminalDocumentField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).commentDocument);

                ImportField directoryGroupTerminalField = new ImportField(terminalLM.findProperty("directory[GroupTerminal]"));
                ImportKey<?> groupTerminalKey = new ImportKey((ConcreteCustomClass) terminalLM.findClass("GroupTerminal"),
                        terminalLM.findProperty("groupTerminalDirectory[VARSTRING[100]]").getMapping(directoryGroupTerminalField));
                keys.add(groupTerminalKey);
                props.add(new ImportProperty(directoryGroupTerminalField, terminalLM.findProperty("groupTerminal[TerminalDocument]").getMapping(terminalDocumentKey),
                        terminalLM.object(terminalLM.findClass("GroupTerminal")).getMapping(groupTerminalKey)));
                fields.add(directoryGroupTerminalField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).directoryGroupTerminal);

                ImportField idTerminalHandbookType1TerminalDocumentField = new ImportField(terminalLM.findProperty("idTerminalHandbookType1[TerminalDocument]"));
                props.add(new ImportProperty(idTerminalHandbookType1TerminalDocumentField, terminalLM.findProperty("idTerminalHandbookType1[TerminalDocument]").getMapping(terminalDocumentKey)));
                fields.add(idTerminalHandbookType1TerminalDocumentField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).idTerminalHandbookType1);

                ImportField idTerminalHandbookType2TerminalDocumentField = new ImportField(terminalLM.findProperty("idTerminalHandbookType2[TerminalDocument]"));
                props.add(new ImportProperty(idTerminalHandbookType2TerminalDocumentField, terminalLM.findProperty("idTerminalHandbookType2[TerminalDocument]").getMapping(terminalDocumentKey)));
                fields.add(idTerminalHandbookType2TerminalDocumentField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).idTerminalHandbookType2);

                ImportField idTerminalDocumentTypeField = new ImportField(terminalLM.findProperty("id[TerminalDocumentType]"));
                ImportKey<?> terminalDocumentTypeKey = new ImportKey((ConcreteCustomClass) terminalLM.findClass("TerminalDocumentType"),
                        terminalLM.findProperty("terminalDocumentType[VARSTRING[100]]").getMapping(idTerminalDocumentTypeField));
                terminalDocumentTypeKey.skipKey = true;
                keys.add(terminalDocumentTypeKey);
                props.add(new ImportProperty(idTerminalDocumentTypeField, terminalLM.findProperty("id[TerminalDocumentType]").getMapping(terminalDocumentTypeKey)));
                props.add(new ImportProperty(idTerminalDocumentTypeField, terminalLM.findProperty("terminalDocumentType[TerminalDocument]").getMapping(terminalDocumentKey),
                        terminalLM.object(terminalLM.findClass("TerminalDocumentType")).getMapping(terminalDocumentTypeKey)));
                fields.add(idTerminalDocumentTypeField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).idTerminalDocumentType);

                ImportField quantityTerminalDocumentField = new ImportField(terminalLM.findProperty("quantity[TerminalDocument]"));
                props.add(new ImportProperty(quantityTerminalDocumentField, terminalLM.findProperty("quantity[TerminalDocument]").getMapping(terminalDocumentKey)));
                fields.add(quantityTerminalDocumentField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).quantityDocument);

                ImportField idTerminalDocumentDetailField = new ImportField(terminalLM.findProperty("id[TerminalDocumentDetail]"));
                ImportKey<?> terminalDocumentDetailKey = new ImportKey((ConcreteCustomClass) terminalLM.findClass("TerminalDocumentDetail"),
                        terminalLM.findProperty("terminalIdTerminalId[VARSTRING[100],VARSTRING[100]]").getMapping(idTerminalDocumentField, idTerminalDocumentDetailField));
                keys.add(terminalDocumentDetailKey);
                props.add(new ImportProperty(idTerminalDocumentDetailField, terminalLM.findProperty("id[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                props.add(new ImportProperty(idTerminalDocumentField, terminalLM.findProperty("terminalDocument[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey),
                        terminalLM.object(terminalLM.findClass("TerminalDocument")).getMapping(terminalDocumentKey)));
                fields.add(idTerminalDocumentDetailField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).idDocumentDetail);

                ImportField numberTerminalDocumentDetailField = new ImportField(terminalLM.findProperty("number[TerminalDocumentDetail]"));
                props.add(new ImportProperty(numberTerminalDocumentDetailField, terminalLM.findProperty("number[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                fields.add(numberTerminalDocumentDetailField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(parseInteger(terminalDocumentDetailList.get(i).numberDocumentDetail));

                ImportField barcodeTerminalDocumentDetailField = new ImportField(terminalLM.findProperty("barcode[TerminalDocumentDetail]"));
                props.add(new ImportProperty(barcodeTerminalDocumentDetailField, terminalLM.findProperty("barcode[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                fields.add(barcodeTerminalDocumentDetailField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).barcodeDocumentDetail);

                ImportField nameTerminalDocumentDetailField = new ImportField(terminalLM.findProperty("name[TerminalDocumentDetail]"));
                props.add(new ImportProperty(nameTerminalDocumentDetailField, terminalLM.findProperty("name[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                fields.add(nameTerminalDocumentDetailField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).nameDocumentDetail);

                ImportField priceTerminalDocumentDetailField = new ImportField(terminalLM.findProperty("price[TerminalDocumentDetail]"));
                props.add(new ImportProperty(priceTerminalDocumentDetailField, terminalLM.findProperty("price[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                fields.add(priceTerminalDocumentDetailField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).priceDocumentDetail);

                ImportField quantityTerminalDocumentDetailField = new ImportField(terminalLM.findProperty("quantity[TerminalDocumentDetail]"));
                props.add(new ImportProperty(quantityTerminalDocumentDetailField, terminalLM.findProperty("quantity[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                fields.add(quantityTerminalDocumentDetailField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).quantityDocumentDetail);

                ImportField sumTerminalDocumentDetailField = new ImportField(terminalLM.findProperty("sum[TerminalDocumentDetail]"));
                props.add(new ImportProperty(sumTerminalDocumentDetailField, terminalLM.findProperty("sum[TerminalDocumentDetail]").getMapping(terminalDocumentDetailKey)));
                fields.add(sumTerminalDocumentDetailField);
                for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                    data.get(i).add(terminalDocumentDetailList.get(i).sumDocumentDetail);

                ImportTable table = new ImportTable(fields, data);

                try (DataSession session = getDbManager().createSession()) {
                    session.pushVolatileStats("ES_TI");
                    IntegrationService service = new IntegrationService(session, table, keys, props);
                    service.synchronize(true, false);
                    String result = session.applyMessage(getBusinessLogics());
                    session.popVolatileStats();
                    return result;
                }
            } else return null;

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private Integer parseInteger(String value) {
        try {
            return value == null || value.isEmpty() ? null : Integer.parseInt(value);
        } catch (Exception e) {
            logger.error("Error occured while parsing integer value: ", e);
            return null;
        }
    }

    @Override
    public Map<String, List<Object>> readRequestZReportSumMap(String idStock, Date dateFrom, Date dateTo) {
        Map<String, List<Object>> zReportSumMap = new HashMap<>();
        if (zReportLM != null && equipmentCashRegisterLM != null) {
            try (DataSession session = getDbManager().createSession()) {
                      
                DataObject stockObject = (DataObject) equipmentCashRegisterLM.findProperty("stock[VARSTRING[100]]").readClasses(session, new DataObject(idStock));
                
                KeyExpr zReportExpr = new KeyExpr("zReport");
                ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "zReport", zReportExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
                String[] names = new String[]{"sumReceiptDetailZReport", "numberZReport", "numberCashRegisterZReport", "dateZReport"};
                LCP<?>[] properties = zReportLM.findProperties("sumReceiptDetail[ZReport]", "number[ZReport]", "numberCashRegister[ZReport]", "date[ZReport]");
                for (int i = 0; i < properties.length; i++) {
                    query.addProperty(names[i], properties[i].getExpr(zReportExpr));
                }
                query.and(zReportLM.findProperty("date[ZReport]").getExpr(zReportExpr).compare(new DataObject(dateFrom, DateClass.instance), Compare.GREATER_EQUALS));
                query.and(zReportLM.findProperty("date[ZReport]").getExpr(zReportExpr).compare(new DataObject(dateTo, DateClass.instance), Compare.LESS_EQUALS));
                query.and(zReportLM.findProperty("departmentStore[ZReport]").getExpr(zReportExpr).compare(stockObject.getExpr(), Compare.EQUALS));
                query.and(zReportLM.findProperty("number[ZReport]").getExpr(zReportExpr).getWhere());
                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> zReportResult = query.execute(session);
                for (ImMap<Object, Object> entry : zReportResult.values()) {
                    String numberZReport = trim((String) entry.get("numberZReport"));
                    Integer numberCashRegisterZReport = (Integer) entry.get("numberCashRegisterZReport");
                    BigDecimal sumZReport = (BigDecimal) entry.get("sumReceiptDetailZReport");
                    Date dateZReport = (Date) entry.get("dateZReport");
                    zReportSumMap.put(numberZReport + "/" + numberCashRegisterZReport, Arrays.asList((Object) sumZReport, dateZReport));
                }
                
                session.apply(getBusinessLogics());
            } catch (ScriptingErrorLog.SemanticErrorException | SQLException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return zReportSumMap;
    }

    @Override
    public void logRequestZReportSumCheck(Integer idRequestExchange, Integer nppGroupMachinery, List<List<Object>> checkSumResult) throws RemoteException, SQLException {
        if (machineryPriceTransactionLM != null && cashRegisterLM != null && notNullNorEmpty(checkSumResult)) {
            try (DataSession session = getDbManager().createSession()) {
                for (List<Object> entry : checkSumResult) {
                    Object nppMachinery = entry.get(0);
                    Object message = entry.get(1);
                    DataObject logObject = session.addObject((ConcreteCustomClass) machineryPriceTransactionLM.findClass("RequestExchangeLog"));
                    ObjectValue cashRegisterObject = cashRegisterLM.findProperty("cashRegisterNppGroupCashRegister[INTEGER,INTEGER]").readClasses(session, new DataObject(nppGroupMachinery), new DataObject((Integer) nppMachinery));
                    machineryPriceTransactionLM.findProperty("date[RequestExchangeLog]").change(getCurrentTimestamp(), session, logObject);
                    machineryPriceTransactionLM.findProperty("message[RequestExchangeLog]").change(message, session, logObject);
                    machineryPriceTransactionLM.findProperty("machinery[RequestExchangeLog]").change(cashRegisterObject.getValue(), session, logObject);
                    machineryPriceTransactionLM.findProperty("requestExchange[RequestExchangeLog]").change(idRequestExchange, session, logObject);
                }
                session.apply(getBusinessLogics());
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }

    @Override
    public Map<Integer, List<List<Object>>> readCashRegistersStock(String idStock) throws RemoteException, SQLException {
        Map<Integer, List<List<Object>>> cashRegisterList = new HashMap<>();
        if(equipmentCashRegisterLM != null)
        try (DataSession session = getDbManager().createSession()) {

            DataObject stockObject = (DataObject) equipmentCashRegisterLM.findProperty("stock[VARSTRING[100]]").readClasses(session, new DataObject(idStock));

            KeyExpr cashRegisterExpr = new KeyExpr("cashRegister");
            ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "cashRegister", cashRegisterExpr);
            QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);

            String[] machineryNames = new String[] {"nppMachinery", "nppGroupMachineryMachinery", "overDirectoryMachinery", "denominationStage"};
            LCP[] machineryProperties = equipmentCashRegisterLM.findProperties("npp[Machinery]", "nppGroupMachinery[Machinery]",
                    "overDirectory[Machinery]", "nameDenominationStage[Machinery]");
            for (int i = 0; i < machineryProperties.length; i++) {
                query.addProperty(machineryNames[i], machineryProperties[i].getExpr(cashRegisterExpr));
            }
            
            query.and(equipmentCashRegisterLM.findProperty("departmentStore[CashRegister]").getExpr(cashRegisterExpr).compare(stockObject.getExpr(), Compare.EQUALS));
            query.and(equipmentCashRegisterLM.findProperty("npp[Machinery]").getExpr(cashRegisterExpr).getWhere());
            query.and(equipmentCashRegisterLM.findProperty("active[CashRegister]").getExpr(cashRegisterExpr).getWhere());
            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> zReportResult = query.execute(session);
            for (ImMap<Object, Object> entry : zReportResult.values()) {
                Integer nppMachinery = (Integer) entry.get("nppMachinery");
                Integer nppGroupMachinery = (Integer) entry.get("nppGroupMachineryMachinery");
                String overDirectoryMachinery = trim((String) entry.get("overDirectoryMachinery"));
                String denominationStage = trim((String) entry.get("denominationStage"));
                if(nppMachinery != null && nppGroupMachinery != null && overDirectoryMachinery != null) {
                    List<List<Object>> nppMachineryList = cashRegisterList.containsKey(nppGroupMachinery) ? cashRegisterList.get(nppGroupMachinery) : new ArrayList<List<Object>>();
                    nppMachineryList.add(Arrays.asList((Object) nppMachinery, overDirectoryMachinery, denominationStage));
                    cashRegisterList.put(nppGroupMachinery, nppMachineryList);
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException | SQLException | SQLHandledException e) {
            throw Throwables.propagate(e);
        }

        return cashRegisterList;
    }

    @Override
    public PromotionInfo readPromotionInfo() throws RemoteException, SQLException {
        return promotionInterface == null ? null : promotionInterface.readPromotionInfo();
    }

    @Override
    public String sendSalesInfo(List<SalesInfo> salesInfoList, String sidEquipmentServer, Integer numberAtATime) throws IOException, SQLException {
        return sendSalesInfoNonRemote(salesInfoList, sidEquipmentServer, numberAtATime);
    }


    public String sendSalesInfoNonRemote(List<SalesInfo> salesInfoList, String sidEquipmentServer, Integer numberAtATime) throws IOException, SQLException {
        try {

            if (zReportLM != null && notNullNorEmpty(salesInfoList)) {

                Collections.sort(salesInfoList, COMPARATOR);

                for (int start = 0; true;) {

                    try (DataSession session = getDbManager().createSession()) {
                        //для динамического изменения кол-ва чеков за раз. В будущем убрать numberAtATime из EqupmentSettings (затронет api)
                        ObjectValue equipmentServerObject = equLM.findProperty("sidTo[VARSTRING[20]]").readClasses(session, new DataObject(sidEquipmentServer));
                        numberAtATime = (Integer) equLM.findProperty("numberAtATime[EquipmentServer]").read(session, equipmentServerObject);
                        if (numberAtATime == null)
                            numberAtATime = salesInfoList.size();

                        Timestamp timeStart = getCurrentTimestamp();

                        int finish = (start + numberAtATime) < salesInfoList.size() ? (start + numberAtATime) : salesInfoList.size();

                        Integer lastNumberReceipt = start < finish ? salesInfoList.get(finish - 1).numberReceipt : null;
                        if (lastNumberReceipt != null) {
                            while (start < finish && salesInfoList.size() > finish && salesInfoList.get(finish).numberReceipt.equals(lastNumberReceipt))
                                finish++;
                        }

                        List<SalesInfo> data = start < finish ? salesInfoList.subList(start, finish) : new ArrayList<SalesInfo>();
                        start = finish;
                        if (!notNullNorEmpty(data))
                            return null;

                        logger.info(String.format("Sending SalesInfo from %s to %s", start, finish));

                        Date startDate = (Date) equLM.findProperty("startDate[EquipmentServer]").read(session, equipmentServerObject);
                        Boolean timeId = (Boolean) equLM.findProperty("timeId[EquipmentServer]").read(session, equipmentServerObject);

                        ImportField nppGroupMachineryField = new ImportField(zReportLM.findProperty("npp[GroupMachinery]"));
                        ImportField nppMachineryField = new ImportField(zReportLM.findProperty("npp[Machinery]"));

                        ImportField idZReportField = new ImportField(zReportLM.findProperty("id[ZReport]"));
                        ImportField numberZReportField = new ImportField(zReportLM.findProperty("number[ZReport]"));
                        ImportField dateZReportField = new ImportField(zReportLM.findProperty("date[ZReport]"));
                        ImportField timeZReportField = new ImportField(zReportLM.findProperty("time[ZReport]"));

                        ImportField idReceiptField = new ImportField(zReportLM.findProperty("id[Receipt]"));
                        ImportField numberReceiptField = new ImportField(zReportLM.findProperty("number[Receipt]"));
                        ImportField dateReceiptField = new ImportField(zReportLM.findProperty("date[Receipt]"));
                        ImportField timeReceiptField = new ImportField(zReportLM.findProperty("time[Receipt]"));
                        ImportField isPostedZReportField = new ImportField(zReportLM.findProperty("isPosted[ZReport]"));

                        ImportField idEmployeeField = new ImportField(zReportLM.findProperty("id[Employee]"));
                        ImportField firstNameContactField = new ImportField(zReportLM.findProperty("firstName[Contact]"));
                        ImportField lastNameContactField = new ImportField(zReportLM.findProperty("lastName[Contact]"));

                        ImportField idReceiptDetailField = new ImportField(zReportLM.findProperty("id[ReceiptDetail]"));
                        ImportField numberReceiptDetailField = new ImportField(zReportLM.findProperty("number[ReceiptDetail]"));
                        ImportField idBarcodeReceiptDetailField = new ImportField(zReportLM.findProperty("idBarcode[ReceiptDetail]"));

                        //sale 1
                        ImportField quantityReceiptSaleDetailField = new ImportField(zReportLM.findProperty("quantity[ReceiptSaleDetail]"));
                        ImportField priceReceiptSaleDetailField = new ImportField(zReportLM.findProperty("price[ReceiptSaleDetail]"));
                        ImportField sumReceiptSaleDetailField = new ImportField(zReportLM.findProperty("sum[ReceiptSaleDetail]"));
                        ImportField discountSumReceiptSaleDetailField = new ImportField(zReportLM.findProperty("discountSum[ReceiptSaleDetail]"));
                        ImportField discountSumSaleReceiptField = new ImportField(zReportLM.findProperty("discountSumSale[Receipt]"));

                        //return 1
                        ImportField idSaleReceiptReceiptReturnDetailField = new ImportField(zReportLM.findProperty("id[Receipt]"));
                        ImportField quantityReceiptReturnDetailField = new ImportField(zReportLM.findProperty("quantity[ReceiptReturnDetail]"));
                        ImportField priceReceiptReturnDetailField = new ImportField(zReportLM.findProperty("price[ReceiptReturnDetail]"));
                        ImportField retailSumReceiptReturnDetailField = new ImportField(zReportLM.findProperty("sum[ReceiptReturnDetail]"));
                        ImportField discountSumReceiptReturnDetailField = new ImportField(zReportLM.findProperty("discountSum[ReceiptReturnDetail]"));
                        ImportField discountSumReturnReceiptField = new ImportField(zReportLM.findProperty("discountSumReturn[Receipt]"));

                        //giftCard 1
                        ImportField priceReceiptGiftCardSaleDetailField = null;
                        ImportField sumReceiptGiftCardSaleDetailField = null;
                        ImportField idGiftCardField = null;
                        if (giftCardLM != null) {
                            priceReceiptGiftCardSaleDetailField = new ImportField(giftCardLM.findProperty("price[ReceiptGiftCardSaleDetail]"));
                            sumReceiptGiftCardSaleDetailField = new ImportField(giftCardLM.findProperty("sum[ReceiptGiftCardSaleDetail]"));
                            idGiftCardField = new ImportField(giftCardLM.findProperty("id[GiftCard]"));
                        }

                        ImportField idPaymentField = new ImportField(zReportLM.findProperty("id[Payment]"));
                        ImportField sidTypePaymentField = new ImportField(zReportLM.findProperty("sid[PaymentType]"));
                        ImportField sumPaymentField = new ImportField(zReportLM.findProperty("sum[Payment]"));
                        ImportField numberPaymentField = new ImportField(zReportLM.findProperty("number[Payment]"));

                        ImportField seriesNumberDiscountCardField = discountCardLM == null ? null : new ImportField(discountCardLM.findProperty("seriesNumber[DiscountCard]"));
                        ImportField idSectionField = zReportSectionLM == null ? null : new ImportField(zReportSectionLM.findProperty("id[Section]"));

                        List<ImportProperty<?>> saleProperties = new ArrayList<>();
                        List<ImportProperty<?>> returnProperties = new ArrayList<>();
                        List<ImportProperty<?>> giftCardProperties = new ArrayList<>();
                        List<ImportProperty<?>> paymentProperties = new ArrayList<>();

                        ImportKey<?> zReportKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("ZReport"), zReportLM.findProperty("zReport[VARSTRING[100]]").getMapping(idZReportField));
                        ImportKey<?> cashRegisterKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("CashRegister"), zReportLM.findProperty("cashRegisterNppGroupCashRegister[INTEGER,INTEGER]").getMapping(nppGroupMachineryField, nppMachineryField));
                        cashRegisterKey.skipKey = true;
                        ImportKey<?> receiptKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("Receipt"), zReportLM.findProperty("receipt[VARSTRING[100]]").getMapping(idReceiptField));
                        ImportKey<?> skuKey = new ImportKey((CustomClass) zReportLM.findClass("Sku"), zReportLM.findProperty("skuBarcode[STRING[15],DATE]").getMapping(idBarcodeReceiptDetailField, dateReceiptField));
                        ImportKey<?> employeeKey = new ImportKey((CustomClass) zReportLM.findClass("Employee"), zReportLM.findProperty("employee[VARSTRING[100]]").getMapping(idEmployeeField));

                        ImportKey<?> discountCardKey = discountCardLM == null ? null : new ImportKey((ConcreteCustomClass) discountCardLM.findClass("DiscountCard"), discountCardLM.findProperty("discountSeriesNumber[STRING[18]]").getMapping(seriesNumberDiscountCardField, dateReceiptField));
                        ImportKey<?> giftCardKey = giftCardLM == null ? null : new ImportKey((ConcreteCustomClass) giftCardLM.findClass("GiftCard"), giftCardLM.findProperty("giftCard[VARSTRING[100]]").getMapping(idGiftCardField));
                        ImportKey<?> sectionKey = zReportSectionLM == null ? null : new ImportKey((ConcreteCustomClass) zReportSectionLM.findClass("Section"), zReportSectionLM.findProperty("section[VARSTRING[100]]").getMapping(idSectionField));

                        //sale 2
                        saleProperties.add(new ImportProperty(idZReportField, zReportLM.findProperty("id[ZReport]").getMapping(zReportKey)));
                        saleProperties.add(new ImportProperty(numberZReportField, zReportLM.findProperty("number[ZReport]").getMapping(zReportKey)));
                        saleProperties.add(new ImportProperty(nppMachineryField, zReportLM.findProperty("cashRegister[ZReport]").getMapping(zReportKey),
                                zReportLM.object(zReportLM.findClass("CashRegister")).getMapping(cashRegisterKey)));
                        saleProperties.add(new ImportProperty(dateZReportField, zReportLM.findProperty("date[ZReport]").getMapping(zReportKey)));
                        saleProperties.add(new ImportProperty(timeZReportField, zReportLM.findProperty("time[ZReport]").getMapping(zReportKey)));
                        saleProperties.add(new ImportProperty(isPostedZReportField, zReportLM.findProperty("isPosted[ZReport]").getMapping(zReportKey)));

                        saleProperties.add(new ImportProperty(idReceiptField, zReportLM.findProperty("id[Receipt]").getMapping(receiptKey)));
                        saleProperties.add(new ImportProperty(numberReceiptField, zReportLM.findProperty("number[Receipt]").getMapping(receiptKey)));
                        saleProperties.add(new ImportProperty(dateReceiptField, zReportLM.findProperty("date[Receipt]").getMapping(receiptKey)));
                        saleProperties.add(new ImportProperty(timeReceiptField, zReportLM.findProperty("time[Receipt]").getMapping(receiptKey)));
                        saleProperties.add(new ImportProperty(discountSumSaleReceiptField, zReportLM.findProperty("discountSumSale[Receipt]").getMapping(receiptKey)));
                        saleProperties.add(new ImportProperty(numberZReportField, zReportLM.findProperty("zReport[Receipt]").getMapping(receiptKey),
                                zReportLM.object(zReportLM.findClass("ZReport")).getMapping(zReportKey)));
                        if (discountCardLM != null && zReportDiscountCardLM != null) {
                            saleProperties.add(new ImportProperty(seriesNumberDiscountCardField, discountCardLM.findProperty("number[DiscountCard]").getMapping(discountCardKey), true));
                            saleProperties.add(new ImportProperty(seriesNumberDiscountCardField, zReportDiscountCardLM.findProperty("discountCard[Receipt]").getMapping(receiptKey),
                                    discountCardLM.object(discountCardLM.findClass("DiscountCard")).getMapping(discountCardKey)));
                        }
                        saleProperties.add(new ImportProperty(idEmployeeField, zReportLM.findProperty("id[Employee]").getMapping(employeeKey)));
                        saleProperties.add(new ImportProperty(idEmployeeField, zReportLM.findProperty("employee[Receipt]").getMapping(receiptKey),
                                zReportLM.object(zReportLM.findClass("Employee")).getMapping(employeeKey)));
                        saleProperties.add(new ImportProperty(firstNameContactField, zReportLM.findProperty("firstName[Contact]").getMapping(employeeKey), true));
                        saleProperties.add(new ImportProperty(lastNameContactField, zReportLM.findProperty("lastName[Contact]").getMapping(employeeKey), true));

                        ImportKey<?> receiptSaleDetailKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("ReceiptSaleDetail"), zReportLM.findProperty("receiptDetail[VARSTRING[100]]").getMapping(idReceiptDetailField));
                        saleProperties.add(new ImportProperty(idReceiptDetailField, zReportLM.findProperty("id[ReceiptDetail]").getMapping(receiptSaleDetailKey)));
                        saleProperties.add(new ImportProperty(numberReceiptDetailField, zReportLM.findProperty("number[ReceiptDetail]").getMapping(receiptSaleDetailKey)));
                        saleProperties.add(new ImportProperty(idBarcodeReceiptDetailField, zReportLM.findProperty("idBarcode[ReceiptDetail]").getMapping(receiptSaleDetailKey)));
                        saleProperties.add(new ImportProperty(quantityReceiptSaleDetailField, zReportLM.findProperty("quantity[ReceiptSaleDetail]").getMapping(receiptSaleDetailKey)));
                        saleProperties.add(new ImportProperty(priceReceiptSaleDetailField, zReportLM.findProperty("price[ReceiptSaleDetail]").getMapping(receiptSaleDetailKey)));
                        saleProperties.add(new ImportProperty(sumReceiptSaleDetailField, zReportLM.findProperty("sum[ReceiptSaleDetail]").getMapping(receiptSaleDetailKey)));
                        saleProperties.add(new ImportProperty(discountSumReceiptSaleDetailField, zReportLM.findProperty("discountSum[ReceiptSaleDetail]").getMapping(receiptSaleDetailKey)));

                        saleProperties.add(new ImportProperty(idReceiptField, zReportLM.findProperty("receipt[ReceiptDetail]").getMapping(receiptSaleDetailKey),
                                zReportLM.object(zReportLM.findClass("Receipt")).getMapping(receiptKey)));

                        saleProperties.add(new ImportProperty(idBarcodeReceiptDetailField, zReportLM.findProperty("sku[ReceiptSaleDetail]").getMapping(receiptSaleDetailKey),
                                zReportLM.object(zReportLM.findClass("Sku")).getMapping(skuKey)));

                        if(zReportSectionLM != null) {
                            saleProperties.add(new ImportProperty(idSectionField, zReportSectionLM.findProperty("id[Section]").getMapping(sectionKey), true));
                            saleProperties.add(new ImportProperty(idSectionField, zReportSectionLM.findProperty("section[ReceiptDetail]").getMapping(receiptSaleDetailKey),
                                    zReportSectionLM.object(zReportSectionLM.findClass("Section")).getMapping(sectionKey)));
                        }

                        //return 2
                        returnProperties.add(new ImportProperty(idZReportField, zReportLM.findProperty("id[ZReport]").getMapping(zReportKey)));
                        returnProperties.add(new ImportProperty(numberZReportField, zReportLM.findProperty("number[ZReport]").getMapping(zReportKey)));
                        returnProperties.add(new ImportProperty(nppMachineryField, zReportLM.findProperty("cashRegister[ZReport]").getMapping(zReportKey),
                                zReportLM.object(zReportLM.findClass("CashRegister")).getMapping(cashRegisterKey)));
                        returnProperties.add(new ImportProperty(dateZReportField, zReportLM.findProperty("date[ZReport]").getMapping(zReportKey)));
                        returnProperties.add(new ImportProperty(timeZReportField, zReportLM.findProperty("time[ZReport]").getMapping(zReportKey)));
                        returnProperties.add(new ImportProperty(isPostedZReportField, zReportLM.findProperty("isPosted[ZReport]").getMapping(zReportKey)));

                        returnProperties.add(new ImportProperty(idReceiptField, zReportLM.findProperty("id[Receipt]").getMapping(receiptKey)));
                        returnProperties.add(new ImportProperty(numberReceiptField, zReportLM.findProperty("number[Receipt]").getMapping(receiptKey)));
                        returnProperties.add(new ImportProperty(dateReceiptField, zReportLM.findProperty("date[Receipt]").getMapping(receiptKey)));
                        returnProperties.add(new ImportProperty(timeReceiptField, zReportLM.findProperty("time[Receipt]").getMapping(receiptKey)));
                        if (discountCardLM != null) {
                            returnProperties.add(new ImportProperty(discountSumReturnReceiptField, zReportLM.findProperty("discountSumReturn[Receipt]").getMapping(receiptKey)));
                        }
                        returnProperties.add(new ImportProperty(numberZReportField, zReportLM.findProperty("zReport[Receipt]").getMapping(receiptKey),
                                zReportLM.object(zReportLM.findClass("ZReport")).getMapping(zReportKey)));
                        if (discountCardLM != null && zReportDiscountCardLM != null) {
                            returnProperties.add(new ImportProperty(seriesNumberDiscountCardField, discountCardLM.findProperty("number[DiscountCard]").getMapping(discountCardKey), true));
                            returnProperties.add(new ImportProperty(seriesNumberDiscountCardField, zReportDiscountCardLM.findProperty("discountCard[Receipt]").getMapping(receiptKey),
                                    discountCardLM.object(discountCardLM.findClass("DiscountCard")).getMapping(discountCardKey)));
                        }
                        returnProperties.add(new ImportProperty(idEmployeeField, zReportLM.findProperty("login[CustomUser]").getMapping(employeeKey), true));
                        returnProperties.add(new ImportProperty(idEmployeeField, zReportLM.findProperty("employee[Receipt]").getMapping(receiptKey),
                                zReportLM.object(zReportLM.findClass("CustomUser")).getMapping(employeeKey)));
                        returnProperties.add(new ImportProperty(firstNameContactField, zReportLM.findProperty("firstName[Contact]").getMapping(employeeKey), true));
                        returnProperties.add(new ImportProperty(lastNameContactField, zReportLM.findProperty("lastName[Contact]").getMapping(employeeKey), true));

                        ImportKey<?> receiptReturnDetailKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("ReceiptReturnDetail"), zReportLM.findProperty("receiptDetail[VARSTRING[100]]").getMapping(idReceiptDetailField));
                        returnProperties.add(new ImportProperty(idReceiptDetailField, zReportLM.findProperty("id[ReceiptDetail]").getMapping(receiptReturnDetailKey)));
                        returnProperties.add(new ImportProperty(numberReceiptDetailField, zReportLM.findProperty("number[ReceiptDetail]").getMapping(receiptReturnDetailKey)));
                        returnProperties.add(new ImportProperty(idBarcodeReceiptDetailField, zReportLM.findProperty("idBarcode[ReceiptDetail]").getMapping(receiptReturnDetailKey)));
                        returnProperties.add(new ImportProperty(quantityReceiptReturnDetailField, zReportLM.findProperty("quantity[ReceiptReturnDetail]").getMapping(receiptReturnDetailKey)));
                        returnProperties.add(new ImportProperty(priceReceiptReturnDetailField, zReportLM.findProperty("price[ReceiptReturnDetail]").getMapping(receiptReturnDetailKey)));
                        returnProperties.add(new ImportProperty(retailSumReceiptReturnDetailField, zReportLM.findProperty("sum[ReceiptReturnDetail]").getMapping(receiptReturnDetailKey)));
                        returnProperties.add(new ImportProperty(discountSumReceiptReturnDetailField, zReportLM.findProperty("discountSum[ReceiptReturnDetail]").getMapping(receiptReturnDetailKey)));
                        returnProperties.add(new ImportProperty(idReceiptField, zReportLM.findProperty("receipt[ReceiptDetail]").getMapping(receiptReturnDetailKey),
                                zReportLM.object(zReportLM.findClass("Receipt")).getMapping(receiptKey)));

                        returnProperties.add(new ImportProperty(idBarcodeReceiptDetailField, zReportLM.findProperty("sku[ReceiptReturnDetail]").getMapping(receiptReturnDetailKey),
                                zReportLM.object(zReportLM.findClass("Sku")).getMapping(skuKey)));

                        ImportKey<?> receiptSaleDetailReceiptReturnDetailKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("Receipt"), zReportLM.findProperty("receipt[VARSTRING[100]]").getMapping(idSaleReceiptReceiptReturnDetailField));
                        receiptSaleDetailReceiptReturnDetailKey.skipKey = true;
                        returnProperties.add(new ImportProperty(idSaleReceiptReceiptReturnDetailField, zReportLM.findProperty("saleReceipt[ReceiptReturnDetail]").getMapping(receiptReturnDetailKey),
                                zReportLM.object(zReportLM.findClass("Receipt")).getMapping(receiptSaleDetailReceiptReturnDetailKey)));

                        if(zReportSectionLM != null) {
                            returnProperties.add(new ImportProperty(idSectionField, zReportSectionLM.findProperty("id[Section]").getMapping(sectionKey), true));
                            returnProperties.add(new ImportProperty(idSectionField, zReportSectionLM.findProperty("section[ReceiptDetail]").getMapping(receiptReturnDetailKey),
                                    zReportSectionLM.object(zReportSectionLM.findClass("Section")).getMapping(sectionKey)));
                        }

                        //giftCard 2
                        ImportKey<?> receiptGiftCardSaleDetailKey = null;
                        if (giftCardLM != null) {
                            giftCardProperties.add(new ImportProperty(idZReportField, zReportLM.findProperty("id[ZReport]").getMapping(zReportKey)));
                            giftCardProperties.add(new ImportProperty(numberZReportField, zReportLM.findProperty("number[ZReport]").getMapping(zReportKey)));
                            giftCardProperties.add(new ImportProperty(nppMachineryField, zReportLM.findProperty("cashRegister[ZReport]").getMapping(zReportKey),
                                    zReportLM.object(zReportLM.findClass("CashRegister")).getMapping(cashRegisterKey)));
                            giftCardProperties.add(new ImportProperty(dateZReportField, zReportLM.findProperty("date[ZReport]").getMapping(zReportKey)));
                            giftCardProperties.add(new ImportProperty(timeZReportField, zReportLM.findProperty("time[ZReport]").getMapping(zReportKey)));
                            giftCardProperties.add(new ImportProperty(isPostedZReportField, zReportLM.findProperty("isPosted[ZReport]").getMapping(zReportKey)));

                            giftCardProperties.add(new ImportProperty(idReceiptField, zReportLM.findProperty("id[Receipt]").getMapping(receiptKey)));
                            giftCardProperties.add(new ImportProperty(numberReceiptField, zReportLM.findProperty("number[Receipt]").getMapping(receiptKey)));
                            giftCardProperties.add(new ImportProperty(dateReceiptField, zReportLM.findProperty("date[Receipt]").getMapping(receiptKey)));
                            giftCardProperties.add(new ImportProperty(timeReceiptField, zReportLM.findProperty("time[Receipt]").getMapping(receiptKey)));

                            giftCardProperties.add(new ImportProperty(numberZReportField, zReportLM.findProperty("zReport[Receipt]").getMapping(receiptKey),
                                    zReportLM.object(zReportLM.findClass("ZReport")).getMapping(zReportKey)));
                            giftCardProperties.add(new ImportProperty(idEmployeeField, zReportLM.findProperty("login[CustomUser]").getMapping(employeeKey), true));
                            giftCardProperties.add(new ImportProperty(idEmployeeField, zReportLM.findProperty("employee[Receipt]").getMapping(receiptKey),
                                    zReportLM.object(zReportLM.findClass("CustomUser")).getMapping(employeeKey)));
                            giftCardProperties.add(new ImportProperty(firstNameContactField, zReportLM.findProperty("firstName[Contact]").getMapping(employeeKey), true));
                            giftCardProperties.add(new ImportProperty(lastNameContactField, zReportLM.findProperty("lastName[Contact]").getMapping(employeeKey), true));

                            receiptGiftCardSaleDetailKey = new ImportKey((ConcreteCustomClass) giftCardLM.findClass("ReceiptGiftCardSaleDetail"), zReportLM.findProperty("receiptDetail[VARSTRING[100]]").getMapping(idReceiptDetailField));
                            giftCardProperties.add(new ImportProperty(idReceiptDetailField, zReportLM.findProperty("id[ReceiptDetail]").getMapping(receiptGiftCardSaleDetailKey)));
                            giftCardProperties.add(new ImportProperty(numberReceiptDetailField, zReportLM.findProperty("number[ReceiptDetail]").getMapping(receiptGiftCardSaleDetailKey)));
                            giftCardProperties.add(new ImportProperty(priceReceiptGiftCardSaleDetailField, giftCardLM.findProperty("price[ReceiptGiftCardSaleDetail]").getMapping(receiptGiftCardSaleDetailKey)));
                            giftCardProperties.add(new ImportProperty(sumReceiptGiftCardSaleDetailField, giftCardLM.findProperty("sum[ReceiptGiftCardSaleDetail]").getMapping(receiptGiftCardSaleDetailKey)));
                            giftCardProperties.add(new ImportProperty(idReceiptField, zReportLM.findProperty("receipt[ReceiptDetail]").getMapping(receiptGiftCardSaleDetailKey),
                                    zReportLM.object(zReportLM.findClass("Receipt")).getMapping(receiptKey)));

                            giftCardProperties.add(new ImportProperty(idGiftCardField, giftCardLM.findProperty("id[GiftCard]").getMapping(giftCardKey)));
                            giftCardProperties.add(new ImportProperty(idGiftCardField, giftCardLM.findProperty("number[GiftCard]").getMapping(giftCardKey)));
                            giftCardProperties.add(new ImportProperty(idGiftCardField, giftCardLM.findProperty("giftCard[ReceiptGiftCardSaleDetail]").getMapping(receiptGiftCardSaleDetailKey),
                                    zReportLM.object(giftCardLM.findClass("GiftCard")).getMapping(giftCardKey)));
                        }

                        List<List<Object>> dataSale = new ArrayList<>();
                        List<List<Object>> dataReturn = new ArrayList<>();
                        List<List<Object>> dataGiftCard = new ArrayList<>();

                        List<List<Object>> dataPayment = new ArrayList<>();

                        Map<Object, String> barcodeMap = new HashMap<>();
                        for (SalesInfo sale : data) {

                            String barcode = (notNullNorEmpty(sale.barcodeItem)) ? sale.barcodeItem :
                                    (sale.itemObject != null ? barcodeMap.get(sale.itemObject) : sale.idItem != null ? barcodeMap.get(sale.idItem) : null);
                            if (barcode == null && sale.itemObject != null) {
                                barcode = trim((String) itemLM.findProperty("idBarcode[Sku]").read(session, new DataObject(sale.itemObject, (ConcreteClass) itemLM.findClass("Item"))));
                                barcodeMap.put(sale.itemObject, barcode);
                            }
                            if (barcode == null && sale.idItem != null) {
                                barcode = trim((String) itemLM.findProperty("idBarcodeSku[VARSTRING[100]]").read(session, new DataObject(sale.idItem, StringClass.get((100)))));
                                barcodeMap.put(sale.idItem, barcode);
                            }

                            String idReceipt = sale.getIdReceipt(startDate, timeId);
                            if (sale.isGiftCard) {
                                //giftCard 3
                                List<Object> row = Arrays.<Object>asList(sale.nppGroupMachinery, sale.nppMachinery, sale.getIdZReport(startDate), sale.numberZReport,
                                        sale.dateZReport, sale.timeZReport, sale.dateReceipt, sale.timeReceipt, true, sale.idEmployee, sale.firstNameContact, sale.lastNameContact,
                                        idReceipt, sale.numberReceipt, sale.getIdReceiptDetail(startDate, timeId), sale.numberReceiptDetail, barcode,
                                        sale.priceReceiptDetail, sale.sumReceiptDetail);
                                dataGiftCard.add(row);
                            } else if (sale.quantityReceiptDetail.doubleValue() < 0) {
                                //return 3
                                List<Object> row = Arrays.<Object>asList(sale.nppGroupMachinery, sale.nppMachinery, sale.getIdZReport(startDate), sale.numberZReport,
                                        sale.dateZReport, sale.timeZReport, sale.dateReceipt, sale.timeReceipt, true, sale.idEmployee, sale.firstNameContact, sale.lastNameContact,
                                        idReceipt, sale.numberReceipt, sale.getIdReceiptDetail(startDate, timeId), sale.numberReceiptDetail, barcode, sale.quantityReceiptDetail.negate(),
                                        sale.priceReceiptDetail, sale.sumReceiptDetail.negate(), sale.discountSumReceiptDetail, sale.discountSumReceipt, sale.idSaleReceiptReceiptReturnDetail);
                                if (discountCardLM != null) {
                                    row = new ArrayList<>(row);
                                    row.add(sale.seriesNumberDiscountCard);
                                }
                                if (zReportSectionLM != null) {
                                    row = new ArrayList<>(row);
                                    row.add(sale.idSection);
                                }
                                dataReturn.add(row);
                            } else {
                                //sale 3
                                List<Object> row = Arrays.<Object>asList(sale.nppGroupMachinery, sale.nppMachinery, sale.getIdZReport(startDate), sale.numberZReport,
                                        sale.dateZReport, sale.timeZReport, sale.dateReceipt, sale.timeReceipt, true, sale.idEmployee, sale.firstNameContact, sale.lastNameContact,
                                        idReceipt, sale.numberReceipt, sale.getIdReceiptDetail(startDate, timeId), sale.numberReceiptDetail, barcode, sale.quantityReceiptDetail,
                                        sale.priceReceiptDetail, sale.sumReceiptDetail, sale.discountSumReceiptDetail, sale.discountSumReceipt);
                                if (discountCardLM != null) {
                                    row = new ArrayList<>(row);
                                    row.add(sale.seriesNumberDiscountCard);
                                }
                                if (zReportSectionLM != null) {
                                    row = new ArrayList<>(row);
                                    row.add(sale.idSection);
                                }
                                dataSale.add(row);
                            }
                            if (sale.sumCash != null && sale.sumCash.doubleValue() != 0) {
                                dataPayment.add(Arrays.<Object>asList(idReceipt + "1", idReceipt, "cash", sale.sumCash, 1));
                            }
                            if (sale.sumCard != null && sale.sumCard.doubleValue() != 0) {
                                dataPayment.add(Arrays.<Object>asList(idReceipt + "2", idReceipt, "card", sale.sumCard, 2));
                            }
                            if (sale.sumGiftCard != null && sale.sumGiftCard.doubleValue() != 0) {
                                dataPayment.add(Arrays.<Object>asList(idReceipt + "3", idReceipt, "giftcard", sale.sumGiftCard, 3));
                            }
                        }

                        //sale 4
                        List<ImportField> saleImportFields = Arrays.asList(nppGroupMachineryField, nppMachineryField,
                                idZReportField, numberZReportField, dateZReportField, timeZReportField, dateReceiptField, timeReceiptField, isPostedZReportField,
                                idEmployeeField, firstNameContactField, lastNameContactField, idReceiptField, numberReceiptField,
                                idReceiptDetailField, numberReceiptDetailField, idBarcodeReceiptDetailField,
                                quantityReceiptSaleDetailField, priceReceiptSaleDetailField, sumReceiptSaleDetailField,
                                discountSumReceiptSaleDetailField, discountSumSaleReceiptField);
                        if (discountCardLM != null) {
                            saleImportFields = new ArrayList<>(saleImportFields);
                            saleImportFields.add(seriesNumberDiscountCardField);
                        }
                        if (zReportSectionLM != null) {
                            saleImportFields = new ArrayList<>(saleImportFields);
                            saleImportFields.add(idSectionField);
                        }

                        //return 4
                        List<ImportField> returnImportFields = Arrays.asList(nppGroupMachineryField, nppMachineryField,
                                idZReportField, numberZReportField, dateZReportField, timeZReportField, dateReceiptField, timeReceiptField, isPostedZReportField,
                                idEmployeeField, firstNameContactField, lastNameContactField, idReceiptField, numberReceiptField,
                                idReceiptDetailField, numberReceiptDetailField, idBarcodeReceiptDetailField,
                                quantityReceiptReturnDetailField, priceReceiptReturnDetailField, retailSumReceiptReturnDetailField,
                                discountSumReceiptReturnDetailField, discountSumReturnReceiptField, idSaleReceiptReceiptReturnDetailField);
                        if (discountCardLM != null) {
                            returnImportFields = new ArrayList<>(returnImportFields);
                            returnImportFields.add(seriesNumberDiscountCardField);
                        }
                        if (zReportSectionLM != null) {
                            returnImportFields = new ArrayList<>(returnImportFields);
                            returnImportFields.add(idSectionField);
                        }

                        //giftCard 4
                        List<ImportField> giftCardImportFields = Arrays.asList(nppGroupMachineryField, nppMachineryField,
                                idZReportField, numberZReportField, dateZReportField, timeZReportField, dateReceiptField, timeReceiptField, isPostedZReportField,
                                idEmployeeField, firstNameContactField, lastNameContactField, idReceiptField, numberReceiptField,
                                idReceiptDetailField, numberReceiptDetailField, idGiftCardField,
                                priceReceiptGiftCardSaleDetailField, sumReceiptGiftCardSaleDetailField);

                        //sale 5
                        List<ImportKey<?>> saleKeys = Arrays.asList(zReportKey, cashRegisterKey, receiptKey, receiptSaleDetailKey, skuKey, employeeKey);
                        if (discountCardLM != null) {
                            saleKeys = new ArrayList<>(saleKeys);
                            saleKeys.add(discountCardKey);
                        }
                        if (zReportSectionLM != null) {
                            saleKeys = new ArrayList<>(saleKeys);
                            saleKeys.add(sectionKey);
                        }
                        new IntegrationService(session, new ImportTable(saleImportFields, dataSale), saleKeys, saleProperties).synchronize(true);

                        //return 5
                        List<ImportKey<?>> returnKeys = Arrays.asList(zReportKey, cashRegisterKey, receiptKey, receiptReturnDetailKey, skuKey, employeeKey, receiptSaleDetailReceiptReturnDetailKey);
                        if (discountCardLM != null) {
                            returnKeys = new ArrayList<>(returnKeys);
                            returnKeys.add(discountCardKey);
                        }
                        if (zReportSectionLM != null) {
                            returnKeys = new ArrayList<>(returnKeys);
                            returnKeys.add(sectionKey);
                        }
                        new IntegrationService(session, new ImportTable(returnImportFields, dataReturn), returnKeys, returnProperties).synchronize(true);

                        //giftCard 5
                        if (giftCardLM != null) {
                            List<ImportKey<?>> giftCardKeys = Arrays.asList(zReportKey, cashRegisterKey, receiptKey, receiptGiftCardSaleDetailKey, giftCardKey, employeeKey);
                            new IntegrationService(session, new ImportTable(giftCardImportFields, dataGiftCard), giftCardKeys, giftCardProperties).synchronize(true);
                        }

                        ImportKey<?> paymentKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("ZReport.Payment"), zReportLM.findProperty("payment[VARSTRING[100]]").getMapping(idPaymentField));
                        ImportKey<?> paymentTypeKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("PaymentType"), zReportLM.findProperty("typePaymentSID[STRING[10]]").getMapping(sidTypePaymentField));
                        paymentProperties.add(new ImportProperty(idPaymentField, zReportLM.findProperty("id[Payment]").getMapping(paymentKey)));
                        paymentProperties.add(new ImportProperty(sumPaymentField, zReportLM.findProperty("sum[Payment]").getMapping(paymentKey)));
                        paymentProperties.add(new ImportProperty(numberPaymentField, zReportLM.findProperty("number[Payment]").getMapping(paymentKey)));
                        paymentProperties.add(new ImportProperty(sidTypePaymentField, zReportLM.findProperty("paymentType[Payment]").getMapping(paymentKey),
                                zReportLM.object(zReportLM.findClass("PaymentType")).getMapping(paymentTypeKey)));
                        paymentProperties.add(new ImportProperty(idReceiptField, zReportLM.findProperty("receipt[Payment]").getMapping(paymentKey),
                                zReportLM.object(zReportLM.findClass("Receipt")).getMapping(receiptKey)));

                        List<ImportField> paymentImportFields = Arrays.asList(idPaymentField, idReceiptField, sidTypePaymentField,
                                sumPaymentField, numberPaymentField);

                        new IntegrationService(session, new ImportTable(paymentImportFields, dataPayment), Arrays.asList(paymentKey, paymentTypeKey, receiptKey),
                                paymentProperties).synchronize(true);

                        session.setKeepLastAttemptCountMap(true);
                        String result = session.applyMessage(getBusinessLogics());
                        if (result == null) {
                            logCompleteMessage(session, data, dataSale.size() + dataReturn.size() + dataGiftCard.size(), timeStart, sidEquipmentServer);
                        } else
                            return result;
                    }
                }
            } else return null;

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
    
    private String logCompleteMessage(DataSession mainSession, List<SalesInfo> data, int dataSize, Timestamp timeStart, String sidEquipmentServer) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        
        String message = formatCompleteMessage(mainSession, data, dataSize, timeStart);
        
        try (DataSession session = getDbManager().createSession()) {
            ObjectValue equipmentServerObject = equLM.findProperty("sidTo[VARSTRING[20]]").readClasses(session, new DataObject(sidEquipmentServer));
            DataObject logObject = session.addObject((ConcreteCustomClass) equLM.findClass("EquipmentServerLog"));
            equLM.findProperty("equipmentServer[EquipmentServerLog]").change(equipmentServerObject.getValue(), session, logObject);
            equLM.findProperty("data[EquipmentServerLog]").change(message, session, logObject);
            equLM.findProperty("date[EquipmentServerLog]").change(getCurrentTimestamp(), session, logObject);
            return session.applyMessage(getBusinessLogics());
        }
    }
    
    private String formatCompleteMessage(DataSession session, List<SalesInfo> data, int dataSize, Timestamp timeStart) {

        String conflicts = session.getLastAttemptCountMap();
        Timestamp timeFinish = getCurrentTimestamp();
        String message = String.format("Затрачено времени: %s с (%s - %s)\nЗагружено записей: %s",  
                (timeFinish.getTime() - timeStart.getTime())/1000, formatDateTime(timeStart), formatDateTime(timeFinish), dataSize);
        if(conflicts != null)
            message += "\nКонфликты: " + conflicts;
        
        Map<Integer, Set<Integer>> nppCashRegisterMap = new HashMap<>();
        List<String> fileNames = new ArrayList<>();
        Set<String> dates = new HashSet<>();
        for (SalesInfo salesInfo : data) {
            if(nppCashRegisterMap.containsKey(salesInfo.nppGroupMachinery))
                nppCashRegisterMap.get(salesInfo.nppGroupMachinery).add(salesInfo.nppMachinery);
            else
                nppCashRegisterMap.put(salesInfo.nppGroupMachinery, new HashSet<>(Collections.singletonList(salesInfo.nppMachinery)));
            if ((salesInfo.filename != null) && (!fileNames.contains(salesInfo.filename.trim())))
                fileNames.add(salesInfo.filename.trim());
            if(salesInfo.dateZReport != null)
                dates.add(formatDate(salesInfo.dateZReport));
        }
        
        message += "\nИз касс: ";
        for (Map.Entry<Integer, Set<Integer>> cashRegisterEntry : nppCashRegisterMap.entrySet()) {
            for(Integer cashRegister : cashRegisterEntry.getValue())
                message += String.format("%s(%s), ", cashRegister, cashRegisterEntry.getKey());
        }
        message = message.substring(0, message.length() - 2);

        if(!fileNames.isEmpty()) {
            message += "\nИз файлов: ";
            for (String filename : fileNames)
                message += filename + ", ";
            message = message.substring(0, message.length() - 2);
        }

        if(notNullNorEmpty(dates)) {
            message += "\nЗа даты: ";
            for (String date : dates)
                message += date + ", ";
            message = message.substring(0, message.length() - 2);
        }
        return message;
    }

    @Override
    public Set<String> readCashDocumentSet(String sidEquipmentServer) throws IOException, SQLException {

        Set<String> cashDocumentSet = new HashSet<>();
        if (collectionLM != null) {
            try (DataSession session = getDbManager().createSession()) {

                KeyExpr cashDocumentExpr = new KeyExpr("cashDocument");
                ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "CashDocument", cashDocumentExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
                query.addProperty("idCashDocument", collectionLM.findProperty("id[CashDocument]").getExpr(cashDocumentExpr));
                query.and(collectionLM.findProperty("id[CashDocument]").getExpr(cashDocumentExpr).getWhere());
                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);

                for (ImMap<Object, Object> row : result.values()) {
                    cashDocumentSet.add((String) row.get("idCashDocument"));
                }
            } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return cashDocumentSet;
    }

    @Override
    public String sendCashDocumentInfo(List<CashDocument> cashDocumentList, String sidEquipmentServer) throws IOException, SQLException {

        if (collectionLM != null && cashDocumentList != null) {

            try {

                List<ImportField> fieldsIncome = new ArrayList<>();
                List<ImportField> fieldsOutcome = new ArrayList<>();

                List<ImportProperty<?>> propsIncome = new ArrayList<>();
                List<ImportProperty<?>> propsOutcome = new ArrayList<>();

                List<ImportKey<?>> keysIncome = new ArrayList<>();
                List<ImportKey<?>> keysOutcome = new ArrayList<>();
                
                List<List<Object>> dataIncome = new ArrayList<>();
                List<List<Object>> dataOutcome = new ArrayList<>();

                ImportField idCashDocumentField = new ImportField(collectionLM.findProperty("id[CashDocument]"));
                
                ImportKey<?> incomeCashOperationKey = new ImportKey((CustomClass) collectionLM.findClass("IncomeCashOperation"),
                        collectionLM.findProperty("cashDocument[VARSTRING[100]]").getMapping(idCashDocumentField));
                keysIncome.add(incomeCashOperationKey);
                propsIncome.add(new ImportProperty(idCashDocumentField, collectionLM.findProperty("id[CashDocument]").getMapping(incomeCashOperationKey)));
                //propsIncome.add(new ImportProperty(idCashDocumentField, collectionLM.findProperty("numberIncomeCashOperation").getMapping(incomeCashOperationKey)));
                fieldsIncome.add(idCashDocumentField);

                ImportKey<?> outcomeCashOperationKey = new ImportKey((CustomClass) collectionLM.findClass("OutcomeCashOperation"),
                        collectionLM.findProperty("cashDocument[VARSTRING[100]]").getMapping(idCashDocumentField));
                keysOutcome.add(outcomeCashOperationKey);
                propsOutcome.add(new ImportProperty(idCashDocumentField, collectionLM.findProperty("id[CashDocument]").getMapping(outcomeCashOperationKey)));
                //propsOutcome.add(new ImportProperty(idCashDocumentField, collectionLM.findProperty("numberOutcomeCashOperation").getMapping(outcomeCashOperationKey)));                
                fieldsOutcome.add(idCashDocumentField);

                ImportField numberIncomeCashOperationField = new ImportField(collectionLM.findProperty("number[IncomeCashOperation]"));
                propsIncome.add(new ImportProperty(numberIncomeCashOperationField, collectionLM.findProperty("number[IncomeCashOperation]").getMapping(incomeCashOperationKey)));
                fieldsIncome.add(numberIncomeCashOperationField);

                ImportField numberOutcomeCashOperationField = new ImportField(collectionLM.findProperty("number[OutcomeCashOperation]"));
                propsOutcome.add(new ImportProperty(numberOutcomeCashOperationField, collectionLM.findProperty("number[OutcomeCashOperation]").getMapping(outcomeCashOperationKey)));
                fieldsOutcome.add(numberOutcomeCashOperationField);
                
                ImportField dateIncomeCashOperationField = new ImportField(collectionLM.findProperty("date[IncomeCashOperation]"));
                propsIncome.add(new ImportProperty(dateIncomeCashOperationField, collectionLM.findProperty("date[IncomeCashOperation]").getMapping(incomeCashOperationKey)));
                fieldsIncome.add(dateIncomeCashOperationField);

                ImportField dateOutcomeCashOperationField = new ImportField(collectionLM.findProperty("date[OutcomeCashOperation]"));
                propsOutcome.add(new ImportProperty(dateOutcomeCashOperationField, collectionLM.findProperty("date[OutcomeCashOperation]").getMapping(outcomeCashOperationKey)));
                fieldsOutcome.add(dateOutcomeCashOperationField);

                ImportField timeIncomeCashOperationField = new ImportField(collectionLM.findProperty("time[IncomeCashOperation]"));
                propsIncome.add(new ImportProperty(timeIncomeCashOperationField, collectionLM.findProperty("time[IncomeCashOperation]").getMapping(incomeCashOperationKey)));
                fieldsIncome.add(timeIncomeCashOperationField);
                
                ImportField timeOutcomeCashOperationField = new ImportField(collectionLM.findProperty("time[OutcomeCashOperation]"));
                propsOutcome.add(new ImportProperty(timeOutcomeCashOperationField, collectionLM.findProperty("time[OutcomeCashOperation]").getMapping(outcomeCashOperationKey)));
                fieldsOutcome.add(timeOutcomeCashOperationField);

                ImportField nppGroupMachineryField = new ImportField(collectionLM.findProperty("npp[GroupMachinery]"));
                ImportField nppMachineryField = new ImportField(collectionLM.findProperty("npp[Machinery]"));
                ImportKey<?> cashRegisterKey = new ImportKey((ConcreteCustomClass) collectionLM.findClass("CashRegister"),
                        zReportLM.findProperty("cashRegisterNppGroupCashRegister[INTEGER,INTEGER]").getMapping(nppGroupMachineryField, nppMachineryField/*, sidEquipmentServerField*/));
                
                keysIncome.add(cashRegisterKey);
                propsIncome.add(new ImportProperty(nppMachineryField, collectionLM.findProperty("cashRegister[IncomeCashOperation]").getMapping(incomeCashOperationKey),
                        collectionLM.object(collectionLM.findClass("CashRegister")).getMapping(cashRegisterKey)));
                fieldsIncome.add(nppGroupMachineryField);
                fieldsIncome.add(nppMachineryField);
                
                keysOutcome.add(cashRegisterKey);
                propsOutcome.add(new ImportProperty(nppMachineryField, collectionLM.findProperty("cashRegister[OutcomeCashOperation]").getMapping(outcomeCashOperationKey),
                        collectionLM.object(collectionLM.findClass("CashRegister")).getMapping(cashRegisterKey)));             
                fieldsOutcome.add(nppGroupMachineryField);
                fieldsOutcome.add(nppMachineryField);
                
                ImportField sumCashIncomeCashOperationField = new ImportField(collectionLM.findProperty("sumCash[IncomeCashOperation]"));
                propsIncome.add(new ImportProperty(sumCashIncomeCashOperationField, collectionLM.findProperty("sumCash[IncomeCashOperation]").getMapping(incomeCashOperationKey)));
                fieldsIncome.add(sumCashIncomeCashOperationField);

                ImportField sumCashOutcomeCashOperationField = new ImportField(collectionLM.findProperty("sumCash[OutcomeCashOperation]"));
                propsOutcome.add(new ImportProperty(sumCashOutcomeCashOperationField, collectionLM.findProperty("sumCash[OutcomeCashOperation]").getMapping(outcomeCashOperationKey)));
                fieldsOutcome.add(sumCashOutcomeCashOperationField);

                for (CashDocument cashDocument : cashDocumentList) {
                    if (cashDocument.sumCashDocument != null) {
                        if (cashDocument.sumCashDocument.compareTo(BigDecimal.ZERO) >= 0)
                            dataIncome.add(Arrays.asList((Object) cashDocument.idCashDocument, cashDocument.numberCashDocument, cashDocument.dateCashDocument,
                                    cashDocument.timeCashDocument, cashDocument.nppGroupMachinery, cashDocument.nppMachinery, cashDocument.sumCashDocument));
                        else
                            dataOutcome.add(Arrays.asList((Object) cashDocument.idCashDocument, cashDocument.numberCashDocument, cashDocument.dateCashDocument,
                                    cashDocument.timeCashDocument, cashDocument.nppGroupMachinery, cashDocument.nppMachinery, cashDocument.sumCashDocument.negate()));
                    }
                }
                
                
                ImportTable table = new ImportTable(fieldsIncome, dataIncome);
                String resultIncome;
                try (DataSession session = getDbManager().createSession()) {
                    session.pushVolatileStats("ES_CDI");
                    IntegrationService service = new IntegrationService(session, table, keysIncome, propsIncome);
                    service.synchronize(true, false);
                    resultIncome = session.applyMessage(getBusinessLogics());
                    session.popVolatileStats();
                }
                if(resultIncome != null)
                    return resultIncome;
                
                table = new ImportTable(fieldsOutcome, dataOutcome);
                String resultOutcome;

                try (DataSession session = getDbManager().createSession()) {
                    session.pushVolatileStats("ES_CDI");
                    IntegrationService service = new IntegrationService(session, table, keysOutcome, propsOutcome);
                    service.synchronize(true, false);
                    resultOutcome = session.applyMessage(getBusinessLogics());
                    session.popVolatileStats();
                }

                return resultOutcome;
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        } else return null;
    }

    @Override
    public void succeedTransaction(Integer transactionId, Timestamp dateTime) throws RemoteException, SQLException {
        synchronized (this) {
            try (DataSession session = getDbManager().createSession()) {
                DataObject transactionObject = session.getDataObject(equLM.findClass("MachineryPriceTransaction"), transactionId);
                equLM.findProperty("succeeded[MachineryPriceTransaction]").change(true, session, transactionObject);
                equLM.findProperty("dateTimeSucceeded[MachineryPriceTransaction]").change(dateTime, session, transactionObject);
                session.apply(getBusinessLogics());
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }

    @Override
    public void processingTransaction(Integer transactionId, Timestamp dateTime) throws RemoteException, SQLException {
        if (machineryPriceTransactionLM != null) {
            try (DataSession session = getDbManager().createSession()) {
                DataObject transactionObject = session.getDataObject(equLM.findClass("MachineryPriceTransaction"), transactionId);
                machineryPriceTransactionLM.findProperty("dateTimeProcessing[MachineryPriceTransaction]").change(dateTime, session, transactionObject);
                session.apply(getBusinessLogics());
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }

    @Override
    public void clearedMachineryTransaction(Integer transactionId, List<MachineryInfo> machineryInfoList) throws RemoteException, SQLException {
        if(machineryPriceTransactionLM != null) {
            try (DataSession session = getDbManager().createSession()) {
                DataObject transactionObject = session.getDataObject(equLM.findClass("MachineryPriceTransaction"), transactionId);
                for (MachineryInfo machineryInfo : machineryInfoList) {
                    ObjectValue machineryObject = null;
                    if (machineryInfo instanceof CashRegisterInfo && cashRegisterLM != null)
                        machineryObject = cashRegisterLM.findProperty("cashRegisterNppGroupCashRegister[INTEGER,INTEGER]").readClasses(session, new DataObject(machineryInfo.numberGroup), new DataObject(machineryInfo.number));
                    else if (machineryInfo instanceof ScalesInfo && scalesLM != null)
                        machineryObject = scalesLM.findProperty("scalesNppGroupScales[INTEGER,INTEGER]").readClasses(session, new DataObject(machineryInfo.numberGroup), new DataObject(machineryInfo.number));
                    if (machineryObject != null && !machineryInfo.cleared)
                        machineryPriceTransactionLM.findProperty("cleared[Machinery,MachineryPriceTransaction]").change(true, session, (DataObject) machineryObject, transactionObject);
                }
                session.apply(getBusinessLogics());
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }

    @Override
    public void succeedMachineryTransaction(Integer transactionId, List<MachineryInfo> machineryInfoList, Timestamp dateTime) throws RemoteException, SQLException {
        synchronized (this) {
            if (machineryPriceTransactionLM != null) {
                try (DataSession session = getDbManager().createSession()) {
                    DataObject transactionObject = session.getDataObject(equLM.findClass("MachineryPriceTransaction"), transactionId);
                    for (MachineryInfo machineryInfo : machineryInfoList) {
                        ObjectValue machineryObject = null;
                        if (machineryInfo instanceof CashRegisterInfo && cashRegisterLM != null)
                            machineryObject = cashRegisterLM.findProperty("cashRegisterNppGroupCashRegister[INTEGER,INTEGER]").readClasses(session, new DataObject(machineryInfo.numberGroup), new DataObject(machineryInfo.number));
                        else if (machineryInfo instanceof ScalesInfo && scalesLM != null)
                            machineryObject = scalesLM.findProperty("scalesNppGroupScales[INTEGER,INTEGER]").readClasses(session, new DataObject(machineryInfo.numberGroup), new DataObject(machineryInfo.number));
                        if (machineryObject != null && (!(machineryInfo instanceof CashRegisterInfo) || !((CashRegisterInfo) machineryInfo).succeeded)) {
                            boolean alreadySucceeded = machineryPriceTransactionLM.findProperty("succeeded[Machinery,MachineryPriceTransaction]").read(session, machineryObject, transactionObject) != null;
                            if (!alreadySucceeded) {
                                machineryPriceTransactionLM.findProperty("succeeded[Machinery,MachineryPriceTransaction]").change(true, session,
                                        (DataObject) machineryObject, transactionObject);
                                machineryPriceTransactionLM.findProperty("dateTimeSucceeded[Machinery,MachineryPriceTransaction]").change(dateTime, session,
                                        (DataObject) machineryObject, transactionObject);
                            }
                        }
                    }
                    session.apply(getBusinessLogics());
                } catch (Exception e) {
                    throw Throwables.propagate(e);
                }
            }
        }
    }

    @Override
    public List<byte[][]> readLabelFormats(List<String> scalesModelsList) throws RemoteException, SQLException {
        try (DataSession session = getDbManager().createSession()) {

            List<byte[][]> fileLabelFormats = new ArrayList<>();

            if (scalesLM != null) {

                for (String scalesModel : scalesModelsList) {

                    ObjectValue scalesModelObject = scalesLM.findProperty("scalesName[VARISTRING[110]]").readClasses(session, new DataObject(scalesModel));

                    KeyExpr labelFormatExpr = new KeyExpr("labelFormat");
                    ImRevMap<Object, KeyExpr> labelFormatKeys = MapFact.singletonRev((Object) "labelFormat", labelFormatExpr);
                    QueryBuilder<Object, Object> labelFormatQuery = new QueryBuilder<>(labelFormatKeys);
                    
                    labelFormatQuery.addProperty("fileLabelFormat", scalesLM.findProperty("file[LabelFormat]").getExpr(labelFormatExpr));
                    labelFormatQuery.addProperty("fileMessageLabelFormat", scalesLM.findProperty("fileMessage[LabelFormat]").getExpr(labelFormatExpr));
                    labelFormatQuery.and(scalesLM.findProperty("scalesModel[LabelFormat]").getExpr(labelFormatExpr).compare(scalesModelObject.getExpr(), Compare.EQUALS));

                    ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> labelFormatResult = labelFormatQuery.execute(session);

                    for (ImMap<Object, Object> row : labelFormatResult.valueIt()) {
                        byte[] fileLabelFormat = (byte[]) row.get("fileLabelFormat");
                        byte[] fileMessageLabelFormat = (byte[]) row.get("fileMessageLabelFormat");
                        fileLabelFormats.add(new byte[][]{fileLabelFormat, fileMessageLabelFormat});
                    }
                }
            }
            return fileLabelFormats;
        } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void errorTransactionReport(Integer transactionID, Throwable e) throws RemoteException, SQLException {
        try (DataSession session = getDbManager().createSession()) {
            DataObject errorObject = session.addObject((ConcreteCustomClass) equLM.findClass("MachineryPriceTransactionError"));
            equLM.findProperty("machineryPriceTransaction[MachineryPriceTransactionError]").change(transactionID, session, errorObject);
            equLM.findProperty("data[MachineryPriceTransactionError]").change(e.toString(), session, errorObject);
            equLM.findProperty("date[MachineryPriceTransactionError]").change(getCurrentTimestamp(), session, errorObject);
            OutputStream os = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(os));
            equLM.findProperty("errorTrace[MachineryPriceTransactionError]").change(os.toString(), session, errorObject);

            session.apply(getBusinessLogics());
        } catch (Exception e2) {
            throw Throwables.propagate(e2);
        }
    }

    @Override
    public void errorEquipmentServerReport(String equipmentServer, Throwable exception) throws
            RemoteException, SQLException {
        try (DataSession session = getDbManager().createSession()) {
            DataObject errorObject = session.addObject((ConcreteCustomClass) equLM.findClass("EquipmentServerError"));
            Object equipmentServerObject = equLM.findProperty("sidTo[VARSTRING[20]]").read(session, new DataObject(equipmentServer, StringClass.get(20)));
            equLM.findProperty("equipmentServer[EquipmentServerError]").change(equipmentServerObject, session, errorObject);
            equLM.findProperty("data[EquipmentServerError]").change(exception.toString(), session, errorObject);
            OutputStream os = new ByteArrayOutputStream();
            exception.printStackTrace(new PrintStream(os));
            equLM.findProperty("erTrace[EquipmentServerError]").change(os.toString(), session, errorObject);

            equLM.findProperty("date[EquipmentServerError]").change(getCurrentTimestamp(), session, errorObject);

            session.apply(getBusinessLogics());
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public EquipmentServerSettings readEquipmentServerSettings(String equipmentServer) throws RemoteException, SQLException {
        try {
            ThreadLocalContext.set(logicsInstance.getContext());
            try (DataSession session = getDbManager().createSession()) {
                ObjectValue equipmentServerObject = equLM.findProperty("sidTo[VARSTRING[20]]").readClasses(session, new DataObject(equipmentServer));
                if (equipmentServerObject instanceof DataObject) {
                    Integer delay = (Integer) equLM.findProperty("delay[EquipmentServer]").read(session, equipmentServerObject);
                    Integer numberAtATime = (Integer) equLM.findProperty("numberAtATime[EquipmentServer]").read(session, equipmentServerObject);
                    Integer sendSalesDelay = (Integer) equLM.findProperty("sendSalesDelay[EquipmentServer]").read(session, equipmentServerObject);
                    return new EquipmentServerSettings(delay, numberAtATime, sendSalesDelay);
                } else return null;
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private String dateTimeCode(Timestamp timeStamp) {
        String result = "";
        long time = timeStamp.getTime() / 1000;
        while (time > 26) {
            result = (char) (time % 26 + 97) + result;
            time = time / 26;
        }
        result = (char) (time + 97) + result;
        return result;
    }

    private String getRowValue(ImMap<Object, Object> row, String key) {
        return trim((String) row.get(key));
    }

    protected boolean notNullNorEmpty(String value) {
        return value != null && !value.isEmpty();
    }

    protected boolean notNullNorEmpty(List value) {
        return value != null && !value.isEmpty();
    }

    protected boolean notNullNorEmpty(Set value) {
        return value != null && !value.isEmpty();
    }
    
    protected String formatDate(Date date) {
        return new SimpleDateFormat("dd.MM.yyyy").format(date);
    }
    
    protected String formatDateTime(Timestamp date) {
        return new SimpleDateFormat("dd.MM.yyyy hh:mm:ss").format(date);
    }
    
    protected Timestamp getCurrentTimestamp() {
        return DateConverter.dateToStamp(Calendar.getInstance().getTime());
    }
    
    private List<List<Object>> initData(int size) {
        List<List<Object>> data = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            data.add(new ArrayList<>());
        }
        return data;
    }

    private static Comparator<SalesInfo> COMPARATOR = new Comparator<SalesInfo>() {
        public int compare(SalesInfo o1, SalesInfo o2) {
            int compareGroupCashRegister = BaseUtils.nullCompareTo(o1.nppGroupMachinery, o2.nppGroupMachinery);
            if (compareGroupCashRegister == 0) {
                int compareCashRegister = BaseUtils.nullCompareTo(o1.nppMachinery, o2.nppMachinery);
                if (compareCashRegister == 0)
                    return BaseUtils.nullCompareTo(o1.numberZReport, o2.numberZReport);
                else
                    return compareCashRegister;
            } else
                return compareGroupCashRegister;
        }
    };

    @Aspect
    private static class RemoteLogicsContextHoldingAspect {
        @Before("execution(* equ.api.EquipmentServerInterface.*(..)) && target(remoteLogics)")
        public void beforeCall(EquipmentServer remoteLogics) {
            ThreadLocalContext.set(remoteLogics.logicsInstance.getContext());
        }

        @AfterReturning("execution(* equ.api.EquipmentServerInterface.*(..)) && target(remoteLogics)")
        public void afterReturning(EquipmentServer remoteLogics) {
            ThreadLocalContext.set(null);
        }
    }
}
