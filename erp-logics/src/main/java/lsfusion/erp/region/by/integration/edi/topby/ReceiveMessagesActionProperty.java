package lsfusion.erp.region.by.integration.edi.topby;

import com.google.common.base.Throwables;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.integration.*;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;
import org.apache.http.HttpResponse;
import org.apache.xmlbeans.impl.util.Base64;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.jdom.output.XMLOutputter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReceiveMessagesActionProperty extends EDIActionProperty {

    public ReceiveMessagesActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {
            String login = (String) findProperty("loginTopBy[]").read(context); //9999564564541
            String password = (String) findProperty("passwordTopBy[]").read(context); //9u06Av
            String host = (String) findProperty("hostTopBy[]").read(context); //topby.by
            Integer port = (Integer) findProperty("portTopBy[]").read(context); //2011
            if (login != null && password != null && host != null && port != null) {
                receiveMessages(context, login, password, host, port);
            } else {
                ServerLoggers.importLogger.info("ReceiveMessages: не заданы имя пользователя / пароль / хост / порт");
                context.delayUserInteraction(new MessageClientAction("Заказ не выгружен: не заданы имя пользователя / пароль / хост / порт", "Экспорт"));
            }

        } catch (ScriptingErrorLog.SemanticErrorException | IOException | JDOMException e) {
            Throwables.propagate(e);
        }
    }

    private void receiveMessages(ExecutionContext context, String login, String password, String host, int port)
            throws IOException, JDOMException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        Element rootElement = new Element("Envelope", soapenvNamespace);
        rootElement.setNamespace(soapenvNamespace);
        rootElement.addNamespaceDeclaration(soapenvNamespace);
        rootElement.addNamespaceDeclaration(topNamespace);

        Document doc = new Document(rootElement);
        doc.setRootElement(rootElement);

        //parent: rootElement
        Element headerElement = new Element("Header", soapenvNamespace);
        rootElement.addContent(headerElement);

        //parent: rootElement
        Element bodyElement = new Element("Body", soapenvNamespace);
        rootElement.addContent(bodyElement);

        //parent: bodyElement
        Element sendDocumentElement = new Element("GetDocuments", topNamespace);
        bodyElement.addContent(sendDocumentElement);

        addStringElement(topNamespace, sendDocumentElement, "username", login);
        addStringElement(topNamespace, sendDocumentElement, "password", password);

        String url = String.format("http://%s:%s/DmcService", host, port);

        String xml = new XMLOutputter().outputString(doc);
        HttpResponse httpResponse = sendRequest(host, port, login, password, url, xml, null);
        ServerLoggers.importLogger.info("ReceiveMessages %s request sent");
        String responseMessage = getResponseMessage(httpResponse);
        RequestResult requestResult = getRequestResult(httpResponse, responseMessage, "ReceiveMessages");
        switch (requestResult) {
            case OK:
                importMessages(context, login, password, host, port, responseMessage);
                break;
            case AUTHORISATION_ERROR:
                ServerLoggers.importLogger.error("ReceiveMessages %s: invalid login-password");
                context.delayUserInteraction(new MessageClientAction("Заказ %s не выгружен: ошибка авторизации", "Экспорт"));
                break;
            case UNKNOWN_ERROR:
                ServerLoggers.importLogger.error("ReceiveMessages %s: unknown error");
                context.delayUserInteraction(new MessageClientAction("Заказ %s не выгружен: неизвестная ошибка", "Экспорт"));
        }
    }

    private void importMessages(ExecutionContext context, String login, String password, String host, Integer port, String responseMessage) throws JDOMException, IOException, ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<String> succeededList = new ArrayList<>();
        List<List<Object>> dataMessage = new ArrayList<>();
        List<List<Object>> dataOrderResponse = new ArrayList<>();
        int countOrderResponse = 0;
        List<List<Object>> dataDespatchAdvice = new ArrayList<>();
        int countDespatchAdvice = 0;

        SAXBuilder builder = new SAXBuilder();
        Document document = builder.build(new ByteArrayInputStream(responseMessage.getBytes("utf-8")));
        Element rootNode = document.getRootElement();
        Namespace ns = rootNode.getNamespace();
        if (ns != null) {
            Element body = rootNode.getChild("Body", ns);
            if (body != null) {
                Element response = body.getChild("GetDocumentsResponse", topNamespace);
                if (response != null) {
                    Element result = response.getChild("GetDocumentsResult", topNamespace);
                    if (result != null) {
                        String successful = result.getChildText("Succesful", topNamespace);
                        if (successful != null && Boolean.parseBoolean(successful)) {

                            Element dataElement = result.getChild("Data", topNamespace);
                            for (Object documentDataObject : dataElement.getChildren("DocumentData", topNamespace)) {
                                Element documentData = (Element) documentDataObject;

                                String subXML = new String(Base64.decode(documentData.getChildText("Data", topNamespace).getBytes()));
                                String documentType = documentData.getChildText("DocumentType", topNamespace);
                                String documentId = documentData.getChildText("Id", topNamespace);

                                switch (documentType) {
                                    case "systemMessage":
                                        List<Object> orderMessage = parseOrderMessage(subXML);
                                        if (orderMessage != null)
                                            dataMessage.add(orderMessage);
                                        break;
                                    case "ordrsp": {
                                        List<List<Object>> orderResponse = parseOrderResponse(subXML);
                                        if (orderResponse != null) {
                                            dataOrderResponse.addAll(orderResponse);
                                            countOrderResponse++;
                                        }
                                        break;
                                    }
                                    case "desadv": {
                                        List<List<Object>> despatchAdvice = parseDespatchAdvice(subXML);
                                        if (despatchAdvice != null) {
                                            dataDespatchAdvice.addAll(despatchAdvice);
                                            countDespatchAdvice++;
                                        }
                                        break;
                                    }
                                }
                                succeededList.add(documentId);
                            }
                        }
                    }
                }
            }
        }

        if(!succeededList.isEmpty()) {
            boolean succeeded = importOrderMessages(context, dataMessage);
            if (succeeded)
                succeeded = importOrderResponses(context, dataOrderResponse, countOrderResponse);
            if (succeeded)
                succeeded = importDespatchAdvices(context, dataDespatchAdvice, countDespatchAdvice);
            if (succeeded) {
                for (String documentId : succeededList) {
                    confirmDocumentReceived(context, documentId, login, password, host, port);
                }
            }
        } else {
            context.delayUserInteraction(new MessageClientAction("Не найдено новых сообщений", "Импорт"));
        }
    }

    private List<Object> parseOrderMessage(String message) throws IOException, JDOMException {
        SAXBuilder builder = new SAXBuilder();
        Document document = builder.build(new ByteArrayInputStream(message.getBytes("utf-8")));
        Element rootNode = document.getRootElement();

        String number = rootNode.getChildText("documentNumber");
        Timestamp dateTime = parseTimestamp(rootNode.getChildText("documentDate"));

        Element reference = rootNode.getChild("reference");
        String documentType = reference.getChildText("documentType");
        if (documentType.equals("ORDERS")) {
            String orderNumber = reference.getChildText("documentNumber");
            String code = reference.getChildText("code");
            String description = reference.getChildText("description");
            if (description.isEmpty()) {
                if (code.equals("1251")) {
                    description = "Сообщение прочитано получателем";
                } else if (code.equals("1252")) {
                    description = "Сообщение принято учётной системой получателя";
                }
            }
            return Arrays.asList((Object) number, dateTime, code, description, orderNumber);
        } else return null;
    }

    private boolean importOrderMessages(ExecutionContext context, List<List<Object>> data) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        boolean result = true;
        if (!data.isEmpty()) {
            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            ImportField numberEOrderMessage = new ImportField(findProperty("number[EOrderMessage]"));
            ImportKey<?> eOrderMessageKey = new ImportKey((CustomClass) findClass("EOrderMessage"),
                    findProperty("eOrderMessage[VARSTRING[24]]").getMapping(numberEOrderMessage));
            keys.add(eOrderMessageKey);
            props.add(new ImportProperty(numberEOrderMessage, findProperty("number[EOrderMessage]").getMapping(eOrderMessageKey)));
            fields.add(numberEOrderMessage);

            ImportField dateTimeEOrderMessage = new ImportField(findProperty("dateTime[EOrderMessage]"));
            props.add(new ImportProperty(dateTimeEOrderMessage, findProperty("dateTime[EOrderMessage]").getMapping(eOrderMessageKey)));
            fields.add(dateTimeEOrderMessage);

            ImportField codeEOrderMessage = new ImportField(findProperty("code[EOrderMessage]"));
            props.add(new ImportProperty(codeEOrderMessage, findProperty("code[EOrderMessage]").getMapping(eOrderMessageKey)));
            fields.add(codeEOrderMessage);

            ImportField descriptionEOrderMessage = new ImportField(findProperty("description[EOrderMessage]"));
            props.add(new ImportProperty(descriptionEOrderMessage, findProperty("description[EOrderMessage]").getMapping(eOrderMessageKey)));
            fields.add(descriptionEOrderMessage);

            ImportField numberEOrderField = new ImportField(findProperty("number[EOrder]"));
            ImportKey<?> eOrderKey = new ImportKey((CustomClass) findClass("EOrder"),
                    findProperty("eOrder[VARSTRING[28]]").getMapping(numberEOrderField));
            eOrderKey.skipKey = true;
            keys.add(eOrderKey);
            props.add(new ImportProperty(numberEOrderField, findProperty("eOrder[EOrderMessage]").getMapping(eOrderMessageKey),
                    object(findClass("EOrder")).getMapping(eOrderKey)));
            fields.add(numberEOrderField);

            ImportTable table = new ImportTable(fields, data);

            try (DataSession session = context.createSession()) {
                session.pushVolatileStats("EDI_OM");
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                String message = session.applyMessage(context);
                session.popVolatileStats();

                if (message == null) {
                    ServerLoggers.importLogger.info(String.format("Import %s EOrderMessages succeeded", data.size()));
                    context.delayUserInteraction(new MessageClientAction(String.format("Загружено сообщений: %s", data.size()), "Импорт"));
                } else {
                    ServerLoggers.importLogger.info("Import EOrderMessages error: " + message);
                    context.delayUserInteraction(new MessageClientAction(message, "Ошибка"));
                    result = false;
                }
            }
        }
        return result;
    }

    private List<List<Object>> parseOrderResponse(String orderResponse) throws IOException, JDOMException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        List<List<Object>> result = new ArrayList<>();
        SAXBuilder builder = new SAXBuilder();
        Document document = builder.build(new ByteArrayInputStream(orderResponse.getBytes("utf-8")));
        Element rootNode = document.getRootElement();

        String number = rootNode.getChildText("documentNumber");
        Timestamp dateTime = parseTimestamp(rootNode.getChildText("documentDate"));
        String responseType = rootNode.getChildText("function");
        String responseTypeObject = getResponseType(responseType);
        String buyerGLN = rootNode.getChildText("buyerGLN");
        String destinationGLN = rootNode.getChildText("destinationGLN");
        String supplierGLN = rootNode.getChildText("supplierGLN");
        String orderNumber = rootNode.getChildText("orderNumber");
        Timestamp deliveryDateTimeSecond = parseTimestamp(rootNode.getChildText("deliveryDateTimeSecond"));

        int i = 1;
        for (Object line : rootNode.getChildren("line")) {
            Element lineElement = (Element) line;
            String barcode = lineElement.getChildText("GTIN");
            String id = number + "/" + i++;
            String action = lineElement.getChildText("action");
            String actionObject = getAction(action);
            BigDecimal quantityOrdered = parseBigDecimal(lineElement.getChildText("quantityOrdered"));
            BigDecimal quantityAccepted = parseBigDecimal(lineElement.getChildText("quantityAccepted"));
            BigDecimal priceNoNDS = parseBigDecimal(lineElement.getChildText("priceNoNDS"));
            BigDecimal priceNDS = parseBigDecimal(lineElement.getChildText("priceNDS"));

            result.add(Arrays.<Object>asList(number, dateTime, responseTypeObject, supplierGLN, buyerGLN, destinationGLN, orderNumber,
                    deliveryDateTimeSecond, id, barcode, actionObject, quantityOrdered, quantityAccepted, priceNoNDS, priceNDS));
        }
        return result;
    }

    private String getResponseType(String id) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        String value = id == null ? null : id.equals("4") ? "changed" : id.equals("27") ? "cancelled" : id.equals("29") ? "accepted" : null;
        return value == null ? null : ("EDI_EOrderResponseType." + value.toLowerCase());
    }

    private String getAction(String id) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        String value = id == null ? null : id.equals("1") ? "added" : id.equals("3") ? "changed" : id.equals("5") ? "accepted" : id.equals("7") ? "cancelled" : null;
        return value == null ? null : ("EDI_EOrderResponseDetailAction." + value.toLowerCase());
    }

    private boolean importOrderResponses(ExecutionContext context, List<List<Object>> data, int count) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        boolean result = true;
        if (!data.isEmpty()) {
            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            ImportField numberEOrderResponseField = new ImportField(findProperty("number[EOrderResponse]"));
            ImportKey<?> eOrderResponseKey = new ImportKey((CustomClass) findClass("EOrderResponse"),
                    findProperty("eOrderResponse[VARSTRING[24]]").getMapping(numberEOrderResponseField));
            keys.add(eOrderResponseKey);
            props.add(new ImportProperty(numberEOrderResponseField, findProperty("number[EOrderResponse]").getMapping(eOrderResponseKey)));
            fields.add(numberEOrderResponseField);

            ImportField dateTimeEOrderResponseField = new ImportField(findProperty("dateTime[EOrderResponse]"));
            props.add(new ImportProperty(dateTimeEOrderResponseField, findProperty("dateTime[EOrderResponse]").getMapping(eOrderResponseKey)));
            fields.add(dateTimeEOrderResponseField);

            ImportField responseTypeField = new ImportField(findProperty("staticName[Object]"));
            ImportKey<?> responseTypeKey = new ImportKey((CustomClass) findClass("EOrderResponseType"),
                    findProperty("nameStatic[STRING[250]]").getMapping(responseTypeField));
            keys.add(responseTypeKey);
            props.add(new ImportProperty(responseTypeField, findProperty("responseType[EOrderResponse]").getMapping(eOrderResponseKey),
                    object(findClass("EOrderResponseType")).getMapping(responseTypeKey)));
            fields.add(responseTypeField);

            ImportField GLNSupplierEOrderResponseField = new ImportField(findProperty("GLN[LegalEntity]"));
            ImportKey<?> supplierKey = new ImportKey((CustomClass) findClass("LegalEntity"),
                    findProperty("legalEntityGLN[VARSTRING[13]]").getMapping(GLNSupplierEOrderResponseField));
            supplierKey.skipKey = true;
            keys.add(supplierKey);
            props.add(new ImportProperty(GLNSupplierEOrderResponseField, findProperty("supplier[EOrderResponse]").getMapping(eOrderResponseKey),
                    object(findClass("LegalEntity")).getMapping(supplierKey)));
            fields.add(GLNSupplierEOrderResponseField);

            ImportField GLNCustomerEOrderResponseField = new ImportField(findProperty("GLN[LegalEntity]"));
            ImportKey<?> customerKey = new ImportKey((CustomClass) findClass("LegalEntity"),
                    findProperty("legalEntityGLN[VARSTRING[13]]").getMapping(GLNCustomerEOrderResponseField));
            customerKey.skipKey = true;
            keys.add(customerKey);
            props.add(new ImportProperty(GLNCustomerEOrderResponseField, findProperty("customer[EOrderResponse]").getMapping(eOrderResponseKey),
                    object(findClass("LegalEntity")).getMapping(customerKey)));
            fields.add(GLNCustomerEOrderResponseField);

            ImportField GLNCustomerStockEOrderResponseField = new ImportField(findProperty("GLN[Stock]"));
            ImportKey<?> customerStockKey = new ImportKey((CustomClass) findClass("Stock"),
                    findProperty("stockGLN[VARSTRING[13]]").getMapping(GLNCustomerStockEOrderResponseField));
            customerStockKey.skipKey = true;
            keys.add(customerStockKey);
            props.add(new ImportProperty(GLNCustomerStockEOrderResponseField, findProperty("customerStock[EOrderResponse]").getMapping(eOrderResponseKey),
                    object(findClass("Stock")).getMapping(customerStockKey)));
            fields.add(GLNCustomerStockEOrderResponseField);

            ImportField numberEOrderField = new ImportField(findProperty("number[EOrder]"));
            ImportKey<?> eOrderKey = new ImportKey((CustomClass) findClass("EOrder"),
                    findProperty("eOrder[VARSTRING[28]]").getMapping(numberEOrderField));
            eOrderKey.skipKey = true;
            keys.add(eOrderKey);
            props.add(new ImportProperty(numberEOrderField, findProperty("eOrder[EOrderResponse]").getMapping(eOrderResponseKey),
                    object(findClass("EOrder")).getMapping(eOrderKey)));
            fields.add(numberEOrderField);

            ImportField deliveryDateTimeEOrderResponseField = new ImportField(findProperty("deliveryDateTime[EOrderResponse]"));
            props.add(new ImportProperty(deliveryDateTimeEOrderResponseField, findProperty("deliveryDateTime[EOrderResponse]").getMapping(eOrderResponseKey)));
            fields.add(deliveryDateTimeEOrderResponseField);

            ImportField idEOrderResponseDetailField = new ImportField(findProperty("id[EOrderResponseDetail]"));
            ImportKey<?> eOrderResponseDetailKey = new ImportKey((CustomClass) findClass("EOrderResponseDetail"),
                    findProperty("eOrderResponseDetail[VARSTRING[100]]").getMapping(idEOrderResponseDetailField));
            keys.add(eOrderResponseDetailKey);
            props.add(new ImportProperty(numberEOrderField, findProperty("orderResponse[EOrderResponseDetail]").getMapping(eOrderResponseDetailKey),
                    object(findClass("EOrderResponse")).getMapping(eOrderResponseKey)));
            props.add(new ImportProperty(idEOrderResponseDetailField, findProperty("id[EOrderResponseDetail]").getMapping(eOrderResponseDetailKey)));
            fields.add(idEOrderResponseDetailField);


            ImportField barcodeEOrderResponseDetailField = new ImportField(findProperty("id[Barcode]"));
            ImportKey<?> skuKey = new ImportKey((CustomClass) findClass("Sku"),
                    findProperty("skuBarcode[VARSTRING[15]]").getMapping(barcodeEOrderResponseDetailField));
            skuKey.skipKey = true;
            keys.add(skuKey);
            props.add(new ImportProperty(barcodeEOrderResponseDetailField, findProperty("sku[EOrderResponseDetail]").getMapping(eOrderResponseDetailKey),
                    object(findClass("Sku")).getMapping(skuKey)));
            fields.add(barcodeEOrderResponseDetailField);

            ImportField actionField = new ImportField(findProperty("staticName[Object]"));
            ImportKey<?> actionKey = new ImportKey((CustomClass) findClass("EOrderResponseDetailAction"),
                    findProperty("nameStatic[STRING[250]]").getMapping(actionField));
            keys.add(actionKey);
            props.add(new ImportProperty(actionField, findProperty("action[EOrderResponseDetail]").getMapping(eOrderResponseDetailKey),
                    object(findClass("EOrderResponseDetailAction")).getMapping(actionKey)));
            fields.add(actionField);

            ImportField quantityOrderedEOrderResponseDetailField = new ImportField(findProperty("quantityOrdered[EOrderResponseDetail]"));
            props.add(new ImportProperty(quantityOrderedEOrderResponseDetailField, findProperty("quantityOrdered[EOrderResponseDetail]").getMapping(eOrderResponseDetailKey)));
            fields.add(quantityOrderedEOrderResponseDetailField);

            ImportField quantityAcceptedEOrderResponseDetailField = new ImportField(findProperty("quantityAccepted[EOrderResponseDetail]"));
            props.add(new ImportProperty(quantityAcceptedEOrderResponseDetailField, findProperty("quantityAccepted[EOrderResponseDetail]").getMapping(eOrderResponseDetailKey)));
            fields.add(quantityAcceptedEOrderResponseDetailField);

            ImportField priceNoNDSEOrderResponseDetailField = new ImportField(findProperty("priceNoNDS[EOrderResponseDetail]"));
            props.add(new ImportProperty(priceNoNDSEOrderResponseDetailField, findProperty("priceNoNDS[EOrderResponseDetail]").getMapping(eOrderResponseDetailKey)));
            fields.add(priceNoNDSEOrderResponseDetailField);

            ImportField priceNDSEOrderResponseDetailField = new ImportField(findProperty("priceNDS[EOrderResponseDetail]"));
            props.add(new ImportProperty(priceNDSEOrderResponseDetailField, findProperty("priceNDS[EOrderResponseDetail]").getMapping(eOrderResponseDetailKey)));
            fields.add(priceNDSEOrderResponseDetailField);

            ImportTable table = new ImportTable(fields, data);

            try (DataSession session = context.createSession()) {
                session.pushVolatileStats("EDI_OR");
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                String message = session.applyMessage(context);
                session.popVolatileStats();

                if (message == null) {
                    ServerLoggers.importLogger.info(String.format("Import %s EOrderResponses succeeded", count));
                    context.delayUserInteraction(new MessageClientAction(String.format("Загружено ответов по заказам: %s", count), "Импорт"));
                } else {
                    ServerLoggers.importLogger.info("Import EOrderResponses error: " + message);
                    context.delayUserInteraction(new MessageClientAction(message, "Ошибка"));
                    result = false;
                }
            }
        }
        return result;
    }

    private List<List<Object>> parseDespatchAdvice(String orderResponse) throws IOException, JDOMException, ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        List<List<Object>> result = new ArrayList<>();
        SAXBuilder builder = new SAXBuilder();
        Document document = builder.build(new ByteArrayInputStream(orderResponse.getBytes("utf-8")));
        Element rootNode = document.getRootElement();

        String number = rootNode.getChildText("documentNumber");
        Timestamp dateTime = parseTimestamp(rootNode.getChildText("documentDate"));
        String deliveryNoteNumber = rootNode.getChildText("deliveryNoteNumber");
        Timestamp deliveryNoteDateTime = parseTimestamp(rootNode.getChildText("deliveryNoteDate"));
        String buyerGLN = rootNode.getChildText("buyerGLN");
        String destinationGLN = rootNode.getChildText("destinationGLN");
        String supplierGLN = rootNode.getChildText("supplierGLN");
        String orderNumber = rootNode.getChildText("orderNumber");
        Timestamp deliveryDateTimeFirst = parseTimestamp(rootNode.getChildText("deliveryDateTimeFirst"));

        int i = 1;
        for (Object line : rootNode.getChildren("line")) {
            Element lineElement = (Element) line;
            String barcode = lineElement.getChildText("GTIN");
            String id = number + "/" + i++;
            BigDecimal quantityOrdered = parseBigDecimal(lineElement.getChildText("quantityOrdered"));
            BigDecimal quantityDespatch = parseBigDecimal(lineElement.getChildText("quantityDespatch"));
            BigDecimal lineItemPrice = parseBigDecimal(lineElement.getChildText("lineItemPrice"));
            BigDecimal lineItemAmountWithoutCharges = parseBigDecimal(lineElement.getChildText("lineItemAmountWithoutCharges"));
            result.add(Arrays.<Object>asList(number, dateTime, deliveryNoteNumber, deliveryNoteDateTime, supplierGLN, buyerGLN, destinationGLN, orderNumber,
                    deliveryDateTimeFirst, id, barcode, quantityOrdered, quantityDespatch, lineItemPrice, lineItemAmountWithoutCharges));
        }
        return result;
    }

    private boolean importDespatchAdvices(ExecutionContext context, List<List<Object>> data, int count) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        boolean result = true;
        if (!data.isEmpty()) {
            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            ImportField numberEOrderDespatchAdviceField = new ImportField(findProperty("number[EOrderDespatchAdvice]"));
            ImportKey<?> eOrderDespatchAdviceKey = new ImportKey((CustomClass) findClass("EOrderDespatchAdvice"),
                    findProperty("eOrderDespatchAdvice[VARSTRING[24]]").getMapping(numberEOrderDespatchAdviceField));
            keys.add(eOrderDespatchAdviceKey);
            props.add(new ImportProperty(numberEOrderDespatchAdviceField, findProperty("number[EOrderDespatchAdvice]").getMapping(eOrderDespatchAdviceKey)));
            fields.add(numberEOrderDespatchAdviceField);

            ImportField dateTimeEOrderDespatchAdviceField = new ImportField(findProperty("dateTime[EOrderDespatchAdvice]"));
            props.add(new ImportProperty(dateTimeEOrderDespatchAdviceField, findProperty("dateTime[EOrderDespatchAdvice]").getMapping(eOrderDespatchAdviceKey)));
            fields.add(dateTimeEOrderDespatchAdviceField);

            ImportField deliveryNoteNumberEOrderDespatchAdviceField = new ImportField(findProperty("deliveryNoteNumber[EOrderDespatchAdvice]"));
            props.add(new ImportProperty(deliveryNoteNumberEOrderDespatchAdviceField, findProperty("deliveryNoteNumber[EOrderDespatchAdvice]").getMapping(eOrderDespatchAdviceKey)));
            fields.add(deliveryNoteNumberEOrderDespatchAdviceField);

            ImportField deliveryNoteDateTimeEOrderDespatchAdviceField = new ImportField(findProperty("deliveryNoteDateTime[EOrderDespatchAdvice]"));
            props.add(new ImportProperty(deliveryNoteDateTimeEOrderDespatchAdviceField, findProperty("deliveryNoteDateTime[EOrderDespatchAdvice]").getMapping(eOrderDespatchAdviceKey)));
            fields.add(deliveryNoteDateTimeEOrderDespatchAdviceField);

            ImportField GLNSupplierEOrderDespatchAdviceField = new ImportField(findProperty("GLN[LegalEntity]"));
            ImportKey<?> supplierKey = new ImportKey((CustomClass) findClass("LegalEntity"),
                    findProperty("legalEntityGLN[VARSTRING[13]]").getMapping(GLNSupplierEOrderDespatchAdviceField));
            supplierKey.skipKey = true;
            keys.add(supplierKey);
            props.add(new ImportProperty(GLNSupplierEOrderDespatchAdviceField, findProperty("supplier[EOrderDespatchAdvice]").getMapping(eOrderDespatchAdviceKey),
                    object(findClass("LegalEntity")).getMapping(supplierKey)));
            fields.add(GLNSupplierEOrderDespatchAdviceField);

            ImportField GLNCustomerEOrderDespatchAdviceField = new ImportField(findProperty("GLN[LegalEntity]"));
            ImportKey<?> customerKey = new ImportKey((CustomClass) findClass("LegalEntity"),
                    findProperty("legalEntityGLN[VARSTRING[13]]").getMapping(GLNCustomerEOrderDespatchAdviceField));
            customerKey.skipKey = true;
            keys.add(customerKey);
            props.add(new ImportProperty(GLNCustomerEOrderDespatchAdviceField, findProperty("customer[EOrderDespatchAdvice]").getMapping(eOrderDespatchAdviceKey),
                    object(findClass("LegalEntity")).getMapping(customerKey)));
            fields.add(GLNCustomerEOrderDespatchAdviceField);

            ImportField GLNCustomerStockEOrderDespatchAdviceField = new ImportField(findProperty("GLN[Stock]"));
            ImportKey<?> customerStockKey = new ImportKey((CustomClass) findClass("Stock"),
                    findProperty("stockGLN[VARSTRING[13]]").getMapping(GLNCustomerStockEOrderDespatchAdviceField));
            customerStockKey.skipKey = true;
            keys.add(customerStockKey);
            props.add(new ImportProperty(GLNCustomerStockEOrderDespatchAdviceField, findProperty("customerStock[EOrderDespatchAdvice]").getMapping(eOrderDespatchAdviceKey),
                    object(findClass("Stock")).getMapping(customerStockKey)));
            fields.add(GLNCustomerStockEOrderDespatchAdviceField);

            ImportField numberEOrderField = new ImportField(findProperty("number[EOrder]"));
            ImportKey<?> eOrderKey = new ImportKey((CustomClass) findClass("EOrder"),
                    findProperty("eOrder[VARSTRING[28]]").getMapping(numberEOrderField));
            eOrderKey.skipKey = true;
            keys.add(eOrderKey);
            props.add(new ImportProperty(numberEOrderField, findProperty("eOrder[EOrderDespatchAdvice]").getMapping(eOrderDespatchAdviceKey),
                    object(findClass("EOrder")).getMapping(eOrderKey)));
            fields.add(numberEOrderField);

            ImportField deliveryDateTimeEOrderDespatchAdviceField = new ImportField(findProperty("deliveryDateTime[EOrderDespatchAdvice]"));
            props.add(new ImportProperty(deliveryDateTimeEOrderDespatchAdviceField, findProperty("deliveryDateTime[EOrderDespatchAdvice]").getMapping(eOrderDespatchAdviceKey)));
            fields.add(deliveryDateTimeEOrderDespatchAdviceField);

            ImportField idEOrderDespatchAdviceDetailField = new ImportField(findProperty("id[EOrderDespatchAdviceDetail]"));
            ImportKey<?> eOrderDespatchAdviceDetailKey = new ImportKey((CustomClass) findClass("EOrderDespatchAdviceDetail"),
                    findProperty("eOrderDespatchAdviceDetail[VARSTRING[100]]").getMapping(idEOrderDespatchAdviceDetailField));
            keys.add(eOrderDespatchAdviceDetailKey);
            props.add(new ImportProperty(numberEOrderField, findProperty("orderDespatchAdvice[EOrderDespatchAdviceDetail]").getMapping(eOrderDespatchAdviceDetailKey),
                    object(findClass("EOrderDespatchAdvice")).getMapping(eOrderDespatchAdviceKey)));
            props.add(new ImportProperty(idEOrderDespatchAdviceDetailField, findProperty("id[EOrderDespatchAdviceDetail]").getMapping(eOrderDespatchAdviceDetailKey)));
            fields.add(idEOrderDespatchAdviceDetailField);

            ImportField barcodeEOrderDespatchAdviceDetailField = new ImportField(findProperty("id[Barcode]"));
            ImportKey<?> skuKey = new ImportKey((CustomClass) findClass("Sku"),
                    findProperty("skuBarcode[VARSTRING[15]]").getMapping(barcodeEOrderDespatchAdviceDetailField));
            skuKey.skipKey = true;
            keys.add(skuKey);
            props.add(new ImportProperty(barcodeEOrderDespatchAdviceDetailField, findProperty("sku[EOrderDespatchAdviceDetail]").getMapping(eOrderDespatchAdviceDetailKey),
                    object(findClass("Sku")).getMapping(skuKey)));
            fields.add(barcodeEOrderDespatchAdviceDetailField);

            ImportField quantityOrderedEOrderDespatchAdviceDetailField = new ImportField(findProperty("quantityOrdered[EOrderDespatchAdviceDetail]"));
            props.add(new ImportProperty(quantityOrderedEOrderDespatchAdviceDetailField, findProperty("quantityOrdered[EOrderDespatchAdviceDetail]").getMapping(eOrderDespatchAdviceDetailKey)));
            fields.add(quantityOrderedEOrderDespatchAdviceDetailField);

            ImportField quantityDespatchEOrderDespatchAdviceDetailField = new ImportField(findProperty("quantityDespatch[EOrderDespatchAdviceDetail]"));
            props.add(new ImportProperty(quantityDespatchEOrderDespatchAdviceDetailField, findProperty("quantityDespatch[EOrderDespatchAdviceDetail]").getMapping(eOrderDespatchAdviceDetailKey)));
            fields.add(quantityDespatchEOrderDespatchAdviceDetailField);

            ImportField lineItemPriceEOrderDespatchAdviceDetailField = new ImportField(findProperty("lineItemPrice[EOrderDespatchAdviceDetail]"));
            props.add(new ImportProperty(lineItemPriceEOrderDespatchAdviceDetailField, findProperty("lineItemPrice[EOrderDespatchAdviceDetail]").getMapping(eOrderDespatchAdviceDetailKey)));
            fields.add(lineItemPriceEOrderDespatchAdviceDetailField);

            ImportField lineItemAmountWithoutChargesEOrderDespatchAdviceDetailField = new ImportField(findProperty("lineItemAmountWithoutCharges[EOrderDespatchAdviceDetail]"));
            props.add(new ImportProperty(lineItemAmountWithoutChargesEOrderDespatchAdviceDetailField, findProperty("lineItemAmountWithoutCharges[EOrderDespatchAdviceDetail]").getMapping(eOrderDespatchAdviceDetailKey)));
            fields.add(lineItemAmountWithoutChargesEOrderDespatchAdviceDetailField);

            ImportTable table = new ImportTable(fields, data);

            try (DataSession session = context.createSession()) {
                session.pushVolatileStats("EDI_DA");
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                String message = session.applyMessage(context);
                session.popVolatileStats();

                if (message == null) {
                    ServerLoggers.importLogger.info(String.format("Import %s EOrderDespatchAdvices succeeded", count));
                    context.delayUserInteraction(new MessageClientAction(String.format("Загружено уведомлений об отгрузке: %s", count), "Импорт"));
                } else {
                    ServerLoggers.importLogger.info("Import EOrderDespatchAdvices error: " + message);
                    context.delayUserInteraction(new MessageClientAction(message, "Ошибка"));
                    result = false;
                }
            }
        }
        return result;
    }

    private void confirmDocumentReceived(ExecutionContext context, String documentId, String login, String password,
                                          String host, Integer port) throws IOException, JDOMException {

        Element rootElement = new Element("Envelope", soapenvNamespace);
        rootElement.setNamespace(soapenvNamespace);
        rootElement.addNamespaceDeclaration(soapenvNamespace);
        rootElement.addNamespaceDeclaration(topNamespace);

        Document doc = new Document(rootElement);
        doc.setRootElement(rootElement);

        //parent: rootElement
        Element headerElement = new Element("Header", soapenvNamespace);
        rootElement.addContent(headerElement);

        //parent: rootElement
        Element bodyElement = new Element("Body", soapenvNamespace);
        rootElement.addContent(bodyElement);

        //parent: bodyElement
        Element confirmDocumentReceivedElement = new Element("ConfirmDocumentReceived", topNamespace);
        bodyElement.addContent(confirmDocumentReceivedElement);

        addStringElement(topNamespace, confirmDocumentReceivedElement, "username", login);
        addStringElement(topNamespace, confirmDocumentReceivedElement, "password", password);
        addStringElement(topNamespace, confirmDocumentReceivedElement, "documentId", documentId);

        String url = String.format("http://%s:%s/DmcService", host, port);

        String xml = new XMLOutputter().outputString(doc);
        HttpResponse httpResponse = sendRequest(host, port, login, password, url, xml, null);
        ServerLoggers.importLogger.info(String.format("ConfirmDocumentReceived request sent for document %s", documentId));
        RequestResult requestResult = getRequestResult(httpResponse, getResponseMessage(httpResponse), "ConfirmDocumentReceived");
        switch (requestResult) {
            case OK:
                ServerLoggers.importLogger.info(String.format("ConfirmDocumentReceived document %s: request succeeded", documentId));
                break;
            case AUTHORISATION_ERROR:
                ServerLoggers.importLogger.error(String.format("ConfirmDocumentReceived document %s: invalid login-password", documentId));
                context.delayUserInteraction(new MessageClientAction(String.format("Документ %s не помечен как обработанный: ошибка авторизации", documentId), "Экспорт"));
                break;
            case UNKNOWN_ERROR:
                ServerLoggers.importLogger.error(String.format("ConfirmDocumentReceived document %s: unknown error", documentId));
                context.delayUserInteraction(new MessageClientAction(String.format("Документ %s не помечен как обработанный", documentId), "Экспорт"));
        }
    }

    private Timestamp parseTimestamp(String value) {
        try {
            return new Timestamp(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(value).getTime());
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parseBigDecimal(String value) {
        return value == null || value.isEmpty() ? null : new BigDecimal(value);
    }
}
