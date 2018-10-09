package lsfusion.erp.region.by.ukm;

import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.StringEntity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

public class DeleteUnexistingCategoriesLoyaActionProperty extends LoyaActionProperty {
    String failCaption = "Loya: Ошибка при удалении несуществующих категорий";

    public DeleteUnexistingCategoriesLoyaActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            boolean logRequests = findProperty("logRequestsLoya[]").read(context) != null;
            SettingsLoya settings = login(context);
            if (settings.error == null) {

                Set<Long> existingCategories = readExistingCategories(context);
                if ((deleteUnexistingCategories(context, settings, existingCategories, logRequests)))
                    context.delayUserInteraction(new MessageClientAction("Удаление несуществующих категорий завершено", "Loya"));

            } else context.delayUserInteraction(new MessageClientAction(settings.error, failCaption));
        } catch (Exception e) {
            ServerLoggers.importLogger.error(failCaption, e);
            context.delayUserInteraction(new MessageClientAction(e.getMessage(), failCaption));
        }
    }

    private Set<Long> readExistingCategories(ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Set<Long> itemGroupSet = new HashSet<>();

        KeyExpr itemGroupExpr = new KeyExpr("ItemGroup");
        ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev((Object) "itemGroup", itemGroupExpr);
        QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);
        query.addProperty("overId", findProperty("overId[ItemGroup]").getExpr(itemGroupExpr));
        query.and(findProperty("overId[ItemGroup]").getExpr(itemGroupExpr).getWhere());

        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> itemGroupResult = query.execute(context);

        for (ImMap<Object, Object> row : itemGroupResult.valueIt()) {
            Long overId = parseGroup((String) row.get("overId"));
            itemGroupSet.add(overId);
        }
        return itemGroupSet;
    }

    protected boolean deleteUnexistingCategories(ExecutionContext context, SettingsLoya settings, Set<Long> existingCategories, boolean logRequests) throws IOException, JSONException {

        ServerLoggers.importLogger.info("Loya: deleting unexisting categories started");
        List<Long> categories = getLoyaCategories(context, settings, logRequests);
        boolean succeeded = true;
        for (Long category : categories) {
            if (!existingCategories.contains(category) && !deleteCategory(context, settings, category, logRequests)) {
                succeeded = false;
            }
        }
        return succeeded;
    }

    private List<Long> getLoyaCategories(ExecutionContext context, SettingsLoya settings, boolean logRequests) throws IOException, JSONException {
        String requestURL = settings.url + "category";
        if (logRequests) {
            ServerLoggers.importLogger.info(String.format("Log Request to URL %s", requestURL));
        }
        HttpGet getRequest = new HttpGet(requestURL);
        HttpResponse response = executeRequest(getRequest, settings.sessionKey);
        boolean succeeded = requestSucceeded(response);
        String responseMessage = getResponseMessage(response);
        List<Long> categories = new ArrayList<>();
        if (succeeded) {
            JSONArray categoriesArray = new JSONArray(responseMessage);
            ServerLoggers.importLogger.info(String.format("Loya: found %s categories", categoriesArray.length()));
            for (int i = 0; i < categoriesArray.length(); i++) {
                JSONObject category = categoriesArray.getJSONObject(i);
                if(category.getString("state").equals("active")) {
                    categories.add(category.getLong("categoryId"));
                }
            }
        } else {
            context.delayUserInteraction(new MessageClientAction(responseMessage, "Loya: Get Categories Error"));
        }
        return categories;
    }

    private boolean deleteCategory(ExecutionContext context, SettingsLoya settings, Long categoryId, boolean logRequests) throws IOException {
        ServerLoggers.importLogger.info(String.format("Loya: deleting category %s", categoryId));
        String requestURL = settings.url + "category/" + settings.partnerId + "/" + categoryId;
        String requestBody = "[" + categoryId + "]";
        if (logRequests) {
            ServerLoggers.importLogger.info(String.format("Log Request to URL %s: %s", requestURL, requestBody));
        }
        HttpDeleteWithBody request = new HttpDeleteWithBody(requestURL);
        request.setEntity(new StringEntity(requestBody));
        HttpResponse response = executeRequest(request, settings.sessionKey);
        boolean succeeded = requestSucceeded(response);
        if (!succeeded)
            context.delayUserInteraction(new MessageClientAction(getResponseMessage(response), "Loya: Delete Category Error"));
        return succeeded;
    }
}