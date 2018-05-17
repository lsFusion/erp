package equ.api.cashregister;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;

public class CashDocument implements Serializable {
    public String idCashDocument;
    public String numberCashDocument;
    public Date dateCashDocument;
    public Time timeCashDocument;
    public Integer nppGroupMachinery;
    public Integer nppMachinery;
    public String numberZReport;
    public BigDecimal sumCashDocument;
    public String idEmployee;

    public CashDocument(String idCashDocument, String numberCashDocument, Date dateCashDocument, Time timeCashDocument, Integer nppGroupMachinery,
                        Integer nppMachinery, String numberZReport, BigDecimal sumCashDocument) {
        this(idCashDocument, numberCashDocument, dateCashDocument, timeCashDocument, nppGroupMachinery, nppMachinery, numberZReport, sumCashDocument, null);
    }

    public CashDocument(String idCashDocument, String numberCashDocument, Date dateCashDocument, Time timeCashDocument, Integer nppGroupMachinery,
                        Integer nppMachinery, String numberZReport, BigDecimal sumCashDocument, String idEmployee) {
        this.idCashDocument = idCashDocument;
        this.numberCashDocument = numberCashDocument;
        this.dateCashDocument = dateCashDocument;
        this.timeCashDocument = timeCashDocument;
        this.nppGroupMachinery = nppGroupMachinery;
        this.nppMachinery = nppMachinery;
        this.numberZReport = numberZReport;
        this.sumCashDocument = sumCashDocument;
        this.idEmployee = idEmployee;
    }
}
