package lsfusion.erp.integration.universal.purchaseinvoice;

import lsfusion.erp.integration.universal.ImportColumnDetail;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.service.ImportField;
import lsfusion.server.physics.dev.integration.service.ImportKey;
import lsfusion.server.physics.dev.integration.service.ImportProperty;

import java.sql.Date;
import java.util.LinkedHashMap;
import java.util.List;

public class ImportPurchaseInvoicePurchaseShipment extends ImportDefaultPurchaseInvoiceAction {

    public ImportPurchaseInvoicePurchaseShipment(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void makeImport(ExecutionContext<ClassPropertyInterface> context, List<ImportField> fields, List<ImportProperty<?>> props, LinkedHashMap<String, ImportColumnDetail> defaultColumns,
                           List<PurchaseInvoiceDetail> userInvoiceDetailsList, List<List<Object>> data, ImportKey<?> userInvoiceDetailKey) throws ScriptingErrorLog.SemanticErrorException {
        ScriptingLogicsModule LM = context.getBL().getModule("PurchaseShipment");

        if (LM != null && userInvoiceDetailKey != null) {

            if (showField(userInvoiceDetailsList, "expiryDate")) {
                addDataField(props, fields, defaultColumns, LM.findProperty("expiryDate[UserInvoiceDetail]"), "expiryDate", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(sqlDateToLocalDate((Date) userInvoiceDetailsList.get(i).getFieldValue("expiryDate")));
            }

            if (showField(userInvoiceDetailsList, "manufactureDate")) {
                addDataField(props, fields, defaultColumns, LM.findProperty("manufactureDate[UserInvoiceDetail]"), "manufactureDate", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(sqlDateToLocalDate((Date) userInvoiceDetailsList.get(i).getFieldValue("manufactureDate")));
            }

            if (showField(userInvoiceDetailsList, "shipmentPrice")) {
                addDataField(props, fields, defaultColumns, LM.findProperty("shipmentPrice[UserInvoiceDetail]"), "shipmentPrice", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("shipmentPrice"));
            }

            if (showField(userInvoiceDetailsList, "shipmentSum")) {
                addDataField(props, fields, defaultColumns, LM.findProperty("shipmentSum[UserInvoiceDetail]"), "shipmentSum", userInvoiceDetailKey);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("shipmentSum"));
            }

        }
        
    }
}
