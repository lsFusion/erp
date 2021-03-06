package equ.srv;

import com.google.common.base.Throwables;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.action.controller.stack.ExecutionStack;
import lsfusion.server.logics.action.session.DataSession;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;

import java.time.LocalDateTime;

public class ProcessMonitorEquipmentServer {

    static ScriptingLogicsModule equLM;

    public static void init(BusinessLogics BL) {
        equLM = BL.getModule("Equipment");
    }

    public static boolean needUpdateProcessMonitor(EquipmentServer server, String sidEquipmentServer) {
        try (DataSession session = server.createSession()) {
            return equLM.findProperty("needLogProcesses[STRING[20]]").read(session, new DataObject(sidEquipmentServer)) != null;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public static void logProcesses(BusinessLogics BL, EquipmentServer server, ExecutionStack stack, String sidEquipmentServer, String data) {

        try (DataSession session = server.createSession()) {
            ObjectValue equipmentServerObject = equLM.findProperty("sidTo[STRING[20]]").readClasses(session, new DataObject(sidEquipmentServer));
            if(equipmentServerObject instanceof DataObject) {
                DataObject logObject = session.addObject((ConcreteCustomClass) equLM.findClass("EquipmentServerLog"));
                equLM.findProperty("equipmentServer[EquipmentServerLog]").change(equipmentServerObject.getValue(), session, logObject);
                equLM.findProperty("data[EquipmentServerLog]").change(data, session, logObject);
                equLM.findProperty("date[EquipmentServerLog]").change(LocalDateTime.now(), session, logObject);

                equLM.findProperty("needLogProcesses[EquipmentServer]").change((Object) null, session, (DataObject) equipmentServerObject);
                session.applyException(BL, stack);
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

}