package lsfusion.erp.integration.universal;

import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.stock.BarcodeUtils;
import lsfusion.interop.Compare;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.integration.ImportField;
import lsfusion.server.integration.ImportKey;
import lsfusion.server.integration.ImportProperty;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.io.File;
import java.sql.SQLException;
import java.util.*;

public abstract class ImportDocumentActionProperty extends ImportUniversalActionProperty {

    public static int IMPORT_RESULT_OK = 1;
    public static int IMPORT_RESULT_EMPTY = 0;
    public static int IMPORT_RESULT_ERROR = -1;

    public ImportDocumentActionProperty(ScriptingLogicsModule LM, ValueClass valueClass) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, valueClass);
    }

    public ImportDocumentActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
    }

    protected List<LinkedHashMap<String, ImportColumnDetail>> readImportColumns(DataSession session, ObjectValue importTypeObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        LinkedHashMap<String, ImportColumnDetail> defaultColumns = new LinkedHashMap<String, ImportColumnDetail>();
        LinkedHashMap<String, ImportColumnDetail> customColumns = new LinkedHashMap<String, ImportColumnDetail>();

        KeyExpr importTypeDetailExpr = new KeyExpr("importTypeDetail");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "importTypeDetail", importTypeDetailExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);
        String[] names = new String[] {"staticName", "staticCaption", "propertyImportTypeDetail", "nameKeyImportTypeDetail"};
        LCP[] properties = findProperties("staticName", "staticCaption", "canonicalNamePropImportTypeDetail", "nameKeyImportTypeDetail");
        for (int j = 0; j < properties.length; j++) {
            query.addProperty(names[j], properties[j].getExpr(importTypeDetailExpr));
        }
        query.addProperty("replaceOnlyNullImportTypeImportTypeDetail", findProperty("replaceOnlyNullImportTypeImportTypeDetail").getExpr(importTypeObject.getExpr(), importTypeDetailExpr));
        query.addProperty("indexImportTypeImportTypeDetail", findProperty("indexImportTypeImportTypeDetail").getExpr(importTypeObject.getExpr(), importTypeDetailExpr));
        query.and(findProperty("indexImportTypeImportTypeDetail").getExpr(importTypeObject.getExpr(), importTypeDetailExpr).getWhere());
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);

        for (ImMap<Object, Object> entry : result.valueIt()) {

            String staticNameProperty = trim((String) entry.get("staticName"));
            String field = getSplittedPart(staticNameProperty, "\\.", -1);
            String staticCaptionProperty = trim((String) entry.get("staticCaption"));
            String propertyImportTypeDetail = (String) entry.get("propertyImportTypeDetail");
            String keyImportTypeDetail = getSplittedPart((String) entry.get("nameKeyImportTypeDetail"), "\\.", 1);
            boolean replaceOnlyNull = entry.get("replaceOnlyNullImportTypeImportTypeDetail") != null;
            String indexes = (String) entry.get("indexImportTypeImportTypeDetail");
            if (indexes != null) {
                String[] splittedIndexes = indexes.split("\\+");
                for (int i = 0; i < splittedIndexes.length; i++)
                    splittedIndexes[i] = splittedIndexes[i].contains("=") ? splittedIndexes[i] : splittedIndexes[i].trim();
                if(field != null)
                    defaultColumns.put(field, new ImportColumnDetail(staticCaptionProperty, indexes, splittedIndexes, replaceOnlyNull));
                else if(keyImportTypeDetail != null)
                    customColumns.put(staticCaptionProperty, new ImportColumnDetail(staticCaptionProperty, indexes, splittedIndexes, replaceOnlyNull,
                            (String) entry.get("propertyImportTypeDetail"), keyImportTypeDetail));
            }
        }
        return Arrays.asList(defaultColumns, customColumns);
    }

    protected Map<String, String> readStockMapping(DataSession session, ObjectValue importTypeObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        Map<String, String> stockMapping = new HashMap<String, String>();

        KeyExpr key = new KeyExpr("stockMappingEntry");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "StockMappingEntry", key);
        QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(keys);
        
        query.addProperty("idStockMappingEntry", findProperty("idStockMappingEntry").getExpr(session.getModifier(), key));
        query.addProperty("idStockStockMappingEntry", findProperty("idStockStockMappingEntry").getExpr(session.getModifier(), key));
        query.and(findProperty("idStockMappingEntry").getExpr(key).getWhere());
        query.and(findProperty("importTypeStockMappingEntry").getExpr(key).compare(importTypeObject.getExpr(), Compare.EQUALS));
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(session);

        for (ImMap<Object, Object> entry : result.valueIt()) {

            String idStockMappingEntry = (String) entry.get("idStockMappingEntry");
            String idStockStockMappingEntry = (String) entry.get("idStockStockMappingEntry");
            stockMapping.put(idStockMappingEntry, idStockStockMappingEntry);            
        }
        return stockMapping;
    }

    public ImportDocumentSettings readImportDocumentSettings(DataSession session, ObjectValue importTypeObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, String> stockMapping = readStockMapping(session, importTypeObject);
        String fileExtension = trim((String) findProperty("captionFileExtensionImportType").read(session, importTypeObject));
        String primaryKeyType = parseKeyType((String) findProperty("namePrimaryKeyTypeImportType").read(session, importTypeObject));
        boolean checkExistence = findProperty("checkExistencePrimaryKeyImportType").read(session, importTypeObject) != null;
        String secondaryKeyType = parseKeyType((String) findProperty("nameSecondaryKeyTypeImportType").read(session, importTypeObject));
        boolean keyIsDigit = findProperty("keyIsDigitImportType").read(session, importTypeObject) != null;
        Integer startRow = (Integer) findProperty("startRowImportType").read(session, importTypeObject);
        startRow = startRow == null ? 1 : startRow;
        Boolean isPosted = (Boolean) findProperty("isPostedImportType").read(session, importTypeObject);
        String separator = formatSeparator((String) findProperty("separatorImportType").read(session, importTypeObject));
        String propertyImportType = trim((String) findProperty("propertyImportTypeDetailImportType").read(session, importTypeObject));
        return new ImportDocumentSettings(stockMapping, fileExtension, primaryKeyType, checkExistence, secondaryKeyType, keyIsDigit, startRow, isPosted, separator, propertyImportType);
    }

    public String parseKeyType(String keyType) {
        String[] primaryParts = keyType == null ? null : keyType.split("\\.");
        return primaryParts == null ? null : trim(primaryParts[primaryParts.length - 1]);
    }

    public String getItemKeyColumn(String keyType) {
        return (keyType == null || keyType.equals("item")) ? "idItem" : keyType.equals("barcode") ? "barcodeItem" : "idBatch";
    }
    
    public LCP getItemKeyGroupAggr(String keyType) throws ScriptingErrorLog.SemanticErrorException {
        return findProperty((keyType == null || keyType.equals("item")) ? "itemId" : keyType.equals("barcode") ? "skuBarcodeId" : "skuBatchId");
    }

    protected void addDataField(List<ImportProperty<?>> props, List<ImportField> fields, Map<String, ImportColumnDetail> importColumns, LCP sidProperty, String nameField, ImportKey<?> key) throws ScriptingErrorLog.SemanticErrorException {
        ImportField field = new ImportField(sidProperty);
        props.add(new ImportProperty(field, sidProperty.getMapping(key), getReplaceOnlyNull(importColumns, nameField)));
        fields.add(field);
    }

    protected void addDataField(List<ImportProperty<?>> props, List<ImportField> fields, Map<String, ImportColumnDetail> importColumns, LCP sidProperty, String nameField, DataObject dataObject) throws ScriptingErrorLog.SemanticErrorException {
        ImportField field = new ImportField(sidProperty);
        props.add(new ImportProperty(field, sidProperty.getMapping(dataObject), getReplaceOnlyNull(importColumns, nameField)));
        fields.add(field);
    }

    protected boolean checkKeyColumnValue(String keyColumn, String keyColumnValue, boolean keyIsDigit)
            throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        return checkKeyColumnValue(keyColumn, keyColumnValue, keyIsDigit, null, null, false);
    }
    
    protected boolean checkKeyColumnValue(String keyColumn, String keyColumnValue, boolean keyIsDigit,
                                          DataSession session, String keyType, boolean checkExistence)
            throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        if(keyColumn != null && keyColumn.equals("barcodeItem"))
            keyColumnValue = BarcodeUtils.appendCheckDigitToBarcode(keyColumnValue, 7);
        return keyColumn != null && keyColumnValue != null && !keyColumnValue.isEmpty() && (!keyIsDigit || keyColumnValue.matches("(\\d|\\-)+")) 
                && (!checkExistence || getItemKeyGroupAggr(keyType).read(session, new DataObject(keyColumnValue)) != null);
    }

    protected static String getSplittedPart(String value, String splitPattern, int index) {
        if(value == null) return null;
        String[] splittedValue = value.trim().split(splitPattern);
        int len = splittedValue.length;
        return index >= 0 ? (len <= index ? null : splittedValue[index]) : len < -index ? null : splittedValue[len + index];
    }
    
    protected String formatSeparator(String separator) {
        String result = trim(separator, ";");
        if(result.equals("|"))
            result = "\\" + result;
        return result;
    }

    protected void renameImportedFile(ExecutionContext context, String oldPath, String extension) {
        File importedFile = new File(oldPath);
        String newExtensionUpCase = extension.substring(0, extension.length() - 1) + "E";
        String newExtensionLowCase = extension.toLowerCase().substring(0, extension.length() - 1) + "e";
        if (importedFile.isFile()) {
            File renamedFile = oldPath.endsWith(extension) ? new File(oldPath.replace(extension, newExtensionUpCase)) :
                    (oldPath.endsWith(extension.toLowerCase()) ? new File(oldPath.replace(extension.toLowerCase(), newExtensionLowCase)) : null);
            if (renamedFile != null && !importedFile.renameTo(renamedFile))
                context.requestUserInteraction(new MessageClientAction("Ошибка при переименовании импортированного файла " + oldPath, "Ошибка"));
        }
    }
}

