package lsfusion.erp.integration.universal;

import lsfusion.base.BaseUtils;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.Compare;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.property.PropertyInterface;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.sql.SQLException;

public class ImportPurchaseInvoicesEmailActionProperty extends ImportDocumentActionProperty {

    public ImportPurchaseInvoicesEmailActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            DataSession session = context.getSession();

            LCP<PropertyInterface> isImportType = (LCP<PropertyInterface>) LM.is(getClass("ImportType"));
            ImRevMap<PropertyInterface, KeyExpr> importTypeKeys = isImportType.getMapKeys();
            KeyExpr importTypeKey = importTypeKeys.singleValue();
            QueryBuilder<PropertyInterface, Object> importTypeQuery = new QueryBuilder<PropertyInterface, Object>(importTypeKeys);
            importTypeQuery.addProperty("autoImportEmailImportType", getLCP("autoImportEmailImportType").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("autoImportAccountImportType", getLCP("autoImportAccountImportType").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("captionFileExtensionImportType", getLCP("captionFileExtensionImportType").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("startRowImportType", getLCP("startRowImportType").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("isPostedImportType", getLCP("isPostedImportType").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("separatorImportType", getLCP("separatorImportType").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("captionPrimaryKeyTypeImportType", getLCP("captionPrimaryKeyTypeImportType").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("captionSecondaryKeyTypeImportType", getLCP("captionSecondaryKeyTypeImportType").getExpr(session.getModifier(), importTypeKey));

            importTypeQuery.addProperty("autoImportSupplierImportType", getLCP("autoImportSupplierImportType").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("autoImportSupplierStockImportType", getLCP("autoImportSupplierStockImportType").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("autoImportCustomerImportType", getLCP("autoImportCustomerImportType").getExpr(session.getModifier(), importTypeKey));
            importTypeQuery.addProperty("autoImportCustomerStockImportType", getLCP("autoImportCustomerStockImportType").getExpr(session.getModifier(), importTypeKey));

            importTypeQuery.and(isImportType.getExpr(importTypeKey).getWhere());
            importTypeQuery.and(getLCP("autoImportImportType").getExpr(importTypeKey).getWhere());
            importTypeQuery.and(getLCP("autoImportEmailImportType").getExpr(importTypeKey).getWhere());
            ImOrderMap<ImMap<PropertyInterface, DataObject>, ImMap<Object, ObjectValue>> importTypeResult = importTypeQuery.executeClasses(session);

            for (int i = 0, size = importTypeResult.size(); i < size; i++) {
                ImMap<Object, ObjectValue> entryValue = importTypeResult.getValue(i);

                DataObject importTypeObject = importTypeResult.getKey(i).valueIt().iterator().next();

                ObjectValue accountObject = entryValue.get("autoImportAccountImportType");
                ObjectValue emailObject = entryValue.get("autoImportEmailImportType");
                String emailPattern = emailObject instanceof DataObject ? ((String) ((DataObject) emailObject).object).replace("*", ".*") : null;
                String fileExtension = trim((String) entryValue.get("captionFileExtensionImportType").getValue());
                Integer startRow = (Integer) entryValue.get("startRowImportType").getValue();
                startRow = startRow == null ? 1 : startRow;
                Boolean isPosted = (Boolean) entryValue.get("isPostedImportType").getValue();
                String csvSeparator = trim((String) getLCP("separatorImportType").read(session, importTypeObject));
                csvSeparator = csvSeparator == null ? ";" : csvSeparator;
                String primaryKeyType = parseKeyType((String) getLCP("namePrimaryKeyTypeImportType").read(session, importTypeObject));
                boolean checkExistence = getLCP("checkExistencePrimaryKeyImportType").read(session, importTypeObject) != null;
                String secondaryKeyType = parseKeyType((String) getLCP("nameSecondaryKeyTypeImportType").read(session, importTypeObject));
                boolean keyIsDigit = getLCP("keyIsDigitImportType").read(session, importTypeObject) != null;
                String propertyImportType = (String) getLCP("propertyImportTypeDetailImportType").read(session, importTypeObject);
                String staticNameImportType = (String) getLCP("staticNameImportTypeDetailImportType").read(session, importTypeObject);
                
                if (fileExtension != null && emailObject instanceof DataObject && accountObject instanceof DataObject) {

                    KeyExpr emailExpr = new KeyExpr("email");
                    KeyExpr attachmentEmailExpr = new KeyExpr("attachmentEmail");
                    ImRevMap<Object, KeyExpr> emailKeys = MapFact.toRevMap((Object) "email", emailExpr, "attachmentEmail", attachmentEmailExpr);

                    QueryBuilder<Object, Object> emailQuery = new QueryBuilder<Object, Object>(emailKeys);
                    emailQuery.addProperty("fromAddressEmail", getLCP("fromAddressEmail").getExpr(session.getModifier(), emailExpr));
                    emailQuery.addProperty("fileAttachmentEmail", getLCP("fileAttachmentEmail").getExpr(session.getModifier(), attachmentEmailExpr));
                    
                    emailQuery.and(getLCP("emailAttachmentEmail").getExpr(session.getModifier(), attachmentEmailExpr).compare(emailExpr, Compare.EQUALS));
                    emailQuery.and(getLCP("accountEmail").getExpr(session.getModifier(), emailExpr).compare(accountObject.getExpr(), Compare.EQUALS));
                    emailQuery.and(getLCP("notImportedAttachmentEmail").getExpr(session.getModifier(), attachmentEmailExpr).getWhere());
                    emailQuery.and(getLCP("fileAttachmentEmail").getExpr(session.getModifier(), attachmentEmailExpr).getWhere());

                    ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> emailResult = emailQuery.executeClasses(session);

                    for (int j = 0, sizej = emailResult.size(); j < sizej; j++) {
                        ImMap<Object, ObjectValue> emailEntryValue = emailResult.getValue(j);
                        ObjectValue attachmentEmailObject = emailResult.getKey(j).get("attachmentEmail");
                        String fromAddressEmail = (String) emailEntryValue.get("fromAddressEmail").getValue();
                        if (fromAddressEmail != null && emailPattern != null && fromAddressEmail.matches(emailPattern)) {
                            byte[] fileAttachment = BaseUtils.getFile((byte[]) emailEntryValue.get("fileAttachmentEmail").getValue());
                            DataSession currentSession = context.createSession();
                            DataObject invoiceObject = currentSession.addObject((ConcreteCustomClass) getClass("Purchase.UserInvoice"));

                            try {

                                boolean importResult = new ImportPurchaseInvoiceActionProperty(LM).makeImport(context, 
                                        currentSession, invoiceObject, importTypeObject, fileAttachment, fileExtension,
                                        startRow, isPosted, csvSeparator, primaryKeyType, checkExistence, secondaryKeyType,
                                        keyIsDigit, propertyImportType, staticNameImportType);

                                if (importResult)
                                    getLCP("importedAttachmentEmail").change(true, currentSession, (DataObject) attachmentEmailObject);
                                currentSession.apply(context);

                            } catch (Exception e) {
                                ServerLoggers.systemLogger.error(e);
                            }
                        }
                    }
                }
            }
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }
}