package lsfusion.erp.integration.universal.purchaseinvoice;

import lsfusion.erp.integration.universal.ImportColumnDetail;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.physics.dev.integration.service.ImportField;
import lsfusion.server.physics.dev.integration.service.ImportKey;
import lsfusion.server.physics.dev.integration.service.ImportProperty;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.util.LinkedHashMap;
import java.util.List;

public class ImportPurchaseInvoiceItemPharmacyBy extends ImportDefaultPurchaseInvoiceActionProperty {

    public ImportPurchaseInvoiceItemPharmacyBy(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void makeImport(ExecutionContext context, List<ImportField> fields, List<ImportKey<?>> keys, List<ImportProperty<?>> props, LinkedHashMap<String, ImportColumnDetail> defaultColumns,
                           List<PurchaseInvoiceDetail> userInvoiceDetailsList, List<List<Object>> data, ImportKey<?> itemKey) throws ScriptingErrorLog.SemanticErrorException {
        ScriptingLogicsModule LM = context.getBL().getModule("ItemPharmBy");

        if (LM != null && itemKey != null) {

            if (showField(userInvoiceDetailsList, "pharmacyPriceGroupItem")) {
                ImportField idPharmacyPriceGroupField = new ImportField(LM.findProperty("id[PharmacyPriceGroup]"));
                ImportKey<?> pharmacyPriceGroupKey = new ImportKey((ConcreteCustomClass) LM.findClass("PharmacyPriceGroup"),
                        LM.findProperty("pharmacyPriceGroup[STRING[100]]").getMapping(idPharmacyPriceGroupField));
                keys.add(pharmacyPriceGroupKey);
                props.add(new ImportProperty(idPharmacyPriceGroupField, LM.findProperty("id[PharmacyPriceGroup]").getMapping(pharmacyPriceGroupKey), getReplaceOnlyNull(defaultColumns, "idPharmacyPriceGroup")));
                props.add(new ImportProperty(idPharmacyPriceGroupField, LM.findProperty("name[PharmacyPriceGroup]").getMapping(pharmacyPriceGroupKey), getReplaceOnlyNull(defaultColumns, "idPharmacyPriceGroup")));
                props.add(new ImportProperty(idPharmacyPriceGroupField, LM.findProperty("pharmacyPriceGroup[Item]").getMapping(itemKey),
                        object(LM.findClass("PharmacyPriceGroup")).getMapping(pharmacyPriceGroupKey), getReplaceOnlyNull(defaultColumns, "idPharmacyPriceGroup")));
                fields.add(idPharmacyPriceGroupField);
                for (int i = 0; i < userInvoiceDetailsList.size(); i++)
                    data.get(i).add(userInvoiceDetailsList.get(i).getFieldValue("pharmacyPriceGroupItem"));
            }

        }
        
    }
}
