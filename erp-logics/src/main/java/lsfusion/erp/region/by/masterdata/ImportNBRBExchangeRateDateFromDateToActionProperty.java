package lsfusion.erp.region.by.masterdata;


import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.jdom.JDOMException;

import java.io.IOException;
import java.sql.Date;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Iterator;

public class ImportNBRBExchangeRateDateFromDateToActionProperty extends ImportNBRBExchangeRateActionProperty {
    private final ClassPropertyInterface currencyInterface;

    public ImportNBRBExchangeRateDateFromDateToActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, LM.findClassByCompoundName("Currency"));

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        currencyInterface = i.next();
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {

        try {

            DataObject currencyObject = context.getDataKeyValue(currencyInterface);

            String shortNameCurrency = (String) LM.findLCPByCompoundName("shortNameCurrency").read(context, currencyObject);
            Date nbrbDateFrom = (Date) LM.findLCPByCompoundName("importNBRBExchangeRateDateFrom").read(context);
            Date nbrbDateTo = (Date) LM.findLCPByCompoundName("importNBRBExchangeRateDateTo").read(context);

            if (nbrbDateFrom != null && nbrbDateTo != null && shortNameCurrency != null)
                importExchanges(nbrbDateFrom, nbrbDateTo, shortNameCurrency, context);

        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (JDOMException e) {
            throw new RuntimeException(e);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

    }
}