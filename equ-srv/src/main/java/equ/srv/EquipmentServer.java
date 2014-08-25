package equ.srv;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.api.cashregister.*;
import equ.api.terminal.*;
import lsfusion.base.BaseUtils;
import lsfusion.base.DateConverter;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.Compare;
import lsfusion.server.classes.*;
import lsfusion.server.context.ThreadLocalContext;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
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

public class EquipmentServer extends LifecycleAdapter implements EquipmentServerInterface, InitializingBean {
    private static final Logger logger = Logger.getLogger(EquipmentServer.class);

    public static final String EXPORT_NAME = "EquipmentServer";

    private LogicsInstance logicsInstance;

    private SoftCheckInterface softCheck;
    
    private ScriptingLogicsModule equLM;

    //Опциональные модули
    private ScriptingLogicsModule cashRegisterLM;
    private ScriptingLogicsModule collectionLM;
    private ScriptingLogicsModule discountCardLM;
    private ScriptingLogicsModule equipmentCashRegisterLM;
    private ScriptingLogicsModule itemLM;
    private ScriptingLogicsModule legalEntityLM;
    private ScriptingLogicsModule machineryPriceTransactionLM;
    private ScriptingLogicsModule machineryPriceTransactionStockTaxLM;
    private ScriptingLogicsModule priceCheckerLM;
    private ScriptingLogicsModule priceListLedgerLM;
    private ScriptingLogicsModule purchaseInvoiceAgreementLM;
    private ScriptingLogicsModule retailCRMLM;
    private ScriptingLogicsModule scalesLM;
    private ScriptingLogicsModule scalesItemLM;
    private ScriptingLogicsModule stopListLM;
    private ScriptingLogicsModule storeSkuLedgerLM;
    private ScriptingLogicsModule terminalLM;
    private ScriptingLogicsModule zReportLM;
    private ScriptingLogicsModule zReportDiscountCardLM;
    
    private boolean started = false;

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
        collectionLM = getBusinessLogics().getModule("Collection");
        discountCardLM = getBusinessLogics().getModule("DiscountCard");
        equipmentCashRegisterLM = getBusinessLogics().getModule("EquipmentCashRegister");
        itemLM = getBusinessLogics().getModule("Item");
        legalEntityLM = getBusinessLogics().getModule("LegalEntity");
        machineryPriceTransactionLM = getBusinessLogics().getModule("MachineryPriceTransaction");
        machineryPriceTransactionStockTaxLM = getBusinessLogics().getModule("MachineryPriceTransactionStockTax");
        priceCheckerLM = getBusinessLogics().getModule("EquipmentPriceChecker");
        priceListLedgerLM = getBusinessLogics().getModule("PriceListLedger");
        purchaseInvoiceAgreementLM = getBusinessLogics().getModule("PurchaseInvoiceAgreement");
        retailCRMLM = getBusinessLogics().getModule("RetailCRM");
        scalesLM = getBusinessLogics().getModule("EquipmentScales");
        scalesItemLM = getBusinessLogics().getModule("ScalesItem");
        stopListLM = getBusinessLogics().getModule("StopList");
        storeSkuLedgerLM = getBusinessLogics().getModule("StoreSkuLedger");
        terminalLM = getBusinessLogics().getModule("EquipmentTerminal");
        zReportLM = getBusinessLogics().getModule("ZReport");
        zReportDiscountCardLM = getBusinessLogics().getModule("ZReportDiscountCard");
    }

    @Override
    protected void onStarted(LifecycleEvent event) {
        logger.info("Binding Equipment Server.");
        try {
            getRmiManager().bindAndExport(EXPORT_NAME, this);
            started = true;
        } catch (Exception e) {
            throw new RuntimeException("Error exporting Equipment Server: ", e);
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
    public List<TransactionInfo> readTransactionInfo(String sidEquipmentServer) throws RemoteException, SQLException {
        try {

            DataSession session = getDbManager().createSession();
            List<TransactionInfo> transactionList = new ArrayList<TransactionInfo>();

            LCP isMachineryPriceTransaction = equLM.is(equLM.findClass("MachineryPriceTransaction"));
            ImRevMap<Object, KeyExpr> keys = isMachineryPriceTransaction.getMapKeys();
            KeyExpr key = keys.singleValue();
            QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);

            String[] mptProperties = new String[]{"dateTimeMachineryPriceTransaction", "groupMachineryMachineryPriceTransaction",
                    "nppGroupMachineryMachineryPriceTransaction", "nameGroupMachineryMachineryPriceTransaction", "snapshotMachineryPriceTransaction"};
            for (String property : mptProperties) {
                query.addProperty(property, equLM.findProperty(property).getExpr(key));
            }
            query.and(equLM.findProperty("sidEquipmentServerMachineryPriceTransaction").getExpr(key).compare(new DataObject(sidEquipmentServer, StringClass.get(20)), Compare.EQUALS));
            query.and(equLM.findProperty("processMachineryPriceTransaction").getExpr(key).getWhere());

            ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> result = query.executeClasses(session);
            List<Object[]> transactionObjects = new ArrayList<Object[]>();
            for (int i = 0, size = result.size(); i < size; i++) {
                ImMap<Object, ObjectValue> value = result.getValue(i);
                DataObject dateTimeMPT = (DataObject) value.get("dateTimeMachineryPriceTransaction");
                DataObject groupMachineryMPT = (DataObject) value.get("groupMachineryMachineryPriceTransaction");
                Integer nppGroupMachineryMPT = (Integer) value.get("nppGroupMachineryMachineryPriceTransaction").getValue();
                String nameGroupMachineryMPT = (String) value.get("nameGroupMachineryMachineryPriceTransaction").getValue();
                DataObject transactionObject = result.getKey(i).singleValue();
                Boolean snapshotMPT = value.get("snapshotMachineryPriceTransaction") instanceof DataObject;
                transactionObjects.add(new Object[]{groupMachineryMPT, nppGroupMachineryMPT, nameGroupMachineryMPT, transactionObject,
                        dateTimeCode((Timestamp) dateTimeMPT.getValue()), dateTimeMPT, snapshotMPT});
            }

            for (Object[] transaction : transactionObjects) {

                DataObject groupMachineryObject = (DataObject) transaction[0];
                Integer nppGroupMachinery = (Integer) transaction[1];
                String nameGroupMachinery = (String) transaction[2];
                DataObject transactionObject = (DataObject) transaction[3];
                String dateTimeCode = (String) transaction[4];
                Date date = new Date(((Timestamp) ((DataObject) transaction[5]).getValue()).getTime());
                Boolean snapshotTransaction = (Boolean) transaction[6];

                KeyExpr barcodeExpr = new KeyExpr("barcode");
                ImRevMap<Object, KeyExpr> skuKeys = MapFact.singletonRev((Object) "barcode", barcodeExpr);

                QueryBuilder<Object, Object> skuQuery = new QueryBuilder<Object, Object>(skuKeys);

                String[] skuProperties = new String[]{"nameMachineryPriceTransactionBarcode", "priceMachineryPriceTransactionBarcode",
                        "expiryDateMachineryPriceTransactionBarcode", "splitMachineryPriceTransactionBarcode", "passScalesMachineryPriceTransactionBarcode",
                        "skuGroupMachineryPriceTransactionBarcode", "idUOMMachineryPriceTransactionBarcode", "shortNameUOMMachineryPriceTransactionBarcode"};
                String[] scalesSkuProperties = new String[]{"pluNumberMachineryPriceTransactionBarcode", "expiryDaysMachineryPriceTransactionBarcode", "hoursExpiryMachineryPriceTransactionBarcode",
                        "labelFormatMachineryPriceTransactionBarcode", "descriptionMachineryPriceTransactionBarcode"};
                String[] taxProperties = new String[]{"VATMachineryPriceTransactionBarcode"};
                skuQuery.addProperty("idBarcode", equLM.findProperty("idBarcode").getExpr(barcodeExpr));
                for (String property : skuProperties) {
                    skuQuery.addProperty(property, equLM.findProperty(property).getExpr(transactionObject.getExpr(), barcodeExpr));
                }
                if (scalesItemLM != null) {
                    for (String property : scalesSkuProperties) {
                        skuQuery.addProperty(property, scalesItemLM.findProperty(property).getExpr(transactionObject.getExpr(), barcodeExpr));
                    }
                }
                if (machineryPriceTransactionStockTaxLM != null) {
                    for (String property : taxProperties) {
                        skuQuery.addProperty(property, machineryPriceTransactionStockTaxLM.findProperty(property).getExpr(transactionObject.getExpr(), barcodeExpr));
                    }
                }

                skuQuery.and(equLM.findProperty("inMachineryPriceTransactionBarcode").getExpr(transactionObject.getExpr(), barcodeExpr).getWhere());

                ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> skuResult = skuQuery.executeClasses(session);                

                if (cashRegisterLM != null && transactionObject.objectClass.equals(cashRegisterLM.findClass("CashRegisterPriceTransaction"))) {
                    
                    List<DiscountCard> discountCardList = snapshotTransaction ? getDiscountCardList(session) : null;
                    
                    java.sql.Date startDateGroupCashRegister = (java.sql.Date) cashRegisterLM.findProperty("startDateGroupCashRegister").read(session, groupMachineryObject);
                    Boolean notDetailedGroupCashRegister = cashRegisterLM.findProperty("notDetailedGroupCashRegister").read(session, groupMachineryObject) != null;

                    List<CashRegisterInfo> cashRegisterInfoList = new ArrayList<CashRegisterInfo>();
                    LCP<PropertyInterface> isCashRegister = (LCP<PropertyInterface>) cashRegisterLM.is(cashRegisterLM.findClass("CashRegister"));

                    ImRevMap<PropertyInterface, KeyExpr> cashRegisterKeys = isCashRegister.getMapKeys();
                    KeyExpr cashRegisterKey = cashRegisterKeys.singleValue();
                    QueryBuilder<PropertyInterface, Object> cashRegisterQuery = new QueryBuilder<PropertyInterface, Object>(cashRegisterKeys);

                    String[] cashRegisterProperties = new String[]{"nppMachinery", "portMachinery", "nameModelMachinery", "handlerModelMachinery",
                                                                   "overDirectoryCashRegister"};
                    for (String property : cashRegisterProperties) {
                        cashRegisterQuery.addProperty(property, cashRegisterLM.findProperty(property).getExpr(cashRegisterKey));
                    }
                    cashRegisterQuery.and(isCashRegister.property.getExpr(cashRegisterKeys).getWhere());
                    cashRegisterQuery.and(cashRegisterLM.findProperty("groupCashRegisterCashRegister").getExpr(cashRegisterKey).compare(groupMachineryObject, Compare.EQUALS));

                    ImOrderMap<ImMap<PropertyInterface, Object>, ImMap<Object, Object>> cashRegisterResult = cashRegisterQuery.execute(session);

                    for (ImMap<Object, Object> row : cashRegisterResult.valueIt()) {
                        Integer nppMachinery = (Integer) row.get("nppMachinery");
                        String nameModel = (String) row.get("nameModelMachinery");
                        String handlerModel = (String) row.get("handlerModelMachinery");
                        String portMachinery = (String) row.get("portMachinery");
                        String directoryCashRegister = (String) row.get("overDirectoryCashRegister");
                        cashRegisterInfoList.add(new CashRegisterInfo(nppGroupMachinery, nppMachinery, nameModel, handlerModel, portMachinery, directoryCashRegister, startDateGroupCashRegister, notDetailedGroupCashRegister));
                    }

                    List<CashRegisterItemInfo> cashRegisterItemInfoList = new ArrayList<CashRegisterItemInfo>();
                    for (int i = 0; i < skuResult.size(); i++) {
                        ImMap<Object, DataObject> keyRow = skuResult.getKey(i);
                        ImMap<Object, ObjectValue> valueRow = skuResult.getValue(i);
                        String barcode = trim((String) valueRow.get("idBarcode").getValue());
                        String name = trim((String) valueRow.get("nameMachineryPriceTransactionBarcode").getValue());
                        BigDecimal price = (BigDecimal) valueRow.get("priceMachineryPriceTransactionBarcode").getValue();
                        boolean split = valueRow.get("splitMachineryPriceTransactionBarcode").getValue() != null;
                        boolean passScales = valueRow.get("passScalesMachineryPriceTransactionBarcode").getValue() != null;
                        String idUOM = (String) valueRow.get("idUOMMachineryPriceTransactionBarcode").getValue();
                        String shortNameUOM = (String) valueRow.get("shortNameUOMMachineryPriceTransactionBarcode").getValue();
                        BigDecimal valueVAT = machineryPriceTransactionStockTaxLM == null ? null : (BigDecimal) valueRow.get("VATMachineryPriceTransactionBarcode").getValue();
                        ObjectValue itemObject = itemLM.findProperty("skuBarcode").readClasses(session, keyRow.get("barcode"));
                        Integer idItem = (Integer) itemObject.getValue();
                        boolean notPromotionItem = storeSkuLedgerLM != null && storeSkuLedgerLM.findProperty("notPromotionSku").read(session, itemObject) != null;
                        String description = scalesItemLM == null ? null : (String) valueRow.get("descriptionMachineryPriceTransactionBarcode").getValue();

                        List<ItemGroup> hierarchyItemGroup = new ArrayList<ItemGroup>();
                        String canonicalNameSkuGroup = null;
                        if (itemLM != null) {
                            ObjectValue skuGroupObject = valueRow.get("skuGroupMachineryPriceTransactionBarcode");
                            if (skuGroupObject instanceof DataObject) {
                                String idItemGroup = (String) itemLM.findProperty("idItemGroup").read(session, skuGroupObject);
                                String nameItemGroup = (String) itemLM.findProperty("nameItemGroup").read(session, skuGroupObject);
                                hierarchyItemGroup.add(new ItemGroup(idItemGroup, nameItemGroup));
                                ObjectValue parentSkuGroup;
                                while ((parentSkuGroup = equLM.findProperty("parentSkuGroup").readClasses(session, (DataObject) skuGroupObject)) instanceof DataObject) {
                                    String idParentGroup = (String) itemLM.findProperty("idItemGroup").read(session, parentSkuGroup);
                                    String nameParentGroup = (String) itemLM.findProperty("nameItemGroup").read(session, parentSkuGroup);
                                    hierarchyItemGroup.add(new ItemGroup(idParentGroup, nameParentGroup));
                                    skuGroupObject = parentSkuGroup;
                                }
                                canonicalNameSkuGroup = idItemGroup == null ? "" : trim((String) equLM.findProperty("canonicalNameSkuGroup").read(session, itemLM.findProperty("itemGroupId").readClasses(session, new DataObject(idItemGroup))));
                            }
                        }
                        
                        cashRegisterItemInfoList.add(new CashRegisterItemInfo(barcode, name, price, split, idItem,
                                description, canonicalNameSkuGroup, hierarchyItemGroup, idUOM, shortNameUOM, passScales, valueVAT, notPromotionItem));
                    }
                    
                    transactionList.add(new TransactionCashRegisterInfo((Integer) transactionObject.getValue(),
                            dateTimeCode, date, cashRegisterItemInfoList, cashRegisterInfoList, snapshotTransaction, 
                            nppGroupMachinery, nameGroupMachinery, discountCardList));

                } else if (scalesLM != null && transactionObject.objectClass.equals(scalesLM.findClass("ScalesPriceTransaction"))) {
                    List<ScalesInfo> scalesInfoList = new ArrayList<ScalesInfo>();
                    String directory = (String) scalesLM.findProperty("directoryGroupScales").read(session, groupMachineryObject);
                    String pieceCodeGroupScales = (String) scalesLM.findProperty("pieceCodeGroupScales").read(session, groupMachineryObject);
                    String weightCodeGroupScales = (String) scalesLM.findProperty("weightCodeGroupScales").read(session, groupMachineryObject);

                    LCP<PropertyInterface> isScales = (LCP<PropertyInterface>) scalesLM.is(scalesLM.findClass("Scales"));

                    ImRevMap<PropertyInterface, KeyExpr> scalesKeys = isScales.getMapKeys();
                    KeyExpr scalesKey = scalesKeys.singleValue();
                    QueryBuilder<PropertyInterface, Object> scalesQuery = new QueryBuilder<PropertyInterface, Object>(scalesKeys);

                    String[] scalesProperties = new String[]{"portMachinery", "nppMachinery", "nameCheckModelCheck", "handlerModelMachinery"};
                    for (String property : scalesProperties) {
                        scalesQuery.addProperty(property, scalesLM.findProperty(property).getExpr(scalesKey));
                    }
                    scalesQuery.and(isScales.property.getExpr(scalesKeys).getWhere());
                    scalesQuery.and(scalesLM.findProperty("groupScalesScales").getExpr(scalesKey).compare(groupMachineryObject, Compare.EQUALS));

                    ImOrderMap<ImMap<PropertyInterface, Object>, ImMap<Object, Object>> scalesResult = scalesQuery.execute(session);

                    for (ImMap<Object, Object> values : scalesResult.valueIt()) {
                        String portMachinery = (String) values.get("portMachinery");
                        Integer nppMachinery = (Integer) values.get("nppMachinery");
                        String nameModel = (String) values.get("nameModelMachinery");
                        String handlerModel = (String) values.get("handlerModelMachinery");
                        scalesInfoList.add(new ScalesInfo(nppMachinery, nameModel, handlerModel, portMachinery, directory,
                                pieceCodeGroupScales, weightCodeGroupScales));
                    }

                    List<ScalesItemInfo> scalesItemInfoList = new ArrayList<ScalesItemInfo>();
                    for (ImMap<Object, ObjectValue> row : skuResult.valueIt()) {
                        String barcode = trim((String) row.get("idBarcode").getValue());
                        String name = trim((String) row.get("nameMachineryPriceTransactionBarcode").getValue());
                        BigDecimal price = (BigDecimal) row.get("priceMachineryPriceTransactionBarcode").getValue();
                        Integer pluNumber = (Integer) row.get("pluNumberMachineryPriceTransactionBarcode").getValue();
                        Date expiryDate = (Date) row.get("expiryDateMachineryPriceTransactionBarcode").getValue();
                        boolean split = row.get("splitMachineryPriceTransactionBarcode").getValue() != null;
                        BigDecimal expiryDays = (BigDecimal) row.get("expiryDaysMachineryPriceTransactionBarcode").getValue();
                        Integer hoursExpiry = (Integer) row.get("hoursExpiryMachineryPriceTransactionBarcode").getValue();
                        Integer labelFormat = (Integer) row.get("labelFormatMachineryPriceTransactionBarcode").getValue();
                        String description = (String) row.get("descriptionMachineryPriceTransactionBarcode").getValue();

                        List<String> hierarchyItemGroup = new ArrayList<String>();
                        if (itemLM != null) {
                            ObjectValue skuGroupObject = row.get("skuGroupMachineryPriceTransactionBarcode");
                            if (skuGroupObject instanceof DataObject) {
                                String idItemGroup = (String) itemLM.findProperty("idItemGroup").read(session, skuGroupObject);
                                hierarchyItemGroup.add(idItemGroup);
                                ObjectValue parentSkuGroup;
                                while ((parentSkuGroup = equLM.findProperty("parentSkuGroup").readClasses(session, (DataObject) skuGroupObject)) instanceof DataObject) {
                                    hierarchyItemGroup.add((String) itemLM.findProperty("idItemGroup").read(session, parentSkuGroup));
                                    skuGroupObject = parentSkuGroup;
                                }
                            }
                        }
                        Integer cellScalesObject = description == null ? null : (Integer) scalesLM.findProperty("cellScalesGroupScalesDescription").read(session, groupMachineryObject, new DataObject(description, StringClass.text));
                        Integer descriptionNumberCellScales = cellScalesObject == null ? null : (Integer) scalesLM.findProperty("numberCellScales").read(session, new DataObject(cellScalesObject, (ConcreteClass) scalesLM.findClass("CellScales")));

                        scalesItemInfoList.add(new ScalesItemInfo(barcode, name, price, split, pluNumber, expiryDays, 
                                hoursExpiry, expiryDate, labelFormat, description, descriptionNumberCellScales, hierarchyItemGroup));
                    }

                    transactionList.add(new TransactionScalesInfo((Integer) transactionObject.getValue(),
                            dateTimeCode, scalesItemInfoList, scalesInfoList, snapshotTransaction));

                } else if (priceCheckerLM != null && transactionObject.objectClass.equals(priceCheckerLM.findClass("PriceCheckerPriceTransaction"))) {
                    List<PriceCheckerInfo> priceCheckerInfoList = new ArrayList<PriceCheckerInfo>();
                    LCP<PropertyInterface> isCheck = (LCP<PropertyInterface>) priceCheckerLM.is(priceCheckerLM.findClass("Check"));

                    ImRevMap<PropertyInterface, KeyExpr> checkKeys = isCheck.getMapKeys();
                    KeyExpr checkKey = checkKeys.singleValue();
                    QueryBuilder<PropertyInterface, Object> checkQuery = new QueryBuilder<PropertyInterface, Object>(checkKeys);

                    String[] checkProperties = new String[]{"portMachinery", "nppMachinery", "nameCheckModelCheck"};
                    for (String property : checkProperties) {
                        checkQuery.addProperty(property, priceCheckerLM.findProperty(property).getExpr(checkKey));
                    }
                    checkQuery.and(isCheck.property.getExpr(checkKeys).getWhere());
                    checkQuery.and(priceCheckerLM.findProperty("groupPriceCheckerPriceChecker").getExpr(checkKey).compare(groupMachineryObject, Compare.EQUALS));

                    ImOrderMap<ImMap<PropertyInterface, Object>, ImMap<Object, Object>> checkResult = checkQuery.execute(session);

                    for (ImMap<Object, Object> values : checkResult.valueIt()) {
                        priceCheckerInfoList.add(new PriceCheckerInfo((Integer) values.get("nppMachinery"), (String) values.get("nameCheckModelCheck"),
                                null, (String) values.get("portMachinery")));
                    }

                    List<PriceCheckerItemInfo> priceCheckerItemInfoList = new ArrayList<PriceCheckerItemInfo>();
                    for (ImMap<Object, ObjectValue> row : skuResult.valueIt()) {
                        String barcode = trim((String) row.get("idBarcode").getValue());
                        String name = trim((String) row.get("nameMachineryPriceTransactionBarcode").getValue());
                        BigDecimal price = (BigDecimal) row.get("priceMachineryPriceTransactionBarcode").getValue();
                        boolean split = row.get("splitMachineryPriceTransactionBarcode").getValue() != null;
                        priceCheckerItemInfoList.add(new PriceCheckerItemInfo(barcode, name, price, split));
                    }
                    
                    transactionList.add(new TransactionPriceCheckerInfo((Integer) transactionObject.getValue(),
                            dateTimeCode, priceCheckerItemInfoList, priceCheckerInfoList));


                } else if (terminalLM != null && transactionObject.objectClass.equals(terminalLM.findClass("TerminalPriceTransaction"))) {
                    List<TerminalInfo> terminalInfoList = new ArrayList<TerminalInfo>();
                    
                    Integer nppGroupTerminal = (Integer) terminalLM.findProperty("nppGroupMachinery").read(session, groupMachineryObject);
                    String directoryGroupTerminal = (String) terminalLM.findProperty("directoryGroupTerminal").read(session, groupMachineryObject);
                    ObjectValue priceListTypeGroupTerminal = terminalLM.findProperty("priceListTypeGroupTerminal").readClasses(session, groupMachineryObject);
                    ObjectValue stockGroupTerminal = terminalLM.findProperty("stockGroupTerminal").readClasses(session, groupMachineryObject);
                    String idPriceListType = (String) terminalLM.findProperty("idPriceListType").read(session, priceListTypeGroupTerminal);
                    
                    LCP<PropertyInterface> isTerminal = (LCP<PropertyInterface>) terminalLM.is(terminalLM.findClass("Terminal"));

                    ImRevMap<PropertyInterface, KeyExpr> terminalKeys = isTerminal.getMapKeys();
                    KeyExpr terminalKey = terminalKeys.singleValue();
                    QueryBuilder<PropertyInterface, Object> terminalQuery = new QueryBuilder<PropertyInterface, Object>(terminalKeys);

                    String[] terminalProperties = new String[]{"portMachinery", "nppMachinery", "nameModelMachinery", "handlerModelMachinery"};
                    for (String property : terminalProperties) {
                        terminalQuery.addProperty(property, terminalLM.findProperty(property).getExpr(terminalKey));
                    }
                    terminalQuery.and(isTerminal.property.getExpr(terminalKeys).getWhere());
                    terminalQuery.and(terminalLM.findProperty("groupTerminalTerminal").getExpr(terminalKey).compare(groupMachineryObject, Compare.EQUALS));

                    ImOrderMap<ImMap<PropertyInterface, Object>, ImMap<Object, Object>> terminalResult = terminalQuery.execute(session);

                    for (ImMap<Object, Object> values : terminalResult.valueIt()) {
                        terminalInfoList.add(new TerminalInfo((Integer) values.get("nppMachinery"),
                                (String) values.get("nameModelMachinery"), (String) values.get("handlerModelMachinery"),
                                (String) values.get("portMachinery"), idPriceListType, directoryGroupTerminal));
                    }

                    List<TerminalItemInfo> terminalItemInfoList = new ArrayList<TerminalItemInfo>();
                    for (ImMap<Object, ObjectValue> row : skuResult.valueIt()) {
                        String barcode = trim((String) row.get("idBarcode").getValue());
                        String name = trim((String) row.get("nameMachineryPriceTransactionBarcode").getValue());
                        BigDecimal price = (BigDecimal) row.get("priceMachineryPriceTransactionBarcode").getValue();
                        boolean split = row.get("splitMachineryPriceTransactionBarcode").getValue() != null;

                        terminalItemInfoList.add(new TerminalItemInfo(barcode, name, price, split, null/*quantity*/, null/*image*/));
                    }

                    List<TerminalHandbookType> terminalHandbookTypeList = readTerminalHandbookTypeList(session);
                    List<TerminalDocumentType> terminalDocumentTypeList = readTerminalDocumentTypeList(session);                   
                    List<TerminalOrder> terminalOrderList = readTerminalOrderList(session);
                    List<TerminalLegalEntity> terminalLegalEntityList = readTerminalLegalEntityList(session);
                    List<TerminalAssortment> terminalAssortmentList = readTerminalAssortmentList(session, priceListTypeGroupTerminal, stockGroupTerminal);
                    
                    transactionList.add(new TransactionTerminalInfo((Integer) transactionObject.getValue(),
                            dateTimeCode, terminalItemInfoList, terminalInfoList, terminalHandbookTypeList,
                            terminalDocumentTypeList, terminalOrderList, terminalLegalEntityList, terminalAssortmentList, 
                            nppGroupTerminal, directoryGroupTerminal, snapshotTransaction));
                }
            }
            return transactionList;
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        } catch (SQLHandledException e) {
            throw Throwables.propagate(e);
        }
    }
    
    private List<DiscountCard> getDiscountCardList(DataSession session) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        if(retailCRMLM == null) return null;
        List<DiscountCard> discountCardList = new ArrayList<DiscountCard>();

        KeyExpr discountCardExpr = new KeyExpr("DiscountCard");
        ImRevMap<Object, KeyExpr> discountCardKeys = MapFact.singletonRev((Object) "discountCard", discountCardExpr);

        QueryBuilder<Object, Object> discountCardQuery = new QueryBuilder<Object, Object>(discountCardKeys);
        String[] discountCardProperties = new String[]{"numberDiscountCard", "nameDiscountCard", "percentDiscountCard"};
        for (String property : discountCardProperties) {
            discountCardQuery.addProperty(property, retailCRMLM.findProperty(property).getExpr(discountCardExpr));
        }      
        discountCardQuery.and(retailCRMLM.findProperty("numberDiscountCard").getExpr(discountCardExpr).getWhere());

        ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> discountCardResult = discountCardQuery.executeClasses(session);

        for (int i = 0, size = discountCardResult.size(); i < size; i++) {

            ImMap<Object, ObjectValue> discountCardValue = discountCardResult.getValue(i);

            String numberDiscountCard = trim((String) discountCardValue.get("numberDiscountCard").getValue());
            String nameDiscountCard = trim((String) discountCardValue.get("nameDiscountCard").getValue());
            BigDecimal percentDiscountCard = (BigDecimal) discountCardValue.get("percentDiscountCard").getValue();

            discountCardList.add(new DiscountCard(numberDiscountCard, nameDiscountCard, percentDiscountCard));
        }       
        return discountCardList;
    }
 
    @Override
    public List<StopListInfo> readStopListInfo(String sidEquipmentServer) throws RemoteException, SQLException {

        List<StopListInfo> stopListInfoList = new ArrayList<StopListInfo>();
        
        if(cashRegisterLM != null && stopListLM != null) {
            try {

                Map<String, StopListInfo> stopListInfoMap = new HashMap<String, StopListInfo>();

                DataSession session = getDbManager().createSession();

                Map<String, Map<String, Set<String>>> stockMap = getStockMap(session);
                         
                KeyExpr stopListExpr = new KeyExpr("stopList");
                ImRevMap<Object, KeyExpr> slKeys = MapFact.singletonRev((Object) "stopList", stopListExpr);
                QueryBuilder<Object, Object> slQuery = new QueryBuilder<Object, Object>(slKeys);
                String[] slProperties = new String[]{"numberStopList", "fromDateStopList", "fromTimeStopList", "toDateStopList", "toTimeStopList"};
                for (String property : slProperties)
                    slQuery.addProperty(property, stopListLM.findProperty(property).getExpr(stopListExpr));
                slQuery.and(stopListLM.findProperty("numberStopList").getExpr(stopListExpr).getWhere());
                ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> slResult = slQuery.executeClasses(session);
                for (int i = 0, size = slResult.size(); i < size; i++) {
                    DataObject stopListObject = slResult.getKey(i).get("stopList");
                    ImMap<Object, ObjectValue> slEntry = slResult.getValue(i);
                    String numberStopList = trim((String) slEntry.get("numberStopList").getValue());
                    Date dateFrom = (Date) slEntry.get("fromDateStopList").getValue();
                    Date dateTo = (Date) slEntry.get("toDateStopList").getValue();
                    Time timeFrom = (Time) slEntry.get("fromTimeStopList").getValue();
                    Time timeTo = (Time) slEntry.get("toTimeStopList").getValue();                    
                                                                              
                    Set<String> idStockSet = new HashSet<String>();
                    Map<String, Set<String>> handlerDirectoryMap = new HashMap<String, Set<String>>();                  
                    KeyExpr stockExpr = new KeyExpr("stock");
                    ImRevMap<Object, KeyExpr> stockKeys = MapFact.singletonRev((Object) "stock", stockExpr);
                    QueryBuilder<Object, Object> stockQuery = new QueryBuilder<Object, Object>(stockKeys);
                    stockQuery.addProperty("idStock", stopListLM.findProperty("idStock").getExpr(stockExpr));
                    stockQuery.and(stopListLM.findProperty("inStockStopList").getExpr(stockExpr, stopListObject.getExpr()).getWhere());
                    stockQuery.and(stopListLM.findProperty("notSucceededStockStopList").getExpr(stockExpr, stopListObject.getExpr()).getWhere());
                    ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> stockResult = stockQuery.execute(session);
                    for (ImMap<Object, Object> stockEntry : stockResult.values()) {
                        String idStock = trim((String) stockEntry.get("idStock"));
                        idStockSet.add(idStock);                       
                        if(stockMap.containsKey(idStock))
                            for (Map.Entry<String, Set<String>> entry : stockMap.get(idStock).entrySet()) {
                                if (handlerDirectoryMap.containsKey(entry.getKey()))
                                    handlerDirectoryMap.get(entry.getKey()).addAll(entry.getValue());
                                else
                                    handlerDirectoryMap.put(entry.getKey(), entry.getValue());
                            }
                    }
                    
                    if(!handlerDirectoryMap.isEmpty()) {
                        List<String> stopListItemList = getStopListItemList(session, stopListObject);
                        stopListInfoMap.put(numberStopList, new StopListInfo(numberStopList, dateFrom, timeFrom, dateTo, timeTo, idStockSet, stopListItemList, handlerDirectoryMap));
                    }
                    for(StopListInfo stopList : stopListInfoMap.values())
                        stopListInfoList.add(stopList);
                    
                }
            } catch (ScriptingErrorLog.SemanticErrorException e) {
                throw Throwables.propagate(e);
            } catch (SQLHandledException e) {
                throw Throwables.propagate(e);
            }            
        }
       
        return stopListInfoList;
    }
    
    private Map<String, Map<String, Set<String>>> getStockMap(DataSession session) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, Map<String, Set<String>>> stockMap = new HashMap<String, Map<String, Set<String>>>();

        KeyExpr groupCashRegisterExpr = new KeyExpr("groupCashRegister");
        KeyExpr cashRegisterExpr = new KeyExpr("cashRegister");
        ImRevMap<Object, KeyExpr> cashRegisterKeys = MapFact.toRevMap((Object) "groupCashRegister", groupCashRegisterExpr, "cashRegister", cashRegisterExpr);
        QueryBuilder<Object, Object> cashRegisterQuery = new QueryBuilder<Object, Object>(cashRegisterKeys);
        cashRegisterQuery.addProperty("handlerModelMachinery", stopListLM.findProperty("handlerModelMachinery").getExpr(cashRegisterExpr));
        cashRegisterQuery.addProperty("idStockGroupMachinery", stopListLM.findProperty("idStockGroupMachinery").getExpr(groupCashRegisterExpr));
        cashRegisterQuery.addProperty("overDirectoryCashRegister", cashRegisterLM.findProperty("overDirectoryCashRegister").getExpr(cashRegisterExpr));
        cashRegisterQuery.and(stopListLM.findProperty("handlerModelMachinery").getExpr(cashRegisterExpr).getWhere());
        cashRegisterQuery.and(stopListLM.findProperty("idStockGroupMachinery").getExpr(groupCashRegisterExpr).getWhere());
        cashRegisterQuery.and(cashRegisterLM.findProperty("overDirectoryCashRegister").getExpr(cashRegisterExpr).getWhere());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> cashRegisterResult = cashRegisterQuery.execute(session);
        for (ImMap<Object, Object> entry : cashRegisterResult.valueIt()) {
            String handlerModel = (String) entry.get("handlerModelMachinery");
            String directory = (String) entry.get("overDirectoryCashRegister");
            String idStockGroupMachinery = (String) entry.get("idStockGroupMachinery");

            Map<String, Set<String>> handlerMap = stockMap.containsKey(idStockGroupMachinery) ? stockMap.get(idStockGroupMachinery) : new HashMap<String, Set<String>>();
            if(!handlerMap.containsKey(handlerModel))
                handlerMap.put(handlerModel, new HashSet<String>());
            handlerMap.get(handlerModel).add(directory);
            stockMap.put(idStockGroupMachinery, handlerMap);
        }
        return stockMap;
    }
    
    private List<String> getStopListItemList(DataSession session, DataObject stopListObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<String> stopListItemList = new ArrayList<String>();

        KeyExpr sldExpr = new KeyExpr("stopListDetail");
        ImRevMap<Object, KeyExpr> sldKeys = MapFact.singletonRev((Object) "stopListDetail", sldExpr);
        QueryBuilder<Object, Object> sldQuery = new QueryBuilder<Object, Object>(sldKeys);
        sldQuery.addProperty("idBarcodeSkuStopListDetail", stopListLM.findProperty("idBarcodeSkuStopListDetail").getExpr(sldExpr));
        sldQuery.and(stopListLM.findProperty("idBarcodeSkuStopListDetail").getExpr(sldExpr).getWhere());
        sldQuery.and(stopListLM.findProperty("stopListStopListDetail").getExpr(sldExpr).compare(stopListObject, Compare.EQUALS));
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> sldResult = sldQuery.execute(session);
        for (ImMap<Object, Object> sldEntry : sldResult.values()) {
            stopListItemList.add(trim((String) sldEntry.get("idBarcodeSkuStopListDetail")));
        }
        return stopListItemList;
    }

    @Override
    public void errorStopListReport(String numberStopList, Exception e) throws RemoteException, SQLException {
        try {
            DataSession session = getDbManager().createSession();
            DataObject errorObject = session.addObject((ConcreteCustomClass) stopListLM.findClass("StopListError"));
            ObjectValue stopListObject = stopListLM.findProperty("stopListNumber").readClasses(session, new DataObject(numberStopList));
            stopListLM.findProperty("stopListStopListError").change(stopListObject.getValue(), session, errorObject);
            stopListLM.findProperty("dataStopListError").change(e.toString(), session, errorObject);
            stopListLM.findProperty("dateStopListError").change(DateConverter.dateToStamp(Calendar.getInstance().getTime()), session, errorObject);
            OutputStream os = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(os));
            stopListLM.findProperty("errorTraceStopListError").change(os.toString(), session, errorObject);

            session.apply(getBusinessLogics());
        } catch (Exception e2) {
            throw Throwables.propagate(e2);
        }
    }

    @Override
    public void succeedStopList(String numberStopList, Set<String> idStockSet) throws RemoteException, SQLException {
        try {
            DataSession session = getDbManager().createSession();
            DataObject stopListObject = (DataObject) stopListLM.findProperty("stopListNumber").readClasses(session, new DataObject(numberStopList));
            for(String idStock : idStockSet) {
                DataObject stockObject = (DataObject) stopListLM.findProperty("stockId").readClasses(session, new DataObject(idStock));
                stopListLM.findProperty("succeededStockStopList").change(true, session, stockObject, stopListObject);
            }
            session.apply(getBusinessLogics());
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }


    private List<TerminalHandbookType> readTerminalHandbookTypeList(DataSession session) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalHandbookType> terminalHandbookTypeList = new ArrayList<TerminalHandbookType>();
        KeyExpr terminalHandbookTypeExpr = new KeyExpr("terminalHandbookType");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "terminalHandbookType", terminalHandbookTypeExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);
        String[] properties = new String[]{"idTerminalHandbookType", "nameTerminalHandbookType"};
        for (String property : properties) {
            query.addProperty(property, terminalLM.findProperty(property).getExpr(terminalHandbookTypeExpr));
        }
        query.and(terminalLM.findProperty("idTerminalHandbookType").getExpr(terminalHandbookTypeExpr).getWhere());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
        for(ImMap<Object, Object> entry : result.values()) {
            String id = trim((String) entry.get("idTerminalHandbookType"));
            String name = trim((String) entry.get("nameTerminalHandbookType"));
            terminalHandbookTypeList.add(new TerminalHandbookType(id, name));
        }
        return terminalHandbookTypeList;
    }
    
    private List<TerminalDocumentType> readTerminalDocumentTypeList(DataSession session) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<TerminalDocumentType> terminalDocumentTypeList = new ArrayList<TerminalDocumentType>();
        KeyExpr terminalDocumentTypeExpr = new KeyExpr("terminalDocumentType");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "terminalDocumentType", terminalDocumentTypeExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);
        String[] properties = new String[]{"idTerminalDocumentType", "nameTerminalDocumentType", "flagTerminalDocumentType",
                "idTerminalHandbookType1TerminalDocumentType", "idTerminalHandbookType2TerminalDocumentType"};
        for (String property : properties) {
            query.addProperty(property, terminalLM.findProperty(property).getExpr(terminalDocumentTypeExpr));
        }
        query.and(terminalLM.findProperty("idTerminalDocumentType").getExpr(terminalDocumentTypeExpr).getWhere());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
        for(ImMap<Object, Object> entry : result.values()) {
            String id = trim((String) entry.get("idTerminalDocumentType"));
            String name = trim((String) entry.get("nameTerminalDocumentType"));
            Integer flag = (Integer) entry.get("flagTerminalDocumentType");
            String analytics1 = trim((String) entry.get("idTerminalHandbookType1TerminalDocumentType"));
            String analytics2 = trim((String) entry.get("idTerminalHandbookType2TerminalDocumentType"));
            terminalDocumentTypeList.add(new TerminalDocumentType(id, name, analytics1, analytics2, flag));
        }
        return terminalDocumentTypeList;
    }

    private List<TerminalOrder> readTerminalOrderList(DataSession session) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        if (purchaseInvoiceAgreementLM != null) {

            List<TerminalOrder> terminalOrderList = new ArrayList<TerminalOrder>();
            KeyExpr orderExpr = new KeyExpr("order");
            KeyExpr orderDetailExpr = new KeyExpr("orderDetail");
            ImRevMap<Object, KeyExpr> orderKeys = MapFact.toRevMap((Object) "Order", orderExpr, "OrderDetail", orderDetailExpr);
            QueryBuilder<Object, Object> orderQuery = new QueryBuilder<Object, Object>(orderKeys);
            String[] orderProperties = new String[]{"dateOrder", "numberOrder", "supplierOrder"};
            for (String property : orderProperties) {
                orderQuery.addProperty(property, purchaseInvoiceAgreementLM.findProperty(property).getExpr(orderExpr));
            }
            String[] orderDetailProperties = new String[]{"idBarcodeSkuOrderDetail", "nameSkuOrderDetail", "priceOrderDetail",
                    "quantityOrderDetail", "minDeviationQuantityOrderDetail", "maxDeviationQuantityOrderDetail",
                    "minDeviationPriceOrderDetail", "maxDeviationPriceOrderDetail"};
            for (String property : orderDetailProperties) {
                orderQuery.addProperty(property, purchaseInvoiceAgreementLM.findProperty(property).getExpr(orderDetailExpr));
            }
            orderQuery.and(purchaseInvoiceAgreementLM.findProperty("Purchase.orderOrderDetail").getExpr(orderDetailExpr).compare(orderExpr, Compare.EQUALS));
            orderQuery.and(purchaseInvoiceAgreementLM.findProperty("Purchase.numberOrder").getExpr(orderExpr).getWhere());
            orderQuery.and(purchaseInvoiceAgreementLM.findProperty("Purchase.isOpenedOrder").getExpr(orderExpr).getWhere());
            orderQuery.and(purchaseInvoiceAgreementLM.findProperty("Purchase.idBarcodeSkuOrderDetail").getExpr(orderDetailExpr).getWhere());
            ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> orderResult = orderQuery.executeClasses(session);
            for (ImMap<Object, ObjectValue> entry : orderResult.values()) {
                Date dateOrder = (Date) entry.get("dateOrder").getValue();
                String numberOrder = trim((String) entry.get("numberOrder").getValue());
                String idSupplier = trim((String) purchaseInvoiceAgreementLM.findProperty("idLegalEntity").read(session, entry.get("supplierOrder")));
                String barcode = trim((String) entry.get("idBarcodeSkuOrderDetail").getValue());
                String name = trim((String) entry.get("nameSkuOrderDetail").getValue());
                BigDecimal price = (BigDecimal) entry.get("priceOrderDetail").getValue();
                BigDecimal quantity = (BigDecimal) entry.get("quantityOrderDetail").getValue();
                BigDecimal minQuantity = (BigDecimal) entry.get("minDeviationQuantityOrderDetail").getValue();
                BigDecimal maxQuantity = (BigDecimal) entry.get("maxDeviationQuantityOrderDetail").getValue();
                BigDecimal minPrice = (BigDecimal) entry.get("minDeviationPriceOrderDetail").getValue();
                BigDecimal maxPrice = (BigDecimal) entry.get("maxDeviationPriceOrderDetail").getValue();
                terminalOrderList.add(new TerminalOrder(dateOrder, numberOrder, idSupplier, barcode, name, price, 
                        quantity, minQuantity, maxQuantity, minPrice, maxPrice));
            }
            return terminalOrderList;
        } else return null;
    }

    private List<TerminalLegalEntity> readTerminalLegalEntityList(DataSession session) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        if (legalEntityLM != null) {

            List<TerminalLegalEntity> terminalLegalEntityList = new ArrayList<TerminalLegalEntity>();
            KeyExpr legalEntityExpr = new KeyExpr("legalEntity");
            ImRevMap<Object, KeyExpr> legalEntityKeys = MapFact.singletonRev((Object) "LegalEntity", legalEntityExpr);
            QueryBuilder<Object, Object> legalEntityQuery = new QueryBuilder<Object, Object>(legalEntityKeys);
            String[] legalEntityProperties = new String[]{"idLegalEntity", "nameLegalEntity"};
            for (String property : legalEntityProperties) {
                legalEntityQuery.addProperty(property, legalEntityLM.findProperty(property).getExpr(legalEntityExpr));
            }
            legalEntityQuery.and(legalEntityLM.findProperty("idLegalEntity").getExpr(legalEntityExpr).getWhere());
            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> legalEntityResult = legalEntityQuery.execute(session);
            for (ImMap<Object, Object> entry : legalEntityResult.values()) {
                String idLegalEntity = trim((String) entry.get("idLegalEntity"));
                String nameLegalEntity = trim((String) entry.get("nameLegalEntity"));
                terminalLegalEntityList.add(new TerminalLegalEntity(idLegalEntity, nameLegalEntity));
            }
            return terminalLegalEntityList;
        } else return null;
    }

    private List<TerminalAssortment> readTerminalAssortmentList(DataSession session, ObjectValue priceListTypeObject, ObjectValue stockGroupMachineryObject)
            throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        if (legalEntityLM != null && priceListLedgerLM != null && itemLM != null) {
            
            DataObject currentDateTimeObject = new DataObject(new Timestamp(Calendar.getInstance().getTime().getTime()), DateTimeClass.instance);
            
            List<TerminalAssortment> terminalAssortmentList = new ArrayList<TerminalAssortment>();
            KeyExpr skuExpr = new KeyExpr("Sku");
            KeyExpr legalEntityExpr = new KeyExpr("legalEntity");
            ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap((Object) "Sku", skuExpr, "LegalEntity", legalEntityExpr);
            QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);
            String property = "priceALedgerPriceListTypeSkuStockCompanyDateTime";
            query.addProperty(property, priceListLedgerLM.findProperty(property).getExpr(priceListTypeObject.getExpr(), 
                    skuExpr, stockGroupMachineryObject.getExpr(), legalEntityExpr, currentDateTimeObject.getExpr()));
            query.addProperty("idBarcodeSku", itemLM.findProperty("idBarcodeSku").getExpr(skuExpr));
            query.addProperty("idLegalEntity", legalEntityLM.findProperty("idLegalEntity").getExpr(legalEntityExpr));
            query.and(legalEntityLM.findProperty("idLegalEntity").getExpr(legalEntityExpr).getWhere());
            query.and(itemLM.findProperty("idBarcodeSku").getExpr(skuExpr).getWhere());
            query.and(priceListLedgerLM.findProperty(property).getExpr(priceListTypeObject.getExpr(), 
                    skuExpr, stockGroupMachineryObject.getExpr(), legalEntityExpr, currentDateTimeObject.getExpr()).getWhere());
            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
            for (ImMap<Object, Object> entry : result.values()) {
                String idBarcodeSku = trim((String) entry.get("idBarcodeSku"));
                String idLegalEntity = trim((String) entry.get("idLegalEntity"));
                terminalAssortmentList.add(new TerminalAssortment(idBarcodeSku, idLegalEntity));
            }
            return terminalAssortmentList;
        } else return null;
    }

    @Override
    public List<RequestExchange> readRequestExchange(String sidEquipmentServer) throws RemoteException, SQLException {

        Map<ObjectValue, RequestExchange> requestExchangeMap = new HashMap<ObjectValue, RequestExchange>();
        List<RequestExchange> requestExchangeList = new ArrayList<RequestExchange>();
        
        if(cashRegisterLM != null && machineryPriceTransactionLM != null) {

            try {
                logger.info("RequestSalesInfoStock started");

                DataSession session = getDbManager().createSession();

                KeyExpr requestExchangeExpr = new KeyExpr("requestExchange");
                KeyExpr cashRegisterExpr = new KeyExpr("cashRegister");
                ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap((Object) "requestExchange", requestExchangeExpr, "cashRegister", cashRegisterExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);

                String[] properties = new String[]{"stockRequestExchange", "dateFromRequestExchange", "dateToRequestExchange", "nameRequestExchangeTypeRequestExchange"};
                for (String property : properties)
                    query.addProperty(property, machineryPriceTransactionLM.findProperty(property).getExpr(requestExchangeExpr));
                query.addProperty("overDirectoryCashRegister", cashRegisterLM.findProperty("overDirectoryCashRegister").getExpr(cashRegisterExpr));
                query.addProperty("idStockMachinery", cashRegisterLM.findProperty("idStockMachinery").getExpr(cashRegisterExpr));
                query.and(machineryPriceTransactionLM.findProperty("notSucceededRequestExchange").getExpr(requestExchangeExpr).getWhere());
                query.and(cashRegisterLM.findProperty("overDirectoryCashRegister").getExpr(cashRegisterExpr).getWhere());
                query.and(cashRegisterLM.findProperty("stockMachinery").getExpr(cashRegisterExpr).compare(
                        machineryPriceTransactionLM.findProperty("stockRequestExchange").getExpr(requestExchangeExpr), Compare.EQUALS));
                ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> result = query.executeClasses(session);
                for (int i = 0, size = result.size(); i < size; i++) {

                    DataObject requestExchangeObject = result.getKey(i).get("requestExchange");
                    ObjectValue stockRequestExchange = result.getValue(i).get("stockRequestExchange");
                    String directoryCashRegister = trim((String) result.getValue(i).get("overDirectoryCashRegister").getValue());
                    String idStockMachinery = trim((String) result.getValue(i).get("idStockMachinery").getValue());
                    Date dateFromRequestExchange = (Date) result.getValue(i).get("dateFromRequestExchange").getValue();
                    Date dateToRequestExchange = (Date) result.getValue(i).get("dateToRequestExchange").getValue();
                    String typeRequestExchange = trim((String) result.getValue(i).get("nameRequestExchangeTypeRequestExchange").getValue());
                    Boolean requestSalesInfo = typeRequestExchange.contains("salesInfo");
                    
                    Set<String> extraStockSet = requestSalesInfo ? new HashSet<String>() : readExtraStockMapRequestExchange(session, requestExchangeObject);
                    
                    if (requestExchangeMap.containsKey(stockRequestExchange))
                        requestExchangeMap.get(stockRequestExchange).directorySet.add(directoryCashRegister);
                    else
                        requestExchangeMap.put(stockRequestExchange, new RequestExchange((Integer) result.getKey(i).get("requestExchange").getValue(), 
                                new HashSet<String>(Arrays.asList(directoryCashRegister)), idStockMachinery, extraStockSet,
                                dateFromRequestExchange, dateToRequestExchange, requestSalesInfo));
                }
              
                for (RequestExchange entry : requestExchangeMap.values())
                    requestExchangeList.add(entry);

                session.apply(getBusinessLogics());
            } catch (ScriptingErrorLog.SemanticErrorException e) {
                throw Throwables.propagate(e);
            } catch (SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return requestExchangeList;             
    }
        
    private Set<String> readExtraStockMapRequestExchange(DataSession session, DataObject requestExchangeObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Set<String> stockMap = new HashSet<String>();
        KeyExpr stockExpr = new KeyExpr("stock");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "stock", stockExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);

        query.addProperty("idStock", machineryPriceTransactionLM.findProperty("idStock").getExpr(stockExpr));
        query.and(machineryPriceTransactionLM.findProperty("inStockRequestExchange").getExpr(stockExpr, requestExchangeObject.getExpr()).getWhere());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);
        for (ImMap<Object, Object> entry : result.values()) {
            stockMap.add(trim((String) entry.get("idStock")));
        }
        return stockMap;
    }

    @Override
    public void finishRequestExchange(Set<Integer> succeededRequestsSet) throws RemoteException, SQLException {        
        try {
            if (machineryPriceTransactionLM != null) {
                DataSession session = getDbManager().createSession();
                for (Integer request : succeededRequestsSet)
                    machineryPriceTransactionLM.findProperty("succeededRequestExchange").change(true, session,
                            (DataObject)new DataObject(request, (ConcreteClass) machineryPriceTransactionLM.findClass("RequestExchange")));
                session.apply(getBusinessLogics());
            }
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        } catch (SQLHandledException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public List<CashRegisterInfo> readCashRegisterInfo(String sidEquipmentServer) throws RemoteException, SQLException {
        try {

            List<CashRegisterInfo> cashRegisterInfoList = new ArrayList<CashRegisterInfo>();

            if (cashRegisterLM != null) {

                DataSession session = getDbManager().createSession();

                KeyExpr groupCashRegisterExpr = new KeyExpr("groupCashRegister");
                KeyExpr cashRegisterExpr = new KeyExpr("cashRegister");

                ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap((Object) "GroupCashRegister", groupCashRegisterExpr, "cashRegister", cashRegisterExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);

                String[] cashRegisterProperties = new String[] {"nppMachinery", "nameModelMachinery", "handlerModelMachinery", 
                        "portMachinery", "overDirectoryCashRegister"};
                for(String property : cashRegisterProperties)
                    query.addProperty(property, cashRegisterLM.findProperty(property).getExpr(cashRegisterExpr));
                String[] groupCashRegisterProperties = new String[] {"startDateGroupCashRegister", "notDetailedGroupCashRegister", "nppGroupMachinery"};
                for(String property : groupCashRegisterProperties) 
                    query.addProperty(property, cashRegisterLM.findProperty(property).getExpr(groupCashRegisterExpr));
                
                query.and(cashRegisterLM.findProperty("handlerModelMachinery").getExpr(cashRegisterExpr).getWhere());
                query.and(cashRegisterLM.findProperty("overDirectoryCashRegister").getExpr(cashRegisterExpr).getWhere());
                query.and(cashRegisterLM.findProperty("groupCashRegisterCashRegister").getExpr(cashRegisterExpr).compare(groupCashRegisterExpr, Compare.EQUALS));
                query.and(equLM.findProperty("sidEquipmentServerGroupMachinery").getExpr(groupCashRegisterExpr).compare(new DataObject(sidEquipmentServer, StringClass.get(20)), Compare.EQUALS));

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);

                for (ImMap<Object, Object> row : result.values()) {
                    cashRegisterInfoList.add(new CashRegisterInfo((Integer) row.get("nppGroupMachinery"), (Integer) row.get("nppMachinery"),
                            (String) row.get("nameModelMachinery"), (String) row.get("handlerModelMachinery"), (String) row.get("portMachinery"),
                            (String) row.get("overDirectoryCashRegister"), (java.sql.Date) row.get("startDateGroupCashRegister"), row.get("notDetailedGroupCashRegister") != null));
                }
            }
            return cashRegisterInfoList;
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        } catch (SQLHandledException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public List<TerminalInfo> readTerminalInfo(String sidEquipmentServer) throws RemoteException, SQLException {
        try {

            List<TerminalInfo> terminalInfoList = new ArrayList<TerminalInfo>();

            if (terminalLM != null) {

                DataSession session = getDbManager().createSession();

                KeyExpr groupTerminalExpr = new KeyExpr("groupTerminal");
                KeyExpr terminalExpr = new KeyExpr("terminal");

                ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap((Object) "GroupTerminal", groupTerminalExpr, "terminal", terminalExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);

                query.addProperty("nppMachinery", terminalLM.findProperty("nppMachinery").getExpr(terminalExpr));
                query.addProperty("nameModelMachinery", terminalLM.findProperty("nameModelMachinery").getExpr(terminalExpr));
                query.addProperty("handlerModelMachinery", terminalLM.findProperty("handlerModelMachinery").getExpr(terminalExpr));
                query.addProperty("portMachinery", terminalLM.findProperty("portMachinery").getExpr(terminalExpr));
                query.addProperty("directoryGroupTerminal", terminalLM.findProperty("directoryGroupTerminal").getExpr(groupTerminalExpr));
                query.addProperty("priceListTypeGroupTerminal", terminalLM.findProperty("priceListTypeGroupTerminal").getExpr(groupTerminalExpr));
                
                query.and(terminalLM.findProperty("handlerModelMachinery").getExpr(terminalExpr).getWhere());
                query.and(terminalLM.findProperty("directoryGroupTerminal").getExpr(groupTerminalExpr).getWhere());
                query.and(terminalLM.findProperty("groupTerminalTerminal").getExpr(terminalExpr).compare(groupTerminalExpr, Compare.EQUALS));
                query.and(equLM.findProperty("sidEquipmentServerGroupMachinery").getExpr(groupTerminalExpr).compare(new DataObject(sidEquipmentServer, StringClass.get(20)), Compare.EQUALS));

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);

                for (ImMap<Object, Object> row : result.values()) {
                    Integer priceListType = (Integer) row.get("priceListTypeGroupTerminal");
                    String idPriceListType = priceListType == null ? null : (String) terminalLM.findProperty("idPriceListType").read(session, new DataObject(priceListType));
                    terminalInfoList.add(new TerminalInfo((Integer) row.get("nppMachinery"), (String) row.get("nameModelMachinery"), 
                            (String) row.get("handlerModelMachinery"), (String) row.get("portMachinery"), idPriceListType, (String) row.get("directoryGroupTerminal")));
                }
            }
            return terminalInfoList;
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e.toString());
        } catch (SQLHandledException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public String sendTerminalInfo(List<TerminalDocumentDetail> terminalDocumentDetailList, String sidEquipmentServer) throws RemoteException, SQLException {

        try {

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(terminalDocumentDetailList.size());

            ImportField idTerminalDocumentField = new ImportField(terminalLM.findProperty("idTerminalDocument"));
            ImportKey<?> terminalDocumentKey = new ImportKey((ConcreteCustomClass) terminalLM.findClass("TerminalDocument"),
                    terminalLM.findProperty("terminalDocumentId").getMapping(idTerminalDocumentField));
            keys.add(terminalDocumentKey);
            props.add(new ImportProperty(idTerminalDocumentField, terminalLM.findProperty("idTerminalDocument").getMapping(terminalDocumentKey)));
            fields.add(idTerminalDocumentField);
            for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                data.get(i).add(terminalDocumentDetailList.get(i).idTerminalDocument);

            ImportField titleTerminalDocumentField = new ImportField(terminalLM.findProperty("titleTerminalDocument"));
            props.add(new ImportProperty(titleTerminalDocumentField, terminalLM.findProperty("titleTerminalDocument").getMapping(terminalDocumentKey)));
            fields.add(titleTerminalDocumentField);
            for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                data.get(i).add(terminalDocumentDetailList.get(i).numberTerminalDocument);

            ImportField directoryGroupTerminalField = new ImportField(terminalLM.findProperty("directoryGroupTerminal"));
            ImportKey<?> groupTerminalKey = new ImportKey((ConcreteCustomClass) terminalLM.findClass("GroupTerminal"),
                    terminalLM.findProperty("groupTerminalDirectory").getMapping(directoryGroupTerminalField));
            keys.add(groupTerminalKey);
            props.add(new ImportProperty(directoryGroupTerminalField, terminalLM.findProperty("groupTerminalTerminalDocument").getMapping(terminalDocumentKey),
                    terminalLM.object(terminalLM.findClass("GroupTerminal")).getMapping(groupTerminalKey)));
            fields.add(directoryGroupTerminalField);
            for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                data.get(i).add(terminalDocumentDetailList.get(i).directoryGroupTerminal);

            ImportField idTerminalHandbookType1TerminalDocumentField = new ImportField(terminalLM.findProperty("idTerminalHandbookType1TerminalDocument"));
            props.add(new ImportProperty(idTerminalHandbookType1TerminalDocumentField, terminalLM.findProperty("idTerminalHandbookType1TerminalDocument").getMapping(terminalDocumentKey)));
            fields.add(idTerminalHandbookType1TerminalDocumentField);
            for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                data.get(i).add(terminalDocumentDetailList.get(i).idTerminalHandbookType1);

            ImportField idTerminalHandbookType2TerminalDocumentField = new ImportField(terminalLM.findProperty("idTerminalHandbookType2TerminalDocument"));
            props.add(new ImportProperty(idTerminalHandbookType2TerminalDocumentField, terminalLM.findProperty("idTerminalHandbookType2TerminalDocument").getMapping(terminalDocumentKey)));
            fields.add(idTerminalHandbookType2TerminalDocumentField);
            for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                data.get(i).add(terminalDocumentDetailList.get(i).idTerminalHandbookType2);

            ImportField idTerminalDocumentTypeField = new ImportField(terminalLM.findProperty("idTerminalDocumentType"));
            ImportKey<?> terminalDocumentTypeKey = new ImportKey((ConcreteCustomClass) terminalLM.findClass("TerminalDocumentType"),
                    terminalLM.findProperty("terminalDocumentTypeId").getMapping(idTerminalDocumentTypeField));
            keys.add(terminalDocumentTypeKey);
            props.add(new ImportProperty(idTerminalDocumentTypeField, terminalLM.findProperty("idTerminalDocumentType").getMapping(terminalDocumentTypeKey)));
            props.add(new ImportProperty(idTerminalDocumentTypeField, terminalLM.findProperty("terminalDocumentTypeTerminalDocument").getMapping(terminalDocumentKey),
                    terminalLM.object(terminalLM.findClass("TerminalDocumentType")).getMapping(terminalDocumentTypeKey)));
            fields.add(idTerminalDocumentTypeField);
            for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                data.get(i).add(terminalDocumentDetailList.get(i).idTerminalDocumentType);
            
            ImportField idTerminalDocumentDetailField = new ImportField(terminalLM.findProperty("idTerminalDocumentDetail"));
            ImportKey<?> terminalDocumentDetailKey = new ImportKey((ConcreteCustomClass) terminalLM.findClass("TerminalDocumentDetail"),
                    terminalLM.findProperty("terminalDocumentDetailIdTerminalDocumentId").getMapping(idTerminalDocumentField, idTerminalDocumentDetailField));
            keys.add(terminalDocumentDetailKey);
            props.add(new ImportProperty(idTerminalDocumentDetailField, terminalLM.findProperty("idTerminalDocumentDetail").getMapping(terminalDocumentDetailKey)));
            props.add(new ImportProperty(idTerminalDocumentField, terminalLM.findProperty("terminalDocumentTerminalDocumentDetail").getMapping(terminalDocumentDetailKey),
                    terminalLM.object(terminalLM.findClass("TerminalDocument")).getMapping(terminalDocumentKey)));
            fields.add(idTerminalDocumentDetailField);
            for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                data.get(i).add(terminalDocumentDetailList.get(i).idTerminalDocumentDetail);

            ImportField numberTerminalDocumentDetailField = new ImportField(terminalLM.findProperty("numberTerminalDocumentDetail"));
            props.add(new ImportProperty(numberTerminalDocumentDetailField, terminalLM.findProperty("numberTerminalDocumentDetail").getMapping(terminalDocumentDetailKey)));
            fields.add(numberTerminalDocumentDetailField);
            for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                data.get(i).add(terminalDocumentDetailList.get(i).numberTerminalDocumentDetail);

            ImportField barcodeTerminalDocumentDetailField = new ImportField(terminalLM.findProperty("barcodeTerminalDocumentDetail"));
            props.add(new ImportProperty(barcodeTerminalDocumentDetailField, terminalLM.findProperty("barcodeTerminalDocumentDetail").getMapping(terminalDocumentDetailKey)));
            fields.add(barcodeTerminalDocumentDetailField);
            for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                data.get(i).add(terminalDocumentDetailList.get(i).barcodeTerminalDocumentDetail);

            ImportField priceTerminalDocumentDetailField = new ImportField(terminalLM.findProperty("priceTerminalDocumentDetail"));
            props.add(new ImportProperty(priceTerminalDocumentDetailField, terminalLM.findProperty("priceTerminalDocumentDetail").getMapping(terminalDocumentDetailKey)));
            fields.add(priceTerminalDocumentDetailField);
            for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                data.get(i).add(terminalDocumentDetailList.get(i).priceTerminalDocumentDetail);

            ImportField quantityTerminalDocumentDetailField = new ImportField(terminalLM.findProperty("quantityTerminalDocumentDetail"));
            props.add(new ImportProperty(quantityTerminalDocumentDetailField, terminalLM.findProperty("quantityTerminalDocumentDetail").getMapping(terminalDocumentDetailKey)));
            fields.add(quantityTerminalDocumentDetailField);
            for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                data.get(i).add(terminalDocumentDetailList.get(i).quantityTerminalDocumentDetail);

            ImportField sumTerminalDocumentDetailField = new ImportField(terminalLM.findProperty("sumTerminalDocumentDetail"));
            props.add(new ImportProperty(sumTerminalDocumentDetailField, terminalLM.findProperty("sumTerminalDocumentDetail").getMapping(terminalDocumentDetailKey)));
            fields.add(sumTerminalDocumentDetailField);
            for (int i = 0; i < terminalDocumentDetailList.size(); i++)
                data.get(i).add(terminalDocumentDetailList.get(i).sumTerminalDocumentDetail);
            

            ImportTable table = new ImportTable(fields, data);

            DataSession session = getDbManager().createSession();
            session.pushVolatileStats("ES_TI");
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            String result = session.applyMessage(getBusinessLogics());
            session.popVolatileStats();
            session.close(); 
            
            return result;

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public Map<String, BigDecimal> readRequestZReportSumMap(String idStock, Date dateFrom, Date dateTo) {
        Map<String, BigDecimal> zReportSumMap = new HashMap<String, BigDecimal>();
        if (zReportLM != null && equipmentCashRegisterLM != null) {
            try {
                DataSession session = getDbManager().createSession();
                      
                DataObject stockObject = (DataObject) equipmentCashRegisterLM.findProperty("stockId").readClasses(session, new DataObject(idStock));
                
                KeyExpr zReportExpr = new KeyExpr("zReport");
                ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "zReport", zReportExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);
                String[] properties = new String[]{"sumReceiptDetailZReport", "numberZReport"};
                for (String property : properties)
                    query.addProperty(property, zReportLM.findProperty(property).getExpr(zReportExpr));
                query.and(zReportLM.findProperty("dateZReport").getExpr(zReportExpr).compare(new DataObject(dateFrom, DateClass.instance), Compare.GREATER_EQUALS));
                query.and(zReportLM.findProperty("dateZReport").getExpr(zReportExpr).compare(new DataObject(dateTo, DateClass.instance), Compare.LESS_EQUALS));
                query.and(zReportLM.findProperty("departmentStoreZReport").getExpr(zReportExpr).compare(stockObject.getExpr(), Compare.EQUALS));
                query.and(zReportLM.findProperty("numberZReport").getExpr(zReportExpr).getWhere());
                ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> zReportResult = query.executeClasses(session);
                for (ImMap<Object, ObjectValue> entry : zReportResult.values()) {
                    String numberZReport = trim((String) entry.get("numberZReport").getValue());
                    BigDecimal sumZReport = (BigDecimal) entry.get("sumReceiptDetailZReport").getValue();
                    zReportSumMap.put(numberZReport, sumZReport);
                }
                
                session.apply(getBusinessLogics());
            } catch (ScriptingErrorLog.SemanticErrorException e) {
                throw Throwables.propagate(e);
            } catch (SQLException e) {
                throw Throwables.propagate(e);
            } catch (SQLHandledException e) {
                throw Throwables.propagate(e);
            }
        }
        return zReportSumMap;
    }

    @Override
    public String sendSalesInfo(List<SalesInfo> salesInfoList, String sidEquipmentServer, Integer numberAtATime) throws IOException, SQLException {
        return sendSalesInfoNonRemote(salesInfoList, sidEquipmentServer, numberAtATime);
    }


    public String sendSalesInfoNonRemote(List<SalesInfo> salesInfoList, String sidEquipmentServer, Integer numberAtATime) throws IOException, SQLException {
        try {

            if (zReportLM != null && notNullNorEmpty(salesInfoList)) {

                Collections.sort(salesInfoList, COMPARATOR);

                if (numberAtATime == null)
                    numberAtATime = salesInfoList.size();

                for (int start = 0; true;) {

                    int finish = (start + numberAtATime) < salesInfoList.size() ? (start + numberAtATime) : salesInfoList.size();
                    
                    Integer lastNumberReceipt = start < finish ? salesInfoList.get(finish - 1).numberReceipt : null;
                    if(lastNumberReceipt != null) {
                        while(start < finish && salesInfoList.size() > finish && salesInfoList.get(finish).numberReceipt.equals(lastNumberReceipt))
                            finish++;                        
                    }
                    
                    List<SalesInfo> data = start < finish ? salesInfoList.subList(start, finish) : new ArrayList<SalesInfo>();
                    start = finish;
                    if (!notNullNorEmpty(data))
                        return null;

                    logger.info(String.format("Kristal: Sending SalesInfo from %s to %s", start, finish));
                    
                    DataSession session = getDbManager().createSession();
                    ImportField nppGroupMachineryField = new ImportField(zReportLM.findProperty("nppGroupMachinery"));
                    ImportField nppMachineryField = new ImportField(zReportLM.findProperty("nppMachinery"));

                    ImportField idZReportField = new ImportField(zReportLM.findProperty("idZReport"));
                    ImportField numberZReportField = new ImportField(zReportLM.findProperty("numberZReport"));

                    ImportField idReceiptField = new ImportField(zReportLM.findProperty("idReceipt"));
                    ImportField numberReceiptField = new ImportField(zReportLM.findProperty("numberReceipt"));
                    ImportField dateReceiptField = new ImportField(zReportLM.findProperty("dateReceipt"));
                    ImportField timeReceiptField = new ImportField(zReportLM.findProperty("timeReceipt"));
                    ImportField isPostedZReportField = new ImportField(zReportLM.findProperty("isPostedZReport"));

                    ImportField idReceiptDetailField = new ImportField(zReportLM.findProperty("idReceiptDetail"));
                    ImportField numberReceiptDetailField = new ImportField(zReportLM.findProperty("numberReceiptDetail"));
                    ImportField idBarcodeReceiptDetailField = new ImportField(zReportLM.findProperty("idBarcodeReceiptDetail"));

                    ImportField quantityReceiptSaleDetailField = new ImportField(zReportLM.findProperty("quantityReceiptSaleDetail"));
                    ImportField priceReceiptSaleDetailField = new ImportField(zReportLM.findProperty("priceReceiptSaleDetail"));
                    ImportField sumReceiptSaleDetailField = new ImportField(zReportLM.findProperty("sumReceiptSaleDetail"));
                    ImportField discountSumReceiptSaleDetailField = new ImportField(zReportLM.findProperty("discountSumReceiptSaleDetail"));
                    ImportField discountSumSaleReceiptField = new ImportField(zReportLM.findProperty("discountSumSaleReceipt"));

                    ImportField quantityReceiptReturnDetailField = new ImportField(zReportLM.findProperty("quantityReceiptReturnDetail"));
                    ImportField priceReceiptReturnDetailField = new ImportField(zReportLM.findProperty("priceReceiptReturnDetail"));
                    ImportField retailSumReceiptReturnDetailField = new ImportField(zReportLM.findProperty("sumReceiptReturnDetail"));
                    ImportField discountSumReceiptReturnDetailField = new ImportField(zReportLM.findProperty("discountSumReceiptReturnDetail"));
                    ImportField discountSumReturnReceiptField = new ImportField(zReportLM.findProperty("discountSumReturnReceipt"));

                    ImportField idPaymentField = new ImportField(zReportLM.findProperty("ZReport.idPayment"));
                    ImportField sidTypePaymentField = new ImportField(zReportLM.findProperty("sidPaymentType"));
                    ImportField sumPaymentField = new ImportField(zReportLM.findProperty("ZReport.sumPayment"));
                    ImportField numberPaymentField = new ImportField(zReportLM.findProperty("ZReport.numberPayment"));

                    ImportField seriesNumberDiscountCardField = null;
                    if (discountCardLM != null)
                        seriesNumberDiscountCardField = new ImportField(discountCardLM.findProperty("seriesNumberDiscountCard"));

                    List<ImportProperty<?>> saleProperties = new ArrayList<ImportProperty<?>>();
                    List<ImportProperty<?>> returnProperties = new ArrayList<ImportProperty<?>>();
                    List<ImportProperty<?>> paymentProperties = new ArrayList<ImportProperty<?>>();

                    ImportKey<?> zReportKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("ZReport"), zReportLM.findProperty("zReportId").getMapping(idZReportField));
                    ImportKey<?> cashRegisterKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("CashRegister"), zReportLM.findProperty("cashRegisterNppGroupCashRegisterNpp").getMapping(nppGroupMachineryField, nppMachineryField));
                    ImportKey<?> receiptKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("Receipt"), zReportLM.findProperty("receiptId").getMapping(idReceiptField));
                    ImportKey<?> skuKey = new ImportKey((CustomClass) zReportLM.findClass("Sku"), zReportLM.findProperty("skuBarcodeIdDate").getMapping(idBarcodeReceiptDetailField, dateReceiptField));
                    ImportKey<?> discountCardKey = null;
                    if (discountCardLM != null)
                        discountCardKey = new ImportKey((ConcreteCustomClass) discountCardLM.findClass("DiscountCard"), discountCardLM.findProperty("discountCardSeriesNumber").getMapping(seriesNumberDiscountCardField, dateReceiptField));

                    saleProperties.add(new ImportProperty(idZReportField, zReportLM.findProperty("idZReport").getMapping(zReportKey)));
                    saleProperties.add(new ImportProperty(numberZReportField, zReportLM.findProperty("numberZReport").getMapping(zReportKey)));
                    saleProperties.add(new ImportProperty(nppMachineryField, zReportLM.findProperty("cashRegisterZReport").getMapping(zReportKey),
                            zReportLM.object(zReportLM.findClass("CashRegister")).getMapping(cashRegisterKey)));
                    saleProperties.add(new ImportProperty(dateReceiptField, zReportLM.findProperty("dateZReport").getMapping(zReportKey)));
                    saleProperties.add(new ImportProperty(timeReceiptField, zReportLM.findProperty("timeZReport").getMapping(zReportKey)));
                    saleProperties.add(new ImportProperty(isPostedZReportField, zReportLM.findProperty("isPostedZReport").getMapping(zReportKey)));

                    saleProperties.add(new ImportProperty(idReceiptField, zReportLM.findProperty("idReceipt").getMapping(receiptKey)));
                    saleProperties.add(new ImportProperty(numberReceiptField, zReportLM.findProperty("numberReceipt").getMapping(receiptKey)));
                    saleProperties.add(new ImportProperty(dateReceiptField, zReportLM.findProperty("dateReceipt").getMapping(receiptKey)));
                    saleProperties.add(new ImportProperty(timeReceiptField, zReportLM.findProperty("timeReceipt").getMapping(receiptKey)));
                    saleProperties.add(new ImportProperty(discountSumSaleReceiptField, zReportLM.findProperty("discountSumSaleReceipt").getMapping(receiptKey)));
                    saleProperties.add(new ImportProperty(numberZReportField, zReportLM.findProperty("zReportReceipt").getMapping(receiptKey),
                            zReportLM.object(zReportLM.findClass("ZReport")).getMapping(zReportKey)));
                    if (discountCardLM != null && zReportDiscountCardLM != null) {
                        saleProperties.add(new ImportProperty(seriesNumberDiscountCardField, discountCardLM.findProperty("seriesNumberDiscountCard").getMapping(discountCardKey)));
                        saleProperties.add(new ImportProperty(seriesNumberDiscountCardField, zReportDiscountCardLM.findProperty("discountCardReceipt").getMapping(receiptKey),
                                discountCardLM.object(discountCardLM.findClass("DiscountCard")).getMapping(discountCardKey)));
                    }
                    ImportKey<?> receiptSaleDetailKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("ReceiptSaleDetail"), zReportLM.findProperty("receiptDetailId").getMapping(idReceiptDetailField));
                    saleProperties.add(new ImportProperty(idReceiptDetailField, zReportLM.findProperty("idReceiptDetail").getMapping(receiptSaleDetailKey)));
                    saleProperties.add(new ImportProperty(numberReceiptDetailField, zReportLM.findProperty("numberReceiptDetail").getMapping(receiptSaleDetailKey)));
                    saleProperties.add(new ImportProperty(idBarcodeReceiptDetailField, zReportLM.findProperty("idBarcodeReceiptDetail").getMapping(receiptSaleDetailKey)));
                    saleProperties.add(new ImportProperty(quantityReceiptSaleDetailField, zReportLM.findProperty("quantityReceiptSaleDetail").getMapping(receiptSaleDetailKey)));
                    saleProperties.add(new ImportProperty(priceReceiptSaleDetailField, zReportLM.findProperty("priceReceiptSaleDetail").getMapping(receiptSaleDetailKey)));
                    saleProperties.add(new ImportProperty(sumReceiptSaleDetailField, zReportLM.findProperty("sumReceiptSaleDetail").getMapping(receiptSaleDetailKey)));
                    if (discountCardLM != null && zReportDiscountCardLM != null) {
                        saleProperties.add(new ImportProperty(discountSumReceiptSaleDetailField, zReportDiscountCardLM.findProperty("discountSumReceiptSaleDetail").getMapping(receiptSaleDetailKey)));
                    }
                    saleProperties.add(new ImportProperty(idReceiptField, zReportLM.findProperty("receiptReceiptDetail").getMapping(receiptSaleDetailKey),
                            zReportLM.object(zReportLM.findClass("Receipt")).getMapping(receiptKey)));

                    saleProperties.add(new ImportProperty(idBarcodeReceiptDetailField, zReportLM.findProperty("skuReceiptSaleDetail").getMapping(receiptSaleDetailKey),
                            zReportLM.object(zReportLM.findClass("Sku")).getMapping(skuKey)));

                    returnProperties.add(new ImportProperty(idZReportField, zReportLM.findProperty("idZReport").getMapping(zReportKey)));
                    returnProperties.add(new ImportProperty(numberZReportField, zReportLM.findProperty("numberZReport").getMapping(zReportKey)));
                    returnProperties.add(new ImportProperty(nppMachineryField, zReportLM.findProperty("cashRegisterZReport").getMapping(zReportKey),
                            zReportLM.object(zReportLM.findClass("CashRegister")).getMapping(cashRegisterKey)));
                    returnProperties.add(new ImportProperty(dateReceiptField, zReportLM.findProperty("dateZReport").getMapping(zReportKey)));
                    returnProperties.add(new ImportProperty(timeReceiptField, zReportLM.findProperty("timeZReport").getMapping(zReportKey)));
                    returnProperties.add(new ImportProperty(isPostedZReportField, zReportLM.findProperty("isPostedZReport").getMapping(zReportKey)));

                    returnProperties.add(new ImportProperty(idReceiptField, zReportLM.findProperty("idReceipt").getMapping(receiptKey)));
                    returnProperties.add(new ImportProperty(numberReceiptField, zReportLM.findProperty("numberReceipt").getMapping(receiptKey)));
                    returnProperties.add(new ImportProperty(dateReceiptField, zReportLM.findProperty("dateReceipt").getMapping(receiptKey)));
                    returnProperties.add(new ImportProperty(timeReceiptField, zReportLM.findProperty("timeReceipt").getMapping(receiptKey)));
                    if (discountCardLM != null) {
                        returnProperties.add(new ImportProperty(discountSumReturnReceiptField, zReportLM.findProperty("discountSumReturnReceipt").getMapping(receiptKey)));
                    }
                    returnProperties.add(new ImportProperty(numberZReportField, zReportLM.findProperty("zReportReceipt").getMapping(receiptKey),
                            zReportLM.object(zReportLM.findClass("ZReport")).getMapping(zReportKey)));
                    if (discountCardLM != null && zReportDiscountCardLM != null) {
                        returnProperties.add(new ImportProperty(seriesNumberDiscountCardField, discountCardLM.findProperty("seriesNumberDiscountCard").getMapping(discountCardKey)));
                        returnProperties.add(new ImportProperty(seriesNumberDiscountCardField, zReportDiscountCardLM.findProperty("discountCardReceipt").getMapping(receiptKey),
                                discountCardLM.object(discountCardLM.findClass("DiscountCard")).getMapping(discountCardKey)));
                    }
                    ImportKey<?> receiptReturnDetailKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("ReceiptReturnDetail"), zReportLM.findProperty("receiptDetailId").getMapping(idReceiptDetailField));
                    returnProperties.add(new ImportProperty(idReceiptDetailField, zReportLM.findProperty("idReceiptDetail").getMapping(receiptReturnDetailKey)));
                    returnProperties.add(new ImportProperty(numberReceiptDetailField, zReportLM.findProperty("numberReceiptDetail").getMapping(receiptReturnDetailKey)));
                    returnProperties.add(new ImportProperty(idBarcodeReceiptDetailField, zReportLM.findProperty("idBarcodeReceiptDetail").getMapping(receiptReturnDetailKey)));
                    returnProperties.add(new ImportProperty(quantityReceiptReturnDetailField, zReportLM.findProperty("quantityReceiptReturnDetail").getMapping(receiptReturnDetailKey)));
                    returnProperties.add(new ImportProperty(priceReceiptReturnDetailField, zReportLM.findProperty("priceReceiptReturnDetail").getMapping(receiptReturnDetailKey)));
                    returnProperties.add(new ImportProperty(retailSumReceiptReturnDetailField, zReportLM.findProperty("sumReceiptReturnDetail").getMapping(receiptReturnDetailKey)));
                    returnProperties.add(new ImportProperty(discountSumReceiptReturnDetailField, zReportLM.findProperty("discountSumReceiptReturnDetail").getMapping(receiptReturnDetailKey)));
                    returnProperties.add(new ImportProperty(idReceiptField, zReportLM.findProperty("receiptReceiptDetail").getMapping(receiptReturnDetailKey),
                            zReportLM.object(zReportLM.findClass("Receipt")).getMapping(receiptKey)));

                    returnProperties.add(new ImportProperty(idBarcodeReceiptDetailField, zReportLM.findProperty("skuReceiptReturnDetail").getMapping(receiptReturnDetailKey),
                            zReportLM.object(zReportLM.findClass("Sku")).getMapping(skuKey)));

                    List<List<Object>> dataSale = new ArrayList<List<Object>>();
                    List<List<Object>> dataReturn = new ArrayList<List<Object>>();

                    List<List<Object>> dataPayment = new ArrayList<List<Object>>();

                    Map<Integer, String> barcodeMap = new HashMap<Integer, String>();
                    for (SalesInfo sale : data) {
                        
                        String barcode = (notNullNorEmpty(sale.barcodeItem)) ? sale.barcodeItem : (sale.itemObject != null ? barcodeMap.get(sale.itemObject) : null);
                        if(barcode == null && sale.itemObject != null) {
                            barcode = trim((String) itemLM.findProperty("idBarcodeSku").read(session, new DataObject(sale.itemObject, (ConcreteClass) itemLM.findClass("Item"))));
                            barcodeMap.put(sale.itemObject, barcode);
                        }
                                                
                        String idZReport = sale.nppGroupMachinery + "_" + sale.nppMachinery + "_" + sale.numberZReport; 
                        String idReceipt = sale.nppGroupMachinery + "_" + sale.nppMachinery + "_" + sale.numberZReport + "_" + sale.numberReceipt;
                        String idReceiptDetail = sale.nppGroupMachinery + "_" + sale.nppMachinery + "_"  + sale.numberZReport + "_" + sale.numberReceipt + "_" + sale.numberReceiptDetail;
                        if (sale.quantityReceiptDetail.doubleValue() < 0) {
                            List<Object> row = Arrays.<Object>asList(sale.nppGroupMachinery, sale.nppMachinery, idZReport, sale.numberZReport,
                                    sale.dateReceipt, sale.timeReceipt, true, idReceipt, sale.numberReceipt,
                                    idReceiptDetail, sale.numberReceiptDetail, barcode, sale.quantityReceiptDetail.negate(),
                                    sale.priceReceiptDetail, sale.sumReceiptDetail.negate(), sale.discountSumReceiptDetail,
                                    sale.discountSumReceipt);
                            if (discountCardLM != null) {
                                row = new ArrayList<Object>(row);
                                row.add(sale.seriesNumberDiscountCard);
                            }
                            dataReturn.add(row);
                        } else {
                            List<Object> row = Arrays.<Object>asList(sale.nppGroupMachinery, sale.nppMachinery, idZReport, sale.numberZReport,
                                    sale.dateReceipt, sale.timeReceipt, true, idReceipt, sale.numberReceipt,
                                    idReceiptDetail, sale.numberReceiptDetail, barcode, sale.quantityReceiptDetail,
                                    sale.priceReceiptDetail, sale.sumReceiptDetail, sale.discountSumReceiptDetail,
                                    sale.discountSumReceipt);
                            if (discountCardLM != null) {
                                row = new ArrayList<Object>(row);
                                row.add(sale.seriesNumberDiscountCard);
                            }
                            dataSale.add(row);
                        }
                        if (sale.sumCash != null && sale.sumCash.doubleValue() != 0) {
                            dataPayment.add(Arrays.<Object>asList(idReceipt + "1", idReceipt, "cash", sale.sumCash, 1));
                        }
                        if (sale.sumCard != null && sale.sumCard.doubleValue() != 0) {
                            dataPayment.add(Arrays.<Object>asList(idReceipt + "2", idReceipt, "card", sale.sumCard, 2));
                        }
                    }

                    List<ImportField> saleImportFields = Arrays.asList(nppGroupMachineryField, nppMachineryField,
                            idZReportField, numberZReportField, dateReceiptField, timeReceiptField, isPostedZReportField, idReceiptField,
                            numberReceiptField, idReceiptDetailField, numberReceiptDetailField, idBarcodeReceiptDetailField,
                            quantityReceiptSaleDetailField, priceReceiptSaleDetailField, sumReceiptSaleDetailField,
                            discountSumReceiptSaleDetailField, discountSumSaleReceiptField);
                    if (discountCardLM != null) {
                        saleImportFields = new ArrayList<ImportField>(saleImportFields);
                        saleImportFields.add(seriesNumberDiscountCardField);
                    }

                    List<ImportField> returnImportFields = Arrays.asList(nppGroupMachineryField, nppMachineryField,
                            idZReportField, numberZReportField, dateReceiptField, timeReceiptField, isPostedZReportField, idReceiptField,
                            numberReceiptField, idReceiptDetailField, numberReceiptDetailField, idBarcodeReceiptDetailField,
                            quantityReceiptReturnDetailField, priceReceiptReturnDetailField, retailSumReceiptReturnDetailField,
                            discountSumReceiptReturnDetailField, discountSumReturnReceiptField);
                    if (discountCardLM != null) {
                        returnImportFields = new ArrayList<ImportField>(returnImportFields);
                        returnImportFields.add(seriesNumberDiscountCardField);
                    }


                    List<ImportKey<?>> saleKeys = Arrays.asList(zReportKey, cashRegisterKey, receiptKey, receiptSaleDetailKey, skuKey);
                    if (discountCardLM != null) {
                        saleKeys = new ArrayList<ImportKey<?>>(saleKeys);
                        saleKeys.add(discountCardKey);
                    }

                    session.pushVolatileStats("ES_SI");
                    new IntegrationService(session, new ImportTable(saleImportFields, dataSale), saleKeys, saleProperties).synchronize(true);

                    List<ImportKey<?>> returnKeys = Arrays.asList(zReportKey, cashRegisterKey, receiptKey, receiptReturnDetailKey, skuKey);
                    if (discountCardLM != null) {
                        returnKeys = new ArrayList<ImportKey<?>>(returnKeys);
                        returnKeys.add(discountCardKey);
                    }
                    new IntegrationService(session, new ImportTable(returnImportFields, dataReturn), returnKeys, returnProperties).synchronize(true);

                    ImportKey<?> paymentKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("ZReport.Payment"), zReportLM.findProperty("ZReport.paymentId").getMapping(idPaymentField));
                    ImportKey<?> paymentTypeKey = new ImportKey((ConcreteCustomClass) zReportLM.findClass("PaymentType"), zReportLM.findProperty("typePaymentSID").getMapping(sidTypePaymentField));
                    paymentProperties.add(new ImportProperty(idPaymentField, zReportLM.findProperty("ZReport.idPayment").getMapping(paymentKey)));
                    paymentProperties.add(new ImportProperty(sumPaymentField, zReportLM.findProperty("ZReport.sumPayment").getMapping(paymentKey)));
                    paymentProperties.add(new ImportProperty(numberPaymentField, zReportLM.findProperty("ZReport.numberPayment").getMapping(paymentKey)));
                    paymentProperties.add(new ImportProperty(sidTypePaymentField, zReportLM.findProperty("paymentTypePayment").getMapping(paymentKey),
                            zReportLM.object(zReportLM.findClass("PaymentType")).getMapping(paymentTypeKey)));
                    paymentProperties.add(new ImportProperty(idReceiptField, zReportLM.findProperty("receiptPayment").getMapping(paymentKey),
                            zReportLM.object(zReportLM.findClass("Receipt")).getMapping(receiptKey)));

                    List<ImportField> paymentImportFields = Arrays.asList(idPaymentField, idReceiptField, sidTypePaymentField,
                            sumPaymentField, numberPaymentField);

                    String message = "Загружено записей: " + (dataSale.size() + dataReturn.size());
                    Map<Integer, Set<Integer>> nppCashRegisterMap = new HashMap<Integer, Set<Integer>>();
                    List<String> fileNames = new ArrayList<String>();
                    Set<String> dates = new HashSet<String>();
                    for (SalesInfo salesInfo : data) {
                        if(nppCashRegisterMap.containsKey(salesInfo.nppGroupMachinery))
                            nppCashRegisterMap.get(salesInfo.nppGroupMachinery).add(salesInfo.nppMachinery);
                        else
                            nppCashRegisterMap.put(salesInfo.nppGroupMachinery, new HashSet<Integer>(Arrays.asList(salesInfo.nppMachinery)));
                        if ((salesInfo.filename != null) && (!fileNames.contains(salesInfo.filename.trim())))
                            fileNames.add(salesInfo.filename.trim());
                        if(salesInfo.dateReceipt != null)
                            dates.add(new SimpleDateFormat("dd.MM.yyyy").format(salesInfo.dateReceipt));
                    }
                    message += "\nИз касс: ";
                    for (Map.Entry<Integer, Set<Integer>> cashRegisterEntry : nppCashRegisterMap.entrySet()) {
                        for(Integer cashRegister : cashRegisterEntry.getValue()) 
                            message += String.format("%s(%s), ", cashRegister, cashRegisterEntry.getKey());
                    }
                    message = message.substring(0, message.length() - 2);

                    message += "\nИз файлов: ";
                    for (String filename : fileNames)
                        message += filename + ", ";
                    message = message.substring(0, message.length() - 2);
                    
                    if(notNullNorEmpty(dates)) {
                        message += "\nЗа даты: ";
                        for (String date : dates)
                            message += date + ", ";
                        message = message.substring(0, message.length() - 2);
                    }

                    DataObject logObject = session.addObject((ConcreteCustomClass) equLM.findClass("EquipmentServerLog"));
                    Object equipmentServerObject = equLM.findProperty("sidToEquipmentServer").read(session, new DataObject(sidEquipmentServer, StringClass.get(20)));
                    equLM.findProperty("equipmentServerEquipmentServerLog").change(equipmentServerObject, session, logObject);
                    equLM.findProperty("dataEquipmentServerLog").change(message, session, logObject);
                    equLM.findProperty("dateEquipmentServerLog").change(DateConverter.dateToStamp(Calendar.getInstance().getTime()), session, logObject);

                    new IntegrationService(session, new ImportTable(paymentImportFields, dataPayment), Arrays.asList(paymentKey, paymentTypeKey, receiptKey),
                            paymentProperties).synchronize(true);

                    String result = session.applyMessage(getBusinessLogics());
                    session.popVolatileStats();
                    if(result != null)
                        return result;
                }
            } else return null;

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public Set<String> readCashDocumentSet(String sidEquipmentServer) throws IOException, SQLException {

        Set<String> cashDocumentSet = new HashSet<String>();

        try {

            if (collectionLM != null) {

                DataSession session = getDbManager().createSession();

                KeyExpr cashDocumentExpr = new KeyExpr("cashDocument");
                ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "CashDocument", cashDocumentExpr);
                QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);
                query.addProperty("idCashDocument", collectionLM.findProperty("idCashDocument").getExpr(cashDocumentExpr));
                query.and(collectionLM.findProperty("idCashDocument").getExpr(cashDocumentExpr).getWhere());
                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);

                for (ImMap<Object, Object> row : result.values()) {
                    cashDocumentSet.add((String) row.get("idCashDocument"));
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        } catch (SQLHandledException e) {
            throw Throwables.propagate(e);
        }
        return cashDocumentSet;
    }

    @Override
    public String sendCashDocumentInfo(List<CashDocument> cashDocumentList, String sidEquipmentServer) throws IOException, SQLException {

        if (collectionLM != null && cashDocumentList != null) {

            try {

                List<ImportField> fieldsIncome = new ArrayList<ImportField>();
                List<ImportField> fieldsOutcome = new ArrayList<ImportField>();

                List<ImportProperty<?>> propsIncome = new ArrayList<ImportProperty<?>>();
                List<ImportProperty<?>> propsOutcome = new ArrayList<ImportProperty<?>>();

                List<ImportKey<?>> keysIncome = new ArrayList<ImportKey<?>>();
                List<ImportKey<?>> keysOutcome = new ArrayList<ImportKey<?>>();
                
                List<List<Object>> dataIncome = new ArrayList<List<Object>>();
                List<List<Object>> dataOutcome = new ArrayList<List<Object>>();

                ImportField idCashDocumentField = new ImportField(collectionLM.findProperty("idCashDocument"));               
                
                ImportKey<?> incomeCashOperationKey = new ImportKey((CustomClass) collectionLM.findClass("IncomeCashOperation"),
                        collectionLM.findProperty("cashDocumentId").getMapping(idCashDocumentField));
                keysIncome.add(incomeCashOperationKey);
                propsIncome.add(new ImportProperty(idCashDocumentField, collectionLM.findProperty("idCashDocument").getMapping(incomeCashOperationKey)));
                propsIncome.add(new ImportProperty(idCashDocumentField, collectionLM.findProperty("numberIncomeCashOperation").getMapping(incomeCashOperationKey)));
                fieldsIncome.add(idCashDocumentField);

                ImportKey<?> outcomeCashOperationKey = new ImportKey((CustomClass) collectionLM.findClass("OutcomeCashOperation"),
                        collectionLM.findProperty("cashDocumentId").getMapping(idCashDocumentField));
                keysOutcome.add(outcomeCashOperationKey);
                propsOutcome.add(new ImportProperty(idCashDocumentField, collectionLM.findProperty("idCashDocument").getMapping(outcomeCashOperationKey)));
                propsOutcome.add(new ImportProperty(idCashDocumentField, collectionLM.findProperty("numberOutcomeCashOperation").getMapping(outcomeCashOperationKey)));                
                fieldsOutcome.add(idCashDocumentField);
                
                ImportField dateIncomeCashOperationField = new ImportField(collectionLM.findProperty("dateIncomeCashOperation"));
                propsIncome.add(new ImportProperty(dateIncomeCashOperationField, collectionLM.findProperty("dateIncomeCashOperation").getMapping(incomeCashOperationKey)));
                fieldsIncome.add(dateIncomeCashOperationField);

                ImportField dateOutcomeCashOperationField = new ImportField(collectionLM.findProperty("dateOutcomeCashOperation"));
                propsOutcome.add(new ImportProperty(dateOutcomeCashOperationField, collectionLM.findProperty("dateOutcomeCashOperation").getMapping(outcomeCashOperationKey)));                
                fieldsOutcome.add(dateOutcomeCashOperationField);

                ImportField timeIncomeCashOperationField = new ImportField(collectionLM.findProperty("timeIncomeCashOperation"));
                propsIncome.add(new ImportProperty(timeIncomeCashOperationField, collectionLM.findProperty("timeIncomeCashOperation").getMapping(incomeCashOperationKey)));
                fieldsIncome.add(timeIncomeCashOperationField);
                
                ImportField timeOutcomeCashOperationField = new ImportField(collectionLM.findProperty("timeOutcomeCashOperation"));
                propsOutcome.add(new ImportProperty(timeOutcomeCashOperationField, collectionLM.findProperty("timeOutcomeCashOperation").getMapping(outcomeCashOperationKey)));
                fieldsOutcome.add(timeOutcomeCashOperationField);

                ImportField nppMachineryField = new ImportField(collectionLM.findProperty("nppMachinery"));
                ImportField sidEquipmentServerField = new ImportField(equLM.findProperty("sidEquipmentServer"));
                ImportKey<?> cashRegisterKey = new ImportKey((ConcreteCustomClass) collectionLM.findClass("CashRegister"),
                        equLM.findProperty("cashRegisterNppEquipmentServer").getMapping(nppMachineryField, sidEquipmentServerField));
                
                keysIncome.add(cashRegisterKey);
                propsIncome.add(new ImportProperty(nppMachineryField, collectionLM.findProperty("cashRegisterIncomeCashOperation").getMapping(incomeCashOperationKey),
                        collectionLM.object(collectionLM.findClass("CashRegister")).getMapping(cashRegisterKey)));
                fieldsIncome.add(nppMachineryField);
                fieldsIncome.add(sidEquipmentServerField);
                
                keysOutcome.add(cashRegisterKey);
                propsOutcome.add(new ImportProperty(nppMachineryField, collectionLM.findProperty("cashRegisterOutcomeCashOperation").getMapping(outcomeCashOperationKey),
                        collectionLM.object(collectionLM.findClass("CashRegister")).getMapping(cashRegisterKey)));                
                fieldsOutcome.add(nppMachineryField);
                fieldsOutcome.add(sidEquipmentServerField);                

                ImportField sumCashIncomeCashOperationField = new ImportField(collectionLM.findProperty("sumCashIncomeCashOperation"));
                propsIncome.add(new ImportProperty(sumCashIncomeCashOperationField, collectionLM.findProperty("sumCashIncomeCashOperation").getMapping(incomeCashOperationKey)));
                fieldsIncome.add(sumCashIncomeCashOperationField);

                ImportField sumCashOutcomeCashOperationField = new ImportField(collectionLM.findProperty("sumCashOutcomeCashOperation"));
                propsOutcome.add(new ImportProperty(sumCashOutcomeCashOperationField, collectionLM.findProperty("sumCashOutcomeCashOperation").getMapping(outcomeCashOperationKey)));
                fieldsOutcome.add(sumCashOutcomeCashOperationField);

                for (CashDocument cashDocument : cashDocumentList) {
                    if (cashDocument.sumCashDocument != null) {
                        if (cashDocument.sumCashDocument.compareTo(BigDecimal.ZERO) >= 0)
                            dataIncome.add(Arrays.asList((Object) cashDocument.numberCashDocument, cashDocument.dateCashDocument,
                                    cashDocument.timeCashDocument, cashDocument.nppMachinery, sidEquipmentServer, cashDocument.sumCashDocument));
                        else
                            dataOutcome.add(Arrays.asList((Object) cashDocument.numberCashDocument, cashDocument.dateCashDocument,
                                    cashDocument.timeCashDocument, cashDocument.nppMachinery, sidEquipmentServer, cashDocument.sumCashDocument.negate()));
                    }
                }
                
                
                ImportTable table = new ImportTable(fieldsIncome, dataIncome);
                DataSession session = getDbManager().createSession();
                session.pushVolatileStats("ES_CDI");
                IntegrationService service = new IntegrationService(session, table, keysIncome, propsIncome);
                service.synchronize(true, false);
                String resultIncome = session.applyMessage(getBusinessLogics());
                session.popVolatileStats();
                session.close();

                if(resultIncome != null)
                    return resultIncome;
                
                table = new ImportTable(fieldsOutcome, dataOutcome);
                session = getDbManager().createSession();
                session.pushVolatileStats("ES_CDI");
                service = new IntegrationService(session, table, keysOutcome, propsOutcome);
                service.synchronize(true, false);
                String resultOutcome = session.applyMessage(getBusinessLogics());
                session.popVolatileStats();
                session.close();
                
                return resultOutcome;
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        } else return null;
    }

    @Override
    public void succeedTransaction(Integer transactionID, Timestamp dateTime) throws RemoteException, SQLException {
        try {
            DataSession session = getDbManager().createSession();
            equLM.findProperty("succeededMachineryPriceTransaction").change(true, session,
                    session.getDataObject(equLM.findClass("MachineryPriceTransaction"), transactionID));
            equLM.findProperty("dateTimeSucceededMachineryPriceTransaction").change(dateTime, session,
                    session.getDataObject(equLM.findClass("MachineryPriceTransaction"), transactionID));
            session.apply(getBusinessLogics());
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public List<byte[][]> readLabelFormats(List<String> scalesModelsList) throws RemoteException, SQLException {
        try {
            DataSession session = getDbManager().createSession();

            List<byte[][]> fileLabelFormats = new ArrayList<byte[][]>();

            if (scalesLM != null) {

                for (String scalesModel : scalesModelsList) {

                    DataObject scalesModelObject = new DataObject(scalesLM.findProperty("scalesModelName").read(session, new DataObject(scalesModel)), (ConcreteClass) scalesLM.findClass("ScalesModel"));

                    LCP<PropertyInterface> isLabelFormat = (LCP<PropertyInterface>) scalesLM.is(scalesLM.findClass("LabelFormat"));

                    ImRevMap<PropertyInterface, KeyExpr> labelFormatKeys = isLabelFormat.getMapKeys();
                    KeyExpr labelFormatKey = labelFormatKeys.singleValue();
                    QueryBuilder<PropertyInterface, Object> labelFormatQuery = new QueryBuilder<PropertyInterface, Object>(labelFormatKeys);

                    labelFormatQuery.addProperty("fileLabelFormat", scalesLM.findProperty("fileLabelFormat").getExpr(labelFormatKey));
                    labelFormatQuery.addProperty("fileMessageLabelFormat", scalesLM.findProperty("fileMessageLabelFormat").getExpr(labelFormatKey));
                    labelFormatQuery.and(isLabelFormat.property.getExpr(labelFormatKeys).getWhere());
                    labelFormatQuery.and(scalesLM.findProperty("scalesModelLabelFormat").getExpr(labelFormatKey).compare((scalesModelObject).getExpr(), Compare.EQUALS));

                    ImOrderMap<ImMap<PropertyInterface, Object>, ImMap<Object, Object>> labelFormatResult = labelFormatQuery.execute(session);

                    for (ImMap<Object, Object> row : labelFormatResult.valueIt()) {
                        byte[] fileLabelFormat = (byte[]) row.get("fileLabelFormat");
                        byte[] fileMessageLabelFormat = (byte[]) row.get("fileMessageLabelFormat");
                        fileLabelFormats.add(new byte[][]{fileLabelFormat, fileMessageLabelFormat});
                    }
                }
            }
            return fileLabelFormats;
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        } catch (SQLHandledException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void errorTransactionReport(Integer transactionID, Throwable e) throws RemoteException, SQLException {
        try {
            DataSession session = getDbManager().createSession();
            DataObject errorObject = session.addObject((ConcreteCustomClass) equLM.findClass("MachineryPriceTransactionError"));
            equLM.findProperty("machineryPriceTransactionMachineryPriceTransactionError").change(transactionID, session, errorObject);
            equLM.findProperty("dataMachineryPriceTransactionError").change(e.toString(), session, errorObject);
            equLM.findProperty("dateMachineryPriceTransactionError").change(DateConverter.dateToStamp(Calendar.getInstance().getTime()), session, errorObject);
            OutputStream os = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(os));
            equLM.findProperty("errorTraceMachineryPriceTransactionError").change(os.toString(), session, errorObject);

            session.apply(getBusinessLogics());
        } catch (Exception e2) {
            throw Throwables.propagate(e2);
        }
    }

    @Override
    public void errorEquipmentServerReport(String equipmentServer, Throwable exception) throws
            RemoteException, SQLException {
        try {
            DataSession session = getDbManager().createSession();
            DataObject errorObject = session.addObject((ConcreteCustomClass) equLM.findClass("EquipmentServerError"));
            Object equipmentServerObject = equLM.findProperty("sidToEquipmentServer").read(session, new DataObject(equipmentServer, StringClass.get(20)));
            equLM.findProperty("equipmentServerEquipmentServerError").change(equipmentServerObject, session, errorObject);
            equLM.findProperty("dataEquipmentServerError").change(exception.toString(), session, errorObject);
            OutputStream os = new ByteArrayOutputStream();
            exception.printStackTrace(new PrintStream(os));
            equLM.findProperty("erTraceEquipmentServerError").change(os.toString(), session, errorObject);

            equLM.findProperty("dateEquipmentServerError").change(DateConverter.dateToStamp(Calendar.getInstance().getTime()), session, errorObject);

            session.apply(getBusinessLogics());
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public EquipmentServerSettings readEquipmentServerSettings(String equipmentServer) throws RemoteException, SQLException {
        try {
            ThreadLocalContext.set(logicsInstance.getContext());
            DataSession session = getDbManager().createSession();
            Integer equipmentServerID = (Integer) equLM.findProperty("sidToEquipmentServer").read(session, new DataObject(equipmentServer, StringClass.get(20)));
            if (equipmentServerID != null) {
                DataObject equipmentServerObject = new DataObject(equipmentServerID, (ConcreteClass) equLM.findClass("EquipmentServer"));
                Integer delay = (Integer) equLM.findProperty("delayEquipmentServer").read(session, equipmentServerObject);
                Integer numberAtATime = (Integer) equLM.findProperty("numberAtATimeEquipmentServer").read(session, equipmentServerObject);
                return new EquipmentServerSettings(delay, numberAtATime);
            } else return null;
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

    private String trim(String input) {
        return input == null ? null : input.trim();
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
    
    private List<List<Object>> initData(int size) {
        List<List<Object>> data = new ArrayList<List<Object>>();
        for (int i = 0; i < size; i++) {
            data.add(new ArrayList<Object>());
        }
        return data;
    }

    private static Comparator<SalesInfo> COMPARATOR = new Comparator<SalesInfo>() {
        public int compare(SalesInfo o1, SalesInfo o2) {
            int compareCashRegister = BaseUtils.nullCompareTo(o1.nppMachinery, o2.nppMachinery); 
            if (compareCashRegister == 0)
                return BaseUtils.nullCompareTo(o1.numberZReport, o2.numberZReport);                    
            else
                return compareCashRegister;
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
