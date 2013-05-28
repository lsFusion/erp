package platform.gwt.paas.server.handlers;

import net.customware.gwt.dispatch.server.ExecutionContext;
import net.customware.gwt.dispatch.shared.DispatchException;
import paas.api.remote.PaasRemoteInterface;
import platform.gwt.base.server.dispatch.SecuredActionHandler;
import platform.gwt.paas.server.spring.PaasDispatchServlet;
import platform.gwt.paas.shared.actions.GetConfigurationsResult;
import platform.gwt.paas.shared.actions.StartConfigurationAction;

import java.rmi.RemoteException;

public class StartConfigurationHandler extends SecuredActionHandler<StartConfigurationAction, GetConfigurationsResult, PaasRemoteInterface> {

    public StartConfigurationHandler(PaasDispatchServlet servlet) {
        super(servlet);
    }

    @Override
    public GetConfigurationsResult executeEx(final StartConfigurationAction action, final ExecutionContext context) throws DispatchException, RemoteException {
        return new GetConfigurationsResult(
                servlet.getLogics().startConfiguration(
                        getAuthentication().getName(), action.configuration));
    }
}

