package lsfusion.erp.region.by.integration.excel;

import jxl.write.WriteException;
import lsfusion.base.Pair;
import lsfusion.base.file.RawFileData;
import lsfusion.server.language.ScriptingLogicsModule;

import java.io.IOException;
import java.util.Arrays;

public class CreateExcelTemplateBanksAction extends CreateExcelTemplateAction {

    public CreateExcelTemplateBanksAction(ScriptingLogicsModule LM) {
        super(LM);

    }

    @Override
    public Pair<String, RawFileData> createFile() throws IOException, WriteException {
        return createFile("importBanksTemplate",
                Arrays.asList("Код банка", "Название", "Адрес", "Отдел банка", "Код МФО", "ЦБУ"),
                Arrays.asList(Arrays.asList("123456789", "Беларусьбанк", "ЛИДА,СОВЕТСКАЯ, 24,231300",
                        "Отделение №500", "153001749", "200")));
    }
}