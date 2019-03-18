package lsfusion.erp.daemon;

import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;

public class DiscountCardDaemonActionProperty extends InternalAction {

    public DiscountCardDaemonActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        context.requestUserInteraction(new DiscountCardDaemonClientAction());
    }
}