package equ.api;

import equ.api.cashregister.CashRegisterInfo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

public class SalesInfo implements Serializable {
    //sumCashEnd, sumProtectedEnd, sumBack, externalSum
    public Map<String, Object> zReportExtraFields;
    //bonusSum, bonusPaid, idBatch
    public Map<String, Object> detailExtraFields;
    public boolean isGiftCard;
    public boolean isReturnGiftCard;
    public Integer nppGroupMachinery;
    public Integer nppMachinery;
    public String numberZReport;
    public LocalDate dateZReport;
    public LocalTime timeZReport;
    public Integer numberReceipt;
    public LocalDate dateReceipt;
    public LocalTime timeReceipt;
    public String idEmployee;
    public String firstNameContact;
    public String lastNameContact;
    public BigDecimal sumCard;
    public BigDecimal sumCash;
    public Map<String, GiftCard> sumGiftCardMap;
    public Map<String, BigDecimal> customPaymentMap;
    public String barcodeItem;
    public String idItem;
    public Long itemObject;
    public String idSaleReceiptReceiptReturnDetail;
    public BigDecimal quantityReceiptDetail;
    public BigDecimal priceReceiptDetail;
    public BigDecimal sumReceiptDetail;
    public BigDecimal discountPercentReceiptDetail;
    public BigDecimal discountSumReceiptDetail;
    public BigDecimal discountSumReceipt;
    public String seriesNumberDiscountCard;
    public Integer numberReceiptDetail;
    public String filename;
    public String idSection;
    public boolean skipReceipt;
    public Map<String, Object> receiptDetailExtraFields;
    public CashRegisterInfo cashRegisterInfo;

    public SalesInfo(boolean isGiftCard, boolean isReturnGiftCard, Integer nppGroupMachinery, Integer nppMachinery, String numberZReport, LocalDate dateZReport,
                     LocalTime timeZReport, Integer numberReceipt, LocalDate dateReceipt, LocalTime timeReceipt, String idEmployee, String firstNameContact,
                     String lastNameContact, BigDecimal sumCard, BigDecimal sumCash, Map<String, GiftCard> sumGiftCardMap, Map<String, BigDecimal> customPaymentMap,
                     String barcodeItem, String idItem, Long itemObject, String idSaleReceiptReceiptReturnDetail, BigDecimal quantityReceiptDetail,
                     BigDecimal priceReceiptDetail, BigDecimal sumReceiptDetail, BigDecimal discountPercentReceiptDetail, BigDecimal discountSumReceiptDetail,
                     BigDecimal discountSumReceipt, String seriesNumberDiscountCard, Integer numberReceiptDetail, String filename,
                     String idSection, boolean skipReceipt, Map<String, Object> receiptDetailExtraFields, CashRegisterInfo cashRegisterInfo) {
        this.isGiftCard = isGiftCard;
        this.isReturnGiftCard = isReturnGiftCard;
        this.nppGroupMachinery = nppGroupMachinery;
        this.nppMachinery = nppMachinery;
        this.numberZReport = numberZReport;
        this.dateZReport = dateZReport;
        this.timeZReport = timeZReport;
        this.numberReceipt = numberReceipt;
        this.dateReceipt = dateReceipt;
        this.timeReceipt = timeReceipt;
        this.idEmployee = idEmployee;
        this.firstNameContact = firstNameContact;
        this.lastNameContact = lastNameContact;
        this.sumCard = sumCard;
        this.sumCash = sumCash;
        this.sumGiftCardMap = sumGiftCardMap;
        this.customPaymentMap = customPaymentMap;
        this.barcodeItem = barcodeItem;
        this.idItem = idItem;
        this.itemObject = itemObject;
        this.idSaleReceiptReceiptReturnDetail = idSaleReceiptReceiptReturnDetail;
        this.quantityReceiptDetail = quantityReceiptDetail;
        this.priceReceiptDetail = priceReceiptDetail;
        this.sumReceiptDetail = sumReceiptDetail;
        this.discountPercentReceiptDetail = discountPercentReceiptDetail;
        this.discountSumReceiptDetail = discountSumReceiptDetail;
        this.discountSumReceipt = discountSumReceipt;
        this.seriesNumberDiscountCard = seriesNumberDiscountCard;
        this.numberReceiptDetail = numberReceiptDetail;
        this.filename = filename;
        this.idSection = idSection;
        this.skipReceipt = skipReceipt;
        this.receiptDetailExtraFields = receiptDetailExtraFields;
        this.cashRegisterInfo = cashRegisterInfo;
    }
}
