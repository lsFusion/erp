package lsfusion.erp.region.by.certificate.declaration;

import lsfusion.base.IOUtils;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.integration.DefaultImportActionProperty;
import lsfusion.interop.Compare;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.Settings;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.CustomStaticFormatFileClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.integration.*;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;
import org.xBaseJ.DBF;
import org.xBaseJ.xBaseJException;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class ImportDeclarationDBFActionProperty extends DefaultImportActionProperty {
    private final ClassPropertyInterface declarationInterface;

    public ImportDeclarationDBFActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, LM.findClassByCompoundName("Declaration"));

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        declarationInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, "Файл G47.DBF", "DBF");
            ObjectValue objectValue = context.requestUserData(valueClass, null);

            DataObject declarationObject = context.getDataKeyValue(declarationInterface);

            if (objectValue != null) {

                List<byte[]> fileList = valueClass.getFiles(objectValue.getValue());
                boolean disableVolatileStats = Settings.get().isDisableExplicitVolatileStats();
                
                for (byte[] entry : fileList) {

                    File tempFile = File.createTempFile("tempTnved", ".dbf");
                    IOUtils.putFileBytes(tempFile, entry);

                    DBF dbfFile = new DBF(tempFile.getPath());

                    importDeclaration(context, declarationObject, dbfFile, disableVolatileStats);
                    
                    tempFile.delete();

                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (xBaseJException e) {
            throw new RuntimeException(e);
        }
    }

    private void importDeclaration(ExecutionContext context, DataObject declarationObject, DBF dbfFile, boolean disableVolatileStats) throws SQLException, ScriptingErrorLog.SemanticErrorException, IOException, xBaseJException, SQLHandledException {

        DataSession session = context.createSession();
        
        List<List<Object>> data = readDeclarationFromDBF(session, declarationObject, dbfFile);

        KeyExpr declarationDetailExpr = new KeyExpr("DeclarationDetail");
        ImRevMap<Object, KeyExpr> declarationDetailKeys = MapFact.singletonRev((Object) "declarationDetail", declarationDetailExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<Object, Object>(declarationDetailKeys);
        
        query.and(getLCP("declarationDeclarationDetail").getExpr(context.getModifier(), declarationDetailExpr).compare(declarationObject.getExpr(), Compare.EQUALS));
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> result = query.execute(context);

        if(result.size() != data.size())
            context.requestUserInteraction(new MessageClientAction(String.format("Разное количество строк во входном файле G47 (%s) и в базе (%s)", data.size(), result.size()), "Ошибка"));
        
        else {

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            ImportField numberDeclarationDetailField = new ImportField(getLCP("numberDeclarationDetail"));
            ImportKey<?> declarationDetailKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("DeclarationDetail"),
                    getLCP("declarationDetailDeclarationNumber").getMapping(declarationObject, numberDeclarationDetailField));
            keys.add(declarationDetailKey);
            props.add(new ImportProperty(declarationObject, getLCP("declarationDeclarationDetail").getMapping(declarationDetailKey)));
            fields.add(numberDeclarationDetailField);

            ImportField dutySumDeclarationDetailField = new ImportField(getLCP("dutySumDeclarationDetail"));
            props.add(new ImportProperty(dutySumDeclarationDetailField, getLCP("dutySumDeclarationDetail").getMapping(declarationDetailKey)));
            fields.add(dutySumDeclarationDetailField);

            ImportField VATSumDeclarationDetailField = new ImportField(getLCP("VATSumDeclarationDetail"));
            props.add(new ImportProperty(VATSumDeclarationDetailField, getLCP("VATSumDeclarationDetail").getMapping(declarationDetailKey)));
            fields.add(VATSumDeclarationDetailField);

            ImportField homeSumDeclarationDetailField = new ImportField(getLCP("homeSumDeclarationDetail"));
            props.add(new ImportProperty(homeSumDeclarationDetailField, getLCP("homeSumDeclarationDetail").getMapping(declarationDetailKey)));
            fields.add(homeSumDeclarationDetailField);

            ImportTable table = new ImportTable(fields, data);

            if (!disableVolatileStats)
                session.pushVolatileStats();
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            if (!disableVolatileStats)
                session.popVolatileStats();
            session.close();
        }
    }

    private List<List<Object>> readDeclarationFromDBF(DataSession session, DataObject declarationObject, DBF importFile) throws ScriptingErrorLog.SemanticErrorException, SQLException, IOException, xBaseJException, SQLHandledException {

        int recordCount = importFile.getRecordCount();

        List<List<Object>> data = new ArrayList<List<Object>>();

        BigDecimal dutySum = null;
        boolean second = true;
        for (int i = 0; i < recordCount; i++) {

            importFile.read();

            String g471 = trim(getFieldValue(importFile, "G471", "cp866", false, null));

            if (g471 != null) {
                if ((g471.equals("2010") || g471.equals("5010"))) {
                    second = !second;

                    Integer numberDeclarationDetail = getIntegerFieldValue(importFile, "G32", "cp866", false, null);
                    BigDecimal homeSum = getBigDecimalFieldValue(importFile, "G472", "cp866", false, null);
                    BigDecimal g474 = getBigDecimalFieldValue(importFile, "G474", "cp866", false, null);  //dutySum - VATSum
                    if (second) {
                        data.add(Arrays.asList((Object) numberDeclarationDetail, dutySum, g474, homeSum));
                    } else {
                        dutySum = g474;
                    }
                }  else if(g471.equals("1010")) {
                    BigDecimal g474 = getBigDecimalFieldValue(importFile, "G474", "cp866", false, null);  //dutySum - VATSum
                    getLCP("registrationSumDeclaration").change(g474, session, declarationObject);
                }
            }
        }
        importFile.close();
        return data;
    }


    private String getFieldValue(DBF importFile, String fieldName, String charset, Boolean zeroIsNull, String defaultValue) throws UnsupportedEncodingException {
        try {
            String result = trim(new String(importFile.getField(fieldName).getBytes(), charset));
            return result.isEmpty() || (zeroIsNull && result.equals("0")) ? defaultValue : result;
        } catch (xBaseJException e) {
            return defaultValue;
        }
    }

    private BigDecimal getBigDecimalFieldValue(DBF importFile, String fieldName, String charset, Boolean zeroIsNull, String defaultValue) throws UnsupportedEncodingException {
        String result = getFieldValue(importFile, fieldName, charset, zeroIsNull, defaultValue);
        return (result == null || result.isEmpty() || (zeroIsNull && Double.valueOf(result).equals(new Double(0)))) ? null : new BigDecimal(result.replace(",", "."));
    }

    private Integer getIntegerFieldValue(DBF importFile, String fieldName, String charset, Boolean zeroIsNull, String defaultValue) throws UnsupportedEncodingException {
        String result = getFieldValue(importFile, fieldName, charset, zeroIsNull, defaultValue);
        return (result == null || (zeroIsNull && Double.valueOf(result).equals(new Double(0)))) ? null : new Double(result).intValue();
    }
}
