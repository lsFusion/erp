package lsfusion.erp.region.by.integration.edi.topby;

import lsfusion.erp.region.by.integration.edi.SendEInvoiceActionProperty;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.jdom.JDOMException;

import java.io.IOException;
import java.sql.SQLException;

public class SendEInvoiceTopByActionProperty extends SendEInvoiceActionProperty {
    String provider = "TopBy";

    public SendEInvoiceTopByActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            String login = (String) findProperty("loginTopBy[]").read(context);
            String password = (String) findProperty("passwordTopBy[]").read(context);
            String host = (String) findProperty("hostTopBy[]").read(context);
            Integer port = (Integer) findProperty("portTopBy[]").read(context);
            if (login != null && password != null && host != null && port != null) {
                String url = String.format("http://%s:%s/DmcService", host, port);
                sendEInvoice(context, url, login, password, host, port, provider);
            } else {
                ServerLoggers.importLogger.info(provider + " SendEInvoice: не заданы имя пользователя / пароль / хост / порт");
                context.delayUserInteraction(new MessageClientAction(provider + " Заказ не выгружен: не заданы имя пользователя / пароль / хост / порт", "Экспорт"));
            }

        } catch (ScriptingErrorLog.SemanticErrorException | IOException | JDOMException e) {
            ServerLoggers.importLogger.error(provider + " error: ", e);
            context.delayUserInteraction(new MessageClientAction(provider + " error: " + e.getMessage(), "Ошибка"));
        }
    }
}