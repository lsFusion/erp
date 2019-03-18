package lsfusion.erp.region.ua.machinery.cashregister.fiscaldatecs;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.session.DataSession;

import java.sql.SQLException;

public class FiscalDatecsZReportActionProperty extends InternalAction {

    public FiscalDatecsZReportActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            DataSession session = context.getSession();

            Integer comPort = (Integer) findProperty("comPortCurrentCashRegister[]").read(context.getSession());
            Integer baudRate = (Integer) findProperty("baudRateCurrentCashRegister[]").read(context.getSession());

            if (context.checkApply()) {
                Object VATSumReceipt = context.requestUserInteraction(new FiscalDatecsCustomOperationClientAction(2, baudRate, comPort));
                if (VATSumReceipt instanceof Double[]) {
//                    ObjectValue zReportObject = findProperty("currentZReport").readClasses(session);
//                    if (!zReportObject.isNull()) {
//                        findProperty("VATSumSaleZReport").change(((Object[]) VATSumReceipt)[0], session, (DataObject) zReportObject);
//                        findProperty("VATSumReturnZReport").change(((Object[]) VATSumReceipt)[1], session, (DataObject) zReportObject);
//                    }
                    context.apply();
                    findAction("closeCurrentZReport[]").execute(session, context.stack);
                } else if (VATSumReceipt != null)
                    context.requestUserInteraction(new MessageClientAction((String) VATSumReceipt, "Ошибка"));
            }
        } catch (SQLException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }
}
