package lsfusion.erp.region.by.machinery.cashregister.fiscalepson;

import java.io.Serializable;

public class PrintReceiptResult implements Serializable {
    Integer documentNumber;
    Integer receiptNumber;
    Integer electronicJournalReadOffset;
    Integer electronicJournalReadSize;
    Integer sessionNumber;
    String error;

    public PrintReceiptResult(String error) {
        this.error = error;
    }

    public PrintReceiptResult(Integer documentNumber, Integer receiptNumber, Integer electronicJournalReadOffset, Integer electronicJournalReadSize, Integer sessionNumber) {
        this.documentNumber = documentNumber;
        this.receiptNumber = receiptNumber;
        this.electronicJournalReadOffset = electronicJournalReadOffset;
        this.electronicJournalReadSize = electronicJournalReadSize;
        this.sessionNumber = sessionNumber;
    }
}