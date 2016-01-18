package lsfusion.erp.region.by.integration.excel;

import com.google.common.base.Throwables;
import jxl.Sheet;
import jxl.read.biff.BiffException;
import lsfusion.erp.integration.ImportActionProperty;
import lsfusion.erp.integration.ImportData;
import lsfusion.erp.integration.Item;
import lsfusion.server.classes.CustomStaticFormatFileClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ImportExcelItemsActionProperty extends ImportExcelActionProperty {

    public ImportExcelItemsActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {

            CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, "Файлы таблиц", "xls");
            ObjectValue objectValue = context.requestUserData(valueClass, null);
            if (objectValue != null) {
                List<byte[]> fileList = valueClass.getFiles(objectValue.getValue());

                for (byte[] file : fileList) {

                    ImportData importData = new ImportData();

                    importData.setItemsList(importItems(file));

                    new ImportActionProperty(LM).makeImport(importData, context);

                }
            }
        } catch (IOException | BiffException | ParseException e) {
            throw Throwables.propagate(e);
        }
    }

    protected static List<Item> importItems(byte[] file) throws IOException, BiffException, ParseException {

        Sheet sheet = getSheet(file, 21);

        List<Item> data = new ArrayList<>();

        for (int i = 1; i < sheet.getRows(); i++) {
            String idItem = parseString(sheet.getCell(0, i));
            String idGroup = parseString(sheet.getCell(1, i));
            String nameItem = parseString(sheet.getCell(2, i));
            String idUOM = parseString(sheet.getCell(3, i));
            String nameBrand = parseString(sheet.getCell(4, i));
            String idBrand = parseString(sheet.getCell(5, i));
            String nameCountry = parseString(sheet.getCell(6, i));
            String barcode = parseString(sheet.getCell(7, i));
            Date date = parseDateValue(sheet.getCell(8, i), new Date(Calendar.getInstance().getTime().getTime()));
            Boolean split = parseBoolean(sheet.getCell(9, i));
            BigDecimal netWeightItem = parseBigDecimal(sheet.getCell(10, i));
            BigDecimal grossWeightItem = parseBigDecimal(sheet.getCell(11, i));
            String compositionItem = parseString(sheet.getCell(12, i));
            BigDecimal retailVAT = parseBigDecimal(sheet.getCell(13, i));
            if(retailVAT != null && retailVAT.doubleValue() > 100)
                retailVAT = null;
            String idWare = parseString(sheet.getCell(14, i));
            BigDecimal priceWare = parseBigDecimal(sheet.getCell(15, i));
            BigDecimal wareVAT = parseBigDecimal(sheet.getCell(16, i));
            if(wareVAT != null && wareVAT.doubleValue() > 100)
                wareVAT = null;
            String idWriteOffRate = parseString(sheet.getCell(17, i));
            BigDecimal baseMarkup = parseBigDecimal(sheet.getCell(18, i));
            BigDecimal retailMarkup = parseBigDecimal(sheet.getCell(19, i));
            BigDecimal amountPack = parseBigDecimal(sheet.getCell(20, i));
            amountPack = (amountPack == null || amountPack.equals(BigDecimal.ZERO)) ? null : amountPack;
            idItem = idItem == null ? nameItem : idItem;
            data.add(new Item(idItem, idGroup, nameItem, idUOM, nameBrand, idBrand, nameCountry,
                    barcode, barcode, date, split, netWeightItem, grossWeightItem, compositionItem, retailVAT, idWare,
                    priceWare, wareVAT, idWriteOffRate, baseMarkup, retailMarkup, idItem, amountPack, null, null, null,
                    null, null));
        }

        return data;
    }
}